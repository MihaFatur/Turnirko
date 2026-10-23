/* Obvestila po kolu skozi pravo varnostno verigo (HTTP Basic).

   Odjava iz e-poste mora delovati brez prijave (povezava v sporocilu in odjava
   z enim klikom, ki jo poslje odjemalec poste), nastavitev pa je osebna: GET
   bi brez izrecnega pravila padel pod "GET je javen", PUT pa pod "mutacije
   sme samo admin" - igralec svojih obvestil ne bi mogel izklopiti. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
class ObvestilaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String PRIJAVA = "obvestila-dostop@test.si";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostNastavitveNeVidiInNeSpremeni() throws Exception {
        mockMvc.perform(get("/api/v1/obvestila")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/obvestila")
                .contentType(MediaType.APPLICATION_JSON).content("{\"poKolu\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void igralecObvestilaIzklopi() throws Exception {
        racun(null);
        mockMvc.perform(get("/api/v1/obvestila").header("Authorization", basic()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poKolu").value(true));
        mockMvc.perform(put("/api/v1/obvestila").header("Authorization", basic())
                .contentType(MediaType.APPLICATION_JSON).content("{\"poKolu\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poKolu").value(false));
    }

    /* Odjava z enim klikom: POST brez prijave s telesom RFC 8058. */
    @Test
    void odjavaZZetonomBrezPrijave() throws Exception {
        Uporabnik u = racun("zeton-za-test-odjave");
        mockMvc.perform(post("/api/v1/obvestila/odjava").param("z", "zeton-za-test-odjave")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isNoContent());
        assertFalse(uporabnikRepozitorij.findById(u.getId()).orElseThrow().isObvestilaPoKolu());
        mockMvc.perform(post("/api/v1/obvestila/odjava").param("z", "ni-tak"))
                .andExpect(status().isNotFound());
    }

    private Uporabnik racun(String zeton) {
        Uporabnik u = new Uporabnik(PRIJAVA, kodirnik.encode(GESLO), Vloga.IGRALEC);
        // racun brez povezanega igralca (spremlja lige) - shema POTRJEN igralca brez zapisa ne dovoli
        u.setStatus(StatusRacuna.CAKA);
        u.setEmailPotrjenOb(LocalDateTime.now());
        u.setZetonOdjave(zeton);
        return uporabnikRepozitorij.save(u);
    }

    private static String basic() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((PRIJAVA + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
