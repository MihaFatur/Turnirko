/* Ucenje mreze stevk (F6, samo admin): nocno in na zahtevo (»Uci zdaj«), z
   zgodovino razlicic in vrnitvijo prejsnje.

   Kopija trenutne mreze se dotrenira na shranjenih stevkah (listki in odobrene
   vadnice, UcenjeIzListkov) in izboru MNIST, da ne pozabi splosnih oblik. Peti del
   stevk (po listku ali listu vadnice) ni v ucenju: na njem se primerja nova mreza
   s staro. Nova se sprejme samo, ce (F6.3)
   - na preizkusnih stevkah ne bere slabse,
   - na zlatem naboru (celi listki, F1.5) nima vec napacnih celic - ce je nabor
     dovolj velik,
   - na MNIST ne nazaduje za vec kot eno odstotno tocko.
   Vsak poskus se zapise (tudi zavrnjen in prekinjen), da admin vidi, kaj se je
   zgodilo; odlozen poskus (premalo stevk) ne, ker se ponovi, ko jih pride vec.

   Hkrati tece najvec eno ucenje (zastavica tece; vrnitev prejsnje vzame isto
   zastavico, zato se ne more prepletati s sprejemom). Aplikacija tece v enem
   vsebniku, zato zaklepa v bazi ni: drzal bi povezavo do 30 minut. »Uci zdaj«
   ima premor 10 minut in potrebuje enako novih stevk kot nocno ucenje - sicer
   bi ponavljanje na istih stevkah prej ali slej po nakljucju »preseglo« staro
   mrezo na istem preizkusnem delu (izbiranje po sumu).

   Ucenje se prekine po 30 minutah (zapis »prekinjeno«, mreza ostane stara).
   Mreza se zamenja sele po zapisu sprejete razlicice: ob padcu streznika vmes
   ostane stara in se ob zagonu nalozi zadnja zapisana (VirMrezeStevk). */
package si.turnirko.storitve;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.dto.MrezeStevkDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.MrezaStevkZapis;
import si.turnirko.modeli.MrezaStevkZapis.Nacin;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.repozitoriji.MrezaStevkRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;

@Service
public class UcenjeMreze {

    private static final Logger log = LoggerFactory.getLogger(UcenjeMreze.class);
    private static final Locale SL = Locale.forLanguageTag("sl");

    /* Toliko novih stevk mora biti, da se ucenje splaca (~10 listkov ali 1,5 lista vadnice). */
    static final int NAJMANJ_NOVIH = 200;
    /* Zgornja meja stevk v enem ucenju (ucenje ostane kratko). */
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
    /* Stevke listkov in vadnic stejejo vec kot MNIST: v vsaki epohi po toliko
       kopij, mocne oznake (popravljene celice, F6.2) se enkrat toliko. */
    static final int UTEZ_STEVK = 4;
    /* Zlati nabor pri sprejemu: prvih toliko listkov (stara in nova mreza, vsak
       listek nekaj desetink sekunde). Pod NAJMANJ_ZLATIH nabor ne odloca. */
    static final int ZLATIH_ZA_SPREJEM = 400;
    static final int NAJMANJ_ZLATIH = 20;
    /* MNIST (splosne oblike stevk) sme pasti za najvec toliko. */
    static final double PADEC_MNIST = 0.01;
    static final Duration NAJDALJ = Duration.ofMinutes(30);
    static final Duration PREMOR = Duration.ofMinutes(10);
    /* Zgodovina na strani: toliko zadnjih razlicic. */
    static final int V_ZGODOVINI = 30;

    private final StevkaListkaRepozitorij stevke;
    private final MrezaStevkRepozitorij mreze;
    private final VirMrezeStevk vir;
    private final MeritevNabora meritevNabora;
    private final ZlatiNabor zlatiNabor;
    private final LastnistvoStoritev lastnistvo;
    private final TransactionTemplate transakcija;

