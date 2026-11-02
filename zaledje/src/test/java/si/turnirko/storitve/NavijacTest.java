/* Racun navijaca (V55): oseba, ki ne igra. Potrdi ga potrjen naslov (in
   skrbnik), ne admin; z zapisom med igralci se ne primerja, tudi ce se ime in
   datum rojstva ujemata; placljivega paketa in kod popusta zanj ni. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NavijacTest {

    private static final String IP = "10.0.0.2";
    private static final LocalDate ODRASEL = LocalDate.of(1975, 3, 8);
    private static final Pattern KODA = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired RegistracijaStoritev registracija;
    @Autowired RacuniStoritev racuni;
    @Autowired PlacilaStoritev placila;
    @Autowired IgralciStoritev igralci;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PomnilniskiPosiljatelj posta;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired EntityManager em;
    @Value("${turnirko.posta.admin}") String naslovAdmina;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
        posta.pocisti();
    }

    @Test
    void potrjenNaslovNavijacaPotrdiBrezAdmina() {
        long cakajocihPrej = racuni.steviloCakajocih();
        registracija.registriraj(navijac("Mojca", "Kos", "mojca@test.si", ODRASEL, null), IP);

        Uporabnik u = racun("mojca@test.si");
        assertEquals(Vloga.NAVIJAC, u.getVloga());
        assertEquals(StatusRacuna.CAKA, u.getStatus(), "nepotrjen naslov je samo rezervacija");

        PotrditevOdgovorDto odgovor = registracija.potrdiEposto(
                new PotrditevKodeVnos("mojca@test.si", koda("mojca@test.si")));

        assertTrue(odgovor.navijac());
        assertFalse(odgovor.povezan());
        assertFalse(odgovor.organizator());
        assertNull(odgovor.preizkusDo(), "navijac preizkusa Premium nima");
        assertTrue(racun("mojca@test.si").jePotrjenNavijac());
        assertEquals(cakajocihPrej, racuni.steviloCakajocih(), "navijac admina ne zaposli");
        assertTrue(posta.vsa().stream().noneMatch(s -> s.prejemnik().equals(naslovAdmina)),
                "admin o navijacu ne dobi e-poste");
    }

    @Test
    void navijacSeNePovezeZIgralcemIstegaImenaInDatuma() {
        igralecVSifrantu("Mojca", "Kos", ODRASEL);
        registracija.registriraj(navijac("Mojca", "Kos", "mojca@test.si", ODRASEL, null), IP);

        registracija.potrdiEposto(new PotrditevKodeVnos("mojca@test.si", koda("mojca@test.si")));

        Uporabnik u = racun("mojca@test.si");
        assertNull(u.getIgralec(), "navijac ne igra, zato ga ujemanje ne sme povezati");
        assertEquals(StatusRacuna.POTRJEN, u.getStatus());
        assertFalse(u.jePotrjenIgralec());
    }

    @Test
    void mlajsiOd15NavijacPotrebujeSkrbnika() {
        LocalDate otrok = LocalDate.now().minusYears(11);
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.registriraj(
                navijac("Lan", "Kos", "lan@test.si", otrok, null), IP), "brez naslova skrbnika ne gre");

        registracija.registriraj(navijac("Lan", "Kos", "lan@test.si", otrok, "mama@test.si"), IP);
        String kodaSkrbnika = koda("mama@test.si");

        PotrditevOdgovorDto poOtroku = registracija.potrdiEposto(
                new PotrditevKodeVnos("lan@test.si", koda("lan@test.si")));
        assertFalse(poOtroku.navijac(), "brez soglasja skrbnika racun ni potrjen");
        assertTrue(poOtroku.potrebnaKodaSkrbnika());
        assertEquals(StatusRacuna.CAKA, racun("lan@test.si").getStatus());

        PotrditevOdgovorDto poSkrbniku = registracija.potrdiSkrbnika(
                new PotrditevKodeVnos("lan@test.si", kodaSkrbnika));
        assertTrue(poSkrbniku.navijac());
        assertTrue(racun("lan@test.si").jePotrjenNavijac());
        assertTrue(posta.vsa().stream().noneMatch(s -> s.prejemnik().equals(naslovAdmina)));
    }

    @Test
    void navijacNimaKodPopustaPlacilaInPovezave() {
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.registriraj(
                new RegistracijaVnos("Mojca", "Kos", null, "mojca@test.si", "geslo1234", false, ODRASEL,
                        null, "PRVI20", true), IP), "kode popusta navijacu ne dajo nic");
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.registriraj(
                new RegistracijaVnos("Mojca", "Kos", null, "mojca@test.si", "geslo1234", true, ODRASEL,
                        null, null, true), IP), "racun ni hkrati organizatorski in navijaski");
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                new PlacanaRegistracijaVnos(navijac("Mojca", "Kos", "mojca@test.si", ODRASEL, null),
                        Paket.PREMIUM, CiklusPlacila.LETNO), IP), "navijac ne gre prek placila");

        registracija.registriraj(navijac("Mojca", "Kos", "mojca@test.si", ODRASEL, null), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("mojca@test.si", koda("mojca@test.si")));
        IgralecJavniDto igralec = igralecVSifrantu("Mojca", "Kos", ODRASEL);
        Long id = racun("mojca@test.si").getId();
        assertThrows(DomenskaIzjema.class, () -> racuni.potrdi(id, new PotrditevRacunaVnos(igralec.id())),
                "admin navijaca ne poveze z igralcem");
    }

    /* Omejitev v shemi (V55), ne le v storitvi: navijac zapisa igralca nima. */
    @Test
    void shemaNavijacuNeDovoliZapisaIgralca() {
        registracija.registriraj(navijac("Mojca", "Kos", "mojca@test.si", ODRASEL, null), IP);
        IgralecJavniDto igralec = igralecVSifrantu("Mojca", "Kos", ODRASEL);
        Long id = racun("mojca@test.si").getId();
        em.flush();

        assertThrows(PersistenceException.class, () -> em.createNativeQuery(
                        "UPDATE uporabnik SET id_igralec = :igralec WHERE id = :id")
                .setParameter("igralec", igralec.id())
                .setParameter("id", id)
                .executeUpdate());
    }

    // ---------- Pomozno ----------

    private static RegistracijaVnos navijac(String ime, String priimek, String email, LocalDate rojen,
                                            String emailSkrbnika) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", false, rojen, emailSkrbnika,
                null, true);
    }

    private IgralecJavniDto igralecVSifrantu(String ime, String priimek, LocalDate datumRojstva) {
        return igralci.ustvari(new IgralecVnos(ime, priimek, Spol.ZENSKI, datumRojstva,
                null, null, null, null, "SLO", null, null, null, null));
    }

    private Uporabnik racun(String email) {
        return uporabnikRepozitorij.najdiZVsem(email).orElseThrow();
    }

    private String koda(String email) {
        String besedilo = posta.zadnje(email).orElseThrow(
                () -> new AssertionError("ni sporocila za " + email)).besedilo();
        Matcher m = KODA.matcher(besedilo);
        assertTrue(m.find(), "v sporocilu ni kode: " + besedilo);
        return m.group(1);
    }
}
