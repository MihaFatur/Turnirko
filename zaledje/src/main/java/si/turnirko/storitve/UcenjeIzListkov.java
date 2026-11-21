/* Bralnik se uci iz potrjenih listkov (odlocitev lastnika, 19. 11. 2026) - tu se
   zbirajo stevke; ucenje samo je v UcenjeMreze.

   1. Ob potrditvi branja se ista fotografija razreze znova in vsaki stevki se
      pripise vrednost, ki jo je potrdil organizator (samo okenca s stevilkami,
      brez imen). Stevke se shranijo v stevka_listka - tudi tiste, ki jih je mreza
      prebrala prav, ker so prav tako primer pisave iz vasih dvoran. Celica, ki
      jo je organizator popravil, je mocna oznaka (F6.2).
   2. Ce organizator izid tekme pozneje popravi (F6.1), so oznake njenih listkov
      napacne: stevke se izbrisejo in, ce fotografija se obstaja, zapisejo znova z
      novim izidom. Popravek, ki spremeni vec kot NAJVEC_POPRAVLJENIH celic (ali
      posebni izid, drugo stevilo nizov), listek postavi pod vprasaj - verjetno je
      bil potrjen k napacni tekmi in nov izid ni njegov: stevke se samo izbrisejo. */
package si.turnirko.storitve;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Niz;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;

@Service
public class UcenjeIzListkov {

    private static final Logger log = LoggerFactory.getLogger(UcenjeIzListkov.class);

    /* Popravek, ki spremeni najvec toliko celic listka, je popravek branja (prepisana
       stevka, zamenjani tocki); vec pomeni drug listek (F6.1). */
    static final int NAJVEC_POPRAVLJENIH = 2;
    /* Celica rezultata v stolpcu celica (niz * 2 + stolpec za nize). */
    static final int CELICA_REZULTATA = 100;

    private final StevkaListkaRepozitorij stevke;
    private final SlikaBranjaListkaRepozitorij slike;
    private final BranjeListkaRepozitorij branja;
    private final TekmaRepozitorij tekme;
    private final NizRepozitorij nizi;
    private final LokalniBralnikListka bralnik;
    private final VirMrezeStevk vir;
    private final ZlatiNabor zlatiNabor;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;
    /* Zapis stevk listka ob potrditvi in ob popravku izida tecejo v izvajalcu morda
       hkrati (popravek takoj po potrditvi): zapis pod zaklepom, da se ne podvojijo. */
    private final Object zapisovanje = new Object();