    private final AtomicBoolean tece = new AtomicBoolean(false);
    private volatile LocalDateTime ucenjeOd;
    private volatile Nacin nacinUcenja;
    private volatile LocalDateTime zadnjiZagon;
    private volatile String sporocilo;
    private volatile LocalDateTime sporociloOb;
    private final ExecutorService nit = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ucenje-mreze");
        t.setDaemon(true);
        // nizja prednost: branje listkov in odgovori strezniku imajo prednost
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public UcenjeMreze(StevkaListkaRepozitorij stevke, MrezaStevkRepozitorij mreze, VirMrezeStevk vir,
                       MeritevNabora meritevNabora, ZlatiNabor zlatiNabor, LastnistvoStoritev lastnistvo,
                       PlatformTransactionManager upraviteljTransakcij) {
        this.stevke = stevke;
        this.mreze = mreze;
        this.vir = vir;
        this.meritevNabora = meritevNabora;
        this.zlatiNabor = zlatiNabor;
        this.lastnistvo = lastnistvo;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
    }

    @PreDestroy
    void ustavi() {
        nit.shutdownNow();
    }

    /* Preizkusni del (petina): stevke istega listka (isti pisec, ista luc) so
       vedno v istem delu - sicer bi mreza pisavo, na kateri se uci, videla tudi
       pri preizkusu in bi bila natancnost videti boljsa, kot je. Enako list
       vadnice: en list je en pisec. Stevka brez listka in lista (izbrisano
       branje) se deli po svojem id. Razdelitev je stalna: vsak peti listek
       (list) po id, tudi med listki istega turnirja. */
    static boolean jePreizkusna(StevkaListka s) {
        long kljuc = s.getIdBranje() != null ? s.getIdBranje()
                : s.getIdVadnica() != null ? s.getIdVadnica() : s.getId();
        return Math.floorMod(kljuc, 5L) == 0;
    }

    // ---------- Stanje, »Uci zdaj«, »Vrni prejsnjo« ----------

