/* Koda popusta, ki jo ze prijavljen racun vpise na strani Narocnina
   (KodePopustaStoritev.uveljaviZaPrijavljenRacun).

   Velja samo NAVIJAC_MESECI na potrjenem racunu navijaca brez narocnine; vse
   ostalo se zavrne z razlago, brez sledi (nobene narocnine, nobene porabe kode).
   Pravila registracije (ena koda na racun, najvec uporab, enako sporocilo za
   neznano/ugasnjeno/potekle kode, omejitev ugibanja) morajo veljati tudi tu. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.KodaPopustaDto;
import si.turnirko.dto.KodaPopustaUrediVnos;
import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KodaPopustaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabaKodePopustaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class KodaPriPrijavljenemRacunuTest extends IntegracijskiTest {

    private static final String IP = "10.0.0.7";

    @Autowired private KodePopustaStoritev kode;
    @Autowired private NarocninaStoritev narocninaStoritev;
    @Autowired private NarocninaRepozitorij narocnine;
    @Autowired private UporabnikRepozitorij uporabniki;
    @Autowired private UporabaKodePopustaRepozitorij uporabe;
    @Autowired private KodaPopustaRepozitorij kodeRepozitorij;
    @Autowired private OmejevalnikPoskusov omejevalnik;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
    }

    // ---------- uspeh ----------

    @Test
    void navijacBrezPaketaDobiMesecePoVpisuKode() {
        kode.ustvari(vnos("NAV2", VrstaKodePopusta.NAVIJAC_MESECI, 2, null));
        Uporabnik u = navijac("nav2@test.si", StatusRacuna.POTRJEN);
        LocalDateTime zdaj = LocalDateTime.now();

        // oblika, kot jo oseba prepise s telefona: male crke, presledki
        Narocnina n = kode.uveljaviZaPrijavljenRacun(u, "  nav2 ", IP, zdaj);

        assertEquals(Paket.NAVIJAC, n.getPaket());
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertEquals(0.0, n.getCenaObSklenitvi(), 0.0001);
        assertEquals(zdaj.plusMonths(2), n.getTrenutnoObdobjeDo(), "mesec se steje od vpisa kode");
        assertTrue(narocninaStoritev.imaNavijaca(u));
        assertTrue(narocninaStoritev.imaSirokoSpremljanje(u));
        assertEquals(n.getId(), narocnine.findByUporabnikId(u.getId()).orElseThrow().getId());
        assertTrue(kode.kodaRacuna(u).isPresent(), "koda je zabelezena pri racunu");
        assertEquals("NAV2", kode.kodaRacuna(u).orElseThrow().getKoda());
        assertEquals(1, stevilo("NAV2"), "uporaba se pozna v seznamu admina");
    }

    @Test
    void kodaZNajvecEnoUporaboJeLahkoPorabljenaSamoEnkrat() {
        kode.ustvari(new KodaPopustaVnos("ENKRAT", null, VrstaKodePopusta.NAVIJAC_MESECI, 1, null, null, null,
                null, 1));
        Uporabnik prvi = navijac("prvi@test.si", StatusRacuna.POTRJEN);
        Uporabnik drugi = navijac("drugi@test.si", StatusRacuna.POTRJEN);

        kode.uveljaviZaPrijavljenRacun(prvi, "ENKRAT", IP, LocalDateTime.now());

        NeveljavenVnosIzjema izjema = assertThrows(NeveljavenVnosIzjema.class,
                () -> kode.uveljaviZaPrijavljenRacun(drugi, "ENKRAT", IP, LocalDateTime.now()));
        assertEquals(KodePopustaStoritev.NEVELJAVNA_KODA, izjema.getMessage());
        assertTrue(narocnine.findByUporabnikId(drugi.getId()).isEmpty());
        assertEquals(1, stevilo("ENKRAT"));
    }

    // ---------- neveljavna koda ----------

    @Test
    void neznanaUgasnjenaInPotekleKodaDobijoIstoSporocilo() {
        KodaPopustaDto ugasnjena = kode.ustvari(vnos("UGASNJ", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        kode.uredi(ugasnjena.id(), new KodaPopustaUrediVnos(null, null, null, false));
        KodaPopustaDto potekla = kode.ustvari(vnos("POTEKLA", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        // pretecen rok je pri popravku dovoljen: tako admin kodo zapre
        kode.uredi(potekla.id(), new KodaPopustaUrediVnos(null, LocalDate.now().minusDays(1), null, true));
        Uporabnik u = navijac("neveljavna@test.si", StatusRacuna.POTRJEN);

        for (String vpisana : new String[] {"NI-TAKE", "UGASNJ", "POTEKLA"}) {
            NeveljavenVnosIzjema izjema = assertThrows(NeveljavenVnosIzjema.class,
                    () -> kode.uveljaviZaPrijavljenRacun(u, vpisana, IP, LocalDateTime.now()), vpisana);
            assertEquals(KodePopustaStoritev.NEVELJAVNA_KODA, izjema.getMessage(), vpisana);
        }
        assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
    }

    @Test
    void praznaKodaSeZavrneZRazlago() {
        Uporabnik u = navijac("prazna@test.si", StatusRacuna.POTRJEN);

        assertThrows(NeveljavenVnosIzjema.class, () -> kode.uveljaviZaPrijavljenRacun(u, "  ", IP, LocalDateTime.now()));
        assertThrows(NeveljavenVnosIzjema.class, () -> kode.uveljaviZaPrijavljenRacun(u, null, IP, LocalDateTime.now()));
    }

    // ---------- druge vrste kod ----------

    @Test
    void drugeVrsteKodNaTejPotiNeVeljajoInNicNeZapisejo() {
        kode.ustvari(vnos("IGRMES", VrstaKodePopusta.PREMIUM_MESECI, 3, null));
        kode.ustvari(new KodaPopustaVnos("ORGMES", null, VrstaKodePopusta.ORGANIZATOR_MESECI, 3, null,
                Paket.ORGANIZATOR_PLUS, null, null, null));
        kode.ustvari(new KodaPopustaVnos("ODSTO", null, VrstaKodePopusta.ODSTOTEK, null, 20, null,
                Vloga.NAVIJAC, null, null));
        Uporabnik u = navijac("drugevrste@test.si", StatusRacuna.POTRJEN);

        for (String vpisana : new String[] {"IGRMES", "ORGMES", "ODSTO"}) {
            NeveljavenVnosIzjema izjema = assertThrows(NeveljavenVnosIzjema.class,
                    () -> kode.uveljaviZaPrijavljenRacun(u, vpisana, IP, LocalDateTime.now()), vpisana);
            assertFalse(KodePopustaStoritev.NEVELJAVNA_KODA.equals(izjema.getMessage()),
                    "oseba mora izvedeti, zakaj koda ne velja: " + vpisana);
            assertEquals(0, stevilo(vpisana), vpisana);
        }
        assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
        assertTrue(kode.kodaRacuna(u).isEmpty());
    }

    // ---------- kateri racun sme ----------

    @Test
    void kodeNeUporabiIgralecInOrganizator() {
        kode.ustvari(vnos("SAMONAV", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        Uporabnik igralec = racun("igralec@test.si", Vloga.IGRALEC, StatusRacuna.CAKA);
        Uporabnik organizator = racun("organizator@test.si", Vloga.ORGANIZATOR, StatusRacuna.POTRJEN);

        for (Uporabnik u : new Uporabnik[] {igralec, organizator}) {
            assertThrows(NeveljavenVnosIzjema.class,
                    () -> kode.uveljaviZaPrijavljenRacun(u, "SAMONAV", IP, LocalDateTime.now()));
            assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
        }
        assertEquals(0, stevilo("SAMONAV"));
    }

    @Test
    void nepotrjenNavijacKodeNeUporabi() {
        kode.ustvari(vnos("NEPOTRJEN", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        Uporabnik u = navijac("nepotrjen@test.si", StatusRacuna.CAKA);

        assertThrows(DomenskaIzjema.class,
                () -> kode.uveljaviZaPrijavljenRacun(u, "NEPOTRJEN", IP, LocalDateTime.now()));
        assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
        assertEquals(0, stevilo("NEPOTRJEN"));
    }

    @Test
    void racunZNarocninoKodeNeUporabi() {
        kode.ustvari(vnos("ZENAROC", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        // aktivna, potekla in zapadla narocnina: kdor je paket ze imel, kode ne dobi
        for (StatusNarocnine status : new StatusNarocnine[] {StatusNarocnine.AKTIVNA, StatusNarocnine.ZAPADLA,
                StatusNarocnine.PREKLICANA}) {
            Uporabnik u = navijac("zenaroc-" + status + "@test.si", StatusRacuna.POTRJEN);
            Narocnina n = new Narocnina(u, Paket.NAVIJAC);
            n.setStatus(status);
            n.setCiklus(CiklusPlacila.LETNO);
            n.setStripeNarocninaId("sub_" + u.getUporabniskoIme());
            LocalDateTime konec = LocalDateTime.now().minusDays(3);
            n.setTrenutnoObdobjeDo(konec);
            narocnine.save(n);

            assertThrows(DomenskaIzjema.class,
                    () -> kode.uveljaviZaPrijavljenRacun(u, "ZENAROC", IP, LocalDateTime.now()), status.name());
            assertEquals(konec, narocnine.findByUporabnikId(u.getId()).orElseThrow().getTrenutnoObdobjeDo(),
                    "obstojece narocnine koda ne podaljsa");
            assertTrue(kode.kodaRacuna(u).isEmpty(), status.name());
        }
        assertEquals(0, stevilo("ZENAROC"));
    }

    @Test
    void racunEnoKodoUporabiNajvecEnkrat() {
        kode.ustvari(vnos("PRVA", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        KodaPopusta druga = kodeRepozitorij.findById(
                kode.ustvari(vnos("DRUGA", VrstaKodePopusta.NAVIJAC_MESECI, 1, null)).id()).orElseThrow();
        Uporabnik u = navijac("enkrat@test.si", StatusRacuna.POTRJEN);
        // koda je zabelezena (npr. ob registraciji), narocnine pa ni
        kode.zabeleziUporabo(u, druga);

        assertThrows(DomenskaIzjema.class, () -> kode.uveljaviZaPrijavljenRacun(u, "PRVA", IP, LocalDateTime.now()));
        assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
        assertEquals(0, stevilo("PRVA"));
        assertEquals(1, stevilo("DRUGA"));
    }

    // ---------- ugibanje ----------

    @Test
    void ugibanjeKodJeOmejenoPoNaslovuIp() {
        kode.ustvari(vnos("PRAVA", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        Uporabnik u = navijac("ugibalec@test.si", StatusRacuna.POTRJEN);

        for (int i = 0; i < KodePopustaStoritev.NAJVEC_PREVERJANJ_NA_URO; i++) {
            assertThrows(NeveljavenVnosIzjema.class,
                    () -> kode.uveljaviZaPrijavljenRacun(u, "NAPACNA", IP, LocalDateTime.now()));
        }

        // pravilna koda po preseganju meje ne pomaga: omejitev velja za vsak poskus
        assertThrows(PrevecZahtevIzjema.class,
                () -> kode.uveljaviZaPrijavljenRacun(u, "PRAVA", IP, LocalDateTime.now()));
        assertTrue(narocnine.findByUporabnikId(u.getId()).isEmpty());
    }

    // ---------- registracija ostane cela ----------

    @Test
    void uveljavitevOdRegistracijeNePodvojiNarocnine() {
        kode.ustvari(vnos("ZREG", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));
        Uporabnik u = navijac("zreg@test.si", StatusRacuna.POTRJEN);

        // brez zabelezene porabe (racun kode ni vpisal ob registraciji) ob potrditvi ni nic za uveljaviti
        assertTrue(kode.uveljaviZaNavijaca(u, LocalDateTime.now()).isEmpty());

        kode.uveljaviZaPrijavljenRacun(u, "ZREG", IP, LocalDateTime.now());
        // koda je zdaj pri racunu, narocnina pa ze obstaja: ponovna potrditev je ne podvoji
        assertTrue(kode.uveljaviZaNavijaca(u, LocalDateTime.now()).isEmpty());
    }

    // ---------- priprava ----------

    private long stevilo(String koda) {
        return uporabe.countByKodaId(kodeRepozitorij.findByKoda(koda).orElseThrow().getId());
    }

    private static KodaPopustaVnos vnos(String koda, VrstaKodePopusta vrsta, Integer meseci, Integer odstotek) {
        return new KodaPopustaVnos(koda, null, vrsta, meseci, odstotek, null, null, null, null);
    }

    private Uporabnik navijac(String prijavnoIme, StatusRacuna status) {
        return racun(prijavnoIme, Vloga.NAVIJAC, status);
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga, StatusRacuna status) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(status);
        return uporabniki.save(u);
    }
}
