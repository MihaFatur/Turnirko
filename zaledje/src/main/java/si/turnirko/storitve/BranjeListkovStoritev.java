/* Branje sodniskih listkov s fotografije (V68): organizator fotografira listek,
   streznik predlaga izid, organizator ga potrdi.

   Potek enega branja:
   1. sprejmi: fotografija (JPEG brez metapodatkov) se shrani, branje dobi stanje
      V_VRSTI in zahteva se takoj konca - organizator ze fotografira naslednjega.
   2. obdelaj (v ozadju, BranjeListkovNastavitve): QR koda doloci tekmo, bralnik
      (LokalniBralnikListka, lastna mreza stevk) prepise tocke; brez berljive kode
      izbere tekmo organizator. Stanje PREBRANO ali NAPAKA.
   3. potrdi: izid, kot ga je organizator potrdil ali popravil, gre skozi
      TekmaStoritev.vnesiRezultat - isto pot in iste preverbe kot rocni vnos.
      Branje samo nikoli ne zapise izida.

   Branje (QR koda, poravnava, mreza stevk; nekaj sto milisekund) tece BREZ odprte
   transakcije, zato ima obdelaj dve kratki transakciji okrog njega.

   Dostop: lastnik turnirja ali admin (LastnistvoStoritev); dokler je
   turnirko.branje-listkov.samo-admin vklopljen, samo admin - preizkus pred
   meritvijo natancnosti na pravih listkih.

   Merilo natancnosti: ob potrditvi se zapise, ali je organizator predlog
   spremenil (popravljeno). Seznam vrne, koliko potrjenih je bilo brez popravka. */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


import si.turnirko.dto.BranjaTurnirjaDto;
import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.CeliceListkaDto;
import si.turnirko.dto.BranjeListkaDto.KandidatListka;
import si.turnirko.dto.BranjeListkaDto.TekmaListka;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.StanjeBranjaListkovDto;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.izjeme.ZasedenoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.SodniskaPotrditev;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.UjemanjeTekmeListka.Kandidat;
import si.turnirko.storitve.UjemanjeTekmeListka.Oseba;

@Service
public class BranjeListkovStoritev {

    private static final Logger log = LoggerFactory.getLogger(BranjeListkovStoritev.class);

    /* Daljsa stranica shranjene fotografije. Stevke na listku (105 x 99 mm, cez
       ves posnetek) so pri 2000 px visoke ~70 px, QR koda ima ~8 px na modul;
       vec samo podaljsa branje in poveca bazo. */
    static final int NAJVEC_STRANICA = 2000;
    /* Meja PREJETE fotografije. Brskalnik jo pomanjsa na 2000 px, postaja na
       ~2600 px; meja dovoli se nepomanjsano 16-Mpx fotografijo telefona
       (4624 x 3472), vec pa ne: dekodiranje 20 Mpx zasede ~140 MB pomnilnika,
       splosna meja slik (40 Mpx) bi ga zasedla dvakrat toliko. */
    static final int NAJVEC_STRANICA_VHODA = 6000;
    static final long NAJVEC_PIKSLOV_VHODA = 20_000_000L;
    /* Hkrati dekodirane fotografije: testni vsebnik ima 1 GB pomnilnika, dve
       veliki sliki hkrati ga se ne ogrozita. Naslednja zahteva pocaka do
       CAKANJE_NA_DEKODIRANJE, nato dobi 503 s ponovitvijo cez nekaj sekund. */
    static final int HKRATNIH_DEKODIRANJ = 2;
    static final int PASOV_UPORABNIKOV = 64;
    static final Duration CAKANJE_NA_DEKODIRANJE = Duration.ofSeconds(10);
    static final int PONOVI_CEZ_SEKUND = 5;
    /* Vsako branje stane: ena fotografija na 5 s je hitrejse, kot organizator
       sploh prinasa listke, zato meja ustavi le zanko ali zlorabo. */
    static final int NAJVEC_V_OKNU = 120;
    static final Duration OKNO = Duration.ofMinutes(10);
    /* Fotografija ostane toliko dni po turnirju (odlocitev lastnika, 19. 11. 2026). */
    static final int DNI_HRAMBE = 30;
    /* Toliko vrsta na telefonu hrani neposlano fotografijo (pomozno/vrstaFotografij.ts). */
    static final Duration KLJUC_POSILJANJA_VELJA = Duration.ofHours(24);
    /* Zakljucenih branj (potrjenih, zavrzenih) seznam pokaze toliko zadnjih. */
    static final int ZADNJIH_ZAKLJUCENIH = 30;
    /* Listek brez kode: toliko tekem, ki cakajo na izid, je ponujenih za izbiro. */
    static final int NAJVEC_IZBIRE = 200;
    /* Razlog zaprtja branja, ki ga je nadomestila novejsa fotografija istega listka. */
    static final String NADOMESCENO = "Nadomeščeno z novejšo fotografijo istega listka.";
    /* Razlog zaprtja novejse neuspele fotografije, ko je starejsa istega listka prebrana. */
    static final String VELJA_STAREJSA = "Fotografije ni bilo mogoče prebrati; velja starejša fotografija istega listka.";
    /* Branje, ki je tako dolgo V_VRSTI, je obviselo (npr. zavrnjeno ob polni vrsti) in se sme prebrati znova. */
    static final Duration OBVISELO_PO = Duration.ofMinutes(2);
    /* Razlog zaprtja odprtega branja, ko je bila tekma potrjena z drugo fotografijo. */
    static final String POTRJENO_DRUGJE = "Tekma je bila potrjena z drugo fotografijo istega listka.";
    /* Opozorilo nakljucne kontrole (V69): predlog je videti zanesljiv, pa se pregleda v celoti. */
    static final String KONTROLA = "Naključna kontrola: primerjaj vse številke s fotografijo.";
    /* Cas pregleda nad uro je pozabljeno okno, ne pregled (meritev ga ne zapise). */
    static final long NAJVEC_CAS_PREGLEDA_MS = 3_600_000L;

    private final BranjeListkaRepozitorij branjaRepozitorij;
    private final SlikaBranjaListkaRepozitorij slikeRepozitorij;
    private final TurnirRepozitorij turnirRepozitorij;
    private final TekmaRepozitorij tekmaRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final LastnistvoStoritev lastnistvo;
    private final TekmaStoritev tekmaStoritev;
    private final MizeStoritev mize;
    private final BralnikListka bralnik;
    private final UcenjeIzListkov ucenje;
    private final Executor izvajalec;
    private final TransactionTemplate transakcija;
    private boolean samoAdmin;
    /* Delez pripravljenih predlogov, ki postanejo nakljucna kontrola (0-1). */
    private double delezKontrole;
    /* Dovoljenja za dekodiranje (HKRATNIH_DEKODIRANJ). */
    private final Semaphore dekodiranje = new Semaphore(HKRATNIH_DEKODIRANJ, true);
    /* Zakljucek branja (zapis predloga ali napake), potrditev, zavrzenje in
       ponovno branje tecejo po eno naenkrat, da se dvojniki istega listka
       zanesljivo vidijo (zapriDvojnike) in si spremembe ne prepisejo. */
    private final Object zakljucevanje = new Object();
    /* Pas semaforjev po uporabniku: fotografije enega uporabnika se dekodirajo
       ena za drugo, zato en organizator z vec hkratnimi zahtevami zasede najvec
       eno od HKRATNIH_DEKODIRANJ dovoljenj in drugi niso blokirani. Pas namesto
       slovarja po uporabniku: nic ni treba pospravljati; dva uporabnika v istem
       pasu se le pocakata. */
    private final Semaphore[] poUporabnikih = new Semaphore[PASOV_UPORABNIKOV];
    {
        for (int i = 0; i < PASOV_UPORABNIKOV; i++) {
            poUporabnikih[i] = new Semaphore(1, true);
        }
    }
    private Duration cakanjeNaDekodiranje = CAKANJE_NA_DEKODIRANJE;

