/* Placilo, iz katerega v aplikaciji ni nastalo nic (najdba E1 pregleda
   kakovosti, nov 2026). Prej je ostala samo vrstica v dnevniku (z naslovom
   placnika), cakajoca registracija pa se je izbrisala. Zdaj:
   - administrator dobi e-posto s Stripovo sejo in razlogom,
   - cakajoca registracija s prejetim placilom ostane in je nocno ciscenje ne
     pobrise,
   - naslova placnika v dnevniku ni.

   Padla obdelava (izjema, razveljavljena transakcija) je v
   PlaciloPadlaObdelavaTest, ker potrebuje prave, potrjene transakcije. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import si.turnirko.modeli.CakajocaRegistracija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaciloBrezRacunaTest {

    /* turnirko.posta.admin v application-test.properties. */
    private static final String ADMIN = "admin@turnirko.test";

    @Autowired private PlacilaStoritev placila;
    @Autowired private CiscenjeRacunovStoritev ciscenje;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private CakajocaRegistracijaRepozitorij cakajoce;
    @Autowired private PomnilniskiPosiljatelj posta;

    private final ListAppender<ILoggingEvent> dnevnik = new ListAppender<>();

    @BeforeEach
    void pripravi() {
        posta.pocisti();
        dnevnik.start();
        ((Logger) LoggerFactory.getLogger(PlacilaStoritev.class)).addAppender(dnevnik);
    }

    @AfterEach
    void pospravi() {
        ((Logger) LoggerFactory.getLogger(PlacilaStoritev.class)).detachAppender(dnevnik);
    }

    /* Naslov je med placilom zasedel potrjen racun: novi racun ne nastane,
       placilo pa je prispelo. Vrstica ostane (tudi po roku ciscenja), admin dobi
       posto s sejo, ponovljena dostava istega dogodka je ne poslje se enkrat. */
    @Test
    void zasedenNaslovPustiSledInObvestiAdmina() {
        LocalDateTime zdaj = LocalDateTime.now();
        Uporabnik obstojeci = new Uporabnik("zaseden@test.si", "{bcrypt}x", Vloga.IGRALEC);
        // potrjen naslov zadostuje, da je zaseden (racun sam se caka na povezavo z igralcem)
        obstojeci.setStatus(StatusRacuna.CAKA);
        obstojeci.setEmailPotrjenOb(zdaj);
        uporabnikRepozitorij.save(obstojeci);
        cakajoca("zeton-zaseden", "zaseden@test.si", zdaj);
        String telo = TestniStripeDogodek.zakljucenaSeja("zaseden",
                TestniStripeDogodek.registracija("zeton-zaseden", "PREMIUM"));

        placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo));

        assertTrue(narocninaRepozitorij.findByStripeNarocninaId("sub_zaseden").isEmpty(),
                "placana narocnina se ne obesi na tuj racun");
        CakajocaRegistracija c = cakajoce.findByZeton("zeton-zaseden").orElseThrow(
                () -> new AssertionError("cakajoca registracija s prejetim placilom ne sme izginiti"));
        assertNotNull(c.getPlacanoOb());
        assertEquals("cs_zaseden", c.getStripeSejaId());
        assertEquals("NASLOV_ZASEDEN", c.getRazlogZastoja());

        List<PomnilniskiPosiljatelj.Sporocilo> adminu = adminu();
        assertEquals(1, adminu.size(), "admin dobi eno sporocilo");
        PomnilniskiPosiljatelj.Sporocilo s = adminu.get(0);
        assertTrue(s.zadeva().contains("cs_zaseden"), s.zadeva());
        for (String vsebina : new String[] {s.besedilo(), s.html()}) {
            assertTrue(vsebina.contains("cs_zaseden"), vsebina);
            assertTrue(vsebina.contains("sub_zaseden"), vsebina);
            assertTrue(vsebina.contains("zaseden@test.si"), "admin mora vedeti, komu odgovoriti");
            assertTrue(vsebina.contains("zasedel drug potrjen"), "razlog");
        }

        // rok neplacanih (4 dni) je mimo: placana vrstica ostane
        ciscenje.pocisti(zdaj.plusDays(5));
        assertTrue(cakajoce.findByZeton("zeton-zaseden").isPresent(), "ciscenje placane vrstice ne pobrise");

        // Stripe dostavi "vsaj enkrat"
        placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo));
        assertEquals(1, adminu().size(), "ponovljena dostava ni novo sporocilo");

        assertTrue(zapisi().stream().anyMatch(z -> z.contains("cs_zaseden")), "seja je v dnevniku");
        assertFalse(zapisi().stream().anyMatch(z -> z.contains("zaseden@test.si")),
                "naslov placnika ne sme v dnevnik");
    }

    /* Cakajoce registracije ni vec (potekla): racuna ni iz cesa ustvariti,
       admin pa mora izvedeti za placilo. */
    @Test
    void placiloBrezCakajoceRegistracijeObvestiAdmina() {
        String telo = TestniStripeDogodek.zakljucenaSeja("brezvrstice",
                TestniStripeDogodek.registracija("ni-ga", "PREMIUM"));

        placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo));

        assertTrue(narocninaRepozitorij.findByStripeNarocninaId("sub_brezvrstice").isEmpty());
        List<PomnilniskiPosiljatelj.Sporocilo> adminu = adminu();
        assertEquals(1, adminu.size());
        assertTrue(adminu.get(0).besedilo().contains("cs_brezvrstice"), adminu.get(0).besedilo());
        assertTrue(adminu.get(0).besedilo().contains("PREMIUM"), "paket iz metapodatkov seje");
    }

    /* Placana nadgradnja racuna, ki je bil med placilom izbrisan. */
    @Test
    void nadgradnjaIzbrisanegaRacunaObvestiAdmina() {
        String telo = TestniStripeDogodek.zakljucenaSeja("brezracuna", Map.of(
                "namen", "nadgradnja", "idUporabnik", "987654321", "paket", "PREMIUM",
                "ciklus", "LETNO", "starejsiOd21", "true"));

        placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo));

        assertTrue(narocninaRepozitorij.findByStripeNarocninaId("sub_brezracuna").isEmpty());
        List<PomnilniskiPosiljatelj.Sporocilo> adminu = adminu();
        assertEquals(1, adminu.size());
        assertTrue(adminu.get(0).besedilo().contains("cs_brezracuna"), adminu.get(0).besedilo());
        assertTrue(adminu.get(0).besedilo().contains("987654321"), "kateri racun je bil placan");
    }

    /* Neplacana vrstica se po 4 dneh se vedno pobrise (opuscen nakup). */
    @Test
    void neplacanaVrsticaSeSeVednoPobrise() {
        LocalDateTime zdaj = LocalDateTime.now();
        cakajoca("zeton-opuscen", "opuscen@test.si", zdaj);

        ciscenje.pocisti(zdaj.plusDays(5));

        assertTrue(cakajoce.findByZeton("zeton-opuscen").isEmpty());
        assertTrue(adminu().isEmpty(), "opuscen nakup ni zadeva za admina");
    }

    // ---------- Pomozno ----------

    private List<PomnilniskiPosiljatelj.Sporocilo> adminu() {
        return posta.vsa().stream().filter(s -> s.prejemnik().equals(ADMIN)).toList();
    }

    private List<String> zapisi() {
        return dnevnik.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void cakajoca(String zeton, String email, LocalDateTime ustvarjenaOb) {
        cakajoce.save(new CakajocaRegistracija(zeton, "Ana", "Novak", null, email, "$2a$10$zgosceno", false,
                LocalDate.of(1990, 1, 1), null, ustvarjenaOb));
    }
}