    public MrezeStevkDto stanje() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        return transakcija.execute(s -> {
            LocalDateTime zadnje = mreze.zadnjeUcenjeOb();
            long novih = stevke.countByOdobrenoTrueAndUstvarjenoObAfter(zadnje == null ? ZACETEK : zadnje);
            LocalDateTime zagon = zadnjiZagon;
            LocalDateTime od = zagon == null ? null : zagon.plus(PREMOR);
            return new MrezeStevkDto(vir.idTrenutne(), tece.get(), ucenjeOd, nacinUcenja, sporocilo, sporociloOb,
                    novih, NAJMANJ_NOVIH, stevke.countByOdobrenoTrue(), stevke.countByIdVadnicaIsNotNullAndOdobrenoTrue(),
                    od != null && od.isAfter(LocalDateTime.now()) ? od : null,
                    mreze.zgodovina(PageRequest.of(0, V_ZGODOVINI)));
        });
    }

    /* Admin sprozi ucenje v ozadju. Ko ze tece, je v premoru ali je novih stevk
       premalo, pove zakaj (409). */
    public MrezeStevkDto uciZdaj() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        LocalDateTime zagon = zadnjiZagon;
        if (zagon != null && zagon.plus(PREMOR).isAfter(LocalDateTime.now())) {
            throw new DomenskaIzjema("Učenje je bilo pognano ob " + ura(zagon) + ". Znova ga lahko poženeš ob "
                    + ura(zagon.plus(PREMOR)) + ".");
        }
        LocalDateTime zadnje = mreze.zadnjeUcenjeOb();
        long novih = stevke.countByOdobrenoTrueAndUstvarjenoObAfter(zadnje == null ? ZACETEK : zadnje);
        if (novih < NAJMANJ_NOVIH) {
            throw new DomenskaIzjema("Od zadnjega učenja je " + novih + " novih števk, učenje jih potrebuje vsaj "
                    + NAJMANJ_NOVIH + ". Odobri še kak list vadnice ali počakaj na potrjene listke.");
        }
        if (!zacni(Nacin.ROCNO)) {
            throw new DomenskaIzjema("Učenje že teče.");
        }
        return stanje();
    }

    @Scheduled(cron = "${turnirko.branje-listkov.ucenje-urnik:0 50 3 * * *}", zone = "Europe/Ljubljana")
    public void nocno() {
        if (!zacni(Nacin.NOC)) {
            log.info("Nocno ucenje mreze stevk preskoceno: ucenje ze tece");
        }
    }

    /* Zazene ucenje v svoji niti; false, ce ze tece. */
    boolean zacni(Nacin nacin) {
        if (!tece.compareAndSet(false, true)) {
            return false;
        }
        LocalDateTime zdaj = LocalDateTime.now();
        ucenjeOd = zdaj;
        nacinUcenja = nacin;
        zadnjiZagon = zdaj;
        try {
            nit.execute(() -> {
                try {
                    uci(nacin, 2, 3, NAJDALJ);
                } catch (RuntimeException | Error e) {
                    // tudi Error (pomnilnik): zastavica ne sme ostati »uci se«
                    log.error("Ucenje mreze stevk ni uspelo", e);
                    sporoci("Učenje ni uspelo. Podrobnosti so v dnevniku strežnika.");
                } finally {
                    ucenjeOd = null;
                    nacinUcenja = null;
                    tece.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            ucenjeOd = null;
            nacinUcenja = null;
            tece.set(false);
            throw new DomenskaIzjema("Strežnik se ustavlja. Poskusi znova.");
        }
        return true;
    }

    /* Umakne razlicico v uporabi in nalozi prejsnjo sprejeto (ali vgrajeno). Med
       ucenjem ne gre: nova razlicica bi nastala iz umaknjene. */
    public MrezeStevkDto vrniPrejsnjo() {
        MeritevBranjaStoritev.preveriAdmina(lastnistvo.trenutni());
        if (!tece.compareAndSet(false, true)) {
            throw new DomenskaIzjema("Učenje teče. Počakaj, da se konča, nato vrni prejšnjo mrežo.");
        }
        try {
            Long idTrenutne = vir.idTrenutne();
            if (idTrenutne == null) {
                throw new DomenskaIzjema("V uporabi je vgrajena mreža (naučena samo na MNIST); prejšnje ni.");
            }
            VirMrezeStevk.Nalozena prejsnja = transakcija.execute(s -> {
                mreze.findById(idTrenutne).ifPresent(m -> m.setUmaknjenaOb(LocalDateTime.now()));
                return mreze.findFirstBySprejetaTrueAndUmaknjenaObIsNullAndIdLessThanOrderByIdDesc(idTrenutne)
                        .map(UcenjeMreze::nalozi)
                        .orElseGet(VirMrezeStevk::vgrajena);
            });
            vir.nastavi(prejsnja.mreza(), prejsnja.oznaka());
            log.info("Mreza stevk {} umaknjena, v uporabi je {}", idTrenutne, prejsnja.oznaka());
        } finally {
            tece.set(false);
        }
        return stanje();
    }

    private static VirMrezeStevk.Nalozena nalozi(MrezaStevkZapis zapis) {
        try {
            return new VirMrezeStevk.Nalozena(MrezaStevk.preberi(new ByteArrayInputStream(zapis.getUtezi())),
                    VirMrezeStevk.PREDPONA_UCENE + zapis.getId());
        } catch (IOException e) {
            throw new IllegalStateException("Razlicica mreze " + zapis.getId() + " ni berljiva.", e);
        }
    }

    // ---------- Ucenje ----------

    private static final LocalDateTime ZACETEK = LocalDateTime.of(2000, 1, 1, 0, 0);

    /* Zlati nabor: listkov in napacnih celic stare in nove mreze. */
    record Zlati(int listkov, float prej, float potem, int narobePrej, int narobePotem) {}

    /* Dotrenira mrezo, ce je novih stevk dovolj (sinhrono: nit ucenja ali test);
       vrne zapis ucenja ali null (odlozeno). Klicatelj skrbi, da tece samo eno. */
    MrezaStevkZapis uci(Nacin nacin, int niti, int epoh, Duration najdalj) {
        long zacetekNs = System.nanoTime();
        long rok = zacetekNs + najdalj.toNanos();
        BooleanSupplier prekini = () -> Thread.currentThread().isInterrupted() || System.nanoTime() > rok;

        VirMrezeStevk.Nalozena trenutna = vir.trenutna();
        if (trenutna == null) {
            return null;
        }
        LocalDateTime zadnje = mreze.zadnjeUcenjeOb();
        long novih = stevke.countByOdobrenoTrueAndUstvarjenoObAfter(zadnje == null ? ZACETEK : zadnje);
        if (novih < NAJMANJ_NOVIH) {
            if (nacin == Nacin.ROCNO) {
                sporoci("Učenje odloženo: " + novih + " novih števk (najmanj " + NAJMANJ_NOVIH + ").");
            }
            return null;
        }
        List<StevkaListka> vse = transakcija.execute(s -> stevke.najnovejse(PageRequest.of(0, NAJVEC_STEVK)));
        List<UcenjeStevk.Vzorec> ucni = new ArrayList<>();
        List<UcenjeStevk.Vzorec> mocni = new ArrayList<>();
        List<UcenjeStevk.Vzorec> preizkusni = new ArrayList<>();
        for (StevkaListka s : vse) {
            UcenjeStevk.Vzorec v = new UcenjeStevk.Vzorec(s.getSlika(), s.getVrednost());
            if (jePreizkusna(s)) {
                preizkusni.add(v);
            } else {
                ucni.add(v);
                if (s.isPopravljena()) {
                    mocni.add(v);
                }
            }
        }
        if (preizkusni.size() < NAJMANJ_PREIZKUSNIH) {
            // brez dovolj preizkusnih stevk nove mreze ni mogoce pravicno primerjati s staro;
            // zapisa ni, zato se ucenje poskusi znova, ko stevk pride vec
            log.info("Ucenje mreze stevk odlozeno: {} preizkusnih stevk (najmanj {})",
                    preizkusni.size(), NAJMANJ_PREIZKUSNIH);
            sporoci("Učenje odloženo: " + preizkusni.size() + " preizkusnih števk (najmanj " + NAJMANJ_PREIZKUSNIH
                    + "). Preizkusna je približno vsaka peta vadnica ali listek.");
            return null;
        }
        List<UcenjeStevk.Vzorec> mnistPreizkus = beri("/branje/mnist-preizkus.bin.gz");
        List<UcenjeStevk.Vzorec> mesano = new ArrayList<>(beri("/branje/mnist-izbor.bin.gz"));
        for (int k = 0; k < UTEZ_STEVK; k++) {
            mesano.addAll(ucni);
            mesano.addAll(mocni);
        }
        double prej = UcenjeStevk.natancnost(trenutna.mreza(), preizkusni);
        MrezaStevk nova = trenutna.mreza().kopija();
        Double potem = null;
        Double mnistPrej = null;
        Double mnistPotem = null;
        Zlati zlati = null;
        String razlog;
        boolean prekinjeno = false;
        try {
            UcenjeStevk.uci(nova, mesano, epoh, 0.0003f, niti, new Random().nextLong(), null, prekini);
            potem = UcenjeStevk.natancnost(nova, preizkusni);
            mnistPrej = UcenjeStevk.natancnost(trenutna.mreza(), mnistPreizkus);
            mnistPotem = UcenjeStevk.natancnost(nova, mnistPreizkus);
            zlati = zlati(trenutna.mreza(), nova, prekini);
            razlog = razlogZavrnitve(prej, potem, mnistPrej, mnistPotem, zlati);
        } catch (UcenjeStevk.Prekinjeno e) {
            if (Thread.currentThread().isInterrupted()) {
                // streznik se ustavlja: nic se ne zapise, mreza ostane stara
                log.warn("Ucenje mreze stevk prekinjeno ob ustavitvi streznika");
                return null;
            }
            prekinjeno = true;
            razlog = "Prekinjeno po " + najdalj.toMinutes() + " min; mreža je ostala stara.";
        }
        boolean sprejeta = razlog == null;
        byte[] utezi = prekinjeno ? null : utezi(nova);
        int trajanje = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - zacetekNs) / 1_000_000);
        MrezaStevkZapis nov = new MrezaStevkZapis(utezi, vse.size(), (float) prej,
                potem == null ? null : potem.floatValue(), sprejeta, LocalDateTime.now())
                .meritve(nacin, preizkusni.size(), f(mnistPrej), f(mnistPotem),
                        zlati == null ? null : zlati.listkov(), zlati == null ? null : zlati.prej(),
                        zlati == null ? null : zlati.potem(), trajanje, razlog);
        MrezaStevkZapis zapis = transakcija.execute(s -> mreze.save(nov));
        if (sprejeta) {
            vir.nastavi(nova, VirMrezeStevk.PREDPONA_UCENE + zapis.getId());
        }
        sporocilo = null;
        log.info("Ucenje mreze stevk ({}): {} stevk, preizkusne {} -> {}, MNIST {} -> {}, zlati nabor {}, {} ms, {}",
                nacin, vse.size(), prej, potem, mnistPrej, mnistPotem, zlati, trajanje,
                sprejeta ? "sprejeta" : "zavrnjena: " + razlog);
        return zapis;
    }

    /* Zlati nabor stare in nove mreze na istih listkih; null, ce je nabor premajhen. */
    private Zlati zlati(MrezaStevk stara, MrezaStevk nova, BooleanSupplier prekini) {
        if (zlatiNabor.velikost() < NAJMANJ_ZLATIH) {
            return null;
        }
        MeritevBranjaDto.Skupina a = meritevNabora.izmeri(stara, ZLATIH_ZA_SPREJEM, prekini, n -> { });
        MeritevBranjaDto.Skupina b = meritevNabora.izmeri(nova, ZLATIH_ZA_SPREJEM, prekini, n -> { });
        return new Zlati(b.izmerjenih(), delez(a.celice()), delez(b.celice()), narobe(a.celice()),
                narobe(b.celice()));
    }

    /* Zakaj nova mreza ni sprejeta; null = sprejeta (F6.3). */
    static String razlogZavrnitve(double prej, double potem, double mnistPrej, double mnistPotem, Zlati zlati) {
        if (potem < prej) {
            return "Na preizkusnih števkah slabša: " + odstotek(prej) + " → " + odstotek(potem) + ".";
        }
        if (zlati != null && zlati.narobePotem() > zlati.narobePrej()) {
            return "Na zlatem naboru več napačnih celic: " + zlati.narobePrej() + " → " + zlati.narobePotem() + ".";
        }
        if (mnistPotem < mnistPrej - PADEC_MNIST) {
            return "Na MNIST slabša za več kot 1 odstotno točko: " + odstotek(mnistPrej) + " → "
                    + odstotek(mnistPotem) + ".";
        }
        return null;
    }

    private static int narobe(MeritevBranjaDto.Celice c) {
        return c.narobeZanesljive() + c.narobeNegotove();
    }

    private static float delez(MeritevBranjaDto.Celice c) {
        int prav = c.pravZanesljive() + c.pravNegotove();
        int vseh = prav + narobe(c);
        return vseh == 0 ? 0f : (float) prav / vseh;
    }

    private static String odstotek(double delez) {
        return String.format(SL, "%.2f %%", delez * 100);
    }

    private static String ura(LocalDateTime cas) {
        LocalDateTime c = cas.truncatedTo(ChronoUnit.MINUTES);
        return String.format("%d.%02d", c.getHour(), c.getMinute());
    }

    private static Float f(Double d) {
        return d == null ? null : d.floatValue();
    }

    private void sporoci(String besedilo) {
        sporocilo = besedilo;
        sporociloOb = LocalDateTime.now();
    }

    private static byte[] utezi(MrezaStevk mreza) {
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            mreza.zapisi(izhod);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return izhod.toByteArray();
    }

    private static List<UcenjeStevk.Vzorec> beri(String vir) {
        try (InputStream vhod = UcenjeMreze.class.getResourceAsStream(vir)) {
            return vhod == null ? List.of() : UcenjeStevk.beriIzbor(vhod);
        } catch (IOException e) {
            throw new IllegalStateException("Izbor MNIST ni berljiv: " + vir, e);
        }
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
}
