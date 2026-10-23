/* Preizkus Premium (V49): mesec Premium brez placila, ki se zacne s povezavo
   racuna z igralcem (samodejno ali pri administratorju), je enkraten, potece
   sam in nekaj dni pred koncem poslje eno obvestilo.

   Koda za potrditev se prebere iz pomnilniskega posiljatelja kot v
   RegistracijaTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.NadgradnjaVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PreizkusPremiumTest {

    private static final String IP = "10.0.0.9";
    private static final LocalDate ODRASEL = LocalDate.of(1990, 5, 20);
    private static final Pattern KODA = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired RegistracijaStoritev registracija;
    @Autowired RacuniStoritev racuni;
    @Autowired IgralciStoritev igralci;
    @Autowired PreizkusPremiumStoritev preizkus;
    @Autowired NarocninaStoritev narocninaStoritev;
    @Autowired PlacilaStoritev placila;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired PomnilniskiPosiljatelj posta;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
        posta.pocisti();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Zacetek ----------

    @Test
    void samodejnaPovezavaZacnePreizkus() {
        igralecVSifrantu("Nina", "Zupan");
        PotrditevOdgovorDto odgovor = registrirajInPotrdi("Nina", "Zupan", "nina@test.si");

        assertTrue(odgovor.povezan());
        assertEquals(LocalDate.now().plusMonths(1), odgovor.preizkusDo(),
                "odgovor pove konec preizkusa, da ga vmesnik pokaze ob koncu registracije");
        Uporabnik u = racun("nina@test.si");
        Narocnina n = narocnina(u);
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertEquals(Paket.PREMIUM, n.getPaket());
        assertNull(n.getStripeNarocninaId(), "preizkus je brez Stripa");
        assertTrue(narocninaStoritev.imaPremium(u), "preizkus odklene Premium");
    }

    @Test
    void administratorjevaPovezavaZacnePreizkus() {
        IgralecJavniDto igralec = igralecVSifrantu("Tine", "Kos");
        // drug datum rojstva: samodejne povezave ni, racun caka na admina
        registracija.registriraj(new RegistracijaVnos("Tine", "Kos", null, "tine@test.si",
                "geslo1234", false, ODRASEL.plusDays(3), null), IP);
        PotrditevOdgovorDto odgovor = registracija.potrdiEposto(
                new PotrditevKodeVnos("tine@test.si", koda("tine@test.si")));
        assertFalse(odgovor.povezan());
        assertNull(odgovor.preizkusDo());
        assertTrue(narocninaRepozitorij.findByUporabnikId(racun("tine@test.si").getId()).isEmpty(),
                "brez povezave preizkus ne tece - mesec bi se izgubil s cakanjem na admina");

        racuni.potrdi(racun("tine@test.si").getId(), new PotrditevRacunaVnos(igralec.id()));

        Narocnina n = narocnina(racun("tine@test.si"));
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertTrue(n.jeVeljavna(LocalDateTime.now()));
    }

    @Test
    void preizkusJeEnkratenTudiPoRazvezavi() {
        IgralecJavniDto igralec = igralecVSifrantu("Eva", "Horvat");
        registrirajInPotrdi("Eva", "Horvat", "eva@test.si");
        Uporabnik u = racun("eva@test.si");
        Narocnina prvi = narocnina(u);
        prvi.setTrenutnoObdobjeDo(LocalDateTime.now().minusDays(1)); // potekel
        narocninaRepozitorij.save(prvi);

        racuni.razvezi(u.getId());
        racuni.potrdi(u.getId(), new PotrditevRacunaVnos(igralec.id()));

        Narocnina n = narocnina(racun("eva@test.si"));
        assertEquals(prvi.getId(), n.getId());
        assertFalse(n.jeVeljavna(LocalDateTime.now()), "nova povezava ne podaljsa poteklega preizkusa");
        assertFalse(narocninaStoritev.imaPremium(racun("eva@test.si")));
    }

    @Test
    void placanaNarocninaOstaneNedotaknjena() {
        IgralecJavniDto igralec = igralecVSifrantu("Luka", "Novak");
        registracija.registriraj(new RegistracijaVnos("Luka", "Novak", null, "luka@test.si",
                "geslo1234", false, ODRASEL.plusDays(3), null), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("luka@test.si", koda("luka@test.si")));
        Uporabnik u = racun("luka@test.si");
        Narocnina placana = new Narocnina(u, Paket.PREMIUM);
        placana.setStatus(StatusNarocnine.AKTIVNA);
        placana.setCiklus(CiklusPlacila.LETNO);
        placana.setStripeNarocninaId("sub_preizkus_1");
        narocninaRepozitorij.save(placana);

        racuni.potrdi(u.getId(), new PotrditevRacunaVnos(igralec.id()));

        Narocnina n = narocnina(racun("luka@test.si"));
        assertEquals(StatusNarocnine.AKTIVNA, n.getStatus());
        assertEquals("sub_preizkus_1", n.getStripeNarocninaId());
    }

    @Test
    void organizatorPreizkusaNeDobi() {
        Uporabnik u = new Uporabnik("org-preizkus@test.si", "{bcrypt}x", si.turnirko.modeli.Vloga.ORGANIZATOR);
        u.setStatus(si.turnirko.modeli.StatusRacuna.POTRJEN);
        u = uporabnikRepozitorij.save(u);

        assertTrue(preizkus.zacni(u, LocalDateTime.now()).isEmpty());
        assertTrue(narocninaRepozitorij.findByUporabnikId(u.getId()).isEmpty());
    }

    // ---------- Potek in nakup ----------

    @Test
    void preizkusPoteceSam() {
        igralecVSifrantu("Maja", "Kralj");
        registrirajInPotrdi("Maja", "Kralj", "maja@test.si");
        Uporabnik u = racun("maja@test.si");
        Narocnina n = narocnina(u);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().minusMinutes(1));
        narocninaRepozitorij.save(n);

        assertFalse(narocninaStoritev.imaPremium(u), "po koncu preizkusa je racun Free brez opravila");
    }

    /* Nakup med preizkusom ni "druga narocnina ob zivi prvi": preizkus nima
       Stripa, zato zavora v zacniNadgradnjo ne sme zavrniti. V testu ni Stripovega
       kljuca, zato zahteva pade sele tam - z drugim sporocilom kot zavora. */
    @Test
    void nakupMedPreizkusomJeDovoljen() {
        igralecVSifrantu("Jan", "Kovač");
        registrirajInPotrdi("Jan", "Kovač", "jan@test.si");
        Uporabnik u = racun("jan@test.si");
        prijava(u);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> placila.zacniNadgradnjo(new NadgradnjaVnos(Paket.PREMIUM, CiklusPlacila.MESECNO)));

        assertTrue(napaka.getMessage().contains("zaceti"), "pricakovan je Stripe, ne zavora: " + napaka.getMessage());
    }

    // ---------- Obvestilo o koncu ----------

    @Test
    void obvestiloGreEnkratNekajDniPredKoncem() {
        igralecVSifrantu("Ana", "Bizjak");
        registrirajInPotrdi("Ana", "Bizjak", "ana@test.si");
        Narocnina n = narocnina(racun("ana@test.si"));
        LocalDateTime zdaj = LocalDateTime.now();

        n.setTrenutnoObdobjeDo(zdaj.plusDays(10));
        narocninaRepozitorij.save(n);
        posta.pocisti();
        assertEquals(0, preizkus.posljiOpomnike(zdaj), "deset dni pred koncem je prezgodaj");

        n.setTrenutnoObdobjeDo(zdaj.plusDays(2));
        narocninaRepozitorij.save(n);
        assertEquals(1, preizkus.posljiOpomnike(zdaj));
        PomnilniskiPosiljatelj.Sporocilo sporocilo = posta.zadnje("ana@test.si").orElseThrow();
        assertTrue(sporocilo.zadeva().contains("preizkus"), sporocilo.zadeva());
        assertTrue(sporocilo.besedilo().contains("2,99 €"), "odrasel vidi svojo ceno: " + sporocilo.besedilo());
        assertTrue(sporocilo.besedilo().contains("/narocnina"));

        assertEquals(0, preizkus.posljiOpomnike(zdaj.plusHours(24)), "obvestilo gre samo enkrat");
        assertNotNull(narocnina(racun("ana@test.si")).getOpomnikPoslanOb());
    }

    @Test
    void poKoncuInPriOtrokuObvestilaNi() {
        igralecVSifrantu("Ula", "Mlakar");
        registrirajInPotrdi("Ula", "Mlakar", "ula@test.si");
        Narocnina n = narocnina(racun("ula@test.si"));
        LocalDateTime zdaj = LocalDateTime.now();
        n.setTrenutnoObdobjeDo(zdaj.minusDays(1));
        narocninaRepozitorij.save(n);
        posta.pocisti();
        assertEquals(0, preizkus.posljiOpomnike(zdaj), "potekel preizkus ne dobi obvestila");

        // racun mlajsega od 15 let (naslov skrbnika) - cene otroku ne posiljamo
        Uporabnik u = racun("ula@test.si");
        u.setEmailSkrbnika("stars@test.si");
        uporabnikRepozitorij.save(u);
        n.setTrenutnoObdobjeDo(zdaj.plusDays(1));
        narocninaRepozitorij.save(n);
        assertEquals(0, preizkus.posljiOpomnike(zdaj));
        assertTrue(posta.vsa().isEmpty());
        assertNotNull(narocnina(u).getOpomnikPoslanOb(), "zabelezeno, da se ne preverja vsak dan znova");
    }

    // ---------- Pomozno ----------

    private PotrditevOdgovorDto registrirajInPotrdi(String ime, String priimek, String email) {
        registracija.registriraj(new RegistracijaVnos(ime, priimek, null, email, "geslo1234",
                false, ODRASEL, null), IP);
        return registracija.potrdiEposto(new PotrditevKodeVnos(email, koda(email)));
    }

    private IgralecJavniDto igralecVSifrantu(String ime, String priimek) {
        return igralci.ustvari(new IgralecVnos(ime, priimek, Spol.MOSKI, ODRASEL,
                null, null, null, null, "SLO", null, null, null, null));
    }

    private Uporabnik racun(String email) {
        return uporabnikRepozitorij.najdiZVsem(email).orElseThrow();
    }

    private Narocnina narocnina(Uporabnik u) {
        return narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }

    private String koda(String email) {
        String besedilo = posta.zadnje(email).orElseThrow(
                () -> new AssertionError("ni sporocila za " + email)).besedilo();
        Matcher m = KODA.matcher(besedilo);
        assertTrue(m.find(), "v sporocilu ni kode: " + besedilo);
        return m.group(1);
    }
}
