/* Podatki za stran »Dostopi« (administrator): dnevnik računa, zadnja prijava in
   prijave v 30 dneh, poraba paketa, klub povezanega igralca in administratorji v
   kartoteki. Čas pisanja mora prestati pot skozi bazo (seja se izprazni). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RacunIgralcaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RacuniDostopiTest {

    private static final String IP = "127.0.0.1";
    private static final LocalDate ODRASEL = LocalDate.of(1990, 1, 1);

    @Autowired RacuniStoritev racuniStoritev;
    @Autowired RegistracijaStoritev registracija;
    @Autowired RacunDnevnikStoritev dnevnik;
    @Autowired IgralciStoritev igralci;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired EntityManager em;

    @BeforeEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    private static RegistracijaVnos igralec(String email) {
        return new RegistracijaVnos("Ana", "Novak", null, email, "geslo1234", false, ODRASEL, null);
    }

    private Uporabnik racun(String email) {
        return uporabnikRepozitorij.najdiZVsem(email).orElseThrow();
    }

    private RacunIgralcaDto vSeznamu(String email) {
        em.flush();
        em.clear();
        return racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals(email)).findFirst().orElseThrow();
    }

    private static List<String> besedila(RacunIgralcaDto r) {
        return r.dnevnik().stream().map(RacunIgralcaDto.Dogodek::tekst).toList();
    }

    @Test
    void dnevnikBelezi_registracijoPotrditevInPovezavo_najnovejsiPrvi() {
        registracija.registriraj(igralec("ana@test.si"), IP);
        IgralecJavniDto igralecZapis = igralci.ustvari(new IgralecVnos("Ana", "Novak", Spol.ZENSKI, ODRASEL,
                null, null, null, null, "SLO", null, null, null, null));
        Uporabnik u = racun("ana@test.si");
        racuniStoritev.potrdi(u.getId(), new PotrditevRacunaVnos(igralecZapis.id()));
        racuniStoritev.nastaviGeslo(u.getId(), "novogeslo1");
        racuniStoritev.nastaviAktiven(u.getId(), false);

        RacunIgralcaDto dto = vSeznamu("ana@test.si");

        assertEquals(List.of(
                "Administrator izklopil račun",
                "Administrator nastavil geslo",
                "Administrator povezal z igralcem Ana Novak",
                "Registracija (igralec)"), besedila(dto));
    }

    @Test
    void dnevnikNosiNajvecOsemDogodkov() {
        registracija.registriraj(igralec("ana@test.si"), IP);
        Uporabnik u = racun("ana@test.si");
        for (int i = 0; i < 12; i++) {
            racuniStoritev.nastaviGeslo(u.getId(), "novogeslo" + i);
        }

        assertEquals(RacunDnevnikStoritev.NAJVEC_DOGODKOV, vSeznamu("ana@test.si").dnevnik().size());
    }

    /* Vsak preizkus prijav ima svoj naslov: storitev si zadnji cas zahteve po
       racunu zapomni v pomnilniku, ta pa preživi med preizkusi. */
    @Test
    void prijavaJePrvaZahtevaPoTridesetihMinutahMirovanja() {
        registracija.registriraj(igralec("mirovanje@test.si"), IP);
        Uporabnik u = racun("mirovanje@test.si");
        LocalDateTime t = LocalDateTime.now().minusDays(2).withNano(0);

        dnevnik.zabeleziPrijavo("mirovanje@test.si", t);
        dnevnik.zabeleziPrijavo("mirovanje@test.si", t.plusMinutes(10)); // ista prijava
        dnevnik.zabeleziPrijavo("mirovanje@test.si", t.plusMinutes(35)); // 25 min po prejšnji: še vedno ista
        dnevnik.zabeleziPrijavo("mirovanje@test.si", t.plusMinutes(70)); // 35 min mirovanja: nova

        RacunIgralcaDto dto = vSeznamu("mirovanje@test.si");
        assertEquals(2, dto.prijavNaZadnjih30Dni());
        assertEquals(t.plusMinutes(70), dto.zadnjaPrijava());
        assertNotNull(u.getId());
    }

    @Test
    void prijaveStarejseOdTridesetDniNeStejejoAZadnjaOstane() {
        registracija.registriraj(igralec("staro@test.si"), IP);
        LocalDateTime staro = LocalDateTime.now().minusDays(45).withNano(0);

        dnevnik.zabeleziPrijavo("staro@test.si", staro);

        RacunIgralcaDto dto = vSeznamu("staro@test.si");
        assertEquals(0, dto.prijavNaZadnjih30Dni());
        assertEquals(staro, dto.zadnjaPrijava());
    }

    @Test
    void racunBrezPrijavNimaZadnjePrijave() {
        registracija.registriraj(igralec("brez@test.si"), IP);

        RacunIgralcaDto dto = vSeznamu("brez@test.si");

        assertNull(dto.zadnjaPrijava());
        assertEquals(0, dto.prijavNaZadnjih30Dni());
    }

    @Test
    void prijavaNeznanegaImenaNiNapaka() {
        dnevnik.zabeleziPrijavo("ni-ga@test.si", LocalDateTime.now());
        dnevnik.zabeleziPrijavo(null, LocalDateTime.now());
    }

    @Test
    void igralecNosiKlubPovezanegaIgralca() {
        Klub klub = klubRepozitorij.save(new Klub("NTK Savinja", "NTKS"));
        IgralecJavniDto zapis = igralci.ustvari(new IgralecVnos("Ana", "Novak", Spol.ZENSKI, ODRASEL,
                null, null, null, null, "SLO", null, null, klub.getId(), null));
        registracija.registriraj(igralec("ana@test.si"), IP);
        racuniStoritev.potrdi(racun("ana@test.si").getId(), new PotrditevRacunaVnos(zapis.id()));

        RacunIgralcaDto dto = vSeznamu("ana@test.si");

        assertEquals("NTK Savinja", dto.klub());
        assertNull(dto.idKlub(), "račun igralca sam kluba nima");
    }

    @Test
    void administratorJeVKartotekiAMutacijeGaZavrnejo() {
        Uporabnik admin = uporabnikRepozitorij.save(new Uporabnik("admin-dostopi@test.si", "x", Vloga.ADMIN));
        admin.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(admin);

        RacunIgralcaDto dto = vSeznamu("admin-dostopi@test.si");

        assertEquals(Vloga.ADMIN, dto.vloga());
        assertNull(dto.narocnina().paket());
        assertThrows(DomenskaIzjema.class, () -> racuniStoritev.nastaviAktiven(admin.getId(), false));
    }

    @Test
    void organizatorSPaketomNosiPorabo_ostaliNe() {
        registracija.registriraj(
                new RegistracijaVnos("Org", "Vodja", null, "org@test.si", "geslo1234", true, null, null), IP);
        registracija.registriraj(igralec("ana@test.si"), IP);
        Uporabnik org = racun("org@test.si");
        Narocnina n = new Narocnina(org, Paket.ORGANIZATOR_PLUS);
        n.setStatus(StatusNarocnine.AKTIVNA);
        n.setCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);

        Turnir t = new Turnir();
        t.setIme("Turnir 1");
        t.setUstvaril(org);
        turnirRepozitorij.save(t);
        Liga l = new Liga();
        l.setIme("Liga 1");
        l.setSezona("2026/27");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(org);
        ligaRepozitorij.save(l);

        Map<String, RacunIgralcaDto> poEmailu = racuniStoritev.racuni().stream()
                .collect(Collectors.toMap(RacunIgralcaDto::email, r -> r));
        RacunIgralcaDto.Poraba poraba = poEmailu.get("org@test.si").poraba();

        assertNotNull(poraba);
        assertEquals(1, poraba.turnirji().uporabljeno());
        assertEquals(1, poraba.lige().uporabljeno());
        assertEquals(5, poraba.turnirji().najvec(), "meje paketa Plus so iste kot pri ustvarjanju");
        assertEquals(3, poraba.lige().najvec());
        assertNull(poEmailu.get("ana@test.si").poraba());
    }

    @Test
    void potrditevOrganizatorjaZapisePotrjenDatumInDnevnik() {
        registracija.registriraj(
                new RegistracijaVnos("Org", "Vodja", null, "org@test.si", "geslo1234", true, null, null), IP);
        Klub klub = klubRepozitorij.save(new Klub("NTK Celje", "NTKC"));

        racuniStoritev.potrdiOrganizatorja(racun("org@test.si").getId(), klub.getId());

        RacunIgralcaDto dto = vSeznamu("org@test.si");
        assertNotNull(dto.povezanOb(), "datum potrditve organizatorja (karton: »Potrjen«)");
        assertTrue(besedila(dto).contains("Administrator potrdil organizatorja (NTK Celje)"));
    }

    @Test
    void placiloVDnevnikuImaPaketInCikel() {
        assertEquals("Plačilo: Premium, letno",
                RacunDnevnikStoritev.opisPlacila(Paket.PREMIUM, CiklusPlacila.LETNO));
        assertEquals("Plačilo: Plus, mesečno",
                RacunDnevnikStoritev.opisPlacila(Paket.ORGANIZATOR_PLUS, CiklusPlacila.MESECNO));
    }
}
