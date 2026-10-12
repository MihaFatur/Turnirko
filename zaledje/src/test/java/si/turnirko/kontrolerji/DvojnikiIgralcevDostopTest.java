/* Cistka duplikatov: kdo sme na koncne tocke (GET /igralci/duplikati,
   POST /igralci/duplikati/ni-duplikat, POST /igralci/{id}/zdruzi).

   Pari nosijo datume rojstva, zdruzitev pa spremeni zgodovino, zato je vse
   samo za ADMIN - ne gost in ne organizator. Test drzi tudi, da odgovor
   adminu res vsebuje par z datumom (stran Igralci ga potrebuje). */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DvojnikiIgralcevDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String ORGANIZATOR = "dvojniki-organizator";
    private static final String ADMIN = "dvojniki-admin";

    @Autowired MockMvc mockMvc;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @BeforeEach
    void pripravi() {
        racun(ORGANIZATOR, Vloga.ORGANIZATOR);
        racun(ADMIN, Vloga.ADMIN);
    }

    @Test
    void gostInOrganizatorParovNevidita() throws Exception {
        mockMvc.perform(get("/api/v1/igralci/duplikati")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/igralci/duplikati").header("Authorization", basic(ORGANIZATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gostInOrganizatorNistaOdlocataNitiZdruzevata() throws Exception {
        String telo = "{\"idA\":1,\"idB\":2}";
        mockMvc.perform(post("/api/v1/igralci/duplikati/ni-duplikat")
                        .contentType(MediaType.APPLICATION_JSON).content(telo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/igralci/duplikati/ni-duplikat")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON).content(telo))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/igralci/1/zdruzi")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idOdvec\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminVidiParZDatumomInGaLahkoZavrne() throws Exception {
        Igralec a = igralec("Dvojnik", "Dostopovic", LocalDate.of(1993, 4, 4));
        Igralec b = igralec("Dvojnik", "Dostopovic", LocalDate.of(1993, 4, 4));

        mockMvc.perform(get("/api/v1/igralci/duplikati").header("Authorization", basic(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + a.getId() + "-" + b.getId() + "')].datum").value("ENAK"))
                .andExpect(jsonPath("$[?(@.id == '" + a.getId() + "-" + b.getId() + "')].levi.datumRojstva")
                        .value("1993-04-04"));

        mockMvc.perform(post("/api/v1/igralci/duplikati/ni-duplikat")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idA\":" + b.getId() + ",\"idB\":" + a.getId() + "}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/igralci/duplikati").header("Authorization", basic(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + a.getId() + "-" + b.getId() + "')]").isEmpty());
    }

    @Test
    void adminZdruziPrekHttp() throws Exception {
        Igralec obdrzan = igralec("Zdruzi", "Prekhttp", LocalDate.of(1993, 4, 4));
        Igralec odvec = igralec("Zdruzi", "Prekhttp", LocalDate.of(1993, 4, 4));

        mockMvc.perform(post("/api/v1/igralci/" + obdrzan.getId() + "/zdruzi")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOdvec\":" + odvec.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igralec.id").value(obdrzan.getId()))
                .andExpect(jsonPath("$.preracunan").value(false));
    }

    @Test
    void zdruzitevZNeznanimIgralcemJeNeNajdeno() throws Exception {
        Igralec obdrzan = igralec("Zdruzi", "Neznan", LocalDate.of(1993, 4, 4));

        mockMvc.perform(post("/api/v1/igralci/" + obdrzan.getId() + "/zdruzi")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOdvec\":999999999}"))
                .andExpect(status().isNotFound());
    }

    private Igralec igralec(String ime, String priimek, LocalDate datum) {
        Igralec igralec = new Igralec();
        igralec.setIme(ime);
        igralec.setPriimek(priimek);
        igralec.setSpol(Spol.MOSKI);
        igralec.setDatumRojstva(datum);
        return igralecRepozitorij.save(igralec);
    }

    private void racun(String prijava, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijava, kodirnik.encode(GESLO), vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijava) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijava + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