    public UcenjeIzListkov(StevkaListkaRepozitorij stevke, SlikaBranjaListkaRepozitorij slike,
                           BranjeListkaRepozitorij branja, TekmaRepozitorij tekme, NizRepozitorij nizi,
                           LokalniBralnikListka bralnik, VirMrezeStevk vir, ZlatiNabor zlatiNabor,
                           @Qualifier("branjeListkovIzvajalec") Executor izvajalec,
                           PlatformTransactionManager upraviteljTransakcij) {
        this.stevke = stevke;
        this.slike = slike;
        this.branja = branja;
        this.tekme = tekme;
        this.nizi = nizi;
        this.bralnik = bralnik;
        this.vir = vir;
        this.zlatiNabor = zlatiNabor;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    /* Celica listka v stolpcu celica stevke: niz * 2 + stolpec (0 levo, 1 desno),
       rezultat CELICA_REZULTATA + stolpec. */
    static int celicaListka(int niz, int stolpec) {
        return niz * 2 + stolpec;
    }

    // ---------- Zbiranje ob potrditvi ----------

    /* Po potrditvi (ko je izid v bazi) razreze fotografijo in zapise stevke.
       obrnjeno: levi stolpec listka je prijava 2; null = ni znano (prazen listek). */
    public void poPotrditvi(Long idBranja, VnosRezultata izid, Boolean obrnjeno, int steviloNizov, String model,
                            PopravljeneCelice popravljene) {
        Runnable delo = () -> {
            try {
                zabelezi(idBranja, izid, obrnjeno, steviloNizov, model, popravljene);
            } catch (RuntimeException e) {
                log.warn("Stevk potrjenega listka {} ni bilo mogoce zabeleziti", idBranja, e);
            }
        };
        poPotrditviTransakcije(idBranja, delo);
    }

    private void poPotrditviTransakcije(Long idBranja, Runnable delo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    oddaj(idBranja, delo);
                }
            });
        } else {
            oddaj(idBranja, delo);
        }
    }

    /* Polna vrsta izvajalca nalogo zavrne (BranjeListkovNastavitve): izid je ze
       potrjen, izgubijo se le stevke tega listka za ucenje. */
    private void oddaj(Long id, Runnable delo) {
        try {
            izvajalec.execute(delo);
        } catch (RejectedExecutionException e) {
            log.warn("Stevke listka (tekme) {} niso bile zabelezene: vrsta branja je polna", id);
        }
    }

    public int zabelezi(Long idBranja, VnosRezultata izid, Boolean obrnjeno, int steviloNizov, String model) {
        return zabelezi(idBranja, izid, obrnjeno, steviloNizov, model, PopravljeneCelice.NIC);
    }

    /* Vrne stevilo zapisanih stevk. Listek gre (morda) se v zlati nabor:
       steviloNizov je stevilo nizov tekme ob potrditvi, model mreza, ki je brala.
       Ce ima listek stevke ze zapisane (popravek izida je prehitel potrditev), se
       ne zapise nic: popravek je novejsi. */
    public int zabelezi(Long idBranja, VnosRezultata izid, Boolean obrnjeno, int steviloNizov, String model,
                        PopravljeneCelice popravljene) {
        Oznaceno o = oznaciListek(idBranja, izid, obrnjeno, popravljene, Set.of());
        if (o == null) {
            return 0;
        }
        boolean zapisano;
        synchronized (zapisovanje) {
            zapisano = Boolean.TRUE.equals(transakcija.execute(s -> {
                if (stevke.existsByIdBranje(idBranja)) {
                    return false;
                }
                stevke.saveAll(o.stevke());
                return true;
            }));
        }
        if (!zapisano) {
            return 0;
        }
        // v zlati nabor samo listek z znano smerjo (koda): smer, izbrana po ujemanju
        // z mrezo, bi resnico prilagodila bralniku in meritev olepsala
        if (obrnjeno != null) {
            // stevke so ze zapisane; napaka nabora ne sme izgubiti njih
            try {
                zlatiNabor.zapisi(idBranja, o.poravnan(), MeritevBranja.resnica(izid, obrnjeno), steviloNizov, model);
            } catch (RuntimeException e) {
                log.warn("Listek {} ni bil zapisan v zlati nabor", idBranja, e);
            }
        }
        return o.stevke().size();
    }

    /* Stevke listka z oznakami (izven transakcije: razrez fotografije traja). null,
       ce izid ni igran, fotografije ni vec ali je ni mogoce poravnati. */
    private record Oznaceno(List<StevkaListka> stevke, LokalniBralnikListka.Poravnan poravnan) {}

    private Oznaceno oznaciListek(Long idBranja, VnosRezultata izid, Boolean obrnjeno,
                                  PopravljeneCelice popravljene, Set<Integer> mocne) {
        if (izid.izidTip() != null && izid.izidTip() != IzidTekme.IGRANO) {
            return null;
        }
        byte[] fotografija = transakcija.execute(s -> slike.findById(idBranja).map(f -> f.getVsebina()).orElse(null));
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (fotografija == null || nalozena == null) {
            return null;
        }
        LokalniBralnikListka.Poravnan poravnan;
        try {
            poravnan = bralnik.poravnaj(fotografija);
        } catch (BralnikListka.NapakaBranjaListka e) {
            return null;
        }
        LokalniBralnikListka.Izrez izrez = LokalniBralnikListka.izrezi(poravnan);
        List<NizVnos> n = izid.nizi() == null ? List.of() : izid.nizi();
        List<StevkaListka> zapis;
        if (obrnjeno != null) {
            zapis = oznaci(idBranja, izrez, n, izid, obrnjeno, nalozena.mreza(), popravljene, mocne);
        } else {
            // prazen listek: smer, v kateri se mreza bolj strinja s potrjenim izidom
            List<StevkaListka> naravnost = oznaci(idBranja, izrez, n, izid, false, nalozena.mreza(), popravljene, mocne);
            List<StevkaListka> obrat = oznaci(idBranja, izrez, n, izid, true, nalozena.mreza(), popravljene, mocne);
            long prav1 = naravnost.stream().filter(StevkaListka::isMrezaPrav).count();
            long prav2 = obrat.stream().filter(StevkaListka::isMrezaPrav).count();
            zapis = prav1 == prav2 ? List.of() : prav1 > prav2 ? naravnost : obrat;
        }
        return new Oznaceno(zapis, poravnan);
    }

    static List<StevkaListka> oznaci(Long idBranja, LokalniBralnikListka.Izrez izrez, List<NizVnos> nizi,
                                     VnosRezultata izid, boolean obrnjeno, MrezaStevk mreza) {
        return oznaci(idBranja, izrez, nizi, izid, obrnjeno, mreza, PopravljeneCelice.NIC, Set.of());
    }

    /* popravljene so v straneh tekme, mocne celice listka (iz prejsnjega zapisa). */
    static List<StevkaListka> oznaci(Long idBranja, LokalniBralnikListka.Izrez izrez, List<NizVnos> nizi,
                                     VnosRezultata izid, boolean obrnjeno, MrezaStevk mreza,
                                     PopravljeneCelice popravljene, Set<Integer> mocne) {
        List<StevkaListka> zapis = new ArrayList<>();
        LocalDateTime zdaj = LocalDateTime.now();
        // stran tekme levega in desnega stolpca listka
        int levaStran = obrnjeno ? 2 : 1;
        int desnaStran = obrnjeno ? 1 : 2;
        for (int i = 0; i < nizi.size() && i < izrez.nizi().size(); i++) {
            NizVnos n = nizi.get(i);
            int levo = obrnjeno ? n.tocke2() : n.tocke1();
            int desno = obrnjeno ? n.tocke1() : n.tocke2();
            int celicaL = celicaListka(i, 0);
            int celicaD = celicaListka(i, 1);
            dodaj(zapis, idBranja, izrez.nizi().get(i)[0], levo, mreza, zdaj, celicaL,
                    popravljene.niz(i, levaStran) || mocne.contains(celicaL));
            dodaj(zapis, idBranja, izrez.nizi().get(i)[1], desno, mreza, zdaj, celicaD,
                    popravljene.niz(i, desnaStran) || mocne.contains(celicaD));
        }
        if (izid.dobljeniNizi1() != null && izid.dobljeniNizi2() != null) {
            int celicaL = CELICA_REZULTATA;
            int celicaD = CELICA_REZULTATA + 1;
            dodaj(zapis, idBranja, izrez.rezultat()[0], obrnjeno ? izid.dobljeniNizi2() : izid.dobljeniNizi1(),
                    mreza, zdaj, celicaL, popravljene.rezultat(levaStran) || mocne.contains(celicaL));
            dodaj(zapis, idBranja, izrez.rezultat()[1], obrnjeno ? izid.dobljeniNizi1() : izid.dobljeniNizi2(),
                    mreza, zdaj, celicaD, popravljene.rezultat(desnaStran) || mocne.contains(celicaD));
        }
        return zapis;
    }

    /* Stevke celice dobijo oznake, ce je razrez z ustreznim stevilom stevk med
       razlagami. Popravljena celica (precrtano in prepisano, F2.5) ne gre v ucenje:
       razrez v njej zajame tudi precrtano vrednost. Vadnica tega pravila nima
       (okenca so ozka in dvomestno stevilo sega cez vecino okenca; slabe stevke
       izloci admin pri pregledu). */
    private static void dodaj(List<StevkaListka> zapis, Long idBranja, RazrezCelice.Vsebina celica, int vrednost,
                              MrezaStevk mreza, LocalDateTime zdaj, int celicaListka, boolean mocna) {
        if (celica.popravljena()) {
            return;
        }
        List<float[]> vhodi = stevkeCelice(celica, vrednost);
        if (vhodi == null) {
            return;
        }
        String stevke = String.valueOf(vrednost);
        for (int i = 0; i < vhodi.size(); i++) {
            int prava = stevke.charAt(i) - '0';
            zapis.add(new StevkaListka(idBranja, UcenjeStevk.vBajte(vhodi.get(i)), prava,
                    mrezaPrav(mreza, vhodi.get(i), prava), zdaj).celica(celicaListka).popravljena(mocna));
        }
    }

    /* Vhodi mreze za stevke znane vrednosti v celici, po vrsti od leve: razlaga
       razreza z ustreznim stevilom stevk (najverjetnejsa taka). null, ce je celica
       prazna ali razrez ne da toliko stevk (npr. »11« v eni potezi brez reza).
       Isto pravilo velja za vadnico (BranjeVadnice). */
    static List<float[]> stevkeCelice(RazrezCelice.Vsebina celica, int vrednost) {
        if (celica.prazna()) {
            return null;
        }
        int dolzina = String.valueOf(vrednost).length();
        RazrezCelice.Razlaga ustrezna = celica.razlage().stream()
                .filter(r -> r.stevke().size() == dolzina)
                .max(java.util.Comparator.comparingDouble(RazrezCelice.Razlaga::verjetnost))
                .orElse(null);
        return ustrezna == null ? null : ustrezna.stevke().stream().map(RazrezCelice.Stevka::vhod).toList();
    }

    /* Ali mreza stevko ze zdaj prebere prav (najverjetnejsa vrednost). */
    static boolean mrezaPrav(MrezaStevk mreza, float[] vhod, int prava) {
        float[] p = mreza.verjetnosti(vhod, 1);
        int najboljsa = 0;
        for (int o = 1; o < p.length; o++) {
            if (p[o] > p[najboljsa]) {
                najboljsa = o;
            }
        }
        return najboljsa == prava;
    }

    // ---------- Popravek izida tekme (F6.1) ----------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void obPopravkuIzida(IzidTekmePopravljen dogodek) {
        oddaj(dogodek.idTekme(), () -> {
            try {
                popraviOznake(dogodek.idTekme());
            } catch (RuntimeException e) {
                log.warn("Stevk listkov popravljene tekme {} ni bilo mogoce popraviti", dogodek.idTekme(), e);
            }
        });
    }

    private record PotrjenListek(Long id, String potrjeno, PrepoznavaTekme prepoznava) {}

    private record Popravek(VnosRezultata izid, List<PotrjenListek> listki) {}

    /* Oznake potrjenih listkov tekme po popravku izida. Vrne stevilo listkov, katerih
       stevke so bile zapisane znova ali izbrisane. */
    int popraviOznake(Long idTekme) {
        Popravek p = transakcija.execute(s -> tekme.findById(idTekme).map(t -> new Popravek(
                izidTekme(t, nizi.findByTekmaIdOrderByZaporednaStAsc(idTekme)),
                branja.findByTekmaIdAndStanjeIn(idTekme, List.of(StanjeBranja.POTRJENO)).stream()
                        .map(b -> new PotrjenListek(b.getId(), b.getPotrjeno(), b.getPrepoznava()))
                        .toList()))
                .orElse(null));
        if (p == null || p.listki().isEmpty()) {
            return 0;
        }
        int spremenjenih = 0;
        for (PotrjenListek l : p.listki()) {
            VnosRezultata star = l.potrjeno() == null ? null : JsonBranja.preberi(l.potrjeno(), VnosRezultata.class);
            PopravljeneCelice razlika = PopravljeneCelice.izPopravka(star, p.izid());
            boolean ustreza = razlika != null && razlika.stevilo() <= NAJVEC_POPRAVLJENIH;
            Boolean obrnjeno = l.prepoznava() == PrepoznavaTekme.KODA ? Boolean.FALSE : null;
            // mocne celice iz potrditve ostanejo mocne; vse oznake gredo po novem izidu (tudi
            // ob razliki 0: prejsnji popravek, ki ga ta vraca, je oznake ze spremenil)
            Set<Integer> mocne = new HashSet<>();
            transakcija.execute(s -> stevke.mocneCelice(l.id())).forEach(c -> mocne.add((int) c));
            Oznaceno o = ustreza ? oznaciListek(l.id(), p.izid(), obrnjeno, razlika, mocne) : null;
            if (ustreza && razlika.stevilo() == 0 && (o == null || o.stevke().isEmpty())) {
                // izid listka ostaja, novih oznak pa ni (fotografije ni vec ali smer
                // praznega listka je neodlocena): obstojece oznake veljajo
                continue;
            }
            synchronized (zapisovanje) {
                transakcija.executeWithoutResult(s -> {
                    stevke.izbrisiBranje(l.id());
                    if (o != null) {
                        stevke.saveAll(o.stevke());
                    }
                });
            }
            zlatiNabor.popravi(l.id(), ustreza && obrnjeno != null ? MeritevBranja.resnica(p.izid(), false) : null);
            spremenjenih++;
            log.info("Popravek izida tekme {}: stevke listka {} {}", idTekme, l.id(),
                    o != null ? "zapisane znova (" + o.stevke().size() + ")" : "izbrisane");
        }
        return spremenjenih;
    }

    /* Trenutni izid tekme v obliki vnosa (strani tekme). */
    static VnosRezultata izidTekme(Tekma t, List<Niz> niziTekme) {
        List<NizVnos> n = niziTekme.stream().map(x -> new NizVnos(x.getTocke1(), x.getTocke2())).toList();
        Integer zmagovalecStran = t.getZmagovalec() == null || t.getPrijava1() == null ? null
                : t.getZmagovalec().getId().equals(t.getPrijava1().getId()) ? 1 : 2;
        return new VnosRezultata(t.getIzidTip(), t.getDobljeniNizi1(), t.getDobljeniNizi2(), zmagovalecStran, n);
    }
}