    public BranjeListkovStoritev(BranjeListkaRepozitorij branjaRepozitorij,
                                 SlikaBranjaListkaRepozitorij slikeRepozitorij,
                                 TurnirRepozitorij turnirRepozitorij,
                                 TekmaRepozitorij tekmaRepozitorij,
                                 SkupinaRepozitorij skupinaRepozitorij,
                                 LastnistvoStoritev lastnistvo,
                                 TekmaStoritev tekmaStoritev,
                                 BralnikListka bralnik,
                                 UcenjeIzListkov ucenje,
                                 @Qualifier("branjeListkovIzvajalec") Executor izvajalec,
                                 PlatformTransactionManager upraviteljTransakcij,
                                 @Value("${turnirko.branje-listkov.samo-admin:true}") boolean samoAdmin,
                                 @Value("${turnirko.branje-listkov.kontrola:0.05}") double delezKontrole,
                                 MizeStoritev mize) {
        this.mize = mize;
        this.branjaRepozitorij = branjaRepozitorij;
        this.slikeRepozitorij = slikeRepozitorij;
        this.turnirRepozitorij = turnirRepozitorij;
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.lastnistvo = lastnistvo;
        this.tekmaStoritev = tekmaStoritev;
        this.bralnik = bralnik;
        this.ucenje = ucenje;
        this.izvajalec = izvajalec;
        this.transakcija = new TransactionTemplate(upraviteljTransakcij);
        this.samoAdmin = samoAdmin;
        nastaviDelezKontrole(delezKontrole);
    }

    /* Samo za teste: preizkus organizatorja brez lastnega konteksta Springa. */
    void nastaviSamoAdmin(boolean samoAdmin) {
        this.samoAdmin = samoAdmin;
    }

    /* Delez kontrol; nastavitev izven 0-1 obvelja kot najblizja meja, NaN kot 0. */
    void nastaviDelezKontrole(double delez) {
        this.delezKontrole = Double.isNaN(delez) ? 0 : Math.clamp(delez, 0.0, 1.0);
    }

    /* Samo za teste: kratko cakanje, da preizkus zasedenosti ne traja 10 s. */
    void nastaviCakanjeNaDekodiranje(Duration cakanje) {
        this.cakanjeNaDekodiranje = cakanje;
    }

    /* Samo za teste: semafor dekodiranja (prek metode, ker je storitev posrednik
       Springa in polja posrednika so prazna). */
    Semaphore semaforDekodiranja() {
        return dekodiranje;
    }

    // ---------- Dostop ----------

