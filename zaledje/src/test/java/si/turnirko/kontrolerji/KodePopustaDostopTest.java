/* Kode popusta skozi pravo varnostno verigo (HTTP Basic).

   Seznam kod nosi stevilo porab, zato je samo administratorjev - tudi branje, ki
   je GET, in "GET je javen" je splosno pravilo verige: ce bi pravilo za
   /api/v1/kode-popusta stalo za njim, bi gost dobil 200 namesto 401. Predogled kode
   pri registraciji je pa JAVEN (gost nima racuna) in brez posledic. Koda gre v
   registraciji tudi prek JSON-a, ne le prek storitve. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabaKodePopustaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KodePopustaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String NOVA_KODA = """
            {"koda":"DOSTOP6","opis":"preizkus","vrsta":"PREMIUM_MESECI","meseci":6}""";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired UporabaKodePopustaRepozitorij uporabe;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @AfterEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    @Test
    void gostKodNeVidiInNeUpravlja() throws Exception {
        mockMvc.perform(get("/api/v1/kode-popusta")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/kode-popusta").contentType(MediaType.APPLICATION_JSON).content(NOVA_KODA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/kode-popusta/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"aktivna\":false}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/kode-popusta/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void navadenRacunKodNeVidiInNeUpravlja() throws Exception {
        racun("organizator-kode@test.si", Vloga.ORGANIZATOR);
        racun("igralec-kode@test.si", Vloga.IGRALEC);

        for (String ime : new String[] {"organizator-kode@test.si", "igralec-kode@test.si"}) {
            mockMvc.perform(get("/api/v1/kode-popusta").header("Authorization", basic(ime)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/kode-popusta").header("Authorization", basic(ime))
                    .contentType(MediaType.APPLICATION_JSON).content(NOVA_KODA)).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/kode-popusta/1").header("Authorization", basic(ime)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminKodoUstvariUrediInIzbrise() throws Exception {
        racun("admin-kode@test.si", Vloga.ADMIN);
        String admin = basic("admin-kode@test.si");

        String odgovor = mockMvc.perform(post("/api/v1/kode-popusta").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(NOVA_KODA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.koda").value("DOSTOP6"))
                .andExpect(jsonPath("$.stanje").value("VELJA"))
                .andExpect(jsonPath("$.uporab").value(0))
                .andReturn().getResponse().getContentAsString();
        Matcher idKode = Pattern.compile("\"id\":(\\d+)").matcher(odgovor);
        assertTrue(idKode.find(), odgovor);
        long id = Long.parseLong(idKode.group(1));

        mockMvc.perform(get("/api/v1/kode-popusta").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].koda").value("DOSTOP6"));
        mockMvc.perform(put("/api/v1/kode-popusta/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opis\":\"izklopljena\",\"aktivna\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stanje").value("IZKLJUCENA"))
                .andExpect(jsonPath("$.meseci").value(6));
        mockMvc.perform(put("/api/v1/kode-popusta/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"opis\":\"brez aktivnosti\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/kode-popusta/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/kode-popusta/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void predogledKodeJeJavenInNeRazkrijeNeobstojecih() throws Exception {
        racun("admin-kode@test.si", Vloga.ADMIN);
        mockMvc.perform(post("/api/v1/kode-popusta").header("Authorization", basic("admin-kode@test.si"))
                .contentType(MediaType.APPLICATION_JSON).content(NOVA_KODA)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/koda-popusta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"koda\":\"dostop6\",\"organizator\":false,\"paket\":\"BREZPLACNO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veljavna").value(true))
                .andExpect(jsonPath("$.uporabna").value(true))
                .andExpect(jsonPath("$.meseci").value(6));
        mockMvc.perform(post("/api/v1/auth/koda-popusta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"koda\":\"NI-TAKE\",\"organizator\":false,\"paket\":\"BREZPLACNO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veljavna").value(false))
                .andExpect(jsonPath("$.meseci").doesNotExist());
        mockMvc.perform(post("/api/v1/auth/koda-popusta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"koda\":\"\",\"organizator\":false,\"paket\":\"BREZPLACNO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kodaGrePrekJsonVRegistracijoInSeZabelezi() throws Exception {
        racun("admin-kode@test.si", Vloga.ADMIN);
        mockMvc.perform(post("/api/v1/kode-popusta").header("Authorization", basic("admin-kode@test.si"))
                .contentType(MediaType.APPLICATION_JSON).content(NOVA_KODA)).andExpect(status().isCreated());
        String registracija = """
                {"ime":"Ana","priimek":"Novak","email":"ana-koda@test.si","geslo":"geslo1234",
                 "datumRojstva":"1990-05-20","kodaPopusta":"%s"}""";

        mockMvc.perform(post("/api/v1/auth/registracija").contentType(MediaType.APPLICATION_JSON)
                        .content(registracija.formatted("NI-TAKE")))
                .andExpect(status().isBadRequest());
        assertFalse(uporabnikRepozitorij.findByUporabniskoIme("ana-koda@test.si").isPresent(),
                "napacna koda ne ustvari racuna");

        mockMvc.perform(post("/api/v1/auth/registracija").contentType(MediaType.APPLICATION_JSON)
                        .content(registracija.formatted("dostop6")))
                .andExpect(status().isOk());
        Uporabnik u = uporabnikRepozitorij.findByUporabniskoIme("ana-koda@test.si").orElseThrow();
        assertTrue(uporabe.najdiZaUporabnika(u.getId()).isPresent(), "koda je zabelezena pri racunu");

        mockMvc.perform(get("/api/v1/kode-popusta").header("Authorization", basic("admin-kode@test.si")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uporab").value(1));
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        // potrjen igralec mora imeti zapis (CHECK v shemi): navaden igralec je tu v stanju CAKA
        u.setStatus(vloga == Vloga.IGRALEC ? StatusRacuna.CAKA : StatusRacuna.POTRJEN);
        u.setAktiven(true);
        u.setEmailPotrjenOb(LocalDateTime.now());
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijavnoIme + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
