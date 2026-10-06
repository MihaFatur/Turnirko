/* Sistem SV_REGIJA: nivoji, skupine, glavni in tolazilni zreb, igra se za VSA mesta.

   POTEK
   1. Igralci se po jakosti (IzborStoritev) razdelijo v NIVOJE. Nivo ima
      privzeto 16 igralcev (4 skupine po 4), zadnji nivo dobi ostanek. Stevilo
      nivojev doloci stevilo prijav, organizator ga lahko nastavi, lahko pa
      velikost vsakega nivoja tudi vpise (Dogodek.velikostiNivojev).
   2. V vsakem nivoju igrajo skupine "vsak z vsakim". Skupine nastanejo po
      jakostnih pasovih kot pri vseh skupinskih sistemih (NosilciStoritev) ali
      pa jih organizator vpise rocno.
   3. Ko so odigrane vse skupine NIVOJA (ne celega dogodka - nivoji tecejo
      neodvisno), nastaneta zreba nivoja: prvo- in drugouvrsceni vsake skupine v
      GLAVNI zreb, tretje- in cetrtouvrsceni v TOLAZILNI zreb, in tako naprej po
      dva ranga. Nosilci se postavijo po istem pravilu kot v izlocilnem delu
      skupin (NosilciStoritev.vMrezoIzSkupin): igralca iz iste skupine se ne
      srecata prej kot v finalu. Razpored mest sme organizator rocno popraviti,
      dokler se zreb ni zacel.
   4. Zreb se igra za VSA mesta: porazenca vsakega kola se srecata med seboj za
      slabsa mesta, zmagovalca za boljsa, do zadnjega para. Mreza z 8 igralci
      ima 12 tekem (4 + 2 + 1 + 1 + 2 + 1 + 1) in odloci vseh 8 mest.

   DREVO ZA VSA MESTA
   Mreza velikosti n (potenca 2) za mesta p .. p+n-1: n/2 tekem v prvem kolu,
   zmagovalci gredo v mrezo n/2 za mesta p .. p+n/2-1, porazenci v mrezo n/2 za
   mesta p+n/2 .. p+n-1; mreza velikosti 1 je koncno mesto. Porazenca sosednjih
   parov se tako srecata med seboj (poraz proti 1. nosilcu in poraz proti
   nosilcu v istem polovici mreze vodita v isto tekmo za 5.-8. mesto).

   PROSTA MESTA (bye). Ce udelezencev ni potenca 2, ostanejo mesta v mrezi
   prazna in nosilci dobijo prosti prehod. Tekem s praznim nasprotnikom NE
   ustvarimo - igralec gre naprej brez zapisa - in prazno ostane tudi mesto
   porazenca, zato se v drevesu za mesta slabsi par srecata samo, ce sta oba
   porazenca resnicna. Prazna mesta tako vedno pristanejo na dnu razpona in
   koncna mesta ostanejo strnjena (1 .. stevilo igralcev).

   KONCNA MESTA se ne racunajo, ampak berejo: vsaka tekma, ki odloca par mest,
   nosi mestoZmagovalca in mestoPorazenca (ABSOLUTNO mesto na dogodku, zato
   zreb drugega nivoja stoji za zadnjim mestom prvega).

   Nivo z eno samo skupino zrebov nima: razvrstitev v skupini je razvrstitev
   nivoja. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.SvPredlogDto;
import si.turnirko.dto.SvRegijaDto;
import si.turnirko.dto.SvVnos;
import si.turnirko.dto.SvZrebPredlogDto;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.Zreb;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.ZrebRepozitorij;

@Service
public class SvRegijaStoritev {

    /* Privzeta oblika nivoja: 4 skupine po 4 igralci = 16. */
    public static final int PRIVZETO_SKUPIN_NA_NIVO = 4;
    public static final int PRIVZETA_VELIKOST_SKUPINE = 4;

    /* Zgornje meje: nivojev je najvec 9 (oznaka skupine "9A" ostane v treh
       znakih), skupin na nivo 26 (A..Z). */
    public static final int NAJVEC_NIVOJEV = 9;
    public static final int NAJVEC_SKUPIN = 26;

    /* Zadnji nivo je lahko manjsi od polnega, a ne manjsi od ene skupine: ostanek,
       ki bi bil manjsi, se pridruzi prejsnjemu nivoju (16 + 2 ni nivo, ampak 18). */
    static final int NAJMANJ_ZADNJEGA_NIVOJA = 4;

    /* Najmanjsa velikost skupine, ki jo razrez se predlaga (skupina dveh bi bila
       ena sama tekma). Rocni vpis sme biti manjsi (2). */
    static final int NAJMANJ_V_SKUPINI = 3;

    /* Privzeto gresta iz vsake skupine v isti zreb dva ranga: 1.-2. v glavni zreb,
       3.-4. v tolazilni, 5.-6. v tretji ... (isto kot finalne skupine za mesta).
       Organizator lahko za nivo izbere 1: vsak rang je svoj zreb (pri 8 skupinah
       so to zrebi po 8 igralcev: prvouvrsceni za 1.-8. mesto, drugouvrsceni za
       9.-16. ...). Vec kot 2 ni mogoce - mreza pozna le "zmagovalce" in "druge". */
    static final int PRIVZETO_RANGOV_V_ZREB = 2;
    static final int NAJVEC_RANGOV_V_ZREB = 2;

    private final DogodekRepozitorij dogodekRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final ZrebRepozitorij zrebRepozitorij;
    private final NosilciStoritev nosilci;
    private final IzborStoritev izborStoritev;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final LastnistvoStoritev lastnistvo;

    public SvRegijaStoritev(DogodekRepozitorij dogodekRepozitorij,
                            PrijavaRepozitorij prijavaRepozitorij,
                            TekmaRepozitorij tekmaRepozitorij,
                            SkupinaRepozitorij skupinaRepozitorij,
                            ZrebRepozitorij zrebRepozitorij,
                            NosilciStoritev nosilci,
                            IzborStoritev izborStoritev,
                            RazvrstitevStoritev razvrstitevStoritev,
                            LastnistvoStoritev lastnistvo) {
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.zrebRepozitorij = zrebRepozitorij;
        this.nosilci = nosilci;
        this.izborStoritev = izborStoritev;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.lastnistvo = lastnistvo;
    }

    // ---------------------------------------------------------------------
    // Razrez (cista logika)
    // ---------------------------------------------------------------------

    /* En nivo razreza: koliko igralcev ima, katera mesta zajame, kako se
       razdeli v skupine in koliko igralcev pride v vsak zreb. */
    public record NivoRazrez(int nivo, int velikost, int odMesta,
                             List<Integer> velikostiSkupin, List<Integer> velikostiZrebov) {
        public int doMesta() { return odMesta + velikost - 1; }
    }

    /* Razrez igralcev po nivojih, skupinah in zrebih.

       skupinNaNivo * velikostSkupine je velikost polnega nivoja (privzeto 16).
       steviloNivojev in rocneVelikosti sta neobvezna; rocne velikosti imajo
       prednost in morajo sestati natanko v stevilo igralcev. */
    public static List<NivoRazrez> razrez(int igralcev, int skupinNaNivo, int velikostSkupine,
                                          Integer steviloNivojev, List<Integer> rocneVelikosti) {
        return razrez(igralcev, skupinNaNivo, velikostSkupine, steviloNivojev, rocneVelikosti, null);
    }

    /* rangovVZreb: po nivojih 1 ali 2 (manjkajoci nivo ima 2). */
    public static List<NivoRazrez> razrez(int igralcev, int skupinNaNivo, int velikostSkupine,
                                          Integer steviloNivojev, List<Integer> rocneVelikosti,
                                          List<Integer> rangovVZreb) {
        if (igralcev < 2) {
            throw new DomenskaIzjema("Za žreb sta potrebna vsaj 2 igralca (trenutno: " + igralcev + ").");
        }
        List<Integer> velikosti = rocneVelikosti != null && !rocneVelikosti.isEmpty()
                ? preveriRocneVelikosti(rocneVelikosti, igralcev)
                : samodejneVelikosti(igralcev, skupinNaNivo * velikostSkupine, steviloNivojev);

        List<NivoRazrez> razrez = new ArrayList<>();
        int odMesta = 1;
        for (int i = 0; i < velikosti.size(); i++) {
            List<Integer> skupine = velikostiSkupin(velikosti.get(i), velikostSkupine);
            if (skupine.size() > NAJVEC_SKUPIN) {
                throw new DomenskaIzjema("Nivo " + (i + 1) + " bi imel " + skupine.size()
                        + " skupin, največ jih je " + NAJVEC_SKUPIN + ". Povečaj število nivojev"
                        + " ali velikost skupine.");
            }
            razrez.add(new NivoRazrez(i + 1, velikosti.get(i), odMesta, skupine,
                    velikostiZrebov(skupine, rangovNivoja(rangovVZreb, i + 1))));
            odMesta += velikosti.get(i);
        }
        return razrez;
    }

    private static List<Integer> preveriRocneVelikosti(List<Integer> velikosti, int igralcev) {
        if (velikosti.size() > NAJVEC_NIVOJEV) {
            throw new DomenskaIzjema("Nivojev je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        int vsota = 0;
        for (int velikost : velikosti) {
            if (velikost < 2) {
                throw new DomenskaIzjema("Nivo mora imeti vsaj 2 igralca.");
            }
            vsota += velikost;
        }
        if (vsota != igralcev) {
            throw new DomenskaIzjema("Vpisane velikosti nivojev (skupaj " + vsota
                    + ") se ne ujemajo s številom prijavljenih (" + igralcev + "). Popravi velikosti"
                    + " ali jih izbriši, da jih določi sistem.");
        }
        return velikosti;
    }

    /* Polni nivoji naprej, zadnji dobi ostanek.

       Brez izbranega stevila nivojev: igralcev / polni nivo polnih nivojev, ostanek
       je zadnji nivo (ali, ce je manjsi od ene skupine, se pridruzi prejsnjemu).
       Z izbranim stevilom L: L-1 polnih nivojev in ostanek - razen ce prijav za
       to ni dovolj, takrat se razdelijo enakomerno. */
    static List<Integer> samodejneVelikosti(int igralcev, int poln, Integer steviloNivojev) {
        List<Integer> velikosti = new ArrayList<>();
        if (steviloNivojev == null) {
            int polnih = igralcev / poln;
            int ostanek = igralcev % poln;
            for (int i = 0; i < polnih; i++) {
                velikosti.add(poln);
            }
            if (ostanek > 0) {
                if (polnih == 0 || ostanek >= NAJMANJ_ZADNJEGA_NIVOJA) {
                    velikosti.add(ostanek);
                } else {
                    velikosti.set(velikosti.size() - 1, velikosti.get(velikosti.size() - 1) + ostanek);
                }
            }
            return velikosti;
        }

        int nivojev = Math.max(1, steviloNivojev);
        if (nivojev > NAJVEC_NIVOJEV) {
            throw new DomenskaIzjema("Nivojev je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        if (igralcev < nivojev * 2) {
            throw new DomenskaIzjema("Za " + nivojev + " nivojev je premalo prijav (" + igralcev
                    + "): vsak nivo potrebuje vsaj 2 igralca.");
        }
        if (igralcev >= (nivojev - 1) * poln + NAJMANJ_ZADNJEGA_NIVOJA) {
            for (int i = 0; i < nivojev - 1; i++) {
                velikosti.add(poln);
            }
            velikosti.add(igralcev - (nivojev - 1) * poln);
            return velikosti;
        }
        // premalo prijav za polne nivoje: enakomerno, mocnejsi nivoji so lahko za enega vecji
        int osnova = igralcev / nivojev;
        int visek = igralcev % nivojev;
        for (int i = 0; i < nivojev; i++) {
            velikosti.add(osnova + (i < visek ? 1 : 0));
        }
        return velikosti;
    }

    /* Velikosti skupin nivoja: toliko skupin, da je v vsaki priblizno velikostSkupine
       igralcev in vsaj 3; vecje skupine najprej. Nivo, ki ga ni mogoce razdeliti
       v skupine po vsaj 3, je ena sama skupina (vsak z vsakim). */
    public static List<Integer> velikostiSkupin(int velikostNivoja, int velikostSkupine) {
        int skupin = (velikostNivoja + velikostSkupine - 1) / velikostSkupine;
        while (skupin > 1 && velikostNivoja / skupin < NAJMANJ_V_SKUPINI) {
            skupin--;
        }
        skupin = Math.max(1, skupin);
        int osnova = velikostNivoja / skupin;
        int visek = velikostNivoja % skupin;
        List<Integer> velikosti = new ArrayList<>();
        for (int i = 0; i < skupin; i++) {
            velikosti.add(osnova + (i < visek ? 1 : 0));
        }
        return velikosti;
    }

    /* Koliko igralcev pride v vsak zreb nivoja: zreb d zbere ranga 2d+1 in 2d+2 iz
       vsake skupine. Nivo z eno skupino zrebov nima. */
    public static List<Integer> velikostiZrebov(List<Integer> velikostiSkupin) {
        return velikostiZrebov(velikostiSkupin, PRIVZETO_RANGOV_V_ZREB);
    }

    /* Isto z izbranim stevilom rangov v zrebu: zreb d zbere rangove
       d*r+1 ... d*r+r iz vsake skupine, ki jih ima. */
    public static List<Integer> velikostiZrebov(List<Integer> velikostiSkupin, int rangov) {
        if (velikostiSkupin.size() <= 1) {
            return List.of();
        }
        int najvecja = velikostiSkupin.stream().mapToInt(Integer::intValue).max().orElse(0);
        int zrebov = (najvecja + rangov - 1) / rangov;
        List<Integer> velikosti = new ArrayList<>();
        for (int d = 0; d < zrebov; d++) {
            int skupaj = 0;
            for (int velikost : velikostiSkupin) {
                skupaj += Math.max(0, Math.min(rangov, velikost - d * rangov));
            }
            velikosti.add(skupaj);
        }
        return velikosti;
    }

    /* Rangov v zrebu za nivo (nivoji so oznaceni od 1): vpisana vrednost ali privzeta 2. */
    static int rangovNivoja(List<Integer> rangovVZreb, int nivo) {
        if (rangovVZreb == null || nivo < 1 || nivo > rangovVZreb.size() || rangovVZreb.get(nivo - 1) == null) {
            return PRIVZETO_RANGOV_V_ZREB;
        }
        return rangovVZreb.get(nivo - 1);
    }


    public static int skupinNaNivo(Dogodek dogodek) {
        return dogodek.getSteviloSkupin() != null ? dogodek.getSteviloSkupin() : PRIVZETO_SKUPIN_NA_NIVO;
    }

    public static int velikostSkupine(Dogodek dogodek) {
        return dogodek.getVelikostSkupine() != null ? dogodek.getVelikostSkupine() : PRIVZETA_VELIKOST_SKUPINE;
    }

    /* Razrez za dogodek z njegovimi nastavitvami. */
    public static List<NivoRazrez> razrez(Dogodek dogodek, int igralcev) {
        return razrez(igralcev, skupinNaNivo(dogodek), velikostSkupine(dogodek),
                dogodek.getSteviloNivojev(), dogodek.velikostiNivojevSeznam(),
                dogodek.rangovVZrebSeznam());
    }

    /* Mesta v mrezi iz seznama nosilcev (poSeedu.get(0) = 1. nosilec): mesto i
       drzi nosilca po standardnem vrstnem redu; mesta, ki jih nosilcev ne
       zadosca, so prosta (null). */
    public static Prijava[] mestaVMrezi(List<Prijava> poSeedu) {
        int velikost = NosilciStoritev.najblizjaPotencaDve(poSeedu.size());
        int[] vrstniRed = NosilciStoritev.seedVrstniRed(velikost);
        Prijava[] mesta = new Prijava[velikost];
        for (int i = 0; i < velikost; i++) {
            if (vrstniRed[i] <= poSeedu.size()) {
                mesta[i] = poSeedu.get(vrstniRed[i] - 1);
            }
        }
        return mesta;
    }

    // ---------------------------------------------------------------------
    // Drevo za vsa mesta
    // ---------------------------------------------------------------------

    /* Od kod pride udelezenec mesta: konkretna prijava, rezultat tekme
       (zmagovalec ali porazenec) ali nic (prosto mesto). */
    private record Vir(Prijava prijava, Tekma tekma, VlogaIzvora vloga) {
        static final Vir PRAZEN = new Vir(null, null, null);

        boolean jePrazen() { return prijava == null && tekma == null; }
    }

    /* Stanje gradnje enega zreba. */
    private static final class Gradnja {
        final Dogodek dogodek;
        final Zreb zreb;
        final Map<Integer, Integer> pozicije = new HashMap<>();
        final List<Tekma> tekme = new ArrayList<>();
        final List<Prijava> samiPrijavljeni = new ArrayList<>();

        Gradnja(Dogodek dogodek, Zreb zreb) {
            this.dogodek = dogodek;
            this.zreb = zreb;
        }
    }

    /* Zgradi tekme zreba iz mest v mrezi (null = prosto). Zbrise morebitne
       stare tekme zreba; klicatelj je preveril, da se ni zacet. */
    private void zgradiZreb(Dogodek dogodek, Zreb zreb, Prijava[] mesta, boolean rocni) {
        odstraniTekmeZreba(zreb);

        Gradnja gradnja = new Gradnja(dogodek, zreb);
        List<Vir> sloji = new ArrayList<>();
        for (Prijava mesto : mesta) {
            sloji.add(mesto == null ? Vir.PRAZEN : new Vir(mesto, null, null));
        }
        razdeli(sloji, zreb.getPrvoMesto(), 1, gradnja);

        tekmaRepozitorij.saveAll(gradnja.tekme);
        if (!gradnja.samiPrijavljeni.isEmpty()) {
            prijavaRepozitorij.saveAll(gradnja.samiPrijavljeni);
        }
        zreb.setStUdelezencev((int) java.util.Arrays.stream(mesta).filter(Objects::nonNull).count());
        zreb.setRazpored(java.util.Arrays.stream(mesta)
                .map(m -> m == null ? "" : String.valueOf(m.getId()))
                .collect(Collectors.joining(",")));
        zreb.setZgrajen(true);
        zreb.setRocni(rocni);
        zrebRepozitorij.save(zreb);
    }

    /* Ena stopnja drevesa: sloji so udelezenci mreze velikosti n po vrsti
       (sosednja dva igrata drug proti drugemu). Zmagovalci gredo v mrezo za
       zgornjo polovico mest, porazenci v mrezo za spodnjo. */
    private void razdeli(List<Vir> sloji, int prvoMesto, int kolo, Gradnja gradnja) {
        int n = sloji.size();
        if (n == 1) {
            dodeliMesto(sloji.get(0), prvoMesto, gradnja);
            return;
        }
        List<Vir> zmagovalci = new ArrayList<>();
        List<Vir> porazenci = new ArrayList<>();
        for (int i = 0; i < n; i += 2) {
            Vir a = sloji.get(i);
            Vir b = sloji.get(i + 1);
            if (a.jePrazen() && b.jePrazen()) {
                zmagovalci.add(Vir.PRAZEN);
                porazenci.add(Vir.PRAZEN);
            } else if (a.jePrazen() || b.jePrazen()) {
                // prosti prehod: tekme ni, porazenca tudi ne
                zmagovalci.add(a.jePrazen() ? b : a);
                porazenci.add(Vir.PRAZEN);
            } else {
                Tekma tekma = novaTekma(gradnja, kolo, prvoMesto, prvoMesto + n - 1);
                postavi(tekma, a, b);
                zmagovalci.add(new Vir(null, tekma, VlogaIzvora.ZMAGOVALEC));
                porazenci.add(new Vir(null, tekma, VlogaIzvora.PORAZENEC));
            }
        }
        razdeli(zmagovalci, prvoMesto, kolo + 1, gradnja);
        razdeli(porazenci, prvoMesto + n / 2, kolo + 1, gradnja);
    }

    /* Udelezenec je na koncu poti in dobi mesto: tekma, ki ga odloca, si ga
       zapomni (iz tega nastane koncna razvrstitev). */
    private void dodeliMesto(Vir vir, int mesto, Gradnja gradnja) {
        if (vir.jePrazen()) {
            return;
        }
        if (vir.prijava() != null) {
            vir.prijava().setKoncnoMesto(mesto);
            gradnja.samiPrijavljeni.add(vir.prijava());
        } else if (vir.vloga() == VlogaIzvora.ZMAGOVALEC) {
            vir.tekma().setMestoZmagovalca(mesto);
        } else {
            vir.tekma().setMestoPorazenca(mesto);
        }
    }

    private Tekma novaTekma(Gradnja gradnja, int kolo, int razponOd, int razponDo) {
        FazaTekme faza = gradnja.zreb.faza();
        int pozicija = gradnja.pozicije.compute(kolo, (k, trenutna) -> (trenutna != null
                ? trenutna
                : tekmaRepozitorij.najvisjaPozicija(gradnja.dogodek.getId(), faza, k)) + 1);

        Tekma tekma = new Tekma();
        tekma.setDogodek(gradnja.dogodek);
        tekma.setFaza(faza);
        tekma.setKolo(kolo);
        tekma.setPozicija(pozicija);
        tekma.setSteviloNizov(gradnja.dogodek.getPrivzetoSteviloNizov());
        tekma.setStatus(StatusTekme.CAKA);
        tekma.setIdZreb(gradnja.zreb.getId());
        tekma.setRazponOd(razponOd);
        tekma.setRazponDo(razponDo);
        // shranimo takoj: naslednja tekma se sklicuje na id te
        tekma = tekmaRepozitorij.save(tekma);
        gradnja.tekme.add(tekma);
        return tekma;
    }

    /* Vpise obe strani tekme: znan igralec neposredno, drugace povezava na
       tekmo, iz katere pride. Tekma z obema znanima igralcema je pripravljena. */
    private static void postavi(Tekma tekma, Vir a, Vir b) {
        if (a.prijava() != null) {
            tekma.setPrijava1(a.prijava());
        } else {
            tekma.setIdIzvorTekma1(a.tekma().getId());
            tekma.setVlogaIzvora1(a.vloga());
        }
        if (b.prijava() != null) {
            tekma.setPrijava2(b.prijava());
        } else {
            tekma.setIdIzvorTekma2(b.tekma().getId());
            tekma.setVlogaIzvora2(b.vloga());
        }
        if (tekma.getPrijava1() != null && tekma.getPrijava2() != null) {
            tekma.setStatus(StatusTekme.PRIPRAVLJENA);
        }
    }

    /* Izbrise tekme zreba. Povezave se najprej pretrgajo in izperejo, sicer bi
       tuji kljuc ustavil brisanje izvorne tekme; izbris se izpere pred novimi
       vstavki, ker bi sicer UNIQUE (dogodek, faza, kolo, pozicija) trcil ob
       stare vrstice (Hibernate vstavke izvede pred izbrisi). */
    private void odstraniTekmeZreba(Zreb zreb) {
        List<Tekma> stare = tekmaRepozitorij.findByIdZreb(zreb.getId());
        if (stare.isEmpty()) {
            return;
        }
        for (Tekma tekma : stare) {
            tekma.setIdIzvorTekma1(null);
            tekma.setVlogaIzvora1(null);
            tekma.setIdIzvorTekma2(null);
            tekma.setVlogaIzvora2(null);
        }
        tekmaRepozitorij.saveAll(stare);
        tekmaRepozitorij.flush();
        tekmaRepozitorij.deleteAll(stare);
        tekmaRepozitorij.flush();
    }

    // ---------------------------------------------------------------------
    // Zreb skupin
    // ---------------------------------------------------------------------

    /* Nakljucen zreb skupin po jakostnih pasovih; igralci so ze urejeni po
       jakosti (najmocnejsi prvi). Kliče ga ZrebStoritev ob zrebu dogodka. */
    @Transactional
    public List<Tekma> izvediZrebSkupin(Dogodek dogodek, List<Prijava> poJakosti) {
        return zapisiSkupine(dogodek, nakljucneSkupine(dogodek, poJakosti));
    }

    /* Predlog skupin BREZ zapisa: isti zreb, kot bi ga izvedel dogodek. */
    @Transactional(readOnly = true)
    public SvPredlogDto predlogSkupin(Long idDogodka) {
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() == StatusTekmovanja.ZAKLJUCEN) {
            throw new DomenskaIzjema("Dogodek je zaključen.");
        }
        List<Prijava> poJakosti = izborStoritev.vrstniRed(idDogodka);
        Map<Long, Integer> ratingi = izborStoritev.ratingi(poJakosti);
        List<List<List<Prijava>>> nivoji = nakljucneSkupine(dogodek, poJakosti);

        List<SvPredlogDto.NivoPredlogDto> izid = new ArrayList<>();
        int odMesta = 1;
        for (int i = 0; i < nivoji.size(); i++) {
            List<SvPredlogDto.SkupinaPredlogDto> skupine = new ArrayList<>();
            int velikost = 0;
            for (int j = 0; j < nivoji.get(i).size(); j++) {
                List<SvPredlogDto.ClanDto> clani = nivoji.get(i).get(j).stream()
                        .map(p -> clan(p, ratingi))
                        .toList();
                velikost += clani.size();
                skupine.add(new SvPredlogDto.SkupinaPredlogDto(oznakaSkupine(i + 1, j), clani));
            }
            izid.add(new SvPredlogDto.NivoPredlogDto(i + 1, odMesta, skupine));
            odMesta += velikost;
        }
        return new SvPredlogDto(izid);
    }

    private List<List<List<Prijava>>> nakljucneSkupine(Dogodek dogodek, List<Prijava> poJakosti) {
        List<NivoRazrez> razrez = razrez(dogodek, poJakosti.size());
        List<List<List<Prijava>>> nivoji = new ArrayList<>();
        int od = 0;
        for (NivoRazrez nivo : razrez) {
            List<Prijava> igralci = poJakosti.subList(od, od + nivo.velikost());
            od += nivo.velikost();
            nivoji.add(nosilci.vSkupine(igralci, nivo.velikostiSkupin().size()));
        }
        return nivoji;
    }

    /* Zapise skupine nivojev in njihove tekme "vsak z vsakim" ter prazne zrebe
       nivojev (njihove tekme nastanejo, ko so skupine odigrane). Struktura je
       lahko nakljucna ali rocno vpisana - od tu naprej je vse enako, zato
       rocni vpis in zreb ne moreta delovati razlicno. */
    @Transactional
    public List<Tekma> zapisiSkupine(Dogodek dogodek, List<List<List<Prijava>>> nivoji) {
        preveriStrukturo(nivoji);

        List<Tekma> vse = new ArrayList<>();
        List<Prijava> vsiClani = new ArrayList<>();
        int globalnaPozicija = 1;
        int odMesta = 1;
        for (int n = 0; n < nivoji.size(); n++) {
            int nivo = n + 1;
            List<List<Prijava>> skupineNivoja = nivoji.get(n);

            List<Skupina> skupine = new ArrayList<>();
            for (int j = 0; j < skupineNivoja.size(); j++) {
                Skupina skupina = new Skupina(dogodek, oznakaSkupine(nivo, j));
                skupina.setNivo(nivo);
                skupine.add(skupina);
            }
            skupinaRepozitorij.saveAll(skupine);

            List<Integer> velikosti = new ArrayList<>();
            int velikostNivoja = 0;
            for (int j = 0; j < skupineNivoja.size(); j++) {
                // clani po jakosti: nosilec skupine zacne z najsibkejsim, dvoboj 1-2 pade v zadnje kolo
                List<Prijava> clani = new ArrayList<>(skupineNivoja.get(j));
                clani.sort(Comparator.comparing(
                        (Prijava p) -> p.getStNosilca() == null ? Integer.MAX_VALUE : p.getStNosilca()));
                Skupina skupina = skupine.get(j);
                for (Prijava clan : clani) {
                    clan.setIdSkupina(skupina.getId());
                    clan.setMestoVSkupini(null);
                    clan.setKoncnoMesto(null);
                    vsiClani.add(clan);
                }
                for (Tekma tekma : ZrebStoritev.kroznePare(dogodek, clani, skupina.getId())) {
                    tekma.setPozicija(globalnaPozicija++);
                    vse.add(tekma);
                }
                velikosti.add(clani.size());
                velikostNivoja += clani.size();
            }

            List<Integer> zrebi = velikostiZrebov(velikosti,
                    rangovNivoja(dogodek.rangovVZrebSeznam(), nivo));
            int mesto = odMesta;
            for (int d = 0; d < zrebi.size(); d++) {
                zrebRepozitorij.save(new Zreb(dogodek, nivo, d, mesto, zrebi.get(d)));
                mesto += zrebi.get(d);
            }
            odMesta += velikostNivoja;
        }
        tekmaRepozitorij.saveAll(vse);
        prijavaRepozitorij.saveAll(vsiClani);
        return vse;
    }

    /* Vsak nivo ima skupino, vsaka skupina vsaj dva igralca, nihce ni dvakrat. */
    private static void preveriStrukturo(List<List<List<Prijava>>> nivoji) {
        if (nivoji.isEmpty()) {
            throw new NeveljavenVnosIzjema("Vpisan mora biti vsaj en nivo.");
        }
        if (nivoji.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Nivojev je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        Set<Long> videni = new HashSet<>();
        for (int n = 0; n < nivoji.size(); n++) {
            List<List<Prijava>> skupine = nivoji.get(n);
            if (skupine.isEmpty()) {
                throw new NeveljavenVnosIzjema("Nivo " + (n + 1) + " nima nobene skupine.");
            }
            if (skupine.size() > NAJVEC_SKUPIN) {
                throw new NeveljavenVnosIzjema("Nivo " + (n + 1) + " ima več kot " + NAJVEC_SKUPIN
                        + " skupin.");
            }
            for (int j = 0; j < skupine.size(); j++) {
                if (skupine.get(j).size() < 2) {
                    throw new NeveljavenVnosIzjema("Skupina " + oznakaSkupine(n + 1, j)
                            + " ima manj kot 2 igralca.");
                }
                for (Prijava p : skupine.get(j)) {
                    if (!videni.add(p.getId())) {
                        throw new NeveljavenVnosIzjema("Igralec " + imeIgralca(p)
                                + " nastopa v več skupinah.");
                    }
                }
            }
        }
    }

    /* Oznaka skupine je enolicna na dogodku: "1A" je skupina A prvega nivoja. */
    static String oznakaSkupine(int nivo, int indeks) {
        return nivo + String.valueOf((char) ('A' + indeks));
    }

    /* Razveljavi zreb skupin (in z njim vse zrebe): dogodek se vrne v pripravo.
       Mogoce samo, dokler nobena tekma ni zacela - sicer bi se z zrebom izbrisali
       rezultati in rating. */
    @Transactional
    public void razveljaviZrebSkupin(Long idDogodka) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Žreb je mogoče razveljaviti samo, dokler dogodek teče.");
        }
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        if (tekme.stream().anyMatch(t -> t.getStatus() == StatusTekme.KONCANA
                || t.getStatus() == StatusTekme.V_IGRI)) {
            throw new DomenskaIzjema("Žreba ni mogoče razveljaviti: nekatere tekme so že začete ali odigrane.");
        }

        for (Tekma tekma : tekme) {
            tekma.setIdIzvorTekma1(null);
            tekma.setVlogaIzvora1(null);
            tekma.setIdIzvorTekma2(null);
            tekma.setVlogaIzvora2(null);
        }
        tekmaRepozitorij.saveAll(tekme);
        tekmaRepozitorij.flush();
        tekmaRepozitorij.deleteAll(tekme);
        tekmaRepozitorij.flush();

        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        for (Prijava prijava : prijave) {
            prijava.setIdSkupina(null);
            prijava.setMestoVSkupini(null);
            prijava.setKoncnoMesto(null);
        }
        prijavaRepozitorij.saveAll(prijave);
        prijavaRepozitorij.flush();

        zrebRepozitorij.deleteAll(zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(idDogodka));
        skupinaRepozitorij.deleteAll(skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka));
        zrebRepozitorij.flush();

        dogodek.setStatus(StatusTekmovanja.PRIPRAVA);
        // turnir se vrne v pripravo samo, ce noben njegov dogodek ni zacel
        var turnir = dogodek.getTurnir();
        boolean kakDrugZacel = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(turnir.getId()).stream()
                .anyMatch(d -> d.getStatus() != StatusTekmovanja.PRIPRAVA);
        if (!kakDrugZacel && turnir.getStatus() == StatusTekmovanja.V_TEKU) {
            turnir.setStatus(StatusTekmovanja.PRIPRAVA);
        }
    }

    // ---------------------------------------------------------------------
    // Nastavitve
    // ---------------------------------------------------------------------

    /* Nastavitve razreza; samo v pripravi (po zrebu se razrez ne spreminja). */
    @Transactional
    public Dogodek nastavi(Long idDogodka, SvVnos.Nastavitve vnos) {
        lastnistvo.preveriTurnirPoDogodku(idDogodka);
        Dogodek dogodek = najdiDogodek(idDogodka);
        if (dogodek.getStatus() != StatusTekmovanja.PRIPRAVA) {
            throw new DomenskaIzjema("Razrez je mogoče spreminjati samo, dokler je dogodek v pripravi.");
        }
        Integer skupin = vnos.steviloSkupin();
        Integer velikost = vnos.velikostSkupine();
        if (skupin != null && (skupin < 1 || skupin > NAJVEC_SKUPIN)) {
            throw new NeveljavenVnosIzjema("Skupin na nivo mora biti med 1 in " + NAJVEC_SKUPIN + ".");
        }
        if (velikost != null && (velikost < 2 || velikost > 24)) {
            throw new NeveljavenVnosIzjema("Velikost skupine mora biti med 2 in 24.");
        }
        List<Integer> velikosti = vnos.velikostiNivojev() == null ? List.of() : vnos.velikostiNivojev();
        if (velikosti.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Nivojev je lahko največ " + NAJVEC_NIVOJEV + ".");
        }
        for (Integer v : velikosti) {
            if (v == null || v < 2) {
                throw new NeveljavenVnosIzjema("Vsak nivo mora imeti vsaj 2 igralca.");
            }
        }
        Integer nivojev = vnos.steviloNivojev();
        if (nivojev != null && (nivojev < 1 || nivojev > NAJVEC_NIVOJEV)) {
            throw new NeveljavenVnosIzjema("Število nivojev mora biti med 1 in " + NAJVEC_NIVOJEV + ".");
        }
        List<Integer> rangov = vnos.rangovVZreb() == null ? List.of() : vnos.rangovVZreb();
        if (rangov.size() > NAJVEC_NIVOJEV) {
            throw new NeveljavenVnosIzjema("Rangov v žrebu je mogoče vpisati za največ "
                    + NAJVEC_NIVOJEV + " nivojev.");
        }
        for (Integer r : rangov) {
            if (r == null || r < 1 || r > NAJVEC_RANGOV_V_ZREB) {
                throw new NeveljavenVnosIzjema("Rangov iz skupine v en žreb je lahko 1 ali "
                        + NAJVEC_RANGOV_V_ZREB + ".");
            }
        }

        dogodek.setSteviloSkupin(skupin);
        dogodek.setVelikostSkupine(velikost);
        dogodek.setRangovVZreb(rangov.isEmpty() ? null : rangov.stream().map(String::valueOf)
                .collect(Collectors.joining(",")));
        if (velikosti.isEmpty()) {
            dogodek.setVelikostiNivojev(null);
            dogodek.setSteviloNivojev(nivojev);
        } else {
            // rocne velikosti so ze samo stevilo nivojev
            dogodek.setVelikostiNivojev(velikosti.stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            dogodek.setSteviloNivojev(null);
        }
        return dogodekRepozitorij.save(dogodek);
    }

    // ---------------------------------------------------------------------
    // Zrebi nivojev
    // ---------------------------------------------------------------------

    /* Po vsaki koncani skupinski tekmi: nivo, katerega skupine so vse odigrane,
       dobi zreba. Nivoji tecejo neodvisno - zreb prvega nivoja ne caka na
       zadnjega. Klic je varen za ponavljanje (zgrajeni zrebi se preskocijo). */
    @Transactional
    public void obKoncaniSkupinski(Dogodek dogodek, List<Skupina> skupine,
                                   List<Prijava> prijave, List<Tekma> tekme) {
        Map<Integer, List<Skupina>> poNivojih = predtekmovalnePoNivojih(skupine);
        for (Map.Entry<Integer, List<Skupina>> nivo : poNivojih.entrySet()) {
            Set<Long> idjiSkupin = nivo.getValue().stream().map(Skupina::getId).collect(Collectors.toSet());
            List<Tekma> skupinske = tekme.stream()
                    .filter(t -> t.getFaza() == FazaTekme.SKUPINA && idjiSkupin.contains(t.getIdSkupina()))
                    .toList();
            if (skupinske.isEmpty()
                    || !skupinske.stream().allMatch(t -> t.getStatus() == StatusTekme.KONCANA)) {
                continue;
            }
            List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(
                    dogodek.getId(), nivo.getKey());
            if (zrebi.stream().allMatch(Zreb::isZgrajen)) {
                continue;
            }
            List<List<Prijava>> uvrstitve = uvrstitveNivoja(nivo.getValue(), prijave, tekme);
            for (Zreb zreb : zrebi) {
                if (!zreb.isZgrajen()) {
                    zgradiZreb(dogodek, zreb, nakljucnaMesta(zreb, uvrstitve), false);
                }
            }
        }
    }

    /* Predtekmovalne skupine po nivojih, urejene po oznaki (A, B, C ...). */
    private static Map<Integer, List<Skupina>> predtekmovalnePoNivojih(List<Skupina> skupine) {
        Map<Integer, List<Skupina>> poNivojih = new TreeMap<>();
        for (Skupina skupina : skupine) {
            if (skupina.getStopnja() == 1) {
                poNivojih.computeIfAbsent(skupina.getNivo(), k -> new ArrayList<>()).add(skupina);
            }
        }
        for (List<Skupina> seznam : poNivojih.values()) {
            seznam.sort(Comparator.comparing(Skupina::getOznaka));
        }
        return poNivojih;
    }

    /* Razvrstitev v vsaki skupini nivoja (skupine po vrsti A, B, ...): igralci od
       prvega mesta navzdol. */
    private List<List<Prijava>> uvrstitveNivoja(List<Skupina> skupine, List<Prijava> prijave,
                                                List<Tekma> tekme) {
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijave) {
            poId.put(p.getId(), p);
        }
        List<List<Prijava>> uvrstitve = new ArrayList<>();
        for (Skupina skupina : skupine) {
            List<Tekma> tekmeSkupine = tekme.stream()
                    .filter(t -> t.getFaza() == FazaTekme.SKUPINA && skupina.getId().equals(t.getIdSkupina()))
                    .toList();
            List<Prijava> poVrsti = new ArrayList<>();
            for (VrsticaLestviceDto vrstica : razvrstitevStoritev.lestvica(
                    SkupineStoritev.clani(skupina, prijave, tekme), tekmeSkupine)) {
                poVrsti.add(poId.get(vrstica.idPrijave()));
            }
            uvrstitve.add(poVrsti);
        }
        return uvrstitve;
    }

    /* Udelezenci zreba iz razvrstitve v skupinah: zreb d zbere ranga 2d+1 (kot
       "zmagovalec") in 2d+2 (kot "drugi") iz vsake skupine, ki ju ima. */
    private record Udelezenci(List<Prijava> prvi, List<Prijava> drugi) {
        List<Prijava> vsi() {
            List<Prijava> vsi = new ArrayList<>(prvi);
            drugi.stream().filter(Objects::nonNull).forEach(vsi::add);
            return vsi;
        }
    }

    private static Udelezenci udelezenci(Zreb zreb, List<List<Prijava>> uvrstitve) {
        int rangov = rangovNivoja(zreb.getDogodek().rangovVZrebSeznam(), zreb.getNivo());
        int rang = zreb.getIndeks() * rangov;
        List<Prijava> prvi = new ArrayList<>();
        List<Prijava> drugi = new ArrayList<>();
        for (List<Prijava> poVrsti : uvrstitve) {
            if (poVrsti.size() <= rang) {
                continue; // skupina nima igralca tega ranga
            }
            prvi.add(poVrsti.get(rang));
            drugi.add(rangov > 1 && poVrsti.size() > rang + 1 ? poVrsti.get(rang + 1) : null);
        }
        return new Udelezenci(prvi, drugi);
    }

    /* Mesta v mrezi po pravilu zreba (nosilci, locitev skupin in klubov). */
    private Prijava[] nakljucnaMesta(Zreb zreb, List<List<Prijava>> uvrstitve) {
        Udelezenci udelezenci = udelezenci(zreb, uvrstitve);
        return mestaVMrezi(nosilci.vMrezoIzSkupin(udelezenci.prvi(), udelezenci.drugi()));
    }

    /* Predlog razporeditve v mrezi BREZ zapisa (isti zreb, kot bi ga naredil
       sistem), da ga organizator popravi in vrne kot rocni vpis. */
    @Transactional(readOnly = true)
    public SvZrebPredlogDto predlogZreba(Long idZreba) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);
        Prijava[] mesta = nakljucnaMesta(zreb, podatki.uvrstitve());
        Map<Long, Integer> ratingi = izborStoritev.ratingi(podatki.prijave());
        List<SvPredlogDto.ClanDto> clani = new ArrayList<>();
        for (Prijava mesto : mesta) {
            clani.add(mesto == null ? null : clan(mesto, ratingi));
        }
        return new SvZrebPredlogDto(mesta.length, clani);
    }

    /* Rocna razporeditev mest v mrezi: id prijav po mestih od vrha navzdol, null
       za prosto mesto. Udelezenci so tisti, ki so prisli iz skupin - razporeditev
       jih samo premesti, nikogar ne doda ali odvzame. */
    @Transactional
    public void nastaviMesta(Long idZreba, List<Long> idjiPoMestih) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);
        Udelezenci udelezenci = udelezenci(zreb, podatki.uvrstitve());

        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : udelezenci.vsi()) {
            poId.put(p.getId(), p);
        }
        int velikost = NosilciStoritev.najblizjaPotencaDve(poId.size());
        if (idjiPoMestih == null || idjiPoMestih.size() != velikost) {
            throw new NeveljavenVnosIzjema("Mrežo sestavlja " + velikost + " mest, prejetih je "
                    + (idjiPoMestih == null ? 0 : idjiPoMestih.size()) + ".");
        }
        Prijava[] mesta = new Prijava[velikost];
        Set<Long> videni = new HashSet<>();
        for (int i = 0; i < velikost; i++) {
            Long id = idjiPoMestih.get(i);
            if (id == null) {
                continue;
            }
            Prijava prijava = poId.get(id);
            if (prijava == null) {
                throw new NeveljavenVnosIzjema("Prijava " + id + " ne sodi v ta žreb.");
            }
            if (!videni.add(id)) {
                throw new NeveljavenVnosIzjema("Igralec " + imeIgralca(prijava) + " je v mreži dvakrat.");
            }
            mesta[i] = prijava;
        }
        if (videni.size() != poId.size()) {
            throw new NeveljavenVnosIzjema("V mreži mora biti vseh " + poId.size()
                    + " udeležencev žreba (vpisanih: " + videni.size() + ").");
        }
        zgradiZreb(zreb.getDogodek(), zreb, mesta, true);
    }

    /* Ponovno nakljucno razporedi zreb (dokler se ni zacet). */
    @Transactional
    public void znovaZrebaj(Long idZreba) {
        Zreb zreb = najdiZreb(idZreba);
        lastnistvo.preveriTurnirPoDogodku(zreb.getDogodek().getId());
        preveriUredljiv(zreb);
        PodatkiNivoja podatki = podatkiNivoja(zreb);
        zgradiZreb(zreb.getDogodek(), zreb, nakljucnaMesta(zreb, podatki.uvrstitve()), false);
    }

    private record PodatkiNivoja(List<Prijava> prijave, List<List<Prijava>> uvrstitve) {}

    private PodatkiNivoja podatkiNivoja(Zreb zreb) {
        Long idDogodka = zreb.getDogodek().getId();
        List<Skupina> skupine = predtekmovalnePoNivojih(
                skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)).get(zreb.getNivo());
        if (skupine == null) {
            throw new NiNajdenoIzjema("Nivo " + zreb.getNivo() + " nima skupin.");
        }
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        return new PodatkiNivoja(prijave, uvrstitveNivoja(skupine, prijave, tekme));
    }

    /* Zreb se sme popravljati, ce je zgrajen in se nobena njegova tekma ni
       zacela: sicer bi popravek unicil ze odigrane rezultate. */
    private void preveriUredljiv(Zreb zreb) {
        if (zreb.getDogodek().getStatus() != StatusTekmovanja.V_TEKU) {
            throw new DomenskaIzjema("Žreb je mogoče urejati samo, dokler dogodek teče.");
        }
        if (!zreb.isZgrajen()) {
            throw new DomenskaIzjema("Žreb še ni zgrajen: počakaj, da se odigrajo vse skupine nivoja.");
        }
        boolean zacet = tekmaRepozitorij.findByIdZreb(zreb.getId()).stream()
                .anyMatch(t -> t.getStatus() == StatusTekme.KONCANA || t.getStatus() == StatusTekme.V_IGRI);
        if (zacet) {
            throw new DomenskaIzjema("Žreba ni mogoče spremeniti: nekatere njegove tekme so že začete.");
        }
    }

    // ---------------------------------------------------------------------
    // Zakljucek
    // ---------------------------------------------------------------------

    /* Ali imajo vsi zrebi dogodka zgrajene tekme. Dogodek se brez tega ne sme
       zakljuciti, ceprav so vse obstojece tekme odigrane (skupine vseh nivojev
       so, zrebov pa se ni). */
    @Transactional(readOnly = true)
    public boolean vsiZrebiZgrajeni(Long idDogodka) {
        return zrebRepozitorij.countByDogodekIdAndZgrajenFalse(idDogodka) == 0;
    }

    /* Koncna mesta: iz tekem zrebov (vsaka nosi mesto zmagovalca in porazenca)
       in iz razvrstitve v skupini za nivoje z eno samo skupino. */
    @Transactional
    public void dodeliKoncnaMesta(Long idDogodka) {
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(idDogodka);
        for (Tekma tekma : tekme) {
            if (tekma.getStatus() != StatusTekme.KONCANA || tekma.getZmagovalec() == null) {
                continue;
            }
            if (tekma.getMestoZmagovalca() != null) {
                tekma.getZmagovalec().setKoncnoMesto(tekma.getMestoZmagovalca());
            }
            Prijava porazenec = tekma.porazenec();
            if (tekma.getMestoPorazenca() != null && porazenec != null) {
                porazenec.setKoncnoMesto(tekma.getMestoPorazenca());
            }
        }

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka);
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(idDogodka);
        Set<Integer> nivojiZZrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(idDogodka).stream()
                .map(Zreb::getNivo).collect(Collectors.toSet());
        Map<Integer, Integer> zacetki = zacetkiNivojev(skupine, prijave);
        for (Map.Entry<Integer, List<Skupina>> nivo : predtekmovalnePoNivojih(skupine).entrySet()) {
            if (nivojiZZrebi.contains(nivo.getKey())) {
                continue;
            }
            Set<Long> idjiSkupin = nivo.getValue().stream().map(Skupina::getId).collect(Collectors.toSet());
            for (Prijava prijava : prijave) {
                if (prijava.getIdSkupina() != null && idjiSkupin.contains(prijava.getIdSkupina())
                        && prijava.getMestoVSkupini() != null) {
                    prijava.setKoncnoMesto(zacetki.get(nivo.getKey()) + prijava.getMestoVSkupini() - 1);
                }
            }
        }
        prijavaRepozitorij.saveAll(prijave);
        tekmaRepozitorij.saveAll(tekme);
    }

    /* Prvo mesto vsakega nivoja: nivoji si sledijo od najmocnejsega. */
    private static Map<Integer, Integer> zacetkiNivojev(List<Skupina> skupine, List<Prijava> prijave) {
        Map<Long, Integer> nivoPoSkupini = new HashMap<>();
        for (Skupina skupina : skupine) {
            nivoPoSkupini.put(skupina.getId(), skupina.getNivo());
        }
        Map<Integer, Integer> velikosti = new TreeMap<>();
        for (Prijava prijava : prijave) {
            Integer nivo = prijava.getIdSkupina() == null ? null : nivoPoSkupini.get(prijava.getIdSkupina());
            if (nivo != null) {
                velikosti.merge(nivo, 1, Integer::sum);
            }
        }
        Map<Integer, Integer> zacetki = new HashMap<>();
        int mesto = 1;
        for (Map.Entry<Integer, Integer> nivo : velikosti.entrySet()) {
            zacetki.put(nivo.getKey(), mesto);
            mesto += nivo.getValue();
        }
        return zacetki;
    }

    // ---------------------------------------------------------------------
    // Pregled za vmesnik
    // ---------------------------------------------------------------------

    /* Nivoji, skupine in zrebi dogodka za izpis. Pred zrebom skupin je to
       predogled razreza, po njem stanje iz baze. */
    @Transactional(readOnly = true)
    public SvRegijaDto pregled(Dogodek dogodek, List<Prijava> prijave, List<Skupina> skupine,
                               List<Tekma> tekme) {
        Map<Integer, List<Skupina>> poNivojih = predtekmovalnePoNivojih(skupine);
        boolean zrebane = !poNivojih.isEmpty();
        boolean zacete = tekme.stream().anyMatch(
                t -> t.getStatus() == StatusTekme.KONCANA || t.getStatus() == StatusTekme.V_IGRI);
        int skupin = skupinNaNivo(dogodek);
        int velikost = velikostSkupine(dogodek);
        List<Integer> rocne = dogodek.velikostiNivojevSeznam();

        List<SvRegijaDto.NivoDto> nivoji = new ArrayList<>();
        String zadrzek = null;
        if (!zrebane) {
            int prijavljenih = (int) prijave.stream()
                    .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN).count();
            try {
                for (NivoRazrez nivo : razrez(dogodek, prijavljenih)) {
                    List<SvRegijaDto.ZrebDto> zrebi = new ArrayList<>();
                    int mesto = nivo.odMesta();
                    for (int d = 0; d < nivo.velikostiZrebov().size(); d++) {
                        int st = nivo.velikostiZrebov().get(d);
                        zrebi.add(zrebDto(null, d, mesto, st, false, false, false, List.of()));
                        mesto += st;
                    }
                    nivoji.add(new SvRegijaDto.NivoDto(nivo.nivo(), nivo.velikost(), nivo.odMesta(),
                            nivo.doMesta(), nivo.velikostiSkupin(),
                            rangovNivoja(dogodek.rangovVZrebSeznam(), nivo.nivo()), zrebi));
                }
            } catch (DomenskaIzjema e) {
                zadrzek = e.getMessage();
            }
        } else {
            Map<Long, Long> clanov = prijave.stream()
                    .filter(p -> p.getIdSkupina() != null)
                    .collect(Collectors.groupingBy(Prijava::getIdSkupina, Collectors.counting()));
            Map<Long, List<Tekma>> tekmePoZrebu = tekme.stream()
                    .filter(t -> t.getIdZreb() != null)
                    .collect(Collectors.groupingBy(Tekma::getIdZreb));
            boolean vTeku = dogodek.getStatus() == StatusTekmovanja.V_TEKU;
            int odMesta = 1;
            for (Map.Entry<Integer, List<Skupina>> nivo : poNivojih.entrySet()) {
                List<Integer> velikosti = nivo.getValue().stream()
                        .map(s -> clanov.getOrDefault(s.getId(), 0L).intValue()).toList();
                int velikostNivoja = velikosti.stream().mapToInt(Integer::intValue).sum();
                List<SvRegijaDto.ZrebDto> zrebi = zrebRepozitorij
                        .findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), nivo.getKey()).stream()
                        .map(z -> zrebDto(z.getId(), z.getIndeks(), z.getPrvoMesto(), z.getStUdelezencev(),
                                z.isZgrajen(), z.isRocni(),
                                vTeku && z.isZgrajen() && tekmePoZrebu.getOrDefault(z.getId(), List.of())
                                        .stream().allMatch(t -> t.getStatus() == StatusTekme.CAKA
                                                || t.getStatus() == StatusTekme.PRIPRAVLJENA),
                                z.razporedKotSeznam()))
                        .toList();
                nivoji.add(new SvRegijaDto.NivoDto(nivo.getKey(), velikostNivoja, odMesta,
                        odMesta + velikostNivoja - 1, velikosti,
                        rangovNivoja(dogodek.rangovVZrebSeznam(), nivo.getKey()), zrebi));
                odMesta += velikostNivoja;
            }
        }
        return new SvRegijaDto(nivoji, zadrzek, zrebane,
                zrebane && dogodek.getStatus() == StatusTekmovanja.V_TEKU && !zacete,
                skupin, velikost, dogodek.getSteviloNivojev(), rocne, dogodek.rangovVZrebSeznam());
    }

    private static SvRegijaDto.ZrebDto zrebDto(Long id, int indeks, int prvoMesto, int stUdelezencev,
                                              boolean zgrajen, boolean rocni, boolean uredljiv,
                                              List<Long> razpored) {
        return new SvRegijaDto.ZrebDto(id, indeks, Zreb.faza(indeks), Zreb.ime(indeks), prvoMesto,
                prvoMesto + stUdelezencev - 1, stUdelezencev, Zreb.velikostMreze(stUdelezencev),
                zgrajen, rocni, uredljiv, razpored);
    }

    // ---------------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------------

    private Dogodek najdiDogodek(Long idDogodka) {
        Dogodek dogodek = dogodekRepozitorij.najdiSTurnirjem(idDogodka)
                .orElseThrow(() -> new NiNajdenoIzjema("Dogodek z id " + idDogodka + " ne obstaja."));
        if (dogodek.getSistemTekmovanja() != SistemTekmovanja.SV_REGIJA) {
            throw new DomenskaIzjema("Dogodek ne igra sistema SV regija.");
        }
        return dogodek;
    }

    private Zreb najdiZreb(Long idZreba) {
        return zrebRepozitorij.findById(idZreba)
                .orElseThrow(() -> new NiNajdenoIzjema("Žreb z id " + idZreba + " ne obstaja."));
    }

    private static SvPredlogDto.ClanDto clan(Prijava prijava, Map<Long, Integer> ratingi) {
        return new SvPredlogDto.ClanDto(
                prijava.getId(),
                imeIgralca(prijava),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.getIgralec() != null ? ratingi.get(prijava.getIgralec().getId()) : null,
                prijava.getStNosilca());
    }

    private static String imeIgralca(Prijava prijava) {
        return prijava.jeEkipa() ? prijava.getEkipa().prikazanoIme() : prijava.getIgralec().polnoIme();
    }
}
