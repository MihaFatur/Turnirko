/* Kode popusta ob registraciji (V53): admin jih nastavi, oseba jih vpise, ugodnost
   se uveljavi na pravi poti.

   Tri vrste: meseci Premium brez placila (preizkus), odstotek na prvo placilo
   (Stripov kupon - Stripe je tu lazen, test ne sme klicati omrezja) in organizatorski
   paket brez placila (od potrditve organizatorja). Koda za potrditev naslova se
   prebere iz pomnilniskega posiljatelja kot v PreizkusPremiumTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.KodaPopustaDto;
import si.turnirko.dto.KodaPopustaUrediVnos;
import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.PreveriKodoVnos;
import si.turnirko.dto.PreverjenaKodaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.KodaPopustaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabaKodePopustaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KodePopustaTest {

    private static final String IP = "10.0.0.7";
    private static final LocalDate ODRASEL = LocalDate.of(1990, 5, 20);
    private static final Pattern KODA = Pattern.compile("\\b(\\d{6})\\b");
    /* Mora se ujemati z turnirko.stripe.webhook-skrivnost v application-test.properties. */
    private static final String SKRIVNOST = "whsec_testna_skrivnost";

    @Autowired EntityManager em;
    @Autowired KodePopustaStoritev kode;
    @Autowired RegistracijaStoritev registracija;
    @Autowired RacuniStoritev racuni;
    @Autowired IgralciStoritev igralci;
    @Autowired PlacilaStoritev placila;
    @Autowired NarocninaStoritev narocninaStoritev;
    @Autowired PreizkusPremiumStoritev preizkus;
    @Autowired CiscenjeRacunovStoritev ciscenje;
    @Autowired KodaPopustaRepozitorij kodeRepozitorij;
    @Autowired UporabaKodePopustaRepozitorij uporabe;
    @Autowired CakajocaRegistracijaRepozitorij cakajoce;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired PomnilniskiPosiljatelj posta;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @MockitoBean StripeNarocnine stripe;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
        posta.pocisti();
    }

    // ---------- Administracija ----------

    @Test
    void adminUstvariVsakoVrstoInKodoNormalizira() {
        KodaPopustaDto premium = kode.ustvari(vnos(" savinja 3 ", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        KodaPopustaDto odstotek = kode.ustvari(vnos("PRVI-20", VrstaKodePopusta.ODSTOTEK, null, 20, null));
        KodaPopustaDto organizator = kode.ustvari(vnos("klub6", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null,
                Paket.ORGANIZATOR_PLUS));

        assertEquals("SAVINJA3", premium.koda(), "velike crke, brez presledkov");
        assertEquals(3, premium.meseci());
        assertNull(premium.odstotek());
        assertEquals(KodaPopustaDto.Stanje.VELJA, premium.stanje());
        assertEquals(20, odstotek.odstotek());
        assertEquals(Paket.ORGANIZATOR_PLUS, organizator.paket());
        assertEquals(6, organizator.meseci());
        assertEquals(3, kode.seznam().size());
    }

    @Test
    void praznaKodaDobiNakljucno() {
        KodaPopustaDto a = kode.ustvari(vnos(null, VrstaKodePopusta.PREMIUM_MESECI, 2, null, null));
        KodaPopustaDto b = kode.ustvari(vnos("  ", VrstaKodePopusta.PREMIUM_MESECI, 2, null, null));

        assertTrue(a.koda().matches("[A-Z2-9]{8}"), a.koda());
        assertNotEquals(a.koda(), b.koda());
    }

    @Test
    void neveljavneKombinacijeSoZavrnjene() {
        // meseci
        zavrnjena(vnos("A1", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null), "oblika");
        zavrnjena(vnos("MESECI0", VrstaKodePopusta.PREMIUM_MESECI, 0, null, null), "0 mesecev");
        zavrnjena(vnos("MESECI37", VrstaKodePopusta.PREMIUM_MESECI, 37, null, null), "37 mesecev");
        zavrnjena(vnos("BREZMESECEV", VrstaKodePopusta.PREMIUM_MESECI, null, null, null), "brez mesecev");
        // odstotek
        zavrnjena(vnos("ODSTOTEK0", VrstaKodePopusta.ODSTOTEK, null, 0, null), "0 %");
        zavrnjena(vnos("ODSTOTEK76", VrstaKodePopusta.ODSTOTEK, null, 76, null), "76 %: Stripe ne sprejme placila pod 0,50 EUR");
        zavrnjena(vnos("BREZODSTOTKA", VrstaKodePopusta.ODSTOTEK, null, null, null), "brez odstotka");
        // organizatorski paket
        zavrnjena(vnos("ORGBREZPAKETA", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, null), "brez paketa");
        zavrnjena(vnos("ORGPREMIUM", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.PREMIUM), "Premium ni organizatorski");
        // sumniki v kodi
        zavrnjena(vnos("ČAJ2026", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null), "sumnik");
        // rok in stevilo uporab
        zavrnjena(new KodaPopustaVnos("PRETEKLA", null, VrstaKodePopusta.PREMIUM_MESECI, 3, null, null, null,
                LocalDate.now().minusDays(1), null), "rok je ze potekel");
        zavrnjena(new KodaPopustaVnos("NIC-UPORAB", null, VrstaKodePopusta.PREMIUM_MESECI, 3, null, null, null,
                null, 0), "0 uporab");
        assertTrue(kode.seznam().isEmpty(), "zavrnjena koda ne pusti zapisa");
    }

    @Test
    void dvojnaKodaSeZavrneTudiCeSeRazlikujeVVelikostiCrk() {
        kode.ustvari(vnos("DVOJNA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));

        assertThrows(DomenskaIzjema.class,
                () -> kode.ustvari(vnos("dvojna", VrstaKodePopusta.PREMIUM_MESECI, 6, null, null)));
    }

    /* Kdor se je ze registriral, je dobil, kar je koda takrat obljubljala: urejanje
       spremeni samo opis, rok, najvec uporab in aktivnost. */
    @Test
    void urejanjeNeSpremeniUgodnosti() {
        KodaPopustaDto k = kode.ustvari(vnos("UREJANJE", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));

        KodaPopustaDto urejena = kode.uredi(k.id(),
                new KodaPopustaUrediVnos("nov opis", LocalDate.now().plusDays(10), 5, false));

        assertEquals(3, urejena.meseci());
        assertEquals(VrstaKodePopusta.PREMIUM_MESECI, urejena.vrsta());
        assertEquals("nov opis", urejena.opis());
        assertEquals(5, urejena.najvecUporab());
        assertEquals(KodaPopustaDto.Stanje.IZKLJUCENA, urejena.stanje());
    }

    @Test
    void izbrisaSeSamoNeuporabljenaKoda() {
        KodaPopustaDto uporabljena = kode.ustvari(vnos("UPORABLJENA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        KodaPopustaDto prosta = kode.ustvari(vnos("PROSTA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        igralecVSifrantu("Ana", "Kos");
        registrirajIgralca("Ana", "Kos", "ana@test.si", "UPORABLJENA");

        assertThrows(DomenskaIzjema.class, () -> kode.zbrisi(uporabljena.id()));
        kode.zbrisi(prosta.id());

        assertEquals(1, kode.seznam().size());
        assertEquals(1, kode.seznam().get(0).uporab());
    }

    // ---------- Meseci Premium (brezplacna registracija) ----------

    @Test
    void kodaZMeseciPodaljsaPreizkus() {
        kode.ustvari(vnos("SAVINJA3", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        igralecVSifrantu("Nina", "Zupan");

        // koda se vpise tudi z malimi crkami in presledkom
        registrirajIgralca("Nina", "Zupan", "nina@test.si", "  savinja3 ");
        PotrditevOdgovorDto odgovor = potrdi("nina@test.si");

        assertTrue(odgovor.povezan());
        assertEquals(LocalDate.now().plusMonths(3), odgovor.preizkusDo(),
                "odgovor pove konec preizkusa, ki ga nosi koda");
        Narocnina n = narocnina(racun("nina@test.si"));
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertEquals(Paket.PREMIUM, n.getPaket());
        assertTrue(narocninaStoritev.imaPremium(racun("nina@test.si")));
        assertEquals(1, kode.seznam().get(0).uporab());
    }

    @Test
    void brezKodeJePreizkusMesec() {
        igralecVSifrantu("Eva", "Horvat");
        registrirajIgralca("Eva", "Horvat", "eva@test.si", null);

        assertEquals(LocalDate.now().plusMonths(1), potrdi("eva@test.si").preizkusDo());
    }

    /* Meseci tecejo od povezave, ne od registracije: racun, ki caka na admina, ne
       sme izgubiti meseca s cakanjem. */
    @Test
    void meseciTecejoOdPovezaveZAdminom() {
        kode.ustvari(vnos("CAKAM", VrstaKodePopusta.PREMIUM_MESECI, 6, null, null));
        var igralec = igralecVSifrantu("Tine", "Kos");
        registracija.registriraj(new RegistracijaVnos("Tine", "Kos", null, "tine@test.si", "geslo1234",
                false, ODRASEL.plusDays(3), null, "CAKAM"), IP); // drug datum rojstva: samodejne povezave ni
        assertFalse(potrdi("tine@test.si").povezan());
        assertTrue(narocninaRepozitorij.findByUporabnikId(racun("tine@test.si").getId()).isEmpty());

        racuni.potrdi(racun("tine@test.si").getId(), new PotrditevRacunaVnos(igralec.id()));

        Narocnina n = narocnina(racun("tine@test.si"));
        assertEquals(LocalDate.now().plusMonths(6), UpravljanjeNarocnineStoritev.dan(n.getTrenutnoObdobjeDo()));
    }

    @Test
    void napacnaKodaZavrneRegistracijoBrezRacunaInBrezPorabeMinute() {
        igralecVSifrantu("Maja", "Kralj");

        NeveljavenVnosIzjema napaka = assertThrows(NeveljavenVnosIzjema.class,
                () -> registrirajIgralca("Maja", "Kralj", "maja@test.si", "NI-TAKE"));

        assertEquals(KodePopustaStoritev.NEVELJAVNA_KODA, napaka.getMessage());
        assertTrue(uporabnikRepozitorij.najdiZVsem("maja@test.si").isEmpty(), "racun ne nastane");
        // minuta za novo kodo ni porabljena: isti naslov sme takoj poskusiti znova
        registrirajIgralca("Maja", "Kralj", "maja@test.si", null);
        assertTrue(uporabnikRepozitorij.najdiZVsem("maja@test.si").isPresent());
    }

    @Test
    void ugasnjenaPoteklaInIzcrpanaKodaDobitaIstoSporocilo() {
        KodaPopustaDto ugasnjena = kode.ustvari(vnos("UGASNJENA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        kode.uredi(ugasnjena.id(), new KodaPopustaUrediVnos(null, null, null, false));
        KodaPopustaDto potekla = kode.ustvari(vnos("POTEKLA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        KodaPopusta p = kodeRepozitorij.findById(potekla.id()).orElseThrow();
        p.setVeljaDo(LocalDate.now().minusDays(1));
        kodeRepozitorij.save(p);
        kode.ustvari(new KodaPopustaVnos("IZCRPANA", null, VrstaKodePopusta.PREMIUM_MESECI, 3, null, null, null,
                null, 1));
        registrirajIgralca("Prvi", "Uporabnik", "prvi@test.si", "IZCRPANA");

        for (String koda : new String[] {"UGASNJENA", "POTEKLA", "IZCRPANA", "NE-OBSTAJA"}) {
            NeveljavenVnosIzjema napaka = assertThrows(NeveljavenVnosIzjema.class,
                    () -> registrirajIgralca("Drugi", "Uporabnik", "drugi-" + koda.toLowerCase() + "@test.si", koda),
                    koda);
            assertEquals(KodePopustaStoritev.NEVELJAVNA_KODA, napaka.getMessage(),
                    koda + ": odgovor ne sme povedati, katere kode obstajajo");
        }
    }

    /* Kdor registracije ni dokoncal in jo ponovi, pri kodi z omejenim stevilom uporab
       ne sme obstati pred zaprtimi vrati zaradi svojega lastnega ostanka. */
    @Test
    void ponovnaRegistracijaIsteOsebeNeZasedeMesta() {
        kode.ustvari(new KodaPopustaVnos("ENA-SAMA", null, VrstaKodePopusta.PREMIUM_MESECI, 3, null, null, null,
                null, 1));
        registrirajIgralca("Ana", "Novak", "ana@test.si", "ENA-SAMA");
        omejevalnik.pocistiVse(); // minuta med poskusoma
        // drugi poskus je v pravem delovanju druga zahteva in druga enota dela: SQLite
        // izbrisani vrstici porabe da isti id novi, nalozena stara pa bi se z njo ujela
        em.flush();
        em.clear();

        registrirajIgralca("Ana", "Novak", "ana@test.si", "ENA-SAMA");

        assertEquals(1, kode.seznam().get(0).uporab(), "stari ostanek je zamenjan, ne podvojen");
        omejevalnik.pocistiVse();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> registrirajIgralca("Druga", "Oseba", "druga@test.si", "ENA-SAMA"),
                "mesto je ena sama, drug naslov ga ne dobi");
    }

    @Test
    void nepotrjenaRegistracijaVrneMestoKodiObCiscenju() {
        kode.ustvari(new KodaPopustaVnos("VRNI-MESTO", null, VrstaKodePopusta.PREMIUM_MESECI, 3, null, null, null,
                null, 1));
        registrirajIgralca("Ana", "Novak", "ana@test.si", "VRNI-MESTO");
        assertEquals(1, kode.seznam().get(0).uporab());

        ciscenje.pocisti(LocalDateTime.now().plusHours(49));

        assertEquals(0, kode.seznam().get(0).uporab(), "racun je izbrisan in z njim njegova poraba");
        registrirajIgralca("Druga", "Oseba", "druga@test.si", "VRNI-MESTO");
    }

    // ---------- Koda na napacni poti ----------

    @Test
    void kodaNaNapacniPotiDobiRazlago() {
        kode.ustvari(vnos("SAMOODSTOTEK", VrstaKodePopusta.ODSTOTEK, null, 20, null));
        kode.ustvari(vnos("SAMOMESECI", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        kode.ustvari(vnos("SAMOORG", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.ORGANIZATOR_BASIC));

        assertTrue(zavrnitev(() -> registrirajIgralca("A", "A", "a1@test.si", "SAMOODSTOTEK")).contains("Premium"));
        assertTrue(zavrnitev(() -> registracija.registriraj(organizator("a2@test.si", "SAMOMESECI"), IP))
                .contains("igralca"));
        assertTrue(zavrnitev(() -> registrirajIgralca("A", "A", "a3@test.si", "SAMOORG")).contains("organizatorja"));
        assertTrue(zavrnitev(() -> registracija.registriraj(organizator("a4@test.si", "SAMOODSTOTEK"), IP))
                .contains("paket s plačilom"));
        assertTrue(uporabnikRepozitorij.najdiZVsem("a1@test.si").isEmpty());
    }

    // ---------- Organizatorski paket brez placila ----------

    @Test
    void organizatorskiPaketZacneTeciSeleObPotrditviOrganizatorja() {
        kode.ustvari(vnos("KLUB6", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.ORGANIZATOR_PLUS));

        registracija.registriraj(organizator("org@test.si", "klub6"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("org@test.si", koda("org@test.si")));
        Uporabnik u = racun("org@test.si");
        assertTrue(narocninaRepozitorij.findByUporabnikId(u.getId()).isEmpty(),
                "do potrditve admina paket ne tece - meseci bi se izgubili s cakanjem");

        racuni.potrdiOrganizatorja(u.getId(), null);

        Narocnina n = narocnina(racun("org@test.si"));
        assertEquals(Paket.ORGANIZATOR_PLUS, n.getPaket());
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertNull(n.getStripeNarocninaId(), "brez Stripa");
        assertEquals(LocalDate.now().plusMonths(6), UpravljanjeNarocnineStoritev.dan(n.getTrenutnoObdobjeDo()));
        assertEquals(Paket.ORGANIZATOR_PLUS, narocninaStoritev.paketOrganizatorja(racun("org@test.si")).orElseThrow(),
                "organizator ima meje paketa Plus");

        // po koncu obdobja paket potece sam
        n.setTrenutnoObdobjeDo(LocalDateTime.now().minusMinutes(1));
        narocninaRepozitorij.save(n);
        assertTrue(narocninaStoritev.paketOrganizatorja(racun("org@test.si")).isEmpty());
    }

    @Test
    void ponovnaPotrditevOrganizatorjaPaketaNePodaljsa() {
        kode.ustvari(vnos("KLUB-ENKRAT", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.ORGANIZATOR_BASIC));
        registracija.registriraj(organizator("org@test.si", "KLUB-ENKRAT"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("org@test.si", koda("org@test.si")));
        Long id = racun("org@test.si").getId();
        racuni.potrdiOrganizatorja(id, null);
        Narocnina prva = narocnina(racun("org@test.si"));
        prva.setTrenutnoObdobjeDo(LocalDateTime.now().minusDays(1));
        narocninaRepozitorij.save(prva);

        racuni.potrdiOrganizatorja(id, null);

        assertFalse(narocnina(racun("org@test.si")).jeVeljavna(LocalDateTime.now()),
                "kdor vrstico narocnine ze ima, paketa ne dobi znova");
    }

    @Test
    void obvestiloOKoncuPreizkusaGreSamoPremiumu() {
        kode.ustvari(vnos("KLUB-OBV", VrstaKodePopusta.ORGANIZATOR_MESECI, 1, null, Paket.ORGANIZATOR_BASIC));
        registracija.registriraj(organizator("org@test.si", "KLUB-OBV"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("org@test.si", koda("org@test.si")));
        racuni.potrdiOrganizatorja(racun("org@test.si").getId(), null);
        Narocnina n = narocnina(racun("org@test.si"));
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(2));
        narocninaRepozitorij.save(n);
        posta.pocisti();

        assertEquals(0, preizkus.posljiOpomnike(LocalDateTime.now()),
                "sporocilo o koncu navaja ceno Premium, organizatorju ga ne posiljamo");
        assertTrue(posta.vsa().isEmpty());
    }

    // ---------- Odstotek na prvo placilo ----------

    @Test
    void odstotekDodaKuponInRezerviraMesto() {
        kode.ustvari(vnos("PRVI20", VrstaKodePopusta.ODSTOTEK, null, 20, null));
        when(stripe.ustvariKupon("PRVI20", 20)).thenReturn("co_prvi20");

        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                placana("maja@test.si", " prvi20", Paket.PREMIUM), IP);

        Map<String, Object> seja = p.seja().toMap();
        String popusti = String.valueOf(seja.get("discounts"));
        assertTrue(popusti.contains("co_prvi20"), "kupon je na Stripovi seji: " + popusti);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) seja.get("metadata");
        assertEquals(Set.of(PlacilaStoritev.M_NAMEN, PlacilaStoritev.M_ZETON, "paket", "ciklus",
                "starejsiOd21"), meta.keySet(), "kode popusta v metapodatkih ni - v Stripe gre samo kupon");
        KodaPopusta k = kodeRepozitorij.findByKoda("PRVI20").orElseThrow();
        assertEquals(k.getId(), p.cakajoca().getIdKoda(), "cakajoca registracija drzi kodo do placila");
        assertEquals("co_prvi20", k.getStripeKuponId());
        assertEquals(1, kode.seznam().get(0).cakajocih());

        // drugi nakup z isto kodo kupona ne ustvari znova
        placila.pripraviRegistracijo(placana("jan@test.si", "PRVI20", Paket.PREMIUM), IP);
        verify(stripe, times(1)).ustvariKupon("PRVI20", 20);
    }

    @Test
    void placiloBrezKodeNimaPopustaInStripaNeKlice() {
        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                placana("brez@test.si", null, Paket.PREMIUM), IP);

        assertFalse(p.seja().toMap().containsKey("discounts"));
        assertNull(p.cakajoca().getIdKoda());
        verify(stripe, never()).ustvariKupon(anyString(), anyInt());
    }

    @Test
    void webhookPoPlaciluZabelezeUporaboInSprostiCakajoco() {
        kode.ustvari(vnos("PRVI20", VrstaKodePopusta.ODSTOTEK, null, 20, null));
        when(stripe.ustvariKupon("PRVI20", 20)).thenReturn("co_prvi20");
        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                placana("maja@test.si", "PRVI20", Paket.PREMIUM), IP);
        String telo = dogodekRegistracija("evt_k1", "cs_k1", "cus_k1", "sub_k1", p.cakajoca().getZeton());

        placila.obdelajDogodek(telo, podpisi(telo));

        Uporabnik u = racun("maja@test.si");
        assertEquals("PRVI20", uporabe.najdiZaUporabnika(u.getId()).orElseThrow().getKoda().getKoda());
        KodaPopustaDto dto = kode.seznam().get(0);
        assertEquals(1, dto.uporab());
        assertEquals(0, dto.cakajocih(), "rezervacija je postala poraba, ne dvojno stetje");
        assertEquals(StatusNarocnine.AKTIVNA, narocnina(u).getStatus());
        assertEquals(Paket.PREMIUM, narocnina(u).getPaket());
    }

    @Test
    void cakajocaRegistracijaDrziMestoDrugemuNeDrugiPonovitviIsteOsebe() {
        kode.ustvari(new KodaPopustaVnos("MALO-MEST", null, VrstaKodePopusta.ODSTOTEK, null, 20, null, null,
                null, 1));
        when(stripe.ustvariKupon("MALO-MEST", 20)).thenReturn("co_malo");
        placila.pripraviRegistracijo(placana("prva@test.si", "MALO-MEST", Paket.PREMIUM), IP);

        // opuscen nakup iste osebe ne zaseda mesta njenemu ponovnemu poskusu ...
        placila.pripraviRegistracijo(placana("prva@test.si", "MALO-MEST", Paket.PREMIUM), IP);
        // ... druga oseba pa mesta ne dobi, dokler ga drzi prva
        assertThrows(NeveljavenVnosIzjema.class,
                () -> placila.pripraviRegistracijo(placana("druga@test.si", "MALO-MEST", Paket.PREMIUM), IP));
    }

    @Test
    void odstotekZaDrugoVlogoInNapacnoPotJeZavrnjen() {
        kode.ustvari(new KodaPopustaVnos("SAMO-ORG", null, VrstaKodePopusta.ODSTOTEK, null, 10, null,
                Vloga.ORGANIZATOR, null, null));
        kode.ustvari(vnos("MESECI-PLACILO", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        kode.ustvari(vnos("ORG-PLACILO", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.ORGANIZATOR_PLUS));

        assertTrue(zavrnitev(() -> placila.pripraviRegistracijo(placana("a@test.si", "SAMO-ORG", Paket.PREMIUM), IP))
                .contains("organizatorje"));
        assertTrue(zavrnitev(() -> placila.pripraviRegistracijo(placana("b@test.si", "MESECI-PLACILO", Paket.PREMIUM), IP))
                .contains("brezplačno"));
        assertTrue(zavrnitev(() -> placila.pripraviRegistracijo(
                placanOrganizator("c@test.si", "ORG-PLACILO", Paket.ORGANIZATOR_PLUS), IP)).contains("brez plačila"));
        assertTrue(cakajoce.findAll().isEmpty(), "zavrnjen vnos ne pusti cakajoce registracije");
    }

    @Test
    void prvoPlaciloSeZaokroziKotPriStripu() {
        assertEquals(799, KodePopustaStoritev.prvoPlacilo(9.99, 20));
        assertEquals(159, KodePopustaStoritev.prvoPlacilo(1.99, 20));
        assertEquals(50, KodePopustaStoritev.prvoPlacilo(1.99, KodePopustaStoritev.NAJVEC_ODSTOTEK),
                "pri najvecjem odstotku najcenejsi paket se doseze Stripov najmanjsi znesek");
        assertTrue(KodePopustaStoritev.prvoPlacilo(1.99, KodePopustaStoritev.NAJVEC_ODSTOTEK)
                >= KodePopustaStoritev.NAJMANJSE_PLACILO_CENTOV);
    }

    // ---------- Predogled ----------

    @Test
    void predogledPovePrinasaInUporabnost() {
        kode.ustvari(vnos("PREDOGLED3", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        kode.ustvari(vnos("PREDOGLED20", VrstaKodePopusta.ODSTOTEK, null, 20, null));
        kode.ustvari(vnos("PREDOGLEDORG", VrstaKodePopusta.ORGANIZATOR_MESECI, 6, null, Paket.ORGANIZATOR_PRO));

        PreverjenaKodaDto meseci = kode.predogled(new PreveriKodoVnos("predogled3", false, Paket.BREZPLACNO, null), IP);
        assertTrue(meseci.veljavna() && meseci.uporabna());
        assertEquals(3, meseci.meseci());
        assertEquals(VrstaKodePopusta.PREMIUM_MESECI, meseci.vrsta());

        PreverjenaKodaDto napacnaPot = kode.predogled(new PreveriKodoVnos("PREDOGLED3", false, Paket.PREMIUM, null), IP);
        assertTrue(napacnaPot.veljavna());
        assertFalse(napacnaPot.uporabna());
        assertNotNull(napacnaPot.sporocilo());

        PreverjenaKodaDto odstotek = kode.predogled(new PreveriKodoVnos("PREDOGLED20", false, Paket.PREMIUM, null), IP);
        assertTrue(odstotek.uporabna());
        assertEquals(20, odstotek.odstotek());
        assertFalse(kode.predogled(new PreveriKodoVnos("PREDOGLED20", false, Paket.BREZPLACNO, null), IP).uporabna());

        // organizator je paket izbral vnaprej: koda z meseci ga nadomesti, odstotek ga zniza
        PreverjenaKodaDto org = kode.predogled(
                new PreveriKodoVnos("PREDOGLEDORG", true, Paket.ORGANIZATOR_BASIC, null), IP);
        assertTrue(org.uporabna());
        assertEquals(Paket.ORGANIZATOR_PRO, org.paket());
        assertTrue(kode.predogled(new PreveriKodoVnos("PREDOGLED20", true, Paket.ORGANIZATOR_BASIC, null), IP)
                .uporabna());
    }

    @Test
    void predogledNeveljavneKodeNeRazkrivaNicesar() {
        KodaPopustaDto ugasnjena = kode.ustvari(vnos("UGASNJENA", VrstaKodePopusta.PREMIUM_MESECI, 3, null, null));
        kode.uredi(ugasnjena.id(), new KodaPopustaUrediVnos(null, null, null, false));

        PreverjenaKodaDto a = kode.predogled(new PreveriKodoVnos("UGASNJENA", false, Paket.BREZPLACNO, null), IP);
        PreverjenaKodaDto b = kode.predogled(new PreveriKodoVnos("NE-OBSTAJA", false, Paket.BREZPLACNO, null), IP);

        assertEquals(a, b, "ugasnjena in neobstojeca koda dobita enak odgovor");
        assertFalse(a.veljavna());
        assertNull(a.koda());
        assertNull(a.meseci());
    }

    @Test
    void ugibanjeKodJeOmejenoPoNaslovuIp() {
        for (int i = 0; i < KodePopustaStoritev.NAJVEC_PREVERJANJ_NA_URO; i++) {
            kode.predogled(new PreveriKodoVnos("UGIBAM" + i, false, Paket.BREZPLACNO, null), IP);
        }

        assertThrows(PrevecZahtevIzjema.class,
                () -> kode.predogled(new PreveriKodoVnos("UGIBAM-ZE-PREVEC", false, Paket.BREZPLACNO, null), IP));
        // drug naslov ni prizadet
        assertFalse(kode.predogled(new PreveriKodoVnos("UGIBAM-DRUG", false, Paket.BREZPLACNO, null), "10.9.9.9")
                .veljavna());
    }

    // ---------- Pomozno ----------

    private static KodaPopustaVnos vnos(String koda, VrstaKodePopusta vrsta, Integer meseci, Integer odstotek,
                                        Paket paket) {
        return new KodaPopustaVnos(koda, null, vrsta, meseci, odstotek, paket, null, null, null);
    }

    private void zavrnjena(KodaPopustaVnos vnos, String zakaj) {
        assertThrows(NeveljavenVnosIzjema.class, () -> kode.ustvari(vnos), zakaj);
    }

    private static String zavrnitev(Runnable dejanje) {
        return assertThrows(NeveljavenVnosIzjema.class, dejanje::run).getMessage();
    }

    private void registrirajIgralca(String ime, String priimek, String email, String kodaPopusta) {
        registracija.registriraj(new RegistracijaVnos(ime, priimek, null, email, "geslo1234", false, ODRASEL,
                null, kodaPopusta), IP);
    }

    private static RegistracijaVnos organizator(String email, String kodaPopusta) {
        return new RegistracijaVnos("Org", "Anizator", null, email, "geslo1234", true, null, null, kodaPopusta);
    }

    private PotrditevOdgovorDto potrdi(String email) {
        return registracija.potrdiEposto(new PotrditevKodeVnos(email, koda(email)));
    }

    private PlacanaRegistracijaVnos placana(String email, String kodaPopusta, Paket paket) {
        return new PlacanaRegistracijaVnos(new RegistracijaVnos("Maja", "Kos", null, email, "geslo1234", false,
                LocalDate.of(1995, 3, 4), null, kodaPopusta), paket, CiklusPlacila.MESECNO);
    }

    private PlacanaRegistracijaVnos placanOrganizator(String email, String kodaPopusta, Paket paket) {
        return new PlacanaRegistracijaVnos(new RegistracijaVnos("Org", "Anizator", null, email, "geslo1234", true,
                null, null, kodaPopusta), paket, CiklusPlacila.LETNO);
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

    private String koda(String email) {
        String besedilo = posta.zadnje(email).orElseThrow(
                () -> new AssertionError("ni sporocila za " + email)).besedilo();
        Matcher m = KODA.matcher(besedilo);
        assertTrue(m.find(), "v sporocilu ni kode: " + besedilo);
        return m.group(1);
    }

    private static String podpisi(String telo) {
        long trenutek = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SKRIVNOST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String podpis = HexFormat.of().formatHex(
                    mac.doFinal((trenutek + "." + telo).getBytes(StandardCharsets.UTF_8)));
            return "t=" + trenutek + ",v1=" + podpis;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dogodekRegistracija(String idDogodka, String idSeje, String customer,
                                              String subscription, String zeton) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "customer": "%s",
                      "subscription": "%s",
                      "metadata": {
                        "namen": "registracija",
                        "registracija": "%s",
                        "paket": "PREMIUM",
                        "ciklus": "MESECNO",
                        "starejsiOd21": "false"
                      }
                    }
                  }
                }
                """.formatted(idDogodka, idSeje, customer, subscription, zeton);
    }
}
