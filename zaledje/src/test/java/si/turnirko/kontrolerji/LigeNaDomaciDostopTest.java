/* Domaco stran ureja samo administrator.

   Pravilo v varnostni verigi je odvisno od VRSTNEGA REDA: splosno pravilo
   "/api/v1/lige/** sme tudi organizator" stoji nizje in bi ob prestavitvi tiho
   pogoltnilo to pot. Organizator bi tako lahko svojo ligo postavil na vhodno
   stran zveze - in tega ne bi opazil nihce, dokler se ne bi zgodilo. Zato test
   in ne le komentar. */
package si.turnirko.kontrolerji;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LigeNaDomaciDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostDomaceStraniNeUreja() throws Exception {
        Long idLiga = liga();

        mockMvc.perform(put("/api/v1/lige/" + idLiga + "/na-domaci"))
                .andExpect(status().isUnauthorized());

        assertFalse(ligaRepozitorij.findById(idLiga).orElseThrow().isNaDomaci());
    }

    /* Organizator sme svoje lige - domaca stran pa ni njegova. */
    @Test
    void organizatorDomaceStraniNeUreja() throws Exception {
        Long idLiga = liga();
        racun("organizator-domov", Vloga.ORGANIZATOR);

        mockMvc.perform(put("/api/v1/lige/" + idLiga + "/na-domaci")
                        .header("Authorization", basic("organizator-domov")))
                .andExpect(status().isForbidden());

        assertFalse(ligaRepozitorij.findById(idLiga).orElseThrow().isNaDomaci());
    }

    @Test
    void adminLigoPostaviNaDomaco() throws Exception {
        Long idLiga = liga();
        racun("admin-domov", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/lige/" + idLiga + "/na-domaci")
                        .header("Authorization", basic("admin-domov")))
                .andExpect(status().isOk());

        assertTrue(ligaRepozitorij.findById(idLiga).orElseThrow().isNaDomaci());
    }

    private Long liga() {
        Liga l = new Liga();
        l.setIme("Testna liga");
        l.setSpolKategorija(SpolKategorija.MOSKI);
        return ligaRepozitorij.save(l).getId();
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        String par = prijavnoIme + ":" + GESLO;
        return "Basic " + Base64.getEncoder()
                .encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