    public StanjeBranjaListkovDto stanje() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            return new StanjeBranjaListkovDto(false, "Za branje listkov se prijavi.");
        }
        if (jaz.getVloga() != Vloga.ADMIN && jaz.getVloga() != Vloga.ORGANIZATOR) {
            return new StanjeBranjaListkovDto(false, "Branje listkov je namenjeno organizatorjem.");
        }
        if (!bralnik.jeNastavljen()) {
            return new StanjeBranjaListkovDto(false, "Branje listkov na tem strežniku ni nastavljeno.");
        }
        if (samoAdmin && jaz.getVloga() != Vloga.ADMIN) {
            return new StanjeBranjaListkovDto(false, "Branje listkov je v preizkusu in še ni na voljo.");
        }
        return new StanjeBranjaListkovDto(true, null);
    }

    private Uporabnik preveriDostop(Long idTurnir) {
        StanjeBranjaListkovDto stanje = stanje();
        if (!stanje.omogoceno()) {
            throw new PrepovedanoIzjema(stanje.razlog());
        }
        lastnistvo.preveriTurnirPoId(idTurnir);
        return lastnistvo.trenutni();
    }

    // ---------- Sprejem fotografije ----------

    /* Sprejem v treh korakih, da dekodiranje (najdrazji del, do ~140 MB
       pomnilnika) ne drzi povezave z bazo in ga ne more sproziti nihce brez
       pravice:
       1. dostop in omejitev hitrosti (kratka transakcija);
       2. dekodiranje in pomanjsanje brez transakcije, najvec
          HKRATNIH_DEKODIRANJ hkrati;
       3. zapis branja in fotografije (kratka transakcija); branje gre v vrsto
          sele po potrditvi zapisa. */
    public BranjeListkaDto sprejmi(Long idTurnir, byte[] vsebina) {
        return sprejmi(idTurnir, vsebina, null);
    }

    /* kljuc: nakljucni kljuc posiljanja z vrste na telefonu (glava Idempotency-Key,
       F5.2) ali null. Ce je uporabnik s tem kljucem fotografijo ze poslal (odgovor
       se je izgubil), vrne ze ustvarjeno branje: brez novega dekodiranja, brez
       dvojnika in brez stetja v omejitev hitrosti. */
    public BranjeListkaDto sprejmi(Long idTurnir, byte[] vsebina, String kljuc) {
        String kljucPosiljanja = preveriKljuc(kljuc);
        Prejeto prejeto = transakcija.execute(s -> {
            Uporabnik jaz = preveriDostop(idTurnir);
            BranjeListkaDto poslano = zePoslano(jaz.getId(), kljucPosiljanja, idTurnir);
            if (poslano != null) {
                return new Prejeto(jaz.getId(), poslano);
            }
            if (branjaRepozitorij.countByNalozilIdAndUstvarjenoObAfter(jaz.getId(),
                    LocalDateTime.now().minus(OKNO)) >= NAJVEC_V_OKNU) {
                throw new PrevecZahtevIzjema("Preveč fotografij v kratkem času. Počakaj nekaj minut.");
            }
            return new Prejeto(jaz.getId(), null);
        });
        if (prejeto.poslano() != null) {
            return prejeto.poslano();
        }
        ObdelavaSlik.Zapis zapis = pripraviFotografijo(vsebina, prejeto.idUporabnika());
        try {
            return transakcija.execute(s -> {
                Uporabnik jaz = preveriDostop(idTurnir);
                // isti kljuc je medtem (med dekodiranjem) prisel se enkrat in je ze zapisan
                BranjeListkaDto poslano = zePoslano(jaz.getId(), kljucPosiljanja, idTurnir);
                if (poslano != null) {
                    return poslano;
                }
                BranjeListka novo = new BranjeListka(turnirRepozitorij.getReferenceById(idTurnir), jaz,
                        LocalDateTime.now());
                novo.setKljucPosiljanja(kljucPosiljanja);
                BranjeListka branje = branjaRepozitorij.saveAndFlush(novo);
                slikeRepozitorij.save(new SlikaBranjaListka(branje.getId(), zapis.vsebina(), zapis.sirina(),
                        zapis.visina()));
                vVrsto(branje.getId());
                return dto(branje, Map.of(), Map.of(), List.of());
            });
        } catch (DataIntegrityViolationException e) {
            // dve zahtevi z istim kljucem hkrati: edinstveni indeks je zavrnil drugo
            if (kljucPosiljanja == null) {
                throw e;
            }
            BranjeListkaDto poslano = transakcija.execute(s -> zePoslano(prejeto.idUporabnika(), kljucPosiljanja,
                    idTurnir));
            if (poslano == null) {
                throw e;
            }
            return poslano;
        }
    }

    /* Prvi korak sprejema: uporabnik in morebitno ze poslano branje z istim kljucem. */
    private record Prejeto(Long idUporabnika, BranjeListkaDto poslano) {}

    /* Kljuc posiljanja: nakljucen niz z vrste (crypto.randomUUID), ne vsebuje nic
       osebnega. Brez kljuca (prazen) ni zascite pred dvojniki, kot doslej. */
    static String preveriKljuc(String kljuc) {
        if (kljuc == null || kljuc.isBlank()) {
            return null;
        }
        if (!kljuc.matches("[A-Za-z0-9-]{8,64}")) {
            throw new NeveljavenVnosIzjema("Neveljaven ključ pošiljanja fotografije.");
        }
        return kljuc;
    }

    /* Branje, ki ga je uporabnik s tem kljucem ze poslal; kljuc drugega turnirja je
       napaka odjemalca (vrsta posilja vsako fotografijo na svoj turnir). */
    private BranjeListkaDto zePoslano(Long idUporabnika, String kljuc, Long idTurnir) {
        if (kljuc == null) {
            return null;
        }
        return branjaRepozitorij.poKljucuPosiljanja(idUporabnika, kljuc)
                .map(b -> {
                    if (!skupniTurnirji(idTurnir).contains(b.getTurnir().getId())) {
                        throw new NeveljavenVnosIzjema("Ključ pošiljanja je že uporabljen za drug turnir.");
                    }
                    return dto(b, Map.of(), Map.of(), List.of());
                })
                .orElse(null);
    }

    /* Dekodira, pomanjsa in znova zapise fotografijo (brez metapodatkov). Cez
       mejo hkratnih dekodiranj zahteva pocaka; ce predolgo, dobi 503 in jo
       odjemalec ponovi - bolje kot da bi vec velikih slik hkrati izcrpalo
       pomnilnik streznika. */
    ObdelavaSlik.Zapis pripraviFotografijo(byte[] vsebina, Long idUporabnika) {
        return podDekodiranjem(idUporabnika, () -> ObdelavaSlik.zapisiFotografijo(ObdelavaSlik.pomanjsaj(
                ObdelavaSlik.preberi(vsebina, NAJVEC_STRANICA_VHODA, NAJVEC_PIKSLOV_VHODA), NAJVEC_STRANICA)));
    }

    /* Delo z veliko sliko pod mejo hkratnih dekodiranj. Meja je skupna listkom in
       vadnici stevk (VadnicaStoritev), ker si delita pomnilnik streznika. */
    <T> T podDekodiranjem(Long idUporabnika, Supplier<T> delo) {
        long rok = System.nanoTime() + cakanjeNaDekodiranje.toNanos();
        Semaphore pas = pasUporabnika(idUporabnika);
        if (!dobi(pas, rok)) {
            throw zasedeno();
        }
        try {
            if (!dobi(dekodiranje, rok)) {
                throw zasedeno();
            }
            try {
                return delo.get();
            } finally {
                dekodiranje.release();
            }
        } finally {
            pas.release();
        }
    }

    /* Dovoljenje do skupnega roka (pas uporabnika in skupni semafor skupaj ne
       cakata dlje kot cakanjeNaDekodiranje). */
    private static boolean dobi(Semaphore semafor, long rok) {
        try {
            return semafor.tryAcquire(Math.max(0, rok - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static ZasedenoIzjema zasedeno() {
        return new ZasedenoIzjema("Strežnik ravno obdeluje druge fotografije. Poskusi znova čez nekaj sekund.",
                PONOVI_CEZ_SEKUND);
    }

    /* Pas uporabnika (vidno testu prek metode - storitev je posrednik Springa). */
    Semaphore pasUporabnika(Long idUporabnika) {
        return poUporabnikih[Math.floorMod(idUporabnika, PASOV_UPORABNIKOV)];
    }

    /* Branje gre na izvajalca sele po potrditvi transakcije - prej ga nit v
       ozadju v bazi se ne bi videla. */
    void vVrsto(Long idBranja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    oddaj(idBranja);
                }
            });
        } else {
            oddaj(idBranja);
        }
    }

    /* Polna vrsta nalogo zavrne (BranjeListkovNastavitve). Pisati v bazo tu ne
       smemo (klic iz afterCommit bi se pridruzil ze potrjeni transakciji), zato
       branje ostane V_VRSTI; ob naslednjem zagonu postane napaka, ki jo
       organizator prebere znova. Pri vrsti 10.000 se to v praksi ne zgodi. */
    private void oddaj(Long idBranja) {
        try {
            izvajalec.execute(() -> obdelaj(idBranja));
        } catch (RejectedExecutionException e) {
            log.error("Branje listka {} ni slo v vrsto: vrsta branja je polna", idBranja);
        }
    }

    // ---------- Branje (v ozadju) ----------

    private record Vhod(Long idTurnir, byte[] slika) {}

    /* Prebere kodo in tocke ter zapise predlog. Ne vrze nicesar: vsaka napaka
       konca v stanju NAPAKA z razlago za organizatorja. */
    public void obdelaj(Long idBranja) {
        Vhod vhod = transakcija.execute(s -> {
            BranjeListka branje = branjaRepozitorij.findById(idBranja).orElse(null);
            if (branje == null || branje.getStanje() != StanjeBranja.V_VRSTI) {
                return null;
            }
            SlikaBranjaListka slika = slikeRepozitorij.findById(idBranja).orElse(null);
            if (slika == null) {
                oznaciNapako(branje, "Fotografije ni več.", List.of());
                return null;
            }
            return new Vhod(branje.getTurnir().getId(), slika.getVsebina());
        });
        if (vhod == null) {
            return;
        }

        List<Long> kode = preberiKode(vhod.slika());
        BralnikListka.Branje prepis;
        try {
            prepis = bralnik.preberi(vhod.slika());
        } catch (BralnikListka.NapakaBranjaListka e) {
            zapisiNapako(idBranja, e.getMessage(), kode);
            return;
        } catch (RuntimeException e) {
            log.error("Branje listka {} ni uspelo", idBranja, e);
            zapisiNapako(idBranja, "Branje ni uspelo. Poskusi znova.", kode);
            return;
        }

        try {
            synchronized (zakljucevanje) {
                transakcija.executeWithoutResult(s -> {
                    BranjeListka branje = branjaRepozitorij.findById(idBranja).orElse(null);
                    if (branje == null || branje.getStanje() != StanjeBranja.V_VRSTI) {
                        return;
                    }
                    prepoznajTekmo(branje, vhod.idTurnir(), kode, prepis.listek());
                    branje.setPrebrano(vJson(prepis.listek()));
                    // poravnan listek za izrez celic ob poljih (F4.1); ob ponovnem branju se zamenja
                    BralnikListka.Poravnava poravnava = prepis.poravnava();
                    branje.setCelice(poravnava == null ? null : vJson(poravnava.celice()));
                    slikeRepozitorij.findById(idBranja)
                            .ifPresent(slika -> slika.setPoravnana(poravnava == null ? null : poravnava.jpeg()));
                    branje.setModel(prepis.model());
                    branje.setStanje(StanjeBranja.PREBRANO);
                    branje.setPrebranoOb(LocalDateTime.now());
                    // kontrola ostane tudi po »Preberi znova«: sicer bi se ji organizator
                    // lahko izognil in ocena ne bi bila vec nepristranska
                    branje.setKontrola(branje.isKontrola() || jeKontrola(branje, prepis.listek()));
                    zapriDvojnike(branje);
                });
            }
        } catch (RuntimeException e) {
            // brez tega bi branje ostalo V_VRSTI (»se bere«) do ponovnega zagona
            log.error("Zapis branja listka {} ni uspel", idBranja, e);
            zapisiNapako(idBranja, "Branje ni uspelo. Poskusi znova.", kode);
        }
    }

    /* Nakljucna kontrola: pripravljen predlog (videti zanesljiv) z verjetnostjo
       delezKontrole ni ponujen za en dotik. Ker je izbira nakljucna, popravki pri
       kontrolah nepristransko ocenijo, kako pogosto ima zanesljiv predlog napako,
       ki je organizator pri potrditvi z enim dotikom ne bi opazil. Najprej zreb,
       sele nato predlog (poizvedbe), saj tece pod zaklepom zakljucevanje. */
    private boolean jeKontrola(BranjeListka branje, PrebranListek listek) {
        if (delezKontrole <= 0 || branje.getTekma() == null || listek == null) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < delezKontrole
                && predlog(branje, listek, branje.getTekma(), false).pripravljen();
    }

    /* Isti listek je lahko fotografiran veckrat (ponovljen posnetek; postaja ga
       zajame znova, ko ga organizator vstavi se enkrat). Med odprtimi branji iste
       tekme po kodi ostane eno: najnovejse uspesno prebrano, sicer najnovejse.
       Ostala se zaprejo kot nadomescena - organizator potrdi eno, drugo ne visi
       v vrsti z opozorilom »izid ze vpisan«. Potrjenih se ne dotakne.
       Klice se samo pod zaklepom zakljucevanje: dve niti, ki hkrati zakljucita
       dve fotografiji istega listka, bi se sicer zgresili (vsaka ne vidi se
       nepotrjene druge) in obe bi ostali odprti. Aplikacija tece v enem
       primerku, zato zadosca zaklep v pomnilniku. */
    private void zapriDvojnike(BranjeListka branje) {
        if (branje.getTekma() == null || branje.getPrepoznava() != PrepoznavaTekme.KODA) {
            return;
        }
        // samo branja po kodi: tekmo, ugotovljeno drugace, je treba se potrditi
        List<BranjeListka> odprta = branjaRepozitorij.findByTekmaIdAndStanjeIn(branje.getTekma().getId(),
                List.of(StanjeBranja.PREBRANO, StanjeBranja.NAPAKA)).stream()
                .filter(b -> b.getPrepoznava() == PrepoznavaTekme.KODA)
                .toList();
        if (odprta.size() < 2) {
            return;
        }
        Long ostane = odprta.stream()
                .max(Comparator.comparing((BranjeListka b) -> b.getStanje() == StanjeBranja.PREBRANO)
                        .thenComparing(BranjeListka::getId))
                .orElseThrow().getId();
        LocalDateTime zdaj = LocalDateTime.now();
        for (BranjeListka b : odprta) {
            if (!b.getId().equals(ostane)) {
                b.setStanje(StanjeBranja.ZAVRZENO);
                // novejsa neuspela fotografija se umakne prebrani starejsi - razlog to pove
                b.setNapaka(b.getId() > ostane ? VELJA_STAREJSA : NADOMESCENO);
                b.setZakljucenoOb(zdaj);
            }
        }
    }

    private static List<Long> preberiKode(byte[] jpeg) {
        try {
            BufferedImage slika = ImageIO.read(new ByteArrayInputStream(jpeg));
            return slika == null ? List.of() : KodaListka.preberiQr(slika);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /* Tekma po QR kodi, sicer po natisnjeni stevilki (ki jo je prebral model in
       se zato preveri z imeni), sicer po imenih. */
    private void prepoznajTekmo(BranjeListka branje, Long idTurnir, List<Long> kode, PrebranListek listek) {
        if (kode.size() > 1) {
            branje.setNapaka("Na fotografiji je več listkov. Fotografiraj vsakega posebej.");
            return;
        }
        if (kode.size() == 1) {
            Tekma tekma = tekmaSkupnihMiz(kode.getFirst(), idTurnir);
            if (tekma != null) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
                return;
            }
            branje.setNapaka("Koda na listku ne pripada tekmi tega turnirja.");
        }
        Long izNatisnjene = KodaListka.izNatisnjene(listek.koda()).orElse(null);
        if (izNatisnjene != null) {
            Tekma tekma = tekmaSkupnihMiz(izNatisnjene, idTurnir);
            // prebrana stevka je lahko napacna (T-48213 / T-48218): veljajo samo, ce se ujemajo imena
            if (tekma != null && (nimaImen(listek) || ujemanjeImen(listek, tekma) >= UjemanjeTekmeListka.PRAG_IZBIRE)) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
                return;
            }
        }
        UjemanjeTekmeListka.Izid izid = UjemanjeTekmeListka.najdi(listek.imeLevo(), listek.imeDesno(),
                listek.kategorija(), kandidati(tekmaRepozitorij.cakajoceNaListekZaTurnirje(skupniTurnirji(idTurnir))));
        if (izid.izbrana() != null) {
            branje.setTekma(tekmaRepozitorij.getReferenceById(izid.izbrana().idTekme()));
            branje.setPrepoznava(PrepoznavaTekme.IMENA);
        }
    }

    /* Tekma turnirja ali turnirja, s katerim si deli mize (V78): pult bere listke vseh
       turnirjev v dvorani (lastnik 25. 11. 2026). Branje ostane na turnirju, kjer je bila
       fotografija poslana; potrditev preveri se dostop do turnirja tekme. */
    private Tekma tekmaSkupnihMiz(Long idTekme, Long idTurnir) {
        List<Long> skupni = skupniTurnirji(idTurnir);
        return tekmaRepozitorij.najdiZVsem(idTekme)
                .filter(t -> skupni.contains(t.getDogodek().getTurnir().getId()))
                .orElse(null);
    }

    /* Turnir in turnirji, s katerimi si deli mize (V78); sam, ce jih ne deli. */
    private List<Long> skupniTurnirji(Long idTurnir) {
        Long glavni = turnirRepozitorij.idGlavnegaZaMize(idTurnir).orElse(idTurnir);
        List<Long> idji = turnirRepozitorij.idjiSkupnihMiz(List.of(glavni));
        return idji.isEmpty() ? List.of(idTurnir) : idji;
    }

    private void zapisiNapako(Long idBranja, String sporocilo, List<Long> kode) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> branjaRepozitorij.findById(idBranja)
                    .filter(b -> b.getStanje() == StanjeBranja.V_VRSTI)
                    .ifPresent(b -> {
                        oznaciNapako(b, sporocilo, kode);
                        zapriDvojnike(b);
                    }));
        }
    }

    /* Ob napaki bralnika se tekma po kodi vseeno zapise: organizator lahko izid
       vpise rocno ob fotografiji, ne da bi tekmo iskal. */
    private void oznaciNapako(BranjeListka branje, String sporocilo, List<Long> kode) {
        branje.setStanje(StanjeBranja.NAPAKA);
        branje.setNapaka(sporocilo);
        branje.setPrebranoOb(LocalDateTime.now());
        if (kode.size() == 1) {
            Tekma tekma = tekmaSkupnihMiz(kode.getFirst(), branje.getTurnir().getId());
            if (tekma != null) {
                branje.setTekma(tekma);
                branje.setPrepoznava(PrepoznavaTekme.KODA);
            }
        }
    }

    // ---------- Seznam ----------

    @Transactional(readOnly = true)
    public BranjaTurnirjaDto seznam(Long idTurnir) {
        preveriDostop(idTurnir);
        List<BranjeListka> vsa = branjaRepozitorij.najdiZaTurnirje(skupniTurnirji(idTurnir));
        List<BranjeListka> prikaz = new ArrayList<>();
        int zakljucenih = 0;
        int potrjenih = 0;
        int brezPopravka = 0;
        for (BranjeListka b : vsa) {
            if (b.getStanje() == StanjeBranja.POTRJENO) {
                potrjenih++;
                if (Boolean.FALSE.equals(b.getPopravljeno())) {
                    brezPopravka++;
                }
            }
            if (b.getStanje().jeOdprto() || zakljucenih++ < ZADNJIH_ZAKLJUCENIH) {
                prikaz.add(b);
            }
        }

        boolean rabimKandidate = prikaz.stream()
                .anyMatch(b -> b.getStanje() == StanjeBranja.PREBRANO && b.getTekma() == null);
        List<Tekma> cakajoce = rabimKandidate ? tekmaRepozitorij.cakajoceNaListekZaTurnirje(skupniTurnirji(idTurnir)) : List.of();
        Set<Long> idji = prikaz.stream().map(BranjeListka::getTekma).filter(Objects::nonNull)
                .map(Tekma::getId).collect(Collectors.toSet());
        Map<Long, Tekma> tekme = new HashMap<>();
        cakajoce.forEach(t -> tekme.put(t.getId(), t));
        idji.removeAll(tekme.keySet());
        if (!idji.isEmpty()) {
            tekmaRepozitorij.najdiZVsemPoIdjih(idji).forEach(t -> tekme.put(t.getId(), t));
        }
        Map<Long, String> skupine = oznakeSkupin(tekme.values());

        List<BranjeListkaDto> branja = prikaz.stream().map(b -> dto(b, tekme, skupine, cakajoce)).toList();
        return new BranjaTurnirjaDto(branja, potrjenih, brezPopravka,
                turnirRepozitorij.findById(idTurnir).map(Turnir::getSodnikiListki).orElse(null));
    }

    /* Stikalo »Sodniki slikajo listke« (S2): null izklopi. Sme ga organizator, ki
       sme brati listke tega turnirja (dokler velja samo-admin, samo admin). Pri
       skupnih mizah (V78) velja za vse turnirje v dvorani: pult jih vodi kot celoto. */
    @Transactional
    public SodniskaPotrditev nastaviSodnike(Long idTurnir, SodniskaPotrditev nacin) {
        preveriDostop(idTurnir);
        turnirRepozitorij.findById(idTurnir)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + idTurnir + " ne obstaja."));
        for (Turnir turnir : turnirRepozitorij.findAllById(skupniTurnirji(idTurnir))) {
            if (!turnir.getId().equals(idTurnir)) {
                preveriDostop(turnir.getId());
            }
            turnir.setSodnikiListki(nacin);
        }
        return nacin;
    }

    private Map<Long, String> oznakeSkupin(Collection<Tekma> tekme) {
        Set<Long> idji = tekme.stream().map(Tekma::getIdSkupina).filter(Objects::nonNull).collect(Collectors.toSet());
        if (idji.isEmpty()) {
            return Map.of();
        }
        return skupinaRepozitorij.findAllById(idji).stream()
                .collect(Collectors.toMap(Skupina::getId, Skupina::getOznaka));
    }

    private BranjeListkaDto dto(BranjeListka b, Map<Long, Tekma> tekme, Map<Long, String> skupine,
                                List<Tekma> cakajoce) {
        PrebranListek listek = b.getPrebrano() == null ? null : izJson(b.getPrebrano());
        TekmaListka tekma = null;
        PredlogIzidaDto predlog = null;
        List<KandidatListka> kandidati = List.of();

        Tekma t = b.getTekma() == null ? null : tekme.get(b.getTekma().getId());
        if (t != null) {
            tekma = tekmaListka(t, skupine);
            if (b.getSodnikovIzid() != null && b.getStanje() == StanjeBranja.PREBRANO) {
                // predpregled (S2): organizator potrdi izid, kot ga je potrdil sodnik
                predlog = predlogSodnika(JsonBranja.preberi(b.getSodnikovIzid(), VnosRezultata.class), t,
                        Boolean.TRUE.equals(b.getSodnikSpremenil()));
            } else if (listek != null) {
                predlog = predlog(b, listek, t);
            }
        } else if (b.getStanje() == StanjeBranja.PREBRANO && listek != null && nimaImen(listek)) {
            // listek brez kode (prazen listek): bralnik imen ne bere, tekmo izbere organizator
            kandidati = cakajoce.stream()
                    .sorted(Comparator.comparing((Tekma k) -> k.getDogodek().getIme())
                            .thenComparing(Tekma::getId))
                    .limit(NAJVEC_IZBIRE)
                    .map(k -> new KandidatListka(tekmaListka(k, skupine), false,
                            PredlogIzidaListka.sestavi(listek, k.getSteviloNizov(), false)))
                    .toList();
        } else if (b.getStanje() == StanjeBranja.PREBRANO && listek != null) {
            UjemanjeTekmeListka.Izid izid = UjemanjeTekmeListka.najdi(listek.imeLevo(), listek.imeDesno(),
                    listek.kategorija(), kandidati(cakajoce));
            Map<Long, Tekma> poId = cakajoce.stream().collect(Collectors.toMap(Tekma::getId, Function.identity()));
            kandidati = izid.kandidati().stream()
                    .map(z -> {
                        Tekma k = poId.get(z.idTekme());
                        return new KandidatListka(tekmaListka(k, skupine), z.obrnjeno(),
                                PredlogIzidaListka.sestavi(listek, k.getSteviloNizov(), z.obrnjeno()));
                    })
                    .toList();
        }
        // izrezi celic samo, dokler fotografija obstaja (poravnana slika gre z njo)
        CeliceListkaDto celice = b.getSlikaIzbrisanaOb() == null && b.getCelice() != null
                ? JsonBranja.preberi(b.getCelice(), CeliceListkaDto.class) : null;
        boolean obrnjeno = t != null && listek != null && b.getPrepoznava() != PrepoznavaTekme.KODA
                && jeObrnjeno(listek, t);
        return new BranjeListkaDto(b.getId(), b.getStanje(), b.getUstvarjenoOb(), b.getPrebranoOb(),
                b.getSlikaIzbrisanaOb() == null, b.getPrepoznava(), tekma, predlog, kandidati,
                listek == null ? null : listek.imeLevo(), listek == null ? null : listek.imeDesno(),
                b.getNapaka(), b.getPopravljeno(), jeObviselo(b), b.isKontrola(), celice, obrnjeno,
                listek == null ? null : listek.miza(), listek != null && listek.negotovaMiza(),
                b.isPoslalSodnik(), b.getSodnikPotrdilOb() != null, b.getSodnikSpremenil(), b.getMizaSodnika());
    }

    /* Sporocilo organizatorju, ko je sodnik spremenil prebrano (S2). */
    static final String SODNIK_SPREMENIL = "Sodnik je popravil prebrano. Primerjaj s fotografijo.";

    /* Sodnikov izid kot predlog v straneh tekme (S2, predpregled). Pripravljen za en
       dotik, razen ce ga je sodnik spremenil ali ima tekma izid ze vpisan. */
    static PredlogIzidaDto predlogSodnika(VnosRezultata izid, Tekma t, boolean spremenil) {
        List<PredlogIzidaDto.NizPredloga> nizi = (izid.nizi() == null ? List.<NizVnos>of() : izid.nizi()).stream()
                .map(n -> new PredlogIzidaDto.NizPredloga(n.tocke1(), n.tocke2(), false, false, false))
                .toList();
        List<String> opozorila = new ArrayList<>();
        if (t.getStatus() == StatusTekme.KONCANA) {
            opozorila.add("Ta tekma ima izid že vpisan. Listek je morda fotografiran dvakrat.");
        }
        if (spremenil) {
            opozorila.add(SODNIK_SPREMENIL);
        }
        Integer zmagovalec = izid.dobljeniNizi1() == null || izid.dobljeniNizi2() == null ? null
                : izid.dobljeniNizi1() > izid.dobljeniNizi2() ? 1 : 2;
        return new PredlogIzidaDto(IzidTekme.IGRANO, izid.dobljeniNizi1(), izid.dobljeniNizi2(), zmagovalec, nizi,
                opozorila, opozorila.isEmpty());
    }

    private static boolean jeObviselo(BranjeListka b) {
        return b.getStanje() == StanjeBranja.V_VRSTI
                && b.getUstvarjenoOb().isBefore(LocalDateTime.now().minus(OBVISELO_PO));
    }

    /* Predlog v straneh tekme z opozorili, ki jih pozna samo streznik: izid je ze
       vpisan, imena na listku niso imena tekme, na posnetku je vec listkov. */
    private PredlogIzidaDto predlog(BranjeListka b, PrebranListek listek, Tekma t) {
        return predlog(b, listek, t, true);
    }

    /* zKontrolo = false: predlog, kot bi bil brez nakljucne kontrole (ali je ponujen
       za en dotik, za kontrolo in meritev). */
    PredlogIzidaDto predlog(BranjeListka b, PrebranListek listek, Tekma t, boolean zKontrolo) {
        boolean poKodi = b.getPrepoznava() == PrepoznavaTekme.KODA;
        boolean obrnjeno = !poKodi && jeObrnjeno(listek, t);
        PredlogIzidaDto osnova = PredlogIzidaListka.sestavi(listek, t.getSteviloNizov(), obrnjeno);
        List<String> dodatna = new ArrayList<>();
        if (b.getStanje().jeOdprto() && t.getStatus() == StatusTekme.KONCANA) {
            dodatna.add("Ta tekma ima izid že vpisan. Listek je morda fotografiran dvakrat.");
        } else if (b.getStanje().jeOdprto() && t.getStatus() == StatusTekme.CAKA) {
            dodatna.add("Tekma še čaka na igralca iz prejšnjega kola.");
        }
        if (poKodi && !nimaImen(listek) && ujemanjeImen(listek, t) < UjemanjeTekmeListka.PRAG_IZBIRE) {
            dodatna.add("Imeni na listku (" + Objects.toString(listek.imeLevo(), "?") + " – "
                    + Objects.toString(listek.imeDesno(), "?") + ") se ne ujemata s tekmo.");
        }
        if (b.getStanje() == StanjeBranja.PREBRANO && b.getNapaka() != null) {
            dodatna.add(b.getNapaka());
        }
        if (zKontrolo && b.getStanje() == StanjeBranja.PREBRANO && b.isKontrola()) {
            dodatna.add(KONTROLA);
        }
        if (dodatna.isEmpty()) {
            return osnova;
        }
        List<String> opozorila = new ArrayList<>(dodatna);
        opozorila.addAll(osnova.opozorila());
        return new PredlogIzidaDto(osnova.izidTip(), osnova.dobljeniNizi1(), osnova.dobljeniNizi2(),
                osnova.zmagovalecStran(), osnova.nizi(), opozorila, false);
    }

    private TekmaListka tekmaListka(Tekma t, Map<Long, String> skupine) {
        return new TekmaListka(TekmaDto.iz(t), t.getDogodek().getId(), t.getDogodek().getIme(),
                t.getIdSkupina() == null ? null : skupine.get(t.getIdSkupina()),
                t.getDogodek().getTurnir().getId(), t.getDogodek().getTurnir().getIme());
    }

    // ---------- Potrditev, zavrnitev, ponovno branje ----------

    /* Potrditev, zavrzenje in ponovno branje spremenijo stanje branj iste tekme
       kot zakljucek branja (zapriDvojnike), zato tecejo pod istim zaklepom in
       vsaka v svoji transakciji, ki se potrdi PRED sprostitvijo zaklepa. Sicer
       bi potrditev in hkrati zakljuceno branje iste tekme drug drugemu prepisala
       stanje (branje nima stolpca verzije). Zaklep se vzame pred transakcijo,
       zato nit, ki nanj caka, ne drzi povezave z bazo. */
    public BranjeListkaDto potrdi(Long idBranja, PotrditevBranjaVnos vnos) {
        synchronized (zakljucevanje) {
            return transakcija.execute(s -> potrdiPodZaklepom(idBranja, vnos));
        }
    }

    /* Delo nad branji pod istim zaklepom in v svoji transakciji kot potrditev
       organizatorja (sodniska potrditev, S2: SodniskaBranjaStoritev). */
    <T> T podZaklepom(Supplier<T> delo) {
        synchronized (zakljucevanje) {
            return transakcija.execute(s -> delo.get());
        }
    }

    /* Ostale odprte fotografije istega listka (po kodi) nimajo vec pomena, ko je
       tekma potrjena s tem branjem. */
    void zapriOstalaBranjaTekme(Long idTekme, Long idBranja) {
        for (BranjeListka drugo : branjaRepozitorij.findByTekmaIdAndStanjeIn(idTekme,
                List.of(StanjeBranja.PREBRANO, StanjeBranja.NAPAKA))) {
            if (!drugo.getId().equals(idBranja) && drugo.getPrepoznava() == PrepoznavaTekme.KODA) {
                drugo.setStanje(StanjeBranja.ZAVRZENO);
                drugo.setNapaka(POTRJENO_DRUGJE);
                drugo.setZakljucenoOb(LocalDateTime.now());
            }
        }
    }

    public void zavrzi(Long idBranja) {
        synchronized (zakljucevanje) {
            transakcija.executeWithoutResult(s -> zavrziPodZaklepom(idBranja));
        }
    }

    /* Isto fotografijo prebere se enkrat (napaka povezave, ali slab prepis). */
    public BranjeListkaDto znova(Long idBranja) {
        synchronized (zakljucevanje) {
            return transakcija.execute(s -> znovaPodZaklepom(idBranja));
        }
    }

    private BranjeListkaDto potrdiPodZaklepom(Long idBranja, PotrditevBranjaVnos vnos) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        if (branje.getStanje() != StanjeBranja.PREBRANO && branje.getStanje() != StanjeBranja.NAPAKA) {
            throw new DomenskaIzjema(branje.getStanje() == StanjeBranja.V_VRSTI
                    ? "Listek se še bere. Počakaj nekaj sekund."
                    : NADOMESCENO.equals(branje.getNapaka())
                    ? "To fotografijo je nadomestila novejša fotografija istega listka. Potrdi tisto."
                    : VELJA_STAREJSA.equals(branje.getNapaka())
                    ? "Te fotografije ni bilo mogoče prebrati; velja starejša fotografija istega listka. Potrdi tisto."
                    : "To branje je že zaključeno.");
        }
        Long idTekme = vnos.idTekme() != null ? vnos.idTekme()
                : branje.getTekma() != null ? branje.getTekma().getId() : null;
        if (idTekme == null) {
            throw new NeveljavenVnosIzjema("Izberi tekmo, ki je na listku.");
        }
        Tekma tekma = tekmaSkupnihMiz(idTekme, branje.getTurnir().getId());
        if (tekma == null) {
            throw new NeveljavenVnosIzjema("Tekma ni s tega turnirja.");
        }
        if (!tekma.getDogodek().getTurnir().getId().equals(branje.getTurnir().getId())) {
            preveriDostop(tekma.getDogodek().getTurnir().getId()); // tekma turnirja s skupnimi mizami
        }

        boolean izbranaRocno = branje.getTekma() == null || !branje.getTekma().getId().equals(idTekme);
        PrepoznavaTekme prepoznava = izbranaRocno ? PrepoznavaTekme.ROCNO : branje.getPrepoznava();
        PrebranListek listek = branje.getPrebrano() == null ? null : izJson(branje.getPrebrano());
        PredlogIzidaDto predlog = listek == null ? null
                : PredlogIzidaListka.sestavi(listek, tekma.getSteviloNizov(),
                        prepoznava != PrepoznavaTekme.KODA && jeObrnjeno(listek, tekma));
        // ali je bil predlog ponujen za en dotik (z opozorili tekme, brez kontrole) - pred
        // vpisom izida, ki bi dodal opozorilo »izid ze vpisan«; tekmo, izbrano rocno,
        // je organizator videl s predlogom kandidata (brez opozoril tekme)
        Boolean ponujen = listek == null ? null
                : izbranaRocno ? predlog.pripravljen() : predlog(branje, listek, tekma, false).pripravljen();

        tekmaStoritev.vnesiRezultat(idTekme, vnos.izid());
        // tekma, ki ni bila poklicana, dobi mizo s listka (M1, 3.7): sodnikovo, sicer
        // zanesljivo prebrano iz okenca
        Integer mizaListka = branje.getMizaSodnika() != null ? branje.getMizaSodnika()
                : listek != null && listek.miza() != null && !listek.negotovaMiza() ? listek.miza() : null;
        mize.zapisiMizoZListka(idTekme, mizaListka);

        // napredovanje v mrezi s skupinskimi posodobitvami izprazni sejo: branje
        // od prej je odklopljeno in sprememba nanj bi se tiho izgubila
        branje = najdi(idBranja);
        branje.setTekma(tekmaRepozitorij.getReferenceById(idTekme));
        branje.setPrepoznava(prepoznava);
        branje.setStanje(StanjeBranja.POTRJENO);
        branje.setZakljucenoOb(LocalDateTime.now());
        branje.setPotrjeno(vJson(vnos.izid()));
        branje.setPopravljeno(predlog == null || !jeEnako(predlog, vnos.izid()));
        // sled pregleda za meritev (V69)
        branje.setPripravljen(ponujen);
        branje.setCasPregledaMs(casPregleda(vnos.casPregledaMs()));
        branje.setNacinPotrditve(vnos.nacin());
        zapriOstalaBranjaTekme(idTekme, idBranja);
        // bralnik se uci iz potrjenega izida; smer stolpcev je znana samo pri listku s kodo
        int steviloNizov = vnos.izid().steviloNizov() != null ? vnos.izid().steviloNizov() : tekma.getSteviloNizov();
        // celice, ki jih je organizator popravil (ali vpisal namesto bralnika), so mocne oznake (F6.2)
        ucenje.poPotrditvi(idBranja, vnos.izid(), prepoznava == PrepoznavaTekme.KODA ? Boolean.FALSE : null,
                steviloNizov, branje.getModel(), PopravljeneCelice.izPredloga(predlog, vnos.izid()));
        Tekma koncana = tekmaRepozitorij.najdiZVsem(idTekme).orElse(tekma);
        return dto(branje, Map.of(idTekme, koncana), oznakeSkupin(List.of(koncana)), List.of());
    }

    private void zavrziPodZaklepom(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        if (!branje.getStanje().jeOdprto()) {
            throw new DomenskaIzjema("To branje je že zaključeno.");
        }
        branje.setStanje(StanjeBranja.ZAVRZENO);
        branje.setZakljucenoOb(LocalDateTime.now());
    }

    private BranjeListkaDto znovaPodZaklepom(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        // obviselo branje (V_VRSTI dlje kot OBVISELO_PO, npr. zavrnjeno ob polni vrsti) se sme
        // ponoviti; ce je vseeno se v vrsti, ga drugi obdelaj preskoci (preveri V_VRSTI)
        boolean obviselo = jeObviselo(branje);
        if (branje.getStanje() != StanjeBranja.NAPAKA && branje.getStanje() != StanjeBranja.PREBRANO && !obviselo) {
            throw new DomenskaIzjema("To branje se ne da ponoviti.");
        }
        if (branje.getSlikaIzbrisanaOb() != null) {
            throw new DomenskaIzjema("Fotografija je bila izbrisana.");
        }
        branje.setStanje(StanjeBranja.V_VRSTI);
        branje.setNapaka(null);
        branje.setPrebrano(null);
        branje.setCelice(null);
        // sodnisko branje ima tekmo po preverjeni skrivnosti (S2): ostane, sicer bi
        // sodnikov zeton ostal brez tekme
        if (!branje.isPoslalSodnik()) {
            branje.setTekma(null);
            branje.setPrepoznava(null);
        }
        vVrsto(branje.getId());
        return dto(branje, Map.of(), Map.of(), List.of());
    }

    @Transactional(readOnly = true)
    public byte[] slika(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        return slikeRepozitorij.findById(idBranja)
                .map(SlikaBranjaListka::getVsebina)
                .orElseThrow(() -> new NiNajdenoIzjema("Fotografija listka je bila izbrisana."));
    }

    /* Poravnan listek za izrez celic (F4.1): isti dostop kot fotografija. */
    @Transactional(readOnly = true)
    public byte[] poravnana(Long idBranja) {
        BranjeListka branje = najdi(idBranja);
        preveriDostop(branje.getTurnir().getId());
        byte[] jpeg = branje.getSlikaIzbrisanaOb() == null ? slikeRepozitorij.poravnana(idBranja) : null;
        if (jpeg == null) {
            throw new NiNajdenoIzjema("Poravnanega listka ni.");
        }
        return jpeg;
    }

    /* Fotografija listka tekme (F4.4): zadnje potrjeno branje, dokler fotografija
       obstaja. Samo organizator turnirja tekme in admin; javnost nikoli. */
    @Transactional(readOnly = true)
    public byte[] listekTekme(Long idTekme) {
        Tekma tekma = tekmaRepozitorij.findById(idTekme)
                .orElseThrow(() -> new NiNajdenoIzjema("Tekma z id " + idTekme + " ne obstaja."));
        preveriDostop(tekma.getDogodek().getTurnir().getId());
        Long idBranja = branjaRepozitorij.zSlikoZaTekmo(idTekme, StanjeBranja.POTRJENO, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new NiNajdenoIzjema("Za to tekmo ni fotografije listka."));
        return slikeRepozitorij.findById(idBranja)
                .map(SlikaBranjaListka::getVsebina)
                .orElseThrow(() -> new NiNajdenoIzjema("Za to tekmo ni fotografije listka."));
    }

    private BranjeListka najdi(Long idBranja) {
        return branjaRepozitorij.najdiZTurnirjem(idBranja)
                .orElseThrow(() -> new NiNajdenoIzjema("Branje listka z id " + idBranja + " ne obstaja."));
    }

    // ---------- Opravila (OpravilaBranjaListkov) ----------

    /* Kljuci posiljanja, starejsi od KLJUC_POSILJANJA_VELJA: vrsta na telefonu
       fotografijo po 24 urah izbrise, zato istega kljuca ne poslje vec. */
    @Transactional
    public int pobrisiStareKljucePosiljanja() {
        return branjaRepozitorij.pobrisiKljucePosiljanja(LocalDateTime.now().minus(KLJUC_POSILJANJA_VELJA));
    }

    /* Fotografije, starejse od 30 dni po turnirju; vrstica branja ostane. */
    @Transactional
    public int izbrisiStareSlike(LocalDate danes) {
        LocalDateTime zdaj = LocalDateTime.now();
        List<Long> idji = branjaRepozitorij.zaIzbrisSlike(danes.minusDays(DNI_HRAMBE), zdaj.minusDays(DNI_HRAMBE));
        if (idji.isEmpty()) {
            return 0;
        }
        slikeRepozitorij.izbrisi(idji);
        return branjaRepozitorij.oznaciIzbrisaneSlike(idji, zdaj);
    }

    /* Ob zagonu: branja, ki so ostala v vrsti, ko se je streznik ustavil, nima
       nihce vec v rokah. Organizator jih vidi kot napako z gumbom za ponovitev. */
    @Transactional
    public int prekiniOstalaVVrsti() {
        List<BranjeListka> ostala = branjaRepozitorij.findByStanje(StanjeBranja.V_VRSTI);
        ostala.forEach(b -> {
            b.setStanje(StanjeBranja.NAPAKA);
            b.setNapaka("Branje se je prekinilo (ponovni zagon strežnika). Poskusi znova.");
        });
        return ostala.size();
    }

    // ---------- Pomozno ----------

    /* Cas pregleda, kot ga je izmeril vmesnik; nesmiselen se ne zapise (null). */
    static Integer casPregleda(Long ms) {
        return ms == null || ms < 0 || ms > NAJVEC_CAS_PREGLEDA_MS ? null : ms.intValue();
    }

    private static boolean nimaImen(PrebranListek listek) {
        return (listek.imeLevo() == null || listek.imeLevo().isBlank())
                && (listek.imeDesno() == null || listek.imeDesno().isBlank());
    }

    /* Ujemanje prebranih imen s tekmo v obeh smereh: levi stolpec = prijava 1
       (naravnost) ali levi stolpec = prijava 2 (obrat). */
    private record Smeri(double naravnost, double obrat) {}

    private static Smeri smeri(PrebranListek listek, Tekma t) {
        List<Oseba> s1 = osebe(t.getPrijava1());
        List<Oseba> s2 = osebe(t.getPrijava2());
        return new Smeri(
                Math.min(UjemanjeTekmeListka.ocena(listek.imeLevo(), s1),
                        UjemanjeTekmeListka.ocena(listek.imeDesno(), s2)),
                Math.min(UjemanjeTekmeListka.ocena(listek.imeLevo(), s2),
                        UjemanjeTekmeListka.ocena(listek.imeDesno(), s1)));
    }

    private static double ujemanjeImen(PrebranListek listek, Tekma t) {
        Smeri s = smeri(listek, t);
        return Math.max(s.naravnost(), s.obrat());
    }

    private static boolean jeObrnjeno(PrebranListek listek, Tekma t) {
        Smeri s = smeri(listek, t);
        return s.obrat() > s.naravnost();
    }

    private static List<Kandidat> kandidati(List<Tekma> tekme) {
        return tekme.stream()
                .map(t -> new Kandidat(t.getId(), osebe(t.getPrijava1()), osebe(t.getPrijava2()),
                        t.getDogodek().getIme()))
                .toList();
    }

    private static List<Oseba> osebe(Prijava p) {
        if (p == null || p.jeEkipa()) {
            return List.of();
        }
        List<Oseba> osebe = new ArrayList<>();
        osebe.add(new Oseba(p.getIgralec().getIme(), p.getIgralec().getPriimek()));
        if (p.jePar()) {
            osebe.add(new Oseba(p.getIgralec2().getIme(), p.getIgralec2().getPriimek()));
        }
        return osebe;
    }

    /* Ali je organizator potrdil natanko predlog (merilo natancnosti bralnika). */
    static boolean jeEnako(PredlogIzidaDto predlog, VnosRezultata vnos) {
        IzidTekme izid = vnos.izidTip() == null ? IzidTekme.IGRANO : vnos.izidTip();
        if (izid != predlog.izidTip()) {
            return false;
        }
        if (izid == IzidTekme.BREZ_BOJA || izid == IzidTekme.DISKVALIFIKACIJA) {
            return Objects.equals(vnos.zmagovalecStran(), predlog.zmagovalecStran());
        }
        if (izid == IzidTekme.PREDAJA && !Objects.equals(vnos.zmagovalecStran(), predlog.zmagovalecStran())) {
            return false;
        }
        if (!Objects.equals(vnos.dobljeniNizi1(), predlog.dobljeniNizi1())
                || !Objects.equals(vnos.dobljeniNizi2(), predlog.dobljeniNizi2())) {
            return false;
        }
        List<NizVnos> nizi = vnos.nizi() == null ? List.of() : vnos.nizi();
        if (nizi.size() != predlog.nizi().size()) {
            return false;
        }
        for (int i = 0; i < nizi.size(); i++) {
            if (!Objects.equals(nizi.get(i).tocke1(), predlog.nizi().get(i).tocke1())
                    || !Objects.equals(nizi.get(i).tocke2(), predlog.nizi().get(i).tocke2())) {
                return false;
            }
        }
        return true;
    }

    private static String vJson(Object vrednost) {
        return JsonBranja.zapisi(vrednost);
    }

    static PrebranListek izJson(String json) {
        return JsonBranja.preberi(json, PrebranListek.class);
    }
}
