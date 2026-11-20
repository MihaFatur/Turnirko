/* Vadnica stevk skozi pravo varnostno verigo (V70): vse poti so samo za admina,
   tudi branje seznama (stevke so rokopis). Test NAMENOMA ni @Transactional
   (vzorec LenoNalaganjeTest): odgovor se sestavi in pretvori v JSON tako kot na
   pravem strezniku. Za seboj pocisti samo svoje vrstice. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vadnica;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.repozitoriji.VadnicaRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VadnicaDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String ADMIN = "admin-vadnica-dostop@test.si";
    private static final String ORGANIZATOR = "organizator-vadnica-dostop@test.si";
    private static final long ID_LISTA = 1_234_567_890L;

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired VadnicaRepozitorij vadnice;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    private final List<Long> uporabniki = new ArrayList<>();

    @BeforeEach
    void pripravi() {
        uporabniki.add(racun(ADMIN, Vloga.ADMIN));
        uporabniki.add(racun(ORGANIZATOR, Vloga.ORGANIZATOR));
        Vadnica v = new Vadnica(ID_LISTA, 1);
        LocalDateTime zdaj = LocalDateTime.now();
        v.nastaviBranje(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, 2, 137, 1, zdaj);
        vadnice.save(v);
        stevke.save(StevkaListka.zVadnice(ID_LISTA, 0, new byte[784], 0, true, zdaj));
        stevke.save(StevkaListka.zVadnice(ID_LISTA, 1, new byte[784], 0, false, zdaj));
    }

    @AfterEach
    void pocisti() {
        // stevke lista izbrise baza (ON DELETE CASCADE)
        vadnice.deleteById(ID_LISTA);
        uporabnikRepozitorij.deleteAllById(uporabniki);
        omejevalnik.pocistiVse();
    }

    @Test
    void gostInOrganizatorNimataDostopa() throws Exception {
        for (String pot : new String[] {"/api/v1/vadnice", "/api/v1/vadnice/" + ID_LISTA,
                "/api/v1/vadnice/" + ID_LISTA + "/slika"}) {
            mockMvc.perform(get(pot)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(pot).header("Authorization", basic(ORGANIZATOR))).andExpect(status().isForbidden());
        }
        MockMultipartFile slika = new MockMultipartFile("slika", "vadnica.jpg", "image/jpeg", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/vadnice").file(slika).header("Authorization", basic(ORGANIZATOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/vadnice/" + ID_LISTA + "/odobri").header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"izlocene\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/vadnice/" + ID_LISTA).header("Authorization", basic(ORGANIZATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminVidiSeznamInListZaPregled() throws Exception {
        String admin = basic(ADMIN);
        mockMvc.perform(get("/api/v1/vadnice").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listi[?(@.id == " + ID_LISTA + ")].stevk").value(2))
                .andExpect(jsonPath("$.listi[?(@.id == " + ID_LISTA + ")].odobrena").value(false));
        mockMvc.perform(get("/api/v1/vadnice/" + ID_LISTA).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.preskocenih").value(1))
                .andExpect(jsonPath("$.stevke.length()").value(2))
                .andExpect(jsonPath("$.stevke[1].mrezaPrav").value(false));
        mockMvc.perform(get("/api/v1/vadnice/99").header("Authorization", admin))
                .andExpect(status().isNotFound());
        // neveljavna slika: 400 z razlago, ne 500
        MockMultipartFile slika = new MockMultipartFile("slika", "vadnica.jpg", "image/jpeg", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/vadnice").file(slika).header("Authorization", admin))
                .andExpect(status().isBadRequest());
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
