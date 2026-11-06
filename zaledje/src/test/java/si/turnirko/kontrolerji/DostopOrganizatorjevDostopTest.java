/* Dostop organizatorjev do tekmovanj ureja in vidi samo administrator.

   Pravilo v varnostni verigi je odvisno od VRSTNEGA REDA: odgovor je GET, zato
   bi ga splosno pravilo »gost sme brati vse« razkrilo (z e-postami
   organizatorjev), pisanje pa bi tiho pogoltnilo pravilo »/api/v1/turnirji/**
   in /lige/** sme tudi organizator« - organizator bi si sam dodelil dostop do
   tuje lige. Zato test in ne le komentar. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.DostopOrganizatorjaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DostopOrganizatorjevDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired DostopOrganizatorjaRepozitorij dostopRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostNiVidiNiDodeljuje() throws Exception {
        Long idTurnir = turnir();
        Long idOrganizator = racun("organizator-gost", Vloga.ORGANIZATOR).getId();

        mockMvc.perform(get("/api/v1/turnirji/" + idTurnir + "/organizatorji"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/turnirji/" + idTurnir + "/organizatorji/" + idOrganizator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steje\":true}"))
                .andExpect(status().isUnauthorized());

        assertTrue(dostopRepozitorij.findByIdTurnir(idTurnir).isEmpty());
    }

    /* Organizator ne vidi sebe niti drugih in si ne more dodeliti tuje lige. */
    @Test
    void organizatorNeVidiNeDodeljujeInNeOdvzema() throws Exception {
        Long idLiga = liga();
        Uporabnik organizator = racun("organizator-dostop", Vloga.ORGANIZATOR);
        String prijava = basic("organizator-dostop");

        mockMvc.perform(get("/api/v1/lige/" + idLiga + "/organizatorji").header("Authorization", prijava))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/lige/" + idLiga + "/organizatorji/" + organizator.getId())
                        .header("Authorization", prijava)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steje\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/lige/" + idLiga + "/organizatorji/" + organizator.getId())
                        .header("Authorization", prijava))
                .andExpect(status().isForbidden());

        assertFalse(dostopRepozitorij.existsByIdLigaAndIdRacun(idLiga, organizator.getId()));
    }

    @Test
    void adminDodeliPreberePreklopiInOdvzame() throws Exception {
        Long idTurnir = turnir();
        Uporabnik organizator = racun("organizator-sodelavec", Vloga.ORGANIZATOR);
        racun("admin-dostop", Vloga.ADMIN);
        String prijava = basic("admin-dostop");
        String pot = "/api/v1/turnirji/" + idTurnir + "/organizatorji";

        mockMvc.perform(get(pot).header("Authorization", prijava))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizatorji.length()").value(0))
                .andExpect(jsonPath("$.kandidati.length()").value(1));

        mockMvc.perform(put(pot + "/" + organizator.getId()).header("Authorization", prijava)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steje\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizatorji.length()").value(1))
                .andExpect(jsonPath("$.organizatorji[0].vir").value("DODELJEN"))
                .andExpect(jsonPath("$.organizatorji[0].steje").value(true))
                .andExpect(jsonPath("$.kandidati.length()").value(0));

        mockMvc.perform(put(pot + "/" + organizator.getId()).header("Authorization", prijava)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steje\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizatorji[0].steje").value(false));

        mockMvc.perform(delete(pot + "/" + organizator.getId()).header("Authorization", prijava))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizatorji.length()").value(0));

        assertEquals(0, dostopRepozitorij.findByIdTurnir(idTurnir).size());
    }

    @Test
    void vnosBrezPolja_stejeJeNapaka() throws Exception {
        Long idLiga = liga();
        Uporabnik organizator = racun("organizator-vnos", Vloga.ORGANIZATOR);
        racun("admin-vnos", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/lige/" + idLiga + "/organizatorji/" + organizator.getId())
                        .header("Authorization", basic("admin-vnos"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Long turnir() {
        Turnir t = new Turnir();
        t.setIme("Testni turnir");
        return turnirRepozitorij.save(t).getId();
    }

    private Long liga() {
        Liga l = new Liga();
        l.setIme("Testna liga");
        l.setSpolKategorija(SpolKategorija.MOSKI);
        return ligaRepozitorij.save(l).getId();
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        String par = prijavnoIme + ":" + GESLO;
        return "Basic " + Base64.getEncoder()
                .encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
