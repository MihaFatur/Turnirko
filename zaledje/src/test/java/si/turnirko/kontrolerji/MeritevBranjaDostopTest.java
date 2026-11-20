/* Meritev branja listkov in zlati nabor skozi pravo varnostno verigo (V69).

   Poti so samo za admina: pravilo zanje mora stati PRED splosnim pravilom
   branja listkov (/api/v1/branje-listkov/** je za vsakega prijavljenega), sicer
   bi organizator videl meritve vseh turnirjev. Test NAMENOMA ni @Transactional
   (vzorec LenoNalaganjeTest): odgovor se sestavi in pretvori v JSON tako kot na
   pravem strezniku. Za seboj pocisti samo svoje vrstice. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeritevBranjaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String ADMIN = "admin-meritev@test.si";
    private static final String ORGANIZATOR = "organizator-meritev@test.si";
    private static final String[] POTI = {
            "/api/v1/branje-listkov/meritev", "/api/v1/branje-listkov/nabor", "/api/v1/branje-listkov/nabor/zip"};

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired BranjeListkaRepozitorij branjaRepozitorij;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    private final List<Long> uporabniki = new ArrayList<>();
    private Turnir turnir;
    private Long idBranja;

    @BeforeEach
    void pripravi() {
        uporabniki.add(racun(ADMIN, Vloga.ADMIN));
        uporabniki.add(racun(ORGANIZATOR, Vloga.ORGANIZATOR));
        Turnir t = new Turnir();
        t.setIme("Turnir meritve branja");
        t.setDatumZacetka(LocalDate.of(2026, 11, 14));
        t.setRaven(RavenTekmovanja.URADNO);
        turnir = turnirRepozitorij.save(t);
        BranjeListka b = new BranjeListka(turnir, null, LocalDateTime.now());
        b.setStanje(StanjeBranja.POTRJENO);
        b.setPrepoznava(PrepoznavaTekme.KODA);
        b.setPrebrano("""
                {"jeListek":true,"nizi":[{"levo":11,"desno":7},{"levo":11,"desno":9},{"levo":11,"desno":5}],
                 "rezultatLevo":3,"rezultatDesno":0}""");
        b.setPotrjeno("""
                {"dobljeniNizi1":3,"dobljeniNizi2":0,"nizi":[{"tocke1":11,"tocke2":7},{"tocke1":11,"tocke2":9},
                 {"tocke1":11,"tocke2":5}]}""");
        b.setPopravljeno(false);
        b.setPripravljen(true);
        b.setCasPregledaMs(1800);
        b.setNacinPotrditve(NacinPotrditve.EN_DOTIK);
        b.setModel("vgrajena");
        idBranja = branjaRepozitorij.save(b).getId();
    }

    @AfterEach
    void pocisti() {
        branjaRepozitorij.deleteById(idBranja);
        turnirRepozitorij.deleteById(turnir.getId());
        uporabnikRepozitorij.deleteAllById(uporabniki);
        omejevalnik.pocistiVse();
    }

    @Test
    void gostInOrganizatorNimataDostopa() throws Exception {
        for (String pot : POTI) {
            mockMvc.perform(get(pot)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(pot).header("Authorization", basic(ORGANIZATOR))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/branje-listkov/nabor/meritev").header("Authorization", basic(ORGANIZATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminVidiMeritevPoTurnirjihInMrezah() throws Exception {
        String admin = basic(ADMIN);
        mockMvc.perform(get("/api/v1/branje-listkov/meritev").header("Authorization", admin)
                        .param("idTurnir", turnir.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTurnir").value(turnir.getId()))
                .andExpect(jsonPath("$.doDatuma").exists())
                .andExpect(jsonPath("$.skupaj.potrjenih").value(1))
                .andExpect(jsonPath("$.skupaj.brezPopravka").value(1))
                .andExpect(jsonPath("$.skupaj.celice.pravZanesljive").value(8))
                .andExpect(jsonPath("$.skupaj.casi.mediana").value(1800))
                .andExpect(jsonPath("$.poTurnirjih[0].oznaka").value("Turnir meritve branja"))
                .andExpect(jsonPath("$.poMrezah[0].oznaka").value("vgrajena"));
        mockMvc.perform(get("/api/v1/branje-listkov/meritev").header("Authorization", admin)
                        .param("od", "2026-11-20").param("do", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminVidiNaborInPrenesiZip() throws Exception {
        String admin = basic(ADMIN);
        mockMvc.perform(get("/api/v1/branje-listkov/nabor").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.najvec").value(5000))
                .andExpect(jsonPath("$.tece").value(false));
        byte[] zip = mockMvc.perform(get("/api/v1/branje-listkov/nabor/zip").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        // zip se zacne s podpisom PK
        assertTrue(zip.length > 4 && zip[0] == 'P' && zip[1] == 'K');
    }

    private Long racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setAktiven(true);
        u.setEmailPotrjenOb(LocalDateTime.now());
        return uporabnikRepozitorij.save(u).getId();
    }

    private static String basic(String prijavnoIme) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijavnoIme + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
