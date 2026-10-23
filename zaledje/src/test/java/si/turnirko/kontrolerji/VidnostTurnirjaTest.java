/* Seznam turnirjev gostu in igralcu ne kaze turnirjev v pripravi, ki so vec
   kot teden dalec (VidnostTurnirja); admin in organizator vidita vse.

   Test gre skozi HTTP, ker je odlocitev odvisna od tega, KDO je zahtevo
   poslal: GET /api/v1/turnirji je javen in racun pozna samo, ce varnostna
   veriga glavo Authorization prebere tudi na poti, ki prijave ne zahteva. */
package si.turnirko.kontrolerji;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VidnostTurnirjaTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;
    private final ObjectMapper json = new ObjectMapper();

    private final LocalDate danes = LocalDate.now(ZoneId.of("Europe/Ljubljana"));

    @BeforeEach
    void turnirji() {
        turnir("Dalec", StatusTekmovanja.PRIPRAVA, danes.plusDays(30));
        turnir("Cez osem dni", StatusTekmovanja.PRIPRAVA, danes.plusDays(8));
        // zreb narejen pred datumom: gledalec vidi "V pripravi", torej skrit
        turnir("Zreb dalec", StatusTekmovanja.V_TEKU, danes.plusDays(20));
        turnir("Cez teden", StatusTekmovanja.PRIPRAVA, danes.plusDays(7));
        turnir("Pozabljen", StatusTekmovanja.PRIPRAVA, danes.minusDays(3));
        turnir("Brez datuma", StatusTekmovanja.PRIPRAVA, null);
        turnir("Zakljucen", StatusTekmovanja.ZAKLJUCEN, danes.minusDays(40));
    }

    @Test
    void gostNeVidiTurnirjevVPripraviVecKotTedenNaprej() throws Exception {
        assertThat(imena(get("/api/v1/turnirji")))
                .contains("Cez teden", "Pozabljen", "Brez datuma", "Zakljucen")
                .doesNotContain("Dalec", "Cez osem dni", "Zreb dalec");
    }

    @Test
    void igralecVidiKotGost() throws Exception {
        racun("vidnost-igralec", Vloga.IGRALEC);
        assertThat(imena(get("/api/v1/turnirji").header("Authorization", basic("vidnost-igralec"))))
                .contains("Cez teden")
                .doesNotContain("Dalec", "Cez osem dni", "Zreb dalec");
    }

    @Test
    void organizatorInAdminViditaVse() throws Exception {
        racun("vidnost-organizator", Vloga.ORGANIZATOR);
        racun("vidnost-admin", Vloga.ADMIN);
        for (String ime : List.of("vidnost-organizator", "vidnost-admin")) {
            assertThat(imena(get("/api/v1/turnirji").header("Authorization", basic(ime))))
                    .contains("Dalec", "Cez osem dni", "Zreb dalec", "Cez teden");
        }
    }

    /* Skrit ni zaklenjen: povezava na stran turnirja deluje tudi gostu. */
    @Test
    void stranSkritegaTurnirjaOstaneDosegljiva() throws Exception {
        Long id = turnirRepozitorij.findAll().stream()
                .filter(t -> t.getIme().equals("Dalec")).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/api/v1/turnirji/" + id)).andExpect(status().isOk());
    }

    // ---------- Pomozno ----------

    private List<String> imena(MockHttpServletRequestBuilder zahteva) throws Exception {
        String telo = mockMvc.perform(zahteva).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(telo).findValues("ime").stream().map(JsonNode::asText).toList();
    }

    private void turnir(String ime, StatusTekmovanja status, LocalDate zacetek) {
        Turnir t = new Turnir();
        t.setIme(ime);
        t.setStatus(status);
        t.setDatumZacetka(zacetek);
        t.setDatumKonca(zacetek);
        turnirRepozitorij.save(t);
    }

    private void racun(String ime, Vloga vloga) {
        Uporabnik u = new Uporabnik(ime, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        if (vloga == Vloga.IGRALEC) {
            Igralec igralec = new Igralec();
            igralec.setIme("Vid");
            igralec.setPriimek("Vidnost");
            igralec.setSpol(Spol.MOSKI);
            igralec.setDatumRojstva(LocalDate.of(2001, 1, 1));
            u.setIgralec(igralecRepozitorij.save(igralec));
        }
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String ime) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((ime + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
