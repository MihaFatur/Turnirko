/* Potisna obvestila med turnirjem (V60, faza 3 paketa Navijac): ko je odigrana
   tekma igralca, ki ga racun s paketom Navijac (ali Igralec Premium) spremlja ali
   ima v druzini, dobi napravo, ki je dovolila obvestila, kratko sporocilo - brez
   aplikacije, prek Web Push (WebPush: sifriranje RFC 8291, podpis VAPID).

   Pravila, ki jih ne razbij:
   - **Streznik kliče samo znane potisne storitve.** Naslov, na katerega streznik
     poslje sifrirano sporocilo, vpise odjemalec, zato bi navijac z naslovom
     notranjega streznika dosegel omrezje za Caddyjem (SSRF). Dovoljene so samo
     storitve FCM, Mozilla, Apple in Windows (jeZnanaPotisnaStoritev), samo https
     na privzetih vratih. Preverba je ob vpisu IN ob posiljanju.
   - **Posiljanje ne drzi transakcije.** SQLite prenese enega pisca, HTTP klic do
     potisne storitve pa traja sekunde: zbiranje in oznaka "poslano" sta ena kratka
     transakcija, posiljanje je zunaj nje, izbris potekle naprave (404/410) spet
     kratka transakcija.
   - **Tekma se oznani enkrat** (potisni_dogodek: tekma + igralec): preracun ratinga
     zapise dnevnika znova ustvari z novimi id-ji, a njihov cas je star (meja 48 ur) in
     kljuc ze obstaja. Ob zagonu se zbiranje zacne pri najvecjem id-ju dnevnika: starih
     tekem ni treba oznanjati.
   - **Brez VAPID kljucev (turnirko.push.*) je vse ugasnjeno**: naprav ni mogoce vpisati,
     opravilo ne dela nic. Razvoj in testi tako ne kličejo omrezja.
   - **Samo racun z veljavnim paketom**: ob potekli narocnini obvestila prenehajo,
     naprava ostane vpisana (ob obnovi spet dela). */
