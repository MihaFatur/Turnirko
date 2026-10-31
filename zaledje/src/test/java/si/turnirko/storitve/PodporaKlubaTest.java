/* Podpora klubu (V61, faza 3): navijac s paketom izbere klub, klub z dovolj
   podporniki dobi popust za organizatorja (stopnje 10/20/30 %), steje samo racun s
   paketom ZDAJ, popust gre kot Stripov kupon na prvo placilo organizatorskega paketa. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import si.turnirko.dto.NadgradnjaVnos;
import si.turnirko.dto.PodporaKlubaDto;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class PodporaKlubaTest extends IntegracijskiTest {

    @Autowired private PodporaKlubaStoritev podpora;
    @Autowired private PlacilaStoritev placila;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @MockitoBean private StripeNarocnine stripe;

    @BeforeEach
    void pripravi() {
        podpora.pozabiKupone();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    // ---------- stopnje ----------

    @Test
    void stopnjePopustaSoVsakih10PodpornikovDo30Odstotkov() {
        int[][] pricakovano = {{0, 0}, {9, 0}, {10, 10}, {19, 10}, {20, 20}, {29, 20}, {30, 30}, {99, 30}};
        for (int[] p : pricakovano) {
            assertEquals(p[1], PodporaKlubaStoritev.popustZa(p[0]).odstotek(), p[0] + " podpornikov");
        }
        PodporaKlubaDto.Popust sedem = PodporaKlubaStoritev.popustZa(7);
        assertEquals(10, sedem.naslednjaMeja(), "do prve stopnje");
        assertEquals(10, sedem.naslednjiOdstotek());
        assertNull(PodporaKlubaStoritev.popustZa(35).naslednjaMeja(), "pri najvecjem popustu naslednje stopnje ni");
    }

    // ---------- izbira ----------

    @Test
    void klubIzbereSamoNavijacSPaketom() {
        Klub klub = klub("Klub podpore");
        prijava(navijac("brez@test", null));
        assertThrows(PrepovedanoIzjema.class, () -> podpora.izberi(klub.getId()));

        Uporabnik placnik = navijac("placnik@test", StatusNarocnine.AKTIVNA);
        prijava(placnik);
        PodporaKlubaDto dto = podpora.izberi(klub.getId());
        assertTrue(dto.izbran());
        assertEquals("Klub podpore", dto.klub());
        assertEquals(1, dto.popust().podpornikov());
        assertEquals(0, dto.popust().odstotek());
    }

    @Test
    void racunPodpiraEnKlubMenjavaJePrepisIzbrisJeIzbris() {
        Klub a = klub("Klub A");
        Klub b = klub("Klub B");
        prijava(navijac("stars@test", StatusNarocnine.AKTIVNA));

        podpora.izberi(a.getId());
        PodporaKlubaDto po = podpora.izberi(b.getId());

        assertEquals(b.getId(), po.idKlub());
        assertEquals(0, podpora.popust(a.getId()).podpornikov(), "prejsnji klub podpornika nima vec");
        assertEquals(1, podpora.popust(b.getId()).podpornikov());

        PodporaKlubaDto prazno = podpora.umakni();
        assertFalse(prazno.izbran());
        assertEquals(0, podpora.popust(b.getId()).podpornikov());
    }

    @Test
    void stejeSamoRacunSPaketomZdaj() {
        Klub klub = klub("Veliki klub");
        Uporabnik izteceni = null;
        for (int i = 0; i < 10; i++) {
            Uporabnik u = navijac("podpornik" + i + "@test", StatusNarocnine.AKTIVNA);
            prijava(u);
            podpora.izberi(klub.getId());
            if (i == 0) {
                izteceni = u;
            }
        }
        assertEquals(10, podpora.popust(klub.getId()).odstotek(), "10 podpornikov = 10 %");

        Narocnina n = narocninaRepozitorij.findByUporabnikId(izteceni.getId()).orElseThrow();
        n.setStatus(StatusNarocnine.ZAPADLA);
        narocninaRepozitorij.save(n);

        PodporaKlubaDto.Popust po = podpora.popust(klub.getId());
        assertEquals(9, po.podpornikov(), "kdor ne placuje, ne steje");
        assertEquals(0, po.odstotek());
    }

    // ---------- organizator in plačilo ----------

    @Test
    void organizatorKlubaDobiPopustSamoZaPotrjenKlub() {
        Klub klub = klub("Domaci klub");
        podporniki(klub, 10);
        Uporabnik zKlubom = organizator("org1@test", klub);
        Uporabnik brezKluba = organizator("org2@test", null);

        assertEquals(10, podpora.popustZaOrganizatorja(zKlubom));
        assertEquals(0, podpora.popustZaOrganizatorja(brezKluba));
        assertEquals(0, podpora.popustZaOrganizatorja(navijac("nav@test", StatusNarocnine.AKTIVNA)),
                "popust je za organizatorje");
    }

    @Test
    void popustKlubaGreKotStripovKuponNaPrvoPlaciloOrganizatorskegaPaketa() {
        Klub klub = klub("Klub s popustom");
        podporniki(klub, 20);
        Uporabnik org = organizator("org3@test", klub);
        when(stripe.ustvariKupon("Podpora kluba 20 %", 20)).thenReturn("co_klub20");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> popusti = (List<Map<String, Object>>) placila
                .pripraviNadgradnjo(org, new NadgradnjaVnos(Paket.ORGANIZATOR_BASIC, CiklusPlacila.LETNO))
                .toMap().get("discounts");
        assertEquals("co_klub20", popusti.get(0).get("coupon"));

        // klub brez podpornikov: seje brez popusta
        Uporabnik drug = organizator("org4@test", klub("Klub brez"));
        assertNull(placila.pripraviNadgradnjo(drug, new NadgradnjaVnos(Paket.ORGANIZATOR_BASIC, CiklusPlacila.LETNO))
                .toMap().get("discounts"));
    }

    // ---------- priprava ----------

    private void podporniki(Klub klub, int n) {
        for (int i = 0; i < n; i++) {
            Uporabnik u = navijac(klub.getIme().replace(' ', '_') + i + "@test", StatusNarocnine.AKTIVNA);
            prijava(u);
            podpora.izberi(klub.getId());
        }
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    private Klub klub(String ime) {
        return klubRepozitorij.save(new Klub(ime, null));
    }

    private Uporabnik organizator(String prijavnoIme, Klub klub) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setKlub(klub);
        return uporabnikRepozitorij.save(u);
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
