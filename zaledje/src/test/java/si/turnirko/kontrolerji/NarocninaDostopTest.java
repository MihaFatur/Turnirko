/* Stran "Narocnina" skozi pravo varnostno verigo (HTTP Basic).

   Pregled narocnine je GET, "GET je javen" pa je splosno pravilo verige: ce bi
   pravilo za /api/v1/narocnina stalo za njim, bi gost dobil odgovor 200 namesto
   401 - storitveni test (UpravljanjeNarocnineStoritevTest) klice storitev z
   rocno postavljenim kontekstom in tega ne pokrije. Stripe je lazen: test ne
   sme klicati omrezja. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.StripeNarocnine;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NarocninaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String PRIJAVA = "narocnina-dostop@test.si";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired PasswordEncoder kodirnik;
    @MockitoBean StripeNarocnine stripe;

    @Test
    void gostNarocnineNeVidiInNeUpravlja() throws Exception {
        mockMvc.perform(get("/api/v1/narocnina")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/narocnina/preklic")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/narocnina/obnova")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/narocnina/preklop")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ciklus\":\"LETNO\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/narocnina/preklop")).andExpect(status().isUnauthorized());
    }

    @Test
    void igralecBrezNarocnineDobiOdgovorBrezNje() throws Exception {
        racun(false);

        mockMvc.perform(get("/api/v1/narocnina").header("Authorization", basic()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paket").value("BREZPLACNO"))
                .andExpect(jsonPath("$.aktivna").value(false));
    }

    /* Datum gre navzven kot ISO niz ("2026-10-12"), ne kot tabela stevil: po tem
       ga bere oblikujDatum v vmesniku. */
    @Test
    void premiumIgralecVidiSvojeObdobje() throws Exception {
        Uporabnik u = racun(true);
        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        n.setTrenutnoObdobjeDo(LocalDateTime.of(2026, 10, 12, 12, 0));
        narocninaRepozitorij.save(n);

        mockMvc.perform(get("/api/v1/narocnina").header("Authorization", basic()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paket").value("PREMIUM"))
                .andExpect(jsonPath("$.aktivna").value(true))
                .andExpect(jsonPath("$.ciklus").value("MESECNO"))
                .andExpect(jsonPath("$.obdobjeDo").value("2026-10-12"))
                .andExpect(jsonPath("$.naslednjiCiklus").doesNotExist());
    }

    private Uporabnik racun(boolean sPremium) {
        Uporabnik u = new Uporabnik(PRIJAVA, kodirnik.encode(GESLO), Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        uporabnikRepozitorij.save(u);
        if (sPremium) {
            Narocnina n = new Narocnina(u, Paket.PREMIUM);
            n.setCiklus(CiklusPlacila.MESECNO);
            n.setStatus(StatusNarocnine.AKTIVNA);
            n.setCenaObSklenitvi(4.99);
            n.setStripeNarocninaId("sub_dostop");
            narocninaRepozitorij.save(n);
        }
        return u;
    }

    private static String basic() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((PRIJAVA + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
