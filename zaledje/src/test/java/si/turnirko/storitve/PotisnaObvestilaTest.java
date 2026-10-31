/* Potisna obvestila (V60): vpis naprave samo za paket in znane potisne storitve
   (zaščita pred SSRF), zbiranje obvestil za novo odigrane tekme spremljanih in
   druzinskih igralcev, ena tekma enkrat. Sifriranje samo je v WebPushTest (uradni
   testni vektor); tu se nic ne poslje po omrezju - zberi() vrne posiljke. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.DruzinaPovezava;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.WebPush;
import si.turnirko.repozitoriji.DruzinaPovezavaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.PotisnaNapravaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class PotisnaObvestilaTest extends IntegracijskiTest {

    /* Javni kljuc in auth iz uradnega primera RFC 8291: veljavna oblika (65 in 16 oktetov). */
    private static final String UA_JAVNI = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc123";

    @DynamicPropertySource
    static void kljuci(DynamicPropertyRegistry registry) {
        String[] par = WebPush.novParKljucevVapid();
        registry.add("turnirko.push.javni-kljuc", () -> par[0]);
        registry.add("turnirko.push.zasebni-kljuc", () -> par[1]);
        // urnik opravila je izklopljen ("-"): test sam poklice zberi(), opravilo bi mu tekmovalo
        registry.add("turnirko.push.urnik", () -> "-");
    }

    @Autowired private PotisnaObvestilaStoritev potisna;
    @Autowired private SpremljaniIgralciStoritev spremljani;
    @Autowired private DruzinaStoritev druzina;
    @Autowired private ZrebStoritev zrebStoritev;
    @Autowired private TekmaStoritev tekmaStoritev;
    @Autowired private PotisnaNapravaRepozitorij naprave;
    @Autowired private DruzinaPovezavaRepozitorij druzinePovezave;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @BeforeEach
    void pripravi() {
        zrebStoritev.nastaviNakljucje(new Random(42));
        potisna.ponastaviKazalec();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    // ---------- vpis naprave ----------

    @Test
    void vpisNapraveZahtevaPaketInVklopljeneKljuce() {
        assertTrue(potisna.jeVklopljeno());
        prijava(navijac("brez@test", null));
        assertThrows(PrepovedanoIzjema.class, () -> potisna.vpisi(FCM, UA_JAVNI, AUTH));

        Uporabnik placnik = navijac("placnik@test", StatusNarocnine.AKTIVNA);
        prijava(placnik);
        potisna.vpisi(FCM, UA_JAVNI, AUTH);
        assertEquals(1, naprave.findByIdRacun(placnik.getId()).size());
        // ista naprava dvakrat je ena vrstica
        potisna.vpisi(FCM, UA_JAVNI, AUTH);
        assertEquals(1, naprave.findByIdRacun(placnik.getId()).size());

        potisna.izpisi(FCM);
        assertTrue(naprave.findByIdRacun(placnik.getId()).isEmpty());
    }

    @Test
    void napravaPrekoNeznaneStoritveSeZavrne() {
        prijava(navijac("ssrf@test", StatusNarocnine.AKTIVNA));
        for (String slab : List.of(
                "http://fcm.googleapis.com/fcm/send/x",
                "https://fcm.googleapis.com.evil.example/fcm/send/x",
                "https://evil.example/fcm.googleapis.com",
                "https://localhost/push",
                "https://10.0.0.5/push",
                "https://fcm.googleapis.com:8443/fcm/send/x",
                "https://uporabnik@fcm.googleapis.com/fcm/send/x")) {
            assertThrows(NeveljavenVnosIzjema.class, () -> potisna.vpisi(slab, UA_JAVNI, AUTH), slab);
        }
    }

    @Test
    void znanePotisneStoritveSoPrepoznane() {
        for (String dober : List.of(
                FCM,
                "https://updates.push.services.mozilla.com/wpush/v2/xyz",
                "https://web.push.apple.com/QxYz",
                "https://wns2-par02p.notify.windows.com/w/?token=abc")) {
            assertTrue(PotisnaObvestilaStoritev.jeZnanaPotisnaStoritev(dober), dober);
        }
        assertFalse(PotisnaObvestilaStoritev.jeZnanaPotisnaStoritev(null));
    }

    @Test
    void neveljavnaKljucaSeZavrneta() {
        prijava(navijac("kljuc@test", StatusNarocnine.AKTIVNA));
        assertThrows(NeveljavenVnosIzjema.class, () -> potisna.vpisi(FCM, "kratek", AUTH));
        assertThrows(NeveljavenVnosIzjema.class, () -> potisna.vpisi(FCM, UA_JAVNI, "kratek"));
    }

    // ---------- zbiranje ----------

    @Test
    void odigranaTekmaSpremljanegaIgralcaDaObvestiloSamoRacunuSPaketom() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmaRepozitorij.findAll().stream()
                .filter(t -> t.getDogodek().getId().equals(dogodek.getId()) && t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();

        Uporabnik placnik = navijac("obvescen@test", StatusNarocnine.AKTIVNA);
        Uporabnik brezPaketa = navijac("neplacnik@test", null);
        prijava(placnik);
        potisna.vpisi(FCM, UA_JAVNI, AUTH);
        spremljani.spremljaj(zmagovalec.getId());
        // naprava racuna brez paketa (paket je potekel po vpisu) obvestil ne dobi
        naprave.save(new si.turnirko.modeli.PotisnaNaprava(brezPaketa.getId(),
                "https://updates.push.services.mozilla.com/wpush/v2/q", UA_JAVNI, AUTH, LocalDateTime.now()));
        prijava(brezPaketa);
        spremljani.spremljaj(zmagovalec.getId());

        assertTrue(potisna.zberi(LocalDateTime.now()).isEmpty(), "prvi klic samo zacne zbiranje");

        // rezultat vnese organizator (tu: brez konteksta, preverba lastnistva ne omejuje)
        SecurityContextHolder.getContext().setAuthentication(null);
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null));
        List<PotisnaObvestilaStoritev.Posiljka> posiljke = potisna.zberi(LocalDateTime.now());

        assertEquals(1, posiljke.size(), "samo naprava racuna s paketom");
        PotisnaObvestilaStoritev.Posiljka p = posiljke.get(0);
        assertEquals(FCM, p.endpoint());
        assertTrue(p.telo().contains(zmagovalec.polnoIme() + ": zmaga 3 : 1"), p.telo());
        assertTrue(p.telo().contains("proti "), p.telo());
        assertTrue(p.telo().contains("/igralci/" + zmagovalec.getId() + "/profil"), p.telo());

        // ista tekma se ne oznani dvakrat
        assertTrue(potisna.zberi(LocalDateTime.now()).isEmpty());
    }

    @Test
    void druzinskiIgralecDaObvestiloRacunuDruzine() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmaRepozitorij.findAll().stream()
                .filter(t -> t.getDogodek().getId().equals(dogodek.getId()) && t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        Igralec otrok = tekma.getPrijava1().getIgralec();
        otrok.setDatumRojstva(LocalDate.now().minusYears(12).minusMonths(2));
        igralecRepozitorij.save(otrok);

        Uporabnik stars = navijac("stars@test", StatusNarocnine.AKTIVNA);
        prijava(stars);
        potisna.vpisi(FCM, UA_JAVNI, AUTH);
        druzina.potrdi(druzina.zahtevaj(otrok.getId()).id());

        potisna.zberi(LocalDateTime.now());
        // rezultat vnese organizator (tu: brez konteksta, preverba lastnistva ne omejuje)
        SecurityContextHolder.getContext().setAuthentication(null);
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));

        List<PotisnaObvestilaStoritev.Posiljka> posiljke = potisna.zberi(LocalDateTime.now());
        assertEquals(1, posiljke.size(), "otrok ni spremljan, a je v druzini");
        assertEquals(1, druzinePovezave.findByIdIgralecAndStatus(otrok.getId(), DruzinaPovezava.Status.POTRJENA).size());
    }

    // ---------- priprava ----------

    private Uporabnik navijac(String prijavnoIme, StatusNarocnine status) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.NAVIJAC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setEmailPotrjenOb(LocalDateTime.now());
        u = uporabnikRepozitorij.save(u);
        if (status != null) {
            Narocnina n = new Narocnina(u, Paket.NAVIJAC);
            n.setStatus(status);
            n.setCiklus(CiklusPlacila.LETNO);
            n.setStripeNarocninaId("sub_" + prijavnoIme);
            n.setTrenutnoObdobjeDo(LocalDateTime.now().plusMonths(6));
            narocninaRepozitorij.save(n);
        }
        return u;
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_NAVIJAC"))));
    }
}
