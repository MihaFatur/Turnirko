/* Bralnik se uci iz potrjenih listkov (odlocitev lastnika, 19. 11. 2026).

   1. Ob potrditvi branja se ista fotografija razreze znova in vsaki stevki se
      pripise vrednost, ki jo je potrdil organizator (samo okenca s stevilkami,
      brez imen). Stevke se shranijo v stevka_listka - tudi tiste, ki jih je mreza
      prebrala prav, ker so prav tako primer pisave iz vasih dvoran.
   2. Vsako noc, ko je novih stevk dovolj, se kopija trenutne mreze dotrenira na
      vseh shranjenih (z nakljucnim popacenjem) in izboru MNIST, da ne pozabi
      splosnih oblik. Peti del stevk listkov ni v ucenju: na njih se primerja nova
      mreza s staro. Nova se sprejme samo, ce bere bolje in na MNIST ne nazaduje.
   Vse tece na strezniku; ucenje traja minute, ponoci. */
package si.turnirko.storitve;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.MrezaStevkZapis;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.repozitoriji.MrezaStevkRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;

@Service
public class UcenjeIzListkov {

    private static final Logger log = LoggerFactory.getLogger(UcenjeIzListkov.class);

    /* Toliko novih stevk mora biti, da se nocno ucenje splaca (~10 listkov). */
    static final int NAJMANJ_NOVIH = 200;
    /* Zgornja meja stevk v enem ucenju (noc ostane kratka). */
    static final int NAJVEC_STEVK = 60_000;
    /* Pod toliko preizkusnimi stevkami primerjava stare in nove mreze ni zanesljiva. */
    static final int NAJMANJ_PREIZKUSNIH = 50;
    /* Hramba: stevk (784 B) najvec toliko, ~160 MB; ucenje vzame le najnovejsih
       NAJVEC_STEVK, starejse so samo zgodovina. */
    static final int NAJVEC_SHRANJENIH = 200_000;
    /* Hramba naucenih razlicic (~320 kB vsaka): sprejete za morebitno vrnitev,
       zavrnjene za pregled, zakaj ucenje ni uspelo. */
    static final int SPREJETIH_V_HRAMBI = 20;
    static final int ZAVRNJENIH_V_HRAMBI = 10;

    /* Preizkusni del (petina): stevke istega listka (isti pisec, ista luc) so
       vedno v istem delu - sicer bi mreza pisavo, na kateri se uci, videla tudi
       pri preizkusu in bi bila natancnost videti boljsa, kot je. Stevka brez
       listka (vadnica, preizkus) se deli po svojem id. Razdelitev je stalna:
       vsak peti listek po id, tudi med listki istega turnirja. */
    static boolean jePreizkusna(StevkaListka s) {
        long kljuc = s.getIdBranje() != null ? s.getIdBranje() : s.getId();
        return Math.floorMod(kljuc, 5L) == 0;
    }

    private final StevkaListkaRepozitorij stevke;
    private final MrezaStevkRepozitorij mreze;
    private final SlikaBranjaListkaRepozitorij slike;
    private final LokalniBralnikListka bralnik;
    private final VirMrezeStevk vir;
    private final ZlatiNabor zlatiNabor;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;

