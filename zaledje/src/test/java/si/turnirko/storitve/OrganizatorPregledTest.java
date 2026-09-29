/* Organizatorski pregled (OrganizatorPregledStoritev): kaj vidi organizator na
   svoji strani. Testi drzijo merila, ki jih pregled ne sme izgubiti:
   - samo LASTNA tekmovanja (ustvaril == on),
   - poraba paketa je stevilka iz NarocninaStoritev, ne stetje vrstic,
   - kaj caka (rezultati, zapisniki, zreb) in kdaj ne,
   - termini in arhiv po sezonah (1. julij). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.OrganizatorPregledDto;
import si.turnirko.dto.OrganizatorPregledDto.Caka;
import si.turnirko.dto.OrganizatorPregledDto.Tekmovanje;
import si.turnirko.dto.OrganizatorPregledDto.VrstaCakanja;
import si.turnirko.dto.OrganizatorPregledDto.VrstaNapredka;
import si.turnirko.dto.OrganizatorPregledDto.VrstaTekmovanja;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Sezona;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class OrganizatorPregledTest extends IntegracijskiTest {

    private static final LocalDate DANES = LocalDate.now();

    @Autowired private OrganizatorPregledStoritev pregledStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @PersistenceContext private EntityManager seja;

    // ---------- Lastnistvo in identiteta ----------

    /* Pregled steje tekmovanja, ki jih je racun USTVARIL. Turnir drugega
       organizatorja - tudi istega kluba - v njem ni, ker bi sicer vrstice
       seznama stele drugo mnozico kot palica ob njem. */
    @Test
    void pregledKazeSamoTekmovanjaKiJihJeRacunUstvaril() {
        Klub klub = klubRepozitorij.save(new Klub("NTK Savinja", null));
        Uporabnik jaz = organizator("jaz@test", "Marko", "Kovač", klub, Paket.ORGANIZATOR_PRO);
        Uporabnik tuj = organizator("tuj@test", "Tina", "Novak", klub, Paket.ORGANIZATOR_PLUS);

        turnir(jaz, "Moj turnir", DANES.plusDays(30), StatusTekmovanja.PRIPRAVA);
        turnir(tuj, "Tuji turnir istega kluba", DANES.plusDays(30), StatusTekmovanja.PRIPRAVA);
        liga(tuj, "Tuja liga", Sezona.oznaka(DANES));

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        assertEquals(List.of("Moj turnir"), p.tekmovanja().stream().map(Tekmovanje::ime).toList());
        assertEquals("Marko Kovač", p.ime());
        assertEquals("NTK Savinja", p.klub());
        assertEquals(1, p.skupajTekmovanj());
    }

    @Test
    void ureditevPregledaNiMogocaBrezPrijaveInAdminuNiNamenjena() {
        assertThrows(PrepovedanoIzjema.class, () -> pregledStoritev.pregled(),
                "brez prijavljenega racuna pregleda ni");
    }

    // ---------- Poraba paketa ----------

    /* Meja in poraba prideta iz NarocninaStoritev - to je tisto, kar strezniku
       ob ustvarjanju res preveri. Turnir, ustvarjen pred 1. julijem in
       odigran v tej sezoni, je na seznamu, v meji pa ga ni. */
    @Test
    void porabaPaketaJeStevilkaKiJoStrezniksPreveri() {
        Uporabnik jaz = organizator("kvota@test", "Ana", "Zupan", null, Paket.ORGANIZATOR_PRO);
        turnir(jaz, "Ustvarjen letos", DANES.plusDays(10), StatusTekmovanja.PRIPRAVA);
        Turnir star = turnir(jaz, "Ustvarjen lani", DANES.plusDays(12), StatusTekmovanja.PRIPRAVA);
        postaviUstvarjenOb("turnir", star.getId(), DANES.minusYears(1));
        liga(jaz, "Liga", Sezona.oznaka(DANES));

        seja.flush();
        seja.clear();
        OrganizatorPregledDto p = pregledStoritev.pregled(uporabnikRepozitorij.findById(jaz.getId()).orElseThrow(), DANES);

        assertEquals(1, p.turnirji().uporabljeno(), "ustvarjen lani ne steje v mejo te sezone");
        assertEquals(10, p.turnirji().meja());
        assertEquals(1, p.lige().uporabljeno());
        assertEquals(5, p.lige().meja());
        assertEquals(Paket.ORGANIZATOR_PRO, p.paket());
        assertEquals(2, p.tekmovanja().stream().filter(t -> t.vrsta() == VrstaTekmovanja.TURNIR).count(),
                "oba turnirja se igrata letos, zato sta na seznamu");
    }

    @Test
    void brezVeljavnegaPaketaKvotNiInNiPaketa() {
        Uporabnik jaz = organizator("brez@test", "Brez", "Paketa", null, Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.findByUporabnikId(jaz.getId()).orElseThrow().setStatus(StatusNarocnine.ZAPADLA);

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        assertNull(p.paket());
        assertNull(p.lige());
        assertNull(p.turnirji());
    }

    @Test
    void naslednjaSezonaSeZacneJuliju() {
        Uporabnik jaz = organizator("sezona@test", "Se", "Zona", null, Paket.ORGANIZATOR_BASIC);
        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, LocalDate.of(2026, 9, 29));
        assertEquals("2026/27", p.sezona());
        assertEquals(LocalDate.of(2027, 7, 1), p.naslednjaSezonaOd());
        assertEquals("2025/26", Sezona.oznaka(LocalDate.of(2026, 6, 30)));
    }

    // ---------- Caka te ----------

    /* Po zrebu so tekme prvega kroga PRIPRAVLJENE: oba igralca znana, izida ni. */
    @Test
    void rezultatiCakajoPoZrebu() {
        Uporabnik jaz = organizator("rezultati@test", "Re", "Zultat", null, Paket.ORGANIZATOR_PRO);
        Dogodek dogodek = pripraviDogodek(8);
        Turnir turnir = dogodek.getTurnir();
        turnir.setUstvaril(jaz);
        turnirRepozitorij.save(turnir);
        zrebStoritev.izvediZreb(dogodek.getId());

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        Caka caka = p.caka().stream().filter(c -> c.vrsta() == VrstaCakanja.REZULTATI).findFirst().orElseThrow();
        assertEquals(4, caka.stevilo(), "osem igralcev = štiri tekme prvega kroga");
        assertEquals(turnir.getId(), caka.idTekmovanja());
        assertEquals(List.of("Clani posamicno"), caka.podrobnosti());
        assertEquals(dogodek.getId(), caka.idDogodka(), "gumb vodi v dogodek, kjer tekme cakajo");

        Tekmovanje vrstica = p.tekmovanja().get(0);
        assertEquals(VrstaCakanja.REZULTATI, vrstica.cakaVrsta());
        assertEquals(4, vrstica.cakaStevilo());
        assertEquals(VrstaNapredka.TEKME, vrstica.napredekVrsta());
    }

    /* Zapisnik caka za srecanje, ki bi moralo biti odigrano do vceraj. Danasnje
       je se prihodnost in stoji v »Prihaja«. */
    @Test
    void zapisnikiCakajoZaPreteklaSrecanjaPrihodnjaSoVPrihaja() {
        Uporabnik jaz = organizator("zapisniki@test", "Za", "Pisnik", null, Paket.ORGANIZATOR_PRO);
        // dve ekipi dvokrozno: 1. kolo pred tremi dnevi, 2. kolo cez štiri
        Liga liga = ligaZRazporedom(jaz, DANES.minusDays(3), 7);

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        Caka caka = p.caka().stream().filter(c -> c.vrsta() == VrstaCakanja.ZAPISNIKI).findFirst().orElseThrow();
        assertEquals(1, caka.stevilo());
        assertEquals(1, caka.kolo());
        assertEquals(DANES.minusDays(3), caka.datum());
        assertEquals(1, caka.podrobnosti().size());
        assertTrue(caka.podrobnosti().get(0).contains(" : "), "par ekip »A : B«");
        assertNotNull(caka.idSrecanja(), "gumb vodi na zapisnik cakajocega srecanja");

        assertEquals(1, p.prihaja().size());
        assertEquals(VrstaTekmovanja.LIGA, p.prihaja().get(0).vrsta());
        assertEquals(2, p.prihaja().get(0).kolo());
        assertEquals(DANES.plusDays(4), p.prihaja().get(0).datum());
        assertEquals(liga.getId(), p.prihaja().get(0).idTekmovanja());

        Tekmovanje vrstica = p.tekmovanja().get(0);
        assertEquals(VrstaTekmovanja.LIGA, vrstica.vrsta());
        assertEquals(VrstaCakanja.ZAPISNIKI, vrstica.cakaVrsta());
        assertEquals(VrstaNapredka.KOLO, vrstica.napredekVrsta());
        assertEquals(1, vrstica.napredekTrenutno(), "tece prvo kolo");
        assertEquals(2, vrstica.napredekVseh());
    }

    /* Prijave vpisuje organizator sam, zato turnir v pripravi ne caka
       »potrditve«, ampak ZREB - in ga caka sele, ko je prijav konec. */
    @Test
    void turnirCakaZrebSeleKoJePrijavKonecInSoVsajDvePrijavi() {
        Uporabnik jaz = organizator("zreb@test", "Zreb", "Nik", null, Paket.ORGANIZATOR_PRO);

        Dogodek blizu = pripraviDogodek(8);
        lastnik(blizu, jaz, "Blizu", DANES.plusDays(5), null);
        Dogodek dalec = pripraviDogodek(8);
        lastnik(dalec, jaz, "Dalec", DANES.plusDays(60), null);
        Dogodek rokPotekel = pripraviDogodek(8);
        lastnik(rokPotekel, jaz, "Rok potekel", DANES.plusDays(60), DANES.minusDays(1));
        Dogodek samSam = pripraviDogodek(1);
        lastnik(samSam, jaz, "En prijavljen", DANES.plusDays(2), null);

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        List<String> cakajoZreb = p.caka().stream().filter(c -> c.vrsta() == VrstaCakanja.ZREB)
                .map(Caka::imeTekmovanja).sorted().toList();
        assertEquals(List.of("Blizu", "Rok potekel"), cakajoZreb);
        assertEquals(8, p.caka().stream().filter(c -> c.imeTekmovanja().equals("Blizu")).findFirst()
                .orElseThrow().stevilo(), "stevilo pri zrebu so prijavljeni");

        Tekmovanje dalecVrstica = vrstica(p, "Dalec");
        assertEquals(VrstaNapredka.PRIJAVE, dalecVrstica.napredekVrsta());
        assertEquals(8, dalecVrstica.napredekTrenutno());
        assertEquals(VrstaCakanja.ZACETEK, dalecVrstica.cakaVrsta(), "brez roka se kaze zacetek turnirja");
        assertEquals(VrstaCakanja.ZREB, vrstica(p, "Blizu").cakaVrsta());
    }

    // ---------- Prihaja in naslednje ----------

    @Test
    void prihajaKazeTurnirjeVNaslednjihCetrnajstDnehNaslednjePaNajblizjiTermin() {
        Uporabnik jaz = organizator("prihaja@test", "Pri", "Haja", null, Paket.ORGANIZATOR_PRO);
        Turnir cez3 = turnir(jaz, "Cez tri dni", DANES.plusDays(3), StatusTekmovanja.PRIPRAVA);
        turnir(jaz, "Cez tri tedne", DANES.plusDays(21), StatusTekmovanja.PRIPRAVA);
        turnir(jaz, "Koncan", DANES.plusDays(1), StatusTekmovanja.ZAKLJUCEN);

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        assertEquals(List.of("Cez tri dni"), p.prihaja().stream().map(t -> t.imeTekmovanja()).toList());
        assertEquals(cez3.getId(), p.naslednje().idTekmovanja());
    }

    @Test
    void naslednjeSegaTudiCezCetrnajstDni() {
        Uporabnik jaz = organizator("dalec@test", "Da", "Lec", null, Paket.ORGANIZATOR_PRO);
        turnir(jaz, "Cez mesec", DANES.plusDays(30), StatusTekmovanja.PRIPRAVA);

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        assertTrue(p.prihaja().isEmpty());
        assertNotNull(p.naslednje());
        assertEquals("Cez mesec", p.naslednje().imeTekmovanja());
    }

    // ---------- Arhiv ----------

    @Test
    void arhivZdruziPretekleSezoneTekocaPaNiVArhivu() {
        Uporabnik jaz = organizator("arhiv@test", "Ar", "Hiv", null, Paket.ORGANIZATOR_PRO);
        LocalDate lani = DANES.minusYears(1);
        Dogodek prejsnji = pripraviDogodek(6);
        Turnir t = lastnik(prejsnji, jaz, "Lanski turnir", lani, null);
        t.setStatus(StatusTekmovanja.ZAKLJUCEN);
        turnirRepozitorij.save(t);
        postaviUstvarjenOb("turnir", t.getId(), lani);
        liga(jaz, "Lanska liga", Sezona.oznaka(lani));
        Liga lanska = ligaRepozitorij.findAll().stream()
                .filter(l -> l.getIme().equals("Lanska liga")).findFirst().orElseThrow();
        postaviUstvarjenOb("liga", lanska.getId(), lani);
        turnir(jaz, "Letosnji", DANES.plusDays(9), StatusTekmovanja.PRIPRAVA);

        seja.flush();
        seja.clear();
        OrganizatorPregledDto p = pregledStoritev.pregled(uporabnikRepozitorij.findById(jaz.getId()).orElseThrow(), DANES);

        assertEquals(1, p.arhiv().size());
        assertEquals(Sezona.oznaka(lani), p.arhiv().get(0).sezona());
        assertEquals(1, p.arhiv().get(0).turnirjev());
        assertEquals(1, p.arhiv().get(0).lig());
        assertEquals(6, p.arhiv().get(0).udelezencev());
        assertEquals(Sezona.oznaka(lani), p.organiziraOdSezone());
        assertEquals(3, p.skupajTekmovanj());
        assertEquals(1, p.tekmovanja().stream().filter(x -> x.sezona().equals(p.sezona())).count());
    }

    /* Igralec, ki nastopa na turnirju in v ligi iste sezone, je en udelezenec. */
    @Test
    void udelezenecVDvehTekmovanjihSeSteveEnkrat() {
        Uporabnik jaz = organizator("udelezenci@test", "Ude", "Lezenec", null, Paket.ORGANIZATOR_PRO);
        Dogodek dogodek = pripraviDogodek(4);
        lastnik(dogodek, jaz, "Turnir", DANES.plusDays(2), null);
        Liga liga = liga(jaz, "Liga", Sezona.oznaka(DANES));
        Klub klub = klubRepozitorij.save(new Klub("Klub Ekipa", null));
        var ekipa = ligaStoritev.dodajEkipo(liga.getId(), new EkipaVnos(klub.getId(), null, null));
        Igralec izTurnirja = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();
        ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(izTurnirja.getId(), 1));
        Igralec nov = noviIgralec("Samo", "VLigi");
        ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(nov.getId(), 2));

        OrganizatorPregledDto p = pregledStoritev.pregled(jaz, DANES);

        assertEquals(5, p.udelezencev(), "4 s turnirja + 1 samo iz lige, prekrivanja ni");
    }

    // ---------- Pomozno ----------

    private Tekmovanje vrstica(OrganizatorPregledDto p, String ime) {
        return p.tekmovanja().stream().filter(t -> t.ime().equals(ime)).findFirst().orElseThrow();
    }

    /* Dogodek iz pripraviDogodek preklopi na lastnika, ime in termin. */
    private Turnir lastnik(Dogodek dogodek, Uporabnik lastnik, String ime, LocalDate zacetek, LocalDate rok) {
        Turnir t = dogodek.getTurnir();
        t.setIme(ime);
        t.setUstvaril(lastnik);
        t.setDatumZacetka(zacetek);
        turnirRepozitorij.save(t);
        dogodek.setRokPrijave(rok);
        dogodekRepozitorij.save(dogodek);
        return t;
    }

    private Uporabnik organizator(String prijavnoIme, String ime, String priimek, Klub klub, Paket paket) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setPrijavljenoIme(ime);
        u.setPrijavljeniPriimek(priimek);
        u.setKlub(klub);
        u = uporabnikRepozitorij.save(u);
        Narocnina n = new Narocnina(u, paket);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        return u;
    }

    private Turnir turnir(Uporabnik lastnik, String ime, LocalDate zacetek, StatusTekmovanja status) {
        Turnir t = new Turnir();
        t.setIme(ime);
        t.setUstvaril(lastnik);
        t.setDatumZacetka(zacetek);
        t.setStatus(status);
        return turnirRepozitorij.save(t);
    }

    private Liga liga(Uporabnik lastnik, String ime, String sezona) {
        Liga l = new Liga();
        l.setIme(ime);
        l.setSezona(sezona);
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        return ligaRepozitorij.save(l);
    }

    /* Liga dveh ekip z zrebom: 1. kolo na dan `prvoKolo`, razmik `razmik` dni. */
    private Liga ligaZRazporedom(Uporabnik lastnik, LocalDate prvoKolo, int razmik) {
        LigaVnos v = new LigaVnos("Liga z razporedom", Sezona.oznaka(DANES), SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, true, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                LocalDateTime.of(prvoKolo, java.time.LocalTime.of(18, 0)), razmik);
        Long id = ligaStoritev.ustvari(v).id();
        for (String klubIme : List.of("Klub A", "Klub B")) {
            Klub klub = klubRepozitorij.save(new Klub(klubIme, null));
            var ekipa = ligaStoritev.dodajEkipo(id, new EkipaVnos(klub.getId(), null, null));
            for (int i = 1; i <= 3; i++) {
                Igralec ig = noviIgralec("Ig" + klubIme.replace(" ", "") + i, "Pri" + i);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
            }
        }
        ligaStoritev.generirajRazpored(id);
        Liga liga = ligaRepozitorij.findById(id).orElseThrow();
        liga.setUstvaril(lastnik);
        return ligaRepozitorij.save(liga);
    }

    /* Ura nastanka je del pravila o meji paketa; test jo sme zamakniti samo z
       zapisom v bazo (@PrePersist jo vedno postavi na zdaj). */
    private void postaviUstvarjenOb(String tabela, Long id, LocalDate dan) {
        seja.createNativeQuery("UPDATE " + tabela + " SET ustvarjen_ob = ?1 WHERE id = ?2")
                .setParameter(1, dan.toString())
                .setParameter(2, id)
                .executeUpdate();
    }
}
