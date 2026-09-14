/* Uvoz iz Stupe je samo administratorjev - tudi branje.

   Predogled nosi datume rojstva novih igralcev in kandidatov za istovetnost,
   splosno pravilo "GET je javen" pa stoji v verigi nizje in bi ob prestavitvi
   pravila tiho odprlo pot gostu. Zato test in ne le komentar. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UvozDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostUvozaNeVidi() throws Exception {
        mockMvc.perform(get("/api/v1/uvoz/stupa/zagoni")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/uvoz/stupa/dogodki/245/predogled")).andExpect(status().isUnauthorized());
    }

    @Test
    void organizatorUvozaNeVidi() throws Exception {
        racun("organizator-uvoz", Vloga.ORGANIZATOR);
        mockMvc.perform(get("/api/v1/uvoz/stupa/zagoni").header("Authorization", basic("organizator-uvoz")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/uvoz/stupa/dogodki/245/uvoz").header("Authorization", basic("organizator-uvoz"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"posnetek\":\"245-20260913-120000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDnevnikUvozaBere() throws Exception {
        racun("admin-uvoz", Vloga.ADMIN);
        mockMvc.perform(get("/api/v1/uvoz/stupa/zagoni").header("Authorization", basic("admin-uvoz")))
                .andExpect(status().isOk());
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijavnoIme + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