package si.turnirko.storitve;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.PrivateKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.PotisnaNaprava;
import si.turnirko.modeli.PotisniDogodek;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.SpremljanIgralec;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.pomozno.WebPush;
import si.turnirko.repozitoriji.PotisnaNapravaRepozitorij;
import si.turnirko.repozitoriji.PotisniDogodekRepozitorij;
import si.turnirko.repozitoriji.SpremljanIgralecRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class PotisnaObvestilaStoritev {

    private static final Logger dnevnik = LoggerFactory.getLogger(PotisnaObvestilaStoritev.class);

    /* Znane potisne storitve (domene, na katerih lezi endpoint naprave). */
    private static final List<String> ZNANE_STORITVE = List.of(
            "fcm.googleapis.com",           // Chrome, Edge, Android
            "push.services.mozilla.com",    // Firefox
            "push.apple.com",               // Safari (macOS, iOS)
            "notify.windows.com");          // Windows (WNS)

    /* Tekma, starejsa od tega, ni novica (zaščita pred preracunom). */
    private static final long URE_NAZAJ = 48;
    private static final int DNI_ZGODOVINE = 14;
    private static final int TTL_SEKUND = 3600;
    private static final long POTEK_VAPID_SEKUND = 12 * 3600L;

    private final PotisnaNapravaRepozitorij naprave;
    private final PotisniDogodekRepozitorij dogodki;
    private final SpremljanIgralecRepozitorij spremljani;
    private final DruzinaStoritev druzina;
    private final UporabnikRepozitorij uporabnikRepozitorij;
    private final NarocninaStoritev narocnina;
    private final LastnistvoStoritev lastnistvo;
    private final TransactionTemplate transakcija;
    private final ObjectMapper json = new ObjectMapper();

    private final String javniKljuc;
    private final String zasebniKljuc;
    private final String subjekt;
    private final String koren;

    /* Zbiranje se zacne pri id-ju, ki je bil najvecji ob prvem zagonu opravila. */
    private final AtomicReference<Long> kazalec = new AtomicReference<>();
    private volatile PrivateKey zasebni;

    public PotisnaObvestilaStoritev(PotisnaNapravaRepozitorij naprave,
                                    PotisniDogodekRepozitorij dogodki,
                                    SpremljanIgralecRepozitorij spremljani,
                                    DruzinaStoritev druzina,
                                    UporabnikRepozitorij uporabnikRepozitorij,
                                    NarocninaStoritev narocnina,
                                    LastnistvoStoritev lastnistvo,
                                    PlatformTransactionManager upravljalec,
                                    @Value("${turnirko.push.javni-kljuc:}") String javniKljuc,
                                    @Value("${turnirko.push.zasebni-kljuc:}") String zasebniKljuc,
                                    @Value("${turnirko.push.subjekt:mailto:info@turnirko-nt.si}") String subjekt,
                                    @Value("${turnirko.javni-naslov:https://turnirko-nt.si}") String koren) {
        this.naprave = naprave;
        this.dogodki = dogodki;
        this.spremljani = spremljani;
        this.druzina = druzina;
        this.uporabnikRepozitorij = uporabnikRepozitorij;
        this.narocnina = narocnina;
        this.lastnistvo = lastnistvo;
        this.transakcija = new TransactionTemplate(upravljalec);
        this.javniKljuc = javniKljuc == null ? "" : javniKljuc.trim();
        this.zasebniKljuc = zasebniKljuc == null ? "" : zasebniKljuc.trim();
        this.subjekt = subjekt;
        this.koren = koren.endsWith("/") ? koren.substring(0, koren.length() - 1) : koren;
    }

    public boolean jeVklopljeno() {
        return !javniKljuc.isEmpty() && !zasebniKljuc.isEmpty();
    }

    /* Javni kljuc VAPID, ki ga brskalnik rabi ob narocnini na obvestila; prazno, ce je ugasnjeno. */
    public String javniKljucAliPrazno() {
        return jeVklopljeno() ? javniKljuc : "";
    }

    // ---------- Naprave ----------

    /* Vpis naprave prijavljenega racuna. Ista naprava (endpoint) je ena vrstica: ce je bila
       vpisana pri drugem racunu (skupen brskalnik), preide na tega. */
    @Transactional
    public void vpisi(String endpoint, String p256dh, String auth) {
        Uporabnik jaz = zahtevajPaket();
        if (!jeVklopljeno()) {
            throw new NeveljavenVnosIzjema("Potisna obvestila na tem strežniku niso vklopljena.");
        }
        preveriNaslov(endpoint);
        preveriKljuca(p256dh, auth);
        PotisnaNaprava n = naprave.findByEndpoint(endpoint).orElse(null);
        if (n == null) {
            naprave.save(new PotisnaNaprava(jaz.getId(), endpoint, p256dh, auth, LocalDateTime.now()));
        } else {
            n.setIdRacun(jaz.getId());
            n.setP256dh(p256dh);
            n.setAuth(auth);
        }
    }

    @Transactional
    public void izpisi(String endpoint) {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za potisna obvestila je potrebna prijava.");
        }
        naprave.deleteByEndpointAndIdRacun(endpoint, jaz.getId());
    }

    private Uporabnik zahtevajPaket() {
        Uporabnik jaz = lastnistvo.trenutni();
        if (jaz == null) {
            throw new PrepovedanoIzjema("Za potisna obvestila je potrebna prijava.");
        }
        if (!narocnina.imaSirokoSpremljanje(jaz)) {
            throw new PrepovedanoIzjema("Potisna obvestila so del paketa Navijač ali Igralec Premium.");
        }
        return jaz;
    }

    /* Samo https, privzeta vrata, brez uporabniskih podatkov in samo znane storitve. */
    static void preveriNaslov(String endpoint) {
        if (!jeZnanaPotisnaStoritev(endpoint)) {
            throw new NeveljavenVnosIzjema("Naslov naprave ni naslov znane potisne storitve.");
        }
    }

    static boolean jeZnanaPotisnaStoritev(String endpoint) {
        if (endpoint == null || endpoint.length() > 600) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String gostitelj = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || gostitelj == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String g = gostitelj.toLowerCase(Locale.ROOT);
            return ZNANE_STORITVE.stream().anyMatch(s -> g.equals(s) || g.endsWith("." + s));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /* p256dh je nestisnjena tocka P-256 (65 oktetov), auth 16 oktetov, oboje base64url. */
    private static void preveriKljuca(String p256dh, String auth) {
        try {
            byte[] javni = Base64.getUrlDecoder().decode(p256dh);
            byte[] skrivnost = Base64.getUrlDecoder().decode(auth);
            if (javni.length != 65 || javni[0] != 4 || skrivnost.length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new NeveljavenVnosIzjema("Ključa naprave nista veljavna.");
        }
    }

    // ---------- Zbiranje in posiljanje ----------

    /* Posiljka: naprava in ze sestavljeno sporocilo. */
    record Posiljka(Long idNaprava, String endpoint, String p256dh, String auth, String telo) {}

    @Scheduled(cron = "${turnirko.push.urnik:0 * * * * *}", zone = "Europe/Ljubljana")
    public void urnoPreverjanje() {
        if (!jeVklopljeno()) {
            return;
        }
        List<Posiljka> posiljke = transakcija.execute(s -> zberi(LocalDateTime.now()));
        if (posiljke == null || posiljke.isEmpty()) {
            return;
        }
        posljiVse(posiljke);
    }

    /* Prva poizvedba zacne zbiranje (starih tekem ne oznanjamo), vsaka naslednja
       vrne obvestila za tekme, ki so prisle od prejsnje. Mora teci v transakciji. */
    List<Posiljka> zberi(LocalDateTime zdaj) {
        Long od = kazalec.get();
        if (od == null) {
            kazalec.compareAndSet(null, dogodki.najvecjiIdDnevnika());
            return List.of();
        }
        List<RatingZgodovina> nove = dogodki.novaTekme(od, zdaj.minusHours(URE_NAZAJ), RatingStanje.SISTEM_TURNIRKO);
        if (nove.isEmpty()) {
            return List.of();
        }
        kazalec.set(nove.get(nove.size() - 1).getId());
        dogodki.pobrisiStareje(zdaj.minusDays(DNI_ZGODOVINE));

        List<Posiljka> posiljke = new ArrayList<>();
        for (RatingZgodovina z : nove) {
            String kljuc = (z.getTekma() != null ? "T" + z.getTekma().getId() : "L" + z.getTekmaSrecanja().getId())
                    + ":" + z.getIgralec().getId();
            if (dogodki.existsById(kljuc)) {
                continue;
            }
            dogodki.save(new PotisniDogodek(kljuc, zdaj));
            posiljke.addAll(posiljkeZa(z));
        }
        return posiljke;
    }

    /* Naprave racunov, ki igralca spremljajo ali ga imajo v druzini in imajo paket. */
    private List<Posiljka> posiljkeZa(RatingZgodovina z) {
        Igralec igralec = z.getIgralec();
        Set<Long> idji = Set.of(igralec.getId());
        Set<Long> racuni = new LinkedHashSet<>();
        for (SpremljanIgralec s : spremljani.findByIdIgralecIn(idji)) {
            racuni.add(s.getIdRacun());
        }
        druzina.veljavnePovezaveIgralcev(idji).forEach(p -> racuni.add(p.getIdRacun()));
        if (racuni.isEmpty()) {
            return List.of();
        }
        String telo = sporocilo(z);
        List<Posiljka> posiljke = new ArrayList<>();
        for (PotisnaNaprava n : naprave.findByIdRacunIn(racuni)) {
            Uporabnik u = uporabnikRepozitorij.findById(n.getIdRacun()).orElse(null);
            if (u != null && u.isAktiven() && narocnina.imaSirokoSpremljanje(u)) {
                posiljke.add(new Posiljka(n.getId(), n.getEndpoint(), n.getP256dh(), n.getAuth(), telo));
            }
        }
        return posiljke;
    }

    /* Sporocilo: "Ana Novak: zmaga 3 : 1" in pod njim nasprotnik, tekmovanje in sprememba ratinga. */
    String sporocilo(RatingZgodovina z) {
        Igralec jaz = z.getIgralec();
        boolean zmaga = z.getTocke() != null ? z.getTocke() == 1 : z.getSprememba() > 0;
        String nasprotnik = null;
        String tekmovanje = null;
        int moji = 0;
        int njegovi = 0;
        Tekma t = z.getTekma();
        if (t != null) {
            boolean prvi = t.getPrijava1() != null && t.getPrijava1().getIgralec() != null
                    && t.getPrijava1().getIgralec().getId().equals(jaz.getId());
            var drugaStran = prvi ? t.getPrijava2() : t.getPrijava1();
            nasprotnik = drugaStran == null ? null : drugaStran.prikazanoIme();
            moji = prvi ? t.getDobljeniNizi1() : t.getDobljeniNizi2();
            njegovi = prvi ? t.getDobljeniNizi2() : t.getDobljeniNizi1();
            tekmovanje = t.getDogodek().getTurnir().getIme();
        } else {
            TekmaSrecanja ts = z.getTekmaSrecanja();
            boolean domaci = jeIgralec(jaz, ts.getIgralecDomaci(), ts.getIgralecDomaci2());
            Igralec on = domaci ? ts.getIgralecGost() : ts.getIgralecDomaci();
            nasprotnik = on == null ? null : on.polnoIme();
            moji = domaci ? ts.getDobljeniNiziDomaci() : ts.getDobljeniNiziGost();
            njegovi = domaci ? ts.getDobljeniNiziGost() : ts.getDobljeniNiziDomaci();
            Srecanje s = ts.getSrecanje();
            tekmovanje = s.getLiga() != null ? s.getLiga().getIme()
                    : s.getTekma() != null ? s.getTekma().getDogodek().getTurnir().getIme() : null;
        }
        StringBuilder telo = new StringBuilder();
        if (nasprotnik != null) {
            telo.append("proti ").append(nasprotnik);
        }
        if (tekmovanje != null) {
            telo.append(telo.length() > 0 ? " · " : "").append(tekmovanje);
        }
        if (z.getSprememba() != 0) {
            telo.append(telo.length() > 0 ? " · " : "").append("rating ")
                    .append(z.getSprememba() > 0 ? "+" : "−").append(Math.abs(z.getSprememba()));
        }
        Map<String, Object> vsebina = new LinkedHashMap<>();
        vsebina.put("title", jaz.polnoIme() + ": " + (zmaga ? "zmaga " : "poraz ") + moji + " : " + njegovi);
        vsebina.put("body", telo.toString());
        vsebina.put("url", koren + "/igralci/" + jaz.getId() + "/profil");
        vsebina.put("tag", "tekma-" + (t != null ? "T" + t.getId() : "L" + z.getTekmaSrecanja().getId())
                + "-" + jaz.getId());
        try {
            return json.writeValueAsString(vsebina);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean jeIgralec(Igralec iskan, Igralec a, Igralec b) {
        return (a != null && a.getId().equals(iskan.getId())) || (b != null && b.getId().equals(iskan.getId()));
    }

    /* Poslje vsako posiljko in pobrise naprave, ki jih potisna storitev ne pozna vec. Izven transakcije. */
    int posljiVse(List<Posiljka> posiljke) {
        int poslanih = 0;
        List<Long> potekle = new ArrayList<>();
        for (Posiljka p : posiljke) {
            if (!jeZnanaPotisnaStoritev(p.endpoint())) {
                dnevnik.warn("Potisna obvestila: naslov naprave {} ni znana storitev - preskocen.", p.idNaprava());
                continue;
            }
            try {
                byte[] sifrirano = WebPush.sifriraj(p.telo().getBytes(StandardCharsets.UTF_8), p.p256dh(), p.auth());
                String jwt = WebPush.vapidJwt(WebPush.izvor(p.endpoint()), subjekt,
                        System.currentTimeMillis() / 1000 + POTEK_VAPID_SEKUND, zasebniKljuc());
                WebPush.Odgovor odgovor = WebPush.poslji(p.endpoint(), sifrirano, jwt, javniKljuc, TTL_SEKUND);
                if (odgovor.jeSprejeto()) {
                    poslanih++;
                } else if (odgovor.jePotekla()) {
                    potekle.add(p.idNaprava());
                } else {
                    dnevnik.warn("Potisno obvestilo za napravo {}: odgovor {} {}", p.idNaprava(), odgovor.koda(),
                            odgovor.telo());
                }
            } catch (RuntimeException e) {
                dnevnik.warn("Potisnega obvestila za napravo {} ni bilo mogoce poslati: {}", p.idNaprava(), e.getMessage());
            }
        }
        if (!potekle.isEmpty()) {
            transakcija.executeWithoutResult(s -> naprave.deleteAllById(potekle));
        }
        return poslanih;
    }

    private PrivateKey zasebniKljuc() {
        PrivateKey k = zasebni;
        if (k == null) {
            k = WebPush.zasebniKljuc(zasebniKljuc);
            zasebni = k;
        }
        return k;
    }

    /* Preizkusno obvestilo na vse naprave prijavljenega racuna: oseba vidi, da delujejo,
       preden naslednja tekma pove, da ne. Vrne stevilo poslanih. */
    public int preizkus() {
        List<Posiljka> posiljke = transakcija.execute(s -> {
            Uporabnik jaz = zahtevajPaket();
            if (!jeVklopljeno()) {
                throw new NeveljavenVnosIzjema("Potisna obvestila na tem strežniku niso vklopljena.");
            }
            String telo;
            try {
                telo = json.writeValueAsString(Map.of("title", "Turnirko",
                        "body", "Obvestila delujejo na tej napravi.", "url", koren, "tag", "preizkus"));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            return naprave.findByIdRacun(jaz.getId()).stream()
                    .map(n -> new Posiljka(n.getId(), n.getEndpoint(), n.getP256dh(), n.getAuth(), telo))
                    .toList();
        });
        return posiljke == null ? 0 : posljiVse(posiljke);
    }

    /* Za teste: zacni zbiranje znova. */
    void ponastaviKazalec() {
        kazalec.set(null);
    }
}
