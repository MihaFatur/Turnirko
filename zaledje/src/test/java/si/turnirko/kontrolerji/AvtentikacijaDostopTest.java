/* Poti brez prijave (registracija, kode, pozabljeno geslo) in blokada po
   prevec neuspelih prijavah.

   Dvoje mora drzati cez zico, ne le v storitvi: (1) odgovor registracije je
   enak, ce naslov ze obstaja - iz njega ni mogoce sestaviti seznama racunov;
   (2) racun s prevec napacnimi gesli dobi 429, se preden se geslo preveri.
   Oboje varuje varnostna veriga oz. filter, zato test tece skozi MockMvc. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;
import si.turnirko.storitve.RegistracijaStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvtentikacijaDostopTest {

    private static final String REGISTRACIJA = """
            {"ime":"Ana","priimek":"Novak","email":"%s","geslo":"geslo1234",
             "organizator":false,"datumRojstva":"1990-05-20"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired RegistracijaStoritev registracija;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @BeforeEach
    @AfterEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    @Test
    void registracijaJeBrezPrijaveInNeRazkrijeObstojaNaslova() throws Exception {
        String prvi = mockMvc.perform(post("/api/v1/auth/registracija")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRACIJA.formatted("ana@test.si")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isPresent());
        // odgovor ne nosi nicesar o racunu (id, status), samo kar je vpisal klicatelj
        assertFalse(prvi.contains("\"id\""), prvi);
        assertFalse(prvi.contains("status"), prvi);

        omejevalnik.pocistiVse();
        String drugi = mockMvc.perform(post("/api/v1/auth/registracija")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRACIJA.formatted("ana@test.si")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(prvi, drugi, "isti odgovor, ne glede na to, ali naslov ze obstaja");
    }

    @Test
    void napacnaKodaInNeznanNaslovDobitaIstoNapako() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "ana@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        String zaObstojeci = mockMvc.perform(post("/api/v1/auth/potrdi-eposto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@test.si\",\"koda\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String zaNeznanega = mockMvc.perform(post("/api/v1/auth/potrdi-eposto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\",\"koda\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals(zaObstojeci, zaNeznanega);
        assertTrue(zaObstojeci.contains(RegistracijaStoritev.NAPACNA_KODA));
    }

    @Test
    void pozabljenoGesloInNovoGesloStaBrezPrijave() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pozabljeno-geslo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/novo-geslo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nihce@test.si\",\"koda\":\"123456\",\"geslo\":\"novoGeslo1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nepotrjenRacunSePrijaviABrezPravic() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "ana@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        String profil = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("ana@test.si", "geslo1234")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(profil.contains("\"emailPotrjen\":false"), profil);

        mockMvc.perform(get("/api/v1/racuni")
                        .header("Authorization", basic("ana@test.si", "geslo1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void prevecNapacnihGeselBlokiraRacun() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "blokada@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("blokada@test.si", "napacno" + i)))
                    .andExpect(status().isUnauthorized());
        }
        // enajsti poskus ne pride do preverbe gesla - tudi s pravim geslom ne
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("blokada@test.si", "geslo1234")))
                .andExpect(status().isTooManyRequests());

        // z drugega naslova se lastnik se vedno prijavi: kdor ugiba, ne more
        // tujega racuna zakleniti za vse
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.9.9.9"); return zahteva; })
                        .header("Authorization", basic("blokada@test.si", "geslo1234")))
                .andExpect(status().isOk());

        // drug racun ni prizadet (blokada je na racun, ne na streznik)
        Uporabnik admin = uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getVloga() == si.turnirko.modeli.Vloga.ADMIN).findFirst().orElseThrow();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic(admin.getUporabniskoIme(), "napacno")))
                .andExpect(status().isUnauthorized());
    }

    /* Porazdeljeno ugibanje (veliko naslovov) ustavi skupna meja racuna. */
    @Test
    void ugibanjeZVecNaslovovZaklenePriSkupniMeji() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "skupna@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 100; i++) {
            String naslov = "10.0." + (i / 5) + "." + i;
            mockMvc.perform(get("/api/v1/auth/me")
                            .with(zahteva -> { zahteva.setRemoteAddr(naslov); return zahteva; })
                            .header("Authorization", basic("skupna@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.200.0.1"); return zahteva; })
                        .header("Authorization", basic("skupna@test.si", "geslo1234")))
                .andExpect(status().isTooManyRequests());

        // novo geslo s kodo po posti blokado odpravi
        omejevalnik.pocistiPrijaveRacuna("skupna@test.si");
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(zahteva -> { zahteva.setRemoteAddr("10.200.0.1"); return zahteva; })
                        .header("Authorization", basic("skupna@test.si", "geslo1234")))
                .andExpect(status().isOk());
    }

    @Test
    void uspesnaPrijavaPobriseStevecNeuspehov() throws Exception {
        registracija.registriraj(new RegistracijaVnos("Ana", "Novak", null, "stevec@test.si",
                "geslo1234", false, LocalDate.of(1990, 5, 20), null), "127.0.0.1");

        for (int i = 0; i < 9; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("stevec@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("stevec@test.si", "geslo1234")))
                .andExpect(status().isOk());
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", basic("stevec@test.si", "napacno")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", basic("stevec@test.si", "geslo1234")))
                .andExpect(status().isOk());
    }

    private static String basic(String ime, String geslo) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (ime + ":" + geslo).getBytes(StandardCharsets.UTF_8));
    }
}
