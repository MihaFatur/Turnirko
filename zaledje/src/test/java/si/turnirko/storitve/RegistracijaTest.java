/* Registracija s potrditvijo e-poste, kode, skrbnik, pozabljeno geslo,
   samodejna povezava z igralcem in nocno ciscenje.

   Koda se prebere iz sporocila v pomnilniskem posiljatelju (profil test):
   v bazi je le zgostitev, kar je hkrati preverba, da nikoli ne pride v
   cistopisu nikamor drugam kot v posto. */
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.NovoGesloVnos;
import si.turnirko.dto.PonovnoPosiljanjeVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.PredogledZapisaDto;
import si.turnirko.dto.PredogledZapisaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.NamenKode;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.PotrditvenaKoda;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirPovezave;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.PotrditvenaKodaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistracijaTest {

    private static final String IP = "10.0.0.1";
    private static final LocalDate ODRASEL = LocalDate.of(1990, 5, 20);
    private static final Pattern KODA = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired RegistracijaStoritev registracija;
    @Autowired RacuniStoritev racuni;
    @Autowired IgralciStoritev igralci;
    @Autowired CiscenjeRacunovStoritev ciscenje;
    @Autowired DostopDoProfila dostopDoProfila;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired PotrditvenaKodaRepozitorij kodaRepozitorij;
    @Autowired PomnilniskiPosiljatelj posta;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired PasswordEncoder kodirnik;
    @Autowired jakarta.persistence.EntityManager em;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
        posta.pocisti();
    }

    // ---------- Potrditev naslova ----------

    @Test
    void registracijaPosljeKodoInPotrditevOznaciNaslov() {
        long cakajocihPrej = racuni.steviloCakajocih();
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);

        Uporabnik u = racun("ana@test.si");
        assertEquals(StatusRacuna.CAKA, u.getStatus());
        assertFalse(u.jeEmailPotrjen());
        assertEquals(cakajocihPrej, racuni.steviloCakajocih(),
                "nepotrjena registracija admina ne zaposluje");

        String koda = koda("ana@test.si");
        assertFalse(kodaRepozitorij.findByRacunIdAndNamen(u.getId(), NamenKode.EPOSTA).get(0)
                .getKodaHash().contains(koda), "v bazi je le zgostitev kode");

        PotrditevOdgovorDto odgovor = registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda));
        assertFalse(odgovor.povezan(), "brez igralca v sifrantu ni samodejne povezave");
        assertTrue(racun("ana@test.si").jeEmailPotrjen());
        assertEquals(cakajocihPrej + 1, racuni.steviloCakajocih());
    }

    /* Casi kode in racuna morajo prezivi zapis v bazo Z URO. Gonilnik
       sqlite-jdbc bi LocalDateTime zapisal kot "2026-09-17" in koda bi potekla
       ob polnoci pred nastankom. @Transactional test tega sam ne vidi (entiteta
       pride iz predpomnilnika seje), zato izrecni flush() + clear(). Napaka je
       bila najdena sele ob preverbi v brskalniku. */
    @Test
    void kodaInCasiRacunaPrezivijoZapisVBazo() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        String koda = koda("ana@test.si");
        em.flush();
        em.clear();

        PotrditvenaKoda zapis = kodaRepozitorij
                .findByRacunIdAndNamen(racun("ana@test.si").getId(), NamenKode.EPOSTA).get(0);
        assertTrue(zapis.getPoteceOb().isAfter(LocalDateTime.now().plusMinutes(9)),
                "potek kode mora imeti uro: " + zapis.getPoteceOb());
        em.clear();

        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda));
        em.flush();
        em.clear();

        Uporabnik u = racun("ana@test.si");
        assertTrue(u.getEmailPotrjenOb().isAfter(LocalDateTime.now().minusMinutes(1)),
                "cas potrditve mora imeti uro: " + u.getEmailPotrjenOb());
        assertTrue(u.getUstvarjenOb().isAfter(LocalDateTime.now().minusMinutes(1)),
                "cas nastanka mora imeti uro: " + u.getUstvarjenOb());
    }

    /* Ciscenje steje ure, ne dni - brez ure v bazi bi registracija izpred
       ene ure padla v ciscenje ze po 24 urah oz. sele po 72. */
    @Test
    void ciscenjeStejeUreSkoziBazo() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        em.flush();
        em.clear();

        ciscenje.pocisti(LocalDateTime.now().plusHours(47));
        em.flush();
        em.clear();
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isPresent(),
                "po 47 urah registracija se ostane");

        ciscenje.pocisti(LocalDateTime.now().plusHours(49));
        em.flush();
        em.clear();
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isEmpty(),
                "po 49 urah je registracija izbrisana");
    }

    @Test
    void kodaSePorabiInPresledkiNeMotijo() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        String koda = koda("ana@test.si");
        String sPresledki = koda.substring(0, 3) + " " + koda.substring(3);

        registracija.potrdiEposto(new PotrditevKodeVnos("Ana@Test.si", sPresledki));

        // ista koda drugic ne velja vec
        assertThrows(NeveljavenVnosIzjema.class,
                () -> registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda)));
    }

    @Test
    void petNapacnihVnosovUbijeKodo() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        String prava = koda("ana@test.si");
        String napacna = prava.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < KodeStoritev.NAJVEC_POSKUSOV; i++) {
            assertThrows(NeveljavenVnosIzjema.class,
                    () -> registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", napacna)));
        }
        assertThrows(NeveljavenVnosIzjema.class,
                () -> registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", prava)),
                "po zadnjem dovoljenem poskusu tudi prava koda ne velja vec");
        assertFalse(racun("ana@test.si").jeEmailPotrjen());
    }

    @Test
    void poteklaKodaNeVelja() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        String koda = koda("ana@test.si");
        PotrditvenaKoda zapis = kodaRepozitorij
                .findByRacunIdAndNamen(racun("ana@test.si").getId(), NamenKode.EPOSTA).get(0);
        zapis.setPoteceOb(LocalDateTime.now().minusMinutes(1));
        kodaRepozitorij.save(zapis);

        assertThrows(NeveljavenVnosIzjema.class,
                () -> registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda)));
    }

    @Test
    void ponovnoPosiljanjeDaNovoKodoInStaroRazveljavi() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        String prva = koda("ana@test.si");
        omejevalnik.pocistiVse();

        registracija.ponovnoPoslji(new PonovnoPosiljanjeVnos("ana@test.si", NamenKode.EPOSTA), IP);
        String druga = koda("ana@test.si");

        assertEquals(1, kodaRepozitorij.findByRacunIdAndNamen(racun("ana@test.si").getId(),
                NamenKode.EPOSTA).size(), "ziva je najvec ena koda");
        if (!prva.equals(druga)) {
            assertThrows(NeveljavenVnosIzjema.class,
                    () -> registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", prva)));
        }
        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", druga));
        assertTrue(racun("ana@test.si").jeEmailPotrjen());
    }

    // ---------- Naslov, ki ze obstaja ----------

    @Test
    void nepotrjenNaslovNovaRegistracijaZamenja() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        omejevalnik.pocistiVse();
        registracija.registriraj(igralec("Anja", "Novak", "ana@test.si"), IP);

        List<Uporabnik> vsi = uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getUporabniskoIme().equals("ana@test.si")).toList();
        assertEquals(1, vsi.size());
        assertEquals("Anja", vsi.get(0).getPrijavljenoIme());
    }

    @Test
    void potrjenNaslovOstaneZasedenBrezRazkritja() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda("ana@test.si")));
        omejevalnik.pocistiVse();

        var odgovor = registracija.registriraj(igralec("Nekdo", "Drug", "ana@test.si"), IP);

        assertEquals("ana@test.si", odgovor.email());
        Uporabnik u = racun("ana@test.si");
        assertEquals("Ana", u.getPrijavljenoIme(), "obstojeci racun ostane nedotaknjen");
        assertTrue(posta.zadnje("ana@test.si").orElseThrow().zadeva().contains("že obstaja"),
                "lastnik dobi obvestilo, klicatelj pa enak odgovor kot sicer");
    }

    @Test
    void zavrnjenRacunNaslovZasedeTudiBrezPotrditve() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        Long id = racun("ana@test.si").getId();
        racuni.zavrni(id);
        omejevalnik.pocistiVse();

        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);

        assertEquals(id, racun("ana@test.si").getId(), "zavrnjenega racuna registracija ne zamenja");
        assertEquals(StatusRacuna.ZAVRNJEN, racun("ana@test.si").getStatus());

        // ciscenje ga ne odnese (naslov ostane zaseden, dokler ga admin ne izbrise)
        ciscenje.pocisti(LocalDateTime.now().plusDays(3));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isPresent());
    }

    @Test
    void omejitevPosiljanjaKodNaIstiNaslov() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        assertThrows(PrevecZahtevIzjema.class,
                () -> registracija.ponovnoPoslji(new PonovnoPosiljanjeVnos("ana@test.si", NamenKode.EPOSTA), IP));
        assertThrows(PrevecZahtevIzjema.class,
                () -> registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP));
    }

    // ---------- Predogled zapisa (pred registracijo) ----------

    @Test
    void predogledZapisaVrneZadetekBrezRacuna() {
        IgralecJavniDto igralec = igralecVSifrantu("Žiga", "Šuštar", ODRASEL);

        PredogledZapisaDto odgovor = registracija.predogledZapisa(
                new PredogledZapisaVnos("ZIGA", "Sustar", ODRASEL), IP);

        assertTrue(odgovor.najden());
        assertEquals(igralec.id(), odgovor.idIgralec());
        assertEquals("Žiga", odgovor.ime());
        assertEquals("Šuštar", odgovor.priimek());
    }

    @Test
    void predogledZapisaBrezZadetkaAliDrugDatum() {
        assertFalse(registracija.predogledZapisa(
                new PredogledZapisaVnos("Ne", "Obstaja", ODRASEL), IP).najden());

        igralecVSifrantu("Ana", "Kovač", ODRASEL);
        assertFalse(registracija.predogledZapisa(
                new PredogledZapisaVnos("Ana", "Kovač", ODRASEL.plusDays(1)), IP).najden(),
                "drug datum rojstva ni isti clovek");

        igralecVSifrantu("Ana", "Kovač", ODRASEL); // soimenjakinja z istim datumom
        assertFalse(registracija.predogledZapisa(
                new PredogledZapisaVnos("Ana", "Kovač", ODRASEL), IP).najden(),
                "dva zadetka - odloci admin, predogled ne izbira");
    }

    @Test
    void predogledZapisaIgralcaZRacunomNeVrneZadetka() {
        IgralecJavniDto igralec = igralecVSifrantu("Ana", "Kovač", ODRASEL);
        registracija.registriraj(igralec("Ana", "Kovač", "ana@test.si"), IP);
        racuni.potrdi(racun("ana@test.si").getId(), new PotrditevRacunaVnos(igralec.id()));

        assertFalse(registracija.predogledZapisa(
                new PredogledZapisaVnos("Ana", "Kovač", ODRASEL), IP).najden(),
                "igralec ze ima racun - poveze ga lahko samo admin");
    }

    @Test
    void omejitevPredogledaPoIp() {
        for (int i = 0; i < RegistracijaStoritev.NA_IP_NA_URO_PREDOGLED; i++) {
            registracija.predogledZapisa(new PredogledZapisaVnos("Ana", "Kovač", ODRASEL), IP);
        }
        assertThrows(PrevecZahtevIzjema.class, () -> registracija.predogledZapisa(
                new PredogledZapisaVnos("Ana", "Kovač", ODRASEL), IP));
    }

    // ---------- Samodejna povezava ----------

    @Test
    void enZadetekPoImenuPriimkuInDatumuPovezeSamodejno() {
        IgralecJavniDto igralec = igralecVSifrantu("Žiga", "Šuštar", ODRASEL);
        // vpis brez sumnikov in z velikimi crkami se mora ujeti
        registracija.registriraj(igralec("ZIGA", "Sustar", "ziga@test.si"), IP);

        PotrditevOdgovorDto odgovor = registracija.potrdiEposto(
                new PotrditevKodeVnos("ziga@test.si", koda("ziga@test.si")));

        assertTrue(odgovor.povezan());
        Uporabnik u = racun("ziga@test.si");
        assertEquals(StatusRacuna.POTRJEN, u.getStatus());
        assertEquals(igralec.id(), u.getIgralec().getId());
        assertEquals(VirPovezave.SAMODEJNO, u.getVirPovezave());
        assertNotNull(u.getPovezanOb());
        // s Premium paketom sme videti svoj zasebni profil (brez njega ne bi)
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        dostopDoProfila.preveriLastnistvo(igralec.id(), "ziga@test.si");
    }

    @Test
    void drugDatumRojstvaAliDvaZadetkaNePovezeta() {
        igralecVSifrantu("Ana", "Kovač", ODRASEL);
        registracija.registriraj(new RegistracijaVnos("Ana", "Kovač", null, "ana@test.si",
                "geslo1234", false, ODRASEL.plusDays(1), null), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda("ana@test.si")));
        assertNull(racun("ana@test.si").getIgralec(), "drug datum rojstva ni isti clovek");

        igralecVSifrantu("Ana", "Kovač", ODRASEL); // soimenjakinja z istim datumom
        registracija.registriraj(igralec("Ana", "Kovač", "ana2@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("ana2@test.si", koda("ana2@test.si")));
        assertNull(racun("ana2@test.si").getIgralec(), "dva zadetka - odloci admin");
        assertEquals(StatusRacuna.CAKA, racun("ana2@test.si").getStatus());
    }

    @Test
    void igralecZRacunomSeNePovezeSeEnkrat() {
        IgralecJavniDto igralec = igralecVSifrantu("Ana", "Kovač", ODRASEL);
        registracija.registriraj(igralec("Ana", "Kovač", "prva@test.si"), IP);
        racuni.potrdi(racun("prva@test.si").getId(), new PotrditevRacunaVnos(igralec.id()));
        assertEquals(VirPovezave.ADMIN, racun("prva@test.si").getVirPovezave());

        registracija.registriraj(igralec("Ana", "Kovač", "druga@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("druga@test.si", koda("druga@test.si")));

        assertNull(racun("druga@test.si").getIgralec());
    }

    @Test
    void razvezavaVrneRacunVCakanjeInDovoliNovoPovezavo() {
        IgralecJavniDto igralec = igralecVSifrantu("Ana", "Kovač", ODRASEL);
        registracija.registriraj(igralec("Ana", "Kovač", "ana@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda("ana@test.si")));
        Long id = racun("ana@test.si").getId();
        assertNotNull(racun("ana@test.si").getIgralec());

        racuni.razvezi(id);

        Uporabnik u = racun("ana@test.si");
        assertNull(u.getIgralec());
        assertEquals(StatusRacuna.CAKA, u.getStatus());
        assertNull(u.getVirPovezave());

        racuni.potrdi(id, new PotrditevRacunaVnos(igralec.id()));
        assertEquals(igralec.id(), racun("ana@test.si").getIgralec().getId());
    }

    // ---------- Skrbnik (mlajsi od 15 let) ----------

    @Test
    void mlajsiOd15PotrebujeSkrbnikaInNjegovoKodo() {
        LocalDate otrok = LocalDate.now().minusYears(12);
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.registriraj(
                new RegistracijaVnos("Tim", "Mali", null, "tim@test.si", "geslo1234", false, otrok, null), IP));
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.registriraj(
                new RegistracijaVnos("Tim", "Mali", null, "tim@test.si", "geslo1234", false, otrok,
                        "tim@test.si"), IP), "skrbnik mora imeti drug naslov");

        IgralecJavniDto igralec = igralecVSifrantu("Tim", "Mali", otrok);
        var odgovorReg = registracija.registriraj(new RegistracijaVnos("Tim", "Mali", null, "tim@test.si",
                "geslo1234", false, otrok, "Stars@test.si"), IP);
        assertTrue(odgovorReg.potrebnaKodaSkrbnika());
        assertEquals("stars@test.si", racun("tim@test.si").getEmailSkrbnika());

        String kodaOtroka = koda("tim@test.si");
        String kodaSkrbnika = koda("stars@test.si");
        assertTrue(posta.zadnje("stars@test.si").orElseThrow().besedilo().contains("Tim Mali"));

        // skrbnikova koda ne velja kot otrokova in obratno
        assertThrows(NeveljavenVnosIzjema.class,
                () -> registracija.potrdiEposto(new PotrditevKodeVnos("tim@test.si", kodaSkrbnika)));

        PotrditevOdgovorDto poOtroku = registracija.potrdiEposto(new PotrditevKodeVnos("tim@test.si", kodaOtroka));
        assertFalse(poOtroku.povezan(), "brez soglasja skrbnika ni povezave");
        assertTrue(poOtroku.potrebnaKodaSkrbnika());

        PotrditevOdgovorDto poSkrbniku = registracija.potrdiSkrbnika(
                new PotrditevKodeVnos("tim@test.si", kodaSkrbnika));
        assertTrue(poSkrbniku.povezan());
        assertFalse(poSkrbniku.potrebnaKodaSkrbnika());
        assertEquals(igralec.id(), racun("tim@test.si").getIgralec().getId());
    }

    @Test
    void odraslemuSkrbnikNiPotreben() {
        var odgovor = registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        assertFalse(odgovor.potrebnaKodaSkrbnika());
        assertNull(racun("ana@test.si").getEmailSkrbnika());
        assertTrue(posta.vsa().stream().allMatch(s -> s.prejemnik().equals("ana@test.si")));
    }

    // ---------- Pozabljeno geslo ----------

    @Test
    void pozabljenoGesloNastaviNovoSKodo() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("ana@test.si", koda("ana@test.si")));
        omejevalnik.pocistiVse();

        registracija.pozabljenoGeslo("ana@test.si", IP);
        String koda = koda("ana@test.si");
        assertTrue(posta.zadnje("ana@test.si").orElseThrow().zadeva().contains("geslo"));

        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.novoGeslo(
                new NovoGesloVnos("ana@test.si", "999999".equals(koda) ? "999998" : "999999", "novoGeslo1")));
        registracija.novoGeslo(new NovoGesloVnos("ana@test.si", koda, "novoGeslo1"));

        assertTrue(kodirnik.matches("novoGeslo1", racun("ana@test.si").getGesloHash()));
        assertFalse(kodirnik.matches("geslo1234", racun("ana@test.si").getGesloHash()));
    }

    @Test
    void pozabljenoGesloBrezPotrjenegaNaslovaAliRacunaNePosljeNicesar() {
        registracija.registriraj(igralec("Ana", "Novak", "ana@test.si"), IP);
        int poslanih = posta.vsa().size();
        omejevalnik.pocistiVse();

        registracija.pozabljenoGeslo("ana@test.si", IP);   // naslov se ni potrjen
        registracija.pozabljenoGeslo("nihce@test.si", IP); // racuna ni

        assertEquals(poslanih, posta.vsa().size());
        assertThrows(NeveljavenVnosIzjema.class, () -> registracija.novoGeslo(
                new NovoGesloVnos("nihce@test.si", "123456", "novoGeslo1")));
    }

    // ---------- Ciscenje ----------

    @Test
    void ciscenjeOdneseNepotrjeneRacuneInPustiPotrjene() {
        registracija.registriraj(igralec("Ana", "Novak", "nepotrjena@test.si"), IP);
        registracija.registriraj(igralec("Bor", "Novak", "potrjen@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("potrjen@test.si", koda("potrjen@test.si")));
        LocalDate otrok = LocalDate.now().minusYears(10);
        registracija.registriraj(new RegistracijaVnos("Tim", "Mali", null, "otrok@test.si",
                "geslo1234", false, otrok, "stars@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("otrok@test.si", koda("otrok@test.si")));

        // cez en dan se nic ne zgodi
        ciscenje.pocisti(LocalDateTime.now().plusDays(1));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("nepotrjena@test.si").isPresent());

        // cez tri dni odpade nepotrjeni, potrjeni in otrok brez soglasja ostaneta
        ciscenje.pocisti(LocalDateTime.now().plusDays(3));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("nepotrjena@test.si").isEmpty());
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("potrjen@test.si").isPresent());
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("otrok@test.si").isPresent());

        // cez mesec dni odpade tudi otrok brez soglasja skrbnika
        ciscenje.pocisti(LocalDateTime.now().plusDays(31));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("otrok@test.si").isEmpty());
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("potrjen@test.si").isPresent());
    }

    // ---------- Placilni tok (PlacilaStoritev klice registrirajPoPlacilu) ----------

    /* Geslo je ze zgosceno PRED klicem (izracunano ob zacetku Stripe placila) -
       registrirajPoPlacilu ga ne sme znova zgostiti, drugace se uporabnik ne
       bi mogel prijaviti s svojim geslom. */
    @Test
    void registracijaPoPlaciluUporabiZeZgoscenoGeslo() {
        String zeZgosceno = kodirnik.encode("mojeGeslo123");
        var racun = registracija.registrirajPoPlacilu(igralec("Ana", "Novak", "placano@test.si"),
                zeZgosceno);

        assertTrue(racun.isPresent());
        assertEquals(zeZgosceno, racun.get().getGesloHash());
        assertEquals(StatusRacuna.CAKA, racun.get().getStatus());
        // koda za potrditev e-poste je bila poslana enako kot pri brezplacni poti
        assertNotNull(koda("placano@test.si"));
    }

    /* Naslov, ki med Stripe placilom postane potrjen racun nekoga drugega,
       se ne sme prepisati - placilo ostane brez novega racuna (rocna obravnava). */
    @Test
    void registracijaPoPlaciluNaPotrjenNaslovNeUstvariRacuna() {
        registracija.registriraj(igralec("Ana", "Novak", "zaseden@test.si"), IP);
        registracija.potrdiEposto(new PotrditevKodeVnos("zaseden@test.si", koda("zaseden@test.si")));
        assertEquals(StatusRacuna.CAKA, racun("zaseden@test.si").getStatus());

        var rezultat = registracija.registrirajPoPlacilu(
                igralec("Ana", "Novak", "zaseden@test.si"), kodirnik.encode("drugoGeslo"));

        assertTrue(rezultat.isEmpty());
        assertEquals(1, uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getUporabniskoIme().equals("zaseden@test.si")).count());
    }

    // ---------- Pomozno ----------

    private static RegistracijaVnos igralec(String ime, String priimek, String email) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", false, ODRASEL, null);
    }

    private IgralecJavniDto igralecVSifrantu(String ime, String priimek, LocalDate datumRojstva) {
        return igralci.ustvari(new IgralecVnos(ime, priimek, Spol.MOSKI, datumRojstva,
                null, null, null, null, "SLO", null, null, null, null));
    }

    private Uporabnik racun(String email) {
        return uporabnikRepozitorij.najdiZVsem(email).orElseThrow();
    }

    /* Koda iz zadnjega sporocila za naslov. */
    private String koda(String email) {
        String besedilo = posta.zadnje(email).orElseThrow(
                () -> new AssertionError("ni sporocila za " + email)).besedilo();
        Matcher m = KODA.matcher(besedilo);
        assertTrue(m.find(), "v sporocilu ni kode: " + besedilo);
        return m.group(1);
    }
}
