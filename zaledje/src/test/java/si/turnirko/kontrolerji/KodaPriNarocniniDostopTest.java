/* Koda popusta na strani Narocnina (POST /api/v1/narocnina/koda) skozi pravo
   varnostno verigo (HTTP Basic): gost je ne sme, navijac jo uporabi in dobi
   posodobljeno narocnino, napaka pa pove razlog. */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

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

import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.KodePopustaStoritev;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KodaPriNarocniniDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String POT = "/api/v1/narocnina/koda";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocnine;
    @Autowired KodePopustaStoritev kode;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @AfterEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    @Test
    void gostKodeNeVpise() throws Exception {
        mockMvc.perform(post(POT).contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"KATERAKOLI\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void navijacKodoUporabiInDobiPosodobljenoNarocnino() throws Exception {
        kode.ustvari(new KodaPopustaVnos("KOLEGA1", null, VrstaKodePopusta.NAVIJAC_MESECI, 1, null, null, null,
                null, null));
        Uporabnik u = racun("kolega@test.si", Vloga.NAVIJAC);

        mockMvc.perform(post(POT).header("Authorization", basic("kolega@test.si"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"kolega1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paket").value("NAVIJAC"))
                .andExpect(jsonPath("$.status").value("PREIZKUS"))
                .andExpect(jsonPath("$.aktivna").value(true))
                .andExpect(jsonPath("$.upravljiva").value(false))
                .andExpect(jsonPath("$.obdobjeDo").isNotEmpty());
        assertTrue(narocnine.findByUporabnikId(u.getId()).isPresent());

        // druga uporaba na istem racunu: paket je ze tu, koda se ne porabi znova
        mockMvc.perform(post(POT).header("Authorization", basic("kolega@test.si"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"KOLEGA1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void napakaPoveRazlog() throws Exception {
        kode.ustvari(new KodaPopustaVnos("IGRALSKA", null, VrstaKodePopusta.PREMIUM_MESECI, 1, null, null, null,
                null, null));
        racun("napaka@test.si", Vloga.NAVIJAC);
        String avtorizacija = basic("napaka@test.si");

        mockMvc.perform(post(POT).header("Authorization", avtorizacija)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"NI-TAKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(KodePopustaStoritev.NEVELJAVNA_KODA));
        mockMvc.perform(post(POT).header("Authorization", avtorizacija)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"IGRALSKA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ta koda je za registracijo igralca."));
        mockMvc.perform(post(POT).header("Authorization", avtorizacija)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void igralecKodeNaTejPotiNeUporabi() throws Exception {
        kode.ustvari(new KodaPopustaVnos("SAMONAV2", null, VrstaKodePopusta.NAVIJAC_MESECI, 1, null, null, null,
                null, null));
        racun("igralec-koda@test.si", Vloga.IGRALEC);

        mockMvc.perform(post(POT).header("Authorization", basic("igralec-koda@test.si"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"koda\":\"SAMONAV2\"}"))
                .andExpect(status().isBadRequest());
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        // potrjen igralec mora imeti zapis (CHECK v shemi): navaden igralec je tu v stanju CAKA
        u.setStatus(vloga == Vloga.IGRALEC ? StatusRacuna.CAKA : StatusRacuna.POTRJEN);
        u.setAktiven(true);
        u.setEmailPotrjenOb(LocalDateTime.now());
        return uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijavnoIme + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