    public UcenjeIzListkov(StevkaListkaRepozitorij stevke, MrezaStevkRepozitorij mreze,
                           SlikaBranjaListkaRepozitorij slike, LokalniBralnikListka bralnik,
                           VirMrezeStevk vir, ZlatiNabor zlatiNabor,
                           @Qualifier("branjeListkovIzvajalec") Executor izvajalec,
                           PlatformTransactionManager upraviteljTransakcij) {
        this.stevke = stevke;
        this.mreze = mreze;
        this.slike = slike;
        this.bralnik = bralnik;
        this.vir = vir;
        this.zlatiNabor = zlatiNabor;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    // ---------- Zbiranje ob potrditvi ----------

    /* Po potrditvi (ko je izid v bazi) razreze fotografijo in zapise stevke.
       obrnjeno: levi stolpec listka je prijava 2; null = ni znano (prazen listek). */
    public void poPotrditvi(Long idBranja, VnosRezultata izid, Boolean obrnjeno, int steviloNizov, String model) {
        Runnable delo = () -> {
            try {
                zabelezi(idBranja, izid, obrnjeno, steviloNizov, model);
            } catch (RuntimeException e) {
                log.warn("Stevk potrjenega listka {} ni bilo mogoce zabeleziti", idBranja, e);
            }
        };
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
    private void oddaj(Long idBranja, Runnable delo) {
        try {
            izvajalec.execute(delo);
        } catch (RejectedExecutionException e) {
            log.warn("Stevke potrjenega listka {} niso bile zabelezene: vrsta branja je polna", idBranja);
        }
    }

    /* Vrne stevilo zapisanih stevk. Listek gre (morda) se v zlati nabor:
       steviloNizov je stevilo nizov tekme ob potrditvi, model mreza, ki je brala. */
    public int zabelezi(Long idBranja, VnosRezultata izid, Boolean obrnjeno, int steviloNizov, String model) {
        if (izid.izidTip() != null && izid.izidTip() != IzidTekme.IGRANO) {
            return 0;
        }
        byte[] fotografija = transakcija.execute(s -> slike.findById(idBranja).map(f -> f.getVsebina()).orElse(null));
        VirMrezeStevk.Nalozena nalozena = vir.trenutna();
        if (fotografija == null || nalozena == null) {
            return 0;
        }
        LokalniBralnikListka.Poravnan poravnan;
        try {
            poravnan = bralnik.poravnaj(fotografija);
        } catch (BralnikListka.NapakaBranjaListka e) {
            return 0;
        }
        LokalniBralnikListka.Izrez izrez = LokalniBralnikListka.izrezi(poravnan);
        List<NizVnos> nizi = izid.nizi() == null ? List.of() : izid.nizi();
        List<StevkaListka> zapis;
        if (obrnjeno != null) {
            zapis = oznaci(idBranja, izrez, nizi, izid, obrnjeno, nalozena.mreza());
        } else {
            // prazen listek: smer, v kateri se mreza bolj strinja s potrjenim izidom
            List<StevkaListka> naravnost = oznaci(idBranja, izrez, nizi, izid, false, nalozena.mreza());
            List<StevkaListka> obrat = oznaci(idBranja, izrez, nizi, izid, true, nalozena.mreza());
            long prav1 = naravnost.stream().filter(StevkaListka::isMrezaPrav).count();
            long prav2 = obrat.stream().filter(StevkaListka::isMrezaPrav).count();
            zapis = prav1 == prav2 ? List.of() : prav1 > prav2 ? naravnost : obrat;
        }
        transakcija.executeWithoutResult(s -> stevke.saveAll(zapis));
        // v zlati nabor samo listek z znano smerjo (koda): smer, izbrana po ujemanju
        // z mrezo, bi resnico prilagodila bralniku in meritev olepsala
        if (obrnjeno != null) {
            // stevke so ze zapisane; napaka nabora ne sme izgubiti njih
            try {
                zlatiNabor.zapisi(idBranja, poravnan, MeritevBranja.resnica(izid, obrnjeno), steviloNizov, model);
            } catch (RuntimeException e) {
                log.warn("Listek {} ni bil zapisan v zlati nabor", idBranja, e);
            }
        }
        return zapis.size();
    }

    private static List<StevkaListka> oznaci(Long idBranja, LokalniBralnikListka.Izrez izrez, List<NizVnos> nizi,
                                             VnosRezultata izid, boolean obrnjeno, MrezaStevk mreza) {
        List<StevkaListka> zapis = new ArrayList<>();
        LocalDateTime zdaj = LocalDateTime.now();
        for (int i = 0; i < nizi.size() && i < izrez.nizi().size(); i++) {
            NizVnos n = nizi.get(i);
            int levo = obrnjeno ? n.tocke2() : n.tocke1();
            int desno = obrnjeno ? n.tocke1() : n.tocke2();
            dodaj(zapis, idBranja, izrez.nizi().get(i)[0], levo, mreza, zdaj);
            dodaj(zapis, idBranja, izrez.nizi().get(i)[1], desno, mreza, zdaj);
        }
        if (izid.dobljeniNizi1() != null && izid.dobljeniNizi2() != null) {
            dodaj(zapis, idBranja, izrez.rezultat()[0], obrnjeno ? izid.dobljeniNizi2() : izid.dobljeniNizi1(), mreza, zdaj);
            dodaj(zapis, idBranja, izrez.rezultat()[1], obrnjeno ? izid.dobljeniNizi1() : izid.dobljeniNizi2(), mreza, zdaj);
        }
        return zapis;
    }

    /* Stevke celice dobijo oznake, ce je razrez z ustreznim stevilom stevk med razlagami. */
    private static void dodaj(List<StevkaListka> zapis, Long idBranja, RazrezCelice.Vsebina celica, int vrednost,
                              MrezaStevk mreza, LocalDateTime zdaj) {
        if (celica.prazna()) {
            return;
        }
        String stevke = String.valueOf(vrednost);
        RazrezCelice.Razlaga ustrezna = celica.razlage().stream()
                .filter(r -> r.stevke().size() == stevke.length())
                .max(java.util.Comparator.comparingDouble(RazrezCelice.Razlaga::verjetnost))
                .orElse(null);
        if (ustrezna == null) {
            return;
        }
        for (int i = 0; i < stevke.length(); i++) {
            int prava = stevke.charAt(i) - '0';
            float[] vhod = ustrezna.stevke().get(i).vhod();
            float[] p = mreza.verjetnosti(vhod, 1);
            int najboljsa = 0;
            for (int o = 1; o < p.length; o++) {
                if (p[o] > p[najboljsa]) {
                    najboljsa = o;
                }
            }
            zapis.add(new StevkaListka(idBranja, UcenjeStevk.vBajte(vhod), prava, najboljsa == prava, zdaj));
        }
    }

    // ---------- Nocno ucenje ----------

    @Scheduled(cron = "${turnirko.branje-listkov.ucenje-urnik:0 50 3 * * *}", zone = "Europe/Ljubljana")
    public void nocno() {
        try {
            uci(2, 3);
        } catch (RuntimeException e) {
            log.error("Nocno ucenje mreze stevk ni uspelo", e);
        }
    }

    /* Dotrenira mrezo, ce je novih stevk dovolj; vrne zapis ucenja ali null. */
    public MrezaStevkZapis uci(int niti, int epoh) {
        VirMrezeStevk.Nalozena trenutna = vir.trenutna();
        if (trenutna == null) {
            return null;
        }
        LocalDateTime zadnje = mreze.findFirstByOrderByIdDesc().map(MrezaStevkZapis::getUstvarjenoOb)
                .orElse(LocalDateTime.of(2000, 1, 1, 0, 0));
        if (stevke.countByUstvarjenoObAfter(zadnje) < NAJMANJ_NOVIH) {
            return null;
        }
        List<StevkaListka> vse = transakcija.execute(s -> stevke.najnovejse(PageRequest.of(0, NAJVEC_STEVK)));
        List<UcenjeStevk.Vzorec> ucni = new ArrayList<>();
        List<UcenjeStevk.Vzorec> preizkusni = new ArrayList<>();
        for (StevkaListka s : vse) {
            UcenjeStevk.Vzorec v = new UcenjeStevk.Vzorec(s.getSlika(), s.getVrednost());
            (jePreizkusna(s) ? preizkusni : ucni).add(v);
        }
        if (preizkusni.size() < NAJMANJ_PREIZKUSNIH) {
            // brez dovolj preizkusnih stevk nove mreze ni mogoce pravicno primerjati s staro;
            // zapisa ni, zato se ucenje poskusi znova, ko listkov pride vec
            log.info("Ucenje mreze stevk odlozeno: {} preizkusnih stevk (najmanj {})",
                    preizkusni.size(), NAJMANJ_PREIZKUSNIH);
            return null;
        }
        List<UcenjeStevk.Vzorec> izbor = beri("/branje/mnist-izbor.bin.gz");
        List<UcenjeStevk.Vzorec> mnistPreizkus = beri("/branje/mnist-preizkus.bin.gz");

        // stevke listkov stejejo vec kot MNIST: po stirikrat v vsaki epohi
        List<UcenjeStevk.Vzorec> mesano = new ArrayList<>(izbor);
        for (int k = 0; k < 4; k++) {
            mesano.addAll(ucni);
        }
        MrezaStevk nova = trenutna.mreza().kopija();
        UcenjeStevk.uci(nova, mesano, epoh, 0.0003f, niti, new Random().nextLong(), null);

        double prej = UcenjeStevk.natancnost(trenutna.mreza(), preizkusni);
        double potem = UcenjeStevk.natancnost(nova, preizkusni);
        double mnistPrej = UcenjeStevk.natancnost(trenutna.mreza(), mnistPreizkus);
        double mnistPotem = UcenjeStevk.natancnost(nova, mnistPreizkus);
        boolean sprejeta = potem >= prej && mnistPotem >= mnistPrej - 0.01;

        ByteArrayOutputStream utezi = new ByteArrayOutputStream();
        try {
            nova.zapisi(utezi);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MrezaStevkZapis zapis = transakcija.execute(s -> mreze.save(new MrezaStevkZapis(utezi.toByteArray(),
                vse.size(), (float) prej, (float) potem, sprejeta, LocalDateTime.now())));
        if (sprejeta) {
            vir.nastavi(nova, VirMrezeStevk.PREDPONA_UCENE + zapis.getId());
        }
        log.info("Ucenje mreze stevk: {} stevk listkov, natancnost {} -> {} (MNIST {} -> {}), {}",
                vse.size(), prej, potem, mnistPrej, mnistPotem, sprejeta ? "sprejeta" : "zavrnjena");
        return zapis;
    }

    // ---------- Hramba ----------

    /* Pocisti stare stevke in razlicice mreze (nocno, OpravilaBranjaListkov).
       Vrne {izbrisanih stevk, izbrisanih razlicic}. */
    public int[] pocisti() {
        return pocisti(NAJVEC_SHRANJENIH, SPREJETIH_V_HRAMBI, ZAVRNJENIH_V_HRAMBI);
    }

    int[] pocisti(int najvecStevk, int sprejetih, int zavrnjenih) {
        // razlicica, ki jo bralnik ta trenutek uporablja, ostane ne glede na starost
        Long trenutna = vir.idTrenutne();
        long ohrani = trenutna == null ? -1 : trenutna;
        return transakcija.execute(s -> new int[] {
                stevke.izbrisiRazenNajnovejsih(najvecStevk),
                mreze.izbrisiStarejse(true, sprejetih, ohrani) + mreze.izbrisiStarejse(false, zavrnjenih, ohrani)});
    }

    private static List<UcenjeStevk.Vzorec> beri(String vir) {
        try (InputStream vhod = UcenjeIzListkov.class.getResourceAsStream(vir)) {
            return vhod == null ? List.of() : UcenjeStevk.beriIzbor(vhod);
        } catch (IOException e) {
            throw new IllegalStateException("Izbor MNIST ni berljiv: " + vir, e);
        }
    }
}
