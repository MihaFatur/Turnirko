/* Osebni koledar .ics in e-posta po turnirju (V58, paket Navijac): obe gradita
   na spremljanih igralcih in obe sta del placljivega paketa. Prvi del preverja
   dostop (paket, zeton), drugi vsebino in pravilo "vsak turnir enkrat". */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class OsebniKoledarInObvestiloTurnirjaTest extends IntegracijskiTest {

    private static final int RAZMIK = 7;

    @Autowired private OsebniKoledarStoritev koledar;
    @Autowired private ObvestilaPoTurnirjuStoritev obvestilaPoTurnirju;
    @Autowired private SpremljaniIgralciStoritev spremljani;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private ZrebStoritev zrebStoritev;
    @Autowired private TekmaStoritev tekmaStoritev;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private PomnilniskiPosiljatelj posta;

    @BeforeEach
    void pripravi() {
        zrebStoritev.nastaviNakljucje(new Random(42));
        posta.pocisti();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- dostop do koledarja ----------

    @Test
    void brezplacenRacunKoledarjaNima() {
        Uporabnik u = navijac("brezplacen@test", null);
        prijava(u);
        assertThrows(PrepovedanoIzjema.class, () -> koledar.zeton());
    }

    @Test
    void gostKoledarjaNima() {
        assertThrows(PrepovedanoIzjema.class, () -> koledar.zeton());
    }

    @Test
    void zetonJeStalenInSeZamenja() {
        Uporabnik u = navijac("zeton@test", StatusNarocnine.AKTIVNA);
        prijava(u);

        String prvi = koledar.zeton();
        assertEquals(prvi, koledar.zeton(), "isti racun dobi isti zeton");
        String nov = koledar.zamenjajZeton();
        assertNotEquals(prvi, nov);
        assertThrows(NiNajdenoIzjema.class, () -> koledar.koledar(prvi), "stara povezava ne dela vec");
        assertTrue(koledar.koledar(nov).startsWith("BEGIN:VCALENDAR"));
    }

    @Test
    void poteklaNarocninaKoledarZavrne() {
        Uporabnik u = navijac("potekla@test", StatusNarocnine.AKTIVNA);
        prijava(u);
        String zeton = koledar.zeton();
        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        n.setStatus(StatusNarocnine.ZAPADLA);
        narocninaRepozitorij.save(n);

        assertThrows(PrepovedanoIzjema.class, () -> koledar.koledar(zeton));
    }

    // ---------- vsebina koledarja ----------

    @Test
    void koledarVsebujeSrecanjaEkipeSpremljanegaIgralcaInPrijavljeniTurnir() {
        LocalDate prvoKolo = LocalDate.now().plusDays(2);
        Igralec spremljan = ligaZIgralcem(prvoKolo);
        Dogodek dogodek = pripraviDogodek(2);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(LocalDate.now().plusDays(10));
        turnir.setDatumKonca(LocalDate.now().plusDays(11));
        turnirRepozitorij.save(turnir);
        Igralec naTurnirju = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();

        Uporabnik u = navijac("koledar@test", StatusNarocnine.AKTIVNA);
        prijava(u);
        spremljani.spremljaj(spremljan.getId());
        // meja je odstranjena (paket), zato lahko spremlja se enega
        spremljani.spremljaj(naTurnirju.getId());
        String ics = koledar.koledar(koledar.zeton());

        assertTrue(ics.contains("BEGIN:VEVENT"), ics);
        assertTrue(ics.contains("UID:srecanje-"), "ligasko srecanje ekipe: " + ics);
        assertTrue(ics.contains("UID:turnir-" + turnir.getId() + "@turnirko-nt.si"), "turnir prijave: " + ics);
        assertTrue(ics.contains("DTSTART;VALUE=DATE:" + turnir.getDatumZacetka().toString().replace("-", "")));
        assertTrue(ics.replace("\r\n ", "").contains("Igralci: " + spremljan.polnoIme()), ics);
        // srecanj, v katerih igralec ne igra, ni: ekipa igra v vsakem kolu kvecjemu eno (3 kola)
        long srecanj = ics.lines().filter(l -> l.startsWith("UID:srecanje-")).count();
        assertEquals(3, srecanj, ics);
    }

    // ---------- e-posta po turnirju ----------

    @Test
    void zakljucenTurnirPosljeSporociloSamoRacunuSPlacljivimPaketom() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmaRepozitorij.findAll().stream()
                .filter(t -> t.getDogodek().getId().equals(dogodek.getId()) && t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null));
        Igralec zmagovalec = tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getPrijava1().getIgralec();
        Turnir turnir = dogodek.getTurnir();
        turnir.setStatus(StatusTekmovanja.ZAKLJUCEN);
        turnir.setDatumZacetka(LocalDate.now().minusDays(1));
        turnir.setDatumKonca(LocalDate.now().minusDays(1));
        turnirRepozitorij.save(turnir);

        Uporabnik placnik = navijac("placnik@test.si", StatusNarocnine.AKTIVNA);
        Uporabnik brezplacen = navijac("brezplacen2@test.si", null);
        Uporabnik izklopljen = navijac("izklopljen@test.si", StatusNarocnine.AKTIVNA);
        izklopljen.setObvestilaPoKolu(false);
        uporabnikRepozitorij.save(izklopljen);
        prijava(placnik);
        spremljani.spremljaj(zmagovalec.getId());
        prijava(brezplacen);
        spremljani.spremljaj(zmagovalec.getId());
        prijava(izklopljen);
        spremljani.spremljaj(zmagovalec.getId());

        int poslanih = obvestilaPoTurnirju.poslji(LocalDateTime.now());

        assertEquals(1, poslanih, "samo racun s paketom in vklopljenimi obvestili");
        var sporocilo = posta.zadnje("placnik@test.si").orElseThrow();
        assertEquals(turnir.getIme() + ": izidi", sporocilo.zadeva());
        assertTrue(sporocilo.besedilo().contains(zmagovalec.polnoIme()), sporocilo.besedilo());
        assertTrue(sporocilo.besedilo().contains("zmaga 3 : 1"), sporocilo.besedilo());
        assertTrue(sporocilo.besedilo().contains("/odjava-obvestil?z="), "odjava v sporocilu");
        assertTrue(posta.zadnje("brezplacen2@test.si").isEmpty());
        assertTrue(posta.zadnje("izklopljen@test.si").isEmpty());

        // vsak turnir enkrat
        posta.pocisti();
        assertEquals(0, obvestilaPoTurnirju.poslji(LocalDateTime.now()));
        assertTrue(posta.vsa().isEmpty());
    }

    @Test
    void staZakljucenTurnirNiNovicaInNezakljucenSeNeObdela() {
        Dogodek stari = pripraviDogodek(2);
        Turnir t1 = stari.getTurnir();
        t1.setStatus(StatusTekmovanja.ZAKLJUCEN);
        t1.setDatumZacetka(LocalDate.now().minusDays(40));
        t1.setDatumKonca(LocalDate.now().minusDays(40));
        turnirRepozitorij.save(t1);
        Dogodek vTeku = pripraviDogodek(2);
        Turnir t2 = vTeku.getTurnir();
        t2.setStatus(StatusTekmovanja.V_TEKU);
        t2.setDatumZacetka(LocalDate.now());
        turnirRepozitorij.save(t2);

        assertEquals(0, obvestilaPoTurnirju.poslji(LocalDateTime.now()));
        assertFalse(obvestilaPoTurnirju.poslji(LocalDateTime.now()) > 0);
    }

    // ---------- priprava ----------

    /* Liga s stirimi ekipami po tri igralci, razpored na sedem dni; vrne igralca prve ekipe. */
    private Igralec ligaZIgralcem(LocalDate prvoKolo) {
        LigaVnos vnos = new LigaVnos("Liga koledarja", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                prvoKolo.atTime(18, 0), RAZMIK);
        Long idLige = ligaStoritev.ustvari(vnos).id();
        Igralec spremljan = null;
        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub Q" + i, null));
            var ekipa = ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 3; j++) {
                Igralec igralec = noviIgralec("Kol" + i + j, "Priimek" + i + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(igralec.getId(), j));
                if (i == 1 && j == 1) {
                    spremljan = igralec;
                }
            }
        }
        ligaStoritev.generirajRazpored(idLige);
        return spremljan;
    }

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
