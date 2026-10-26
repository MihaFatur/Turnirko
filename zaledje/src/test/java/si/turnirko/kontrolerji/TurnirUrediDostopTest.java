/* Popravek turnirja skozi pravo varnostno verigo: vloga v VarnostneNastavitve, lastnistvo
   v LastnistvoStoritev in zaklep uvoza v isti storitvi. Test prehodi vse tri plasti,
   ker vsaka zavrne drugo napako (401, 403, 409). */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TurnirUrediDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String POPRAVEK =
            "{\"ime\":\"Novo ime turnirja\",\"datumZacetka\":\"2026-11-07\","
            + "\"datumKonca\":\"2026-11-08\",\"raven\":\"URADNO\"}";

    @Autowired MockMvc mockMvc;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostInIgralecNePopravita() throws Exception {
        Long turnir = turnir(null, null);
        racun("igralec-popravek", Vloga.IGRALEC);

        mockMvc.perform(put(url(turnir))
                        .contentType(MediaType.APPLICATION_JSON).content(POPRAVEK))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("igralec-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(POPRAVEK))
                .andExpect(status().isForbidden());
    }

    @Test
    void tujOrganizatorNePopravi() throws Exception {
        Long turnir = turnir(racun("lastnik-popravek", Vloga.ORGANIZATOR), null);
        racun("tuj-popravek", Vloga.ORGANIZATOR);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("tuj-popravek"))
                        .contentType(MediaType.APPLICATION_JSON).content(POPRAVEK))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastnikPopraviVPripravi() throws Exception {
        Long turnir = turnir(racun("lastnik-uredi", Vloga.ORGANIZATOR), null);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-uredi"))
                        .contentType(MediaType.APPLICATION_JSON).content(POPRAVEK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ime").value("Novo ime turnirja"))
                .andExpect(jsonPath("$.dejanskiStatus").value("PRIPRAVA"));
    }

    /* Uvoz je zveza: popravek vnese zveza, ne lastnik - zavrne ga storitev (409). */
    @Test
    void uvozenegaTurnirjaNePopravi() throws Exception {
        Long turnir = turnir(racun("lastnik-uvoz", Vloga.ORGANIZATOR), VirTekmovanja.STUPA);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-uvoz"))
                        .contentType(MediaType.APPLICATION_JSON).content(POPRAVEK))
                .andExpect(status().isConflict());
    }

    // ---------- Pomozno ----------

    private static String url(Long turnir) {
        return "/api/v1/turnirji/" + turnir;
    }

    private Long turnir(Uporabnik lastnik, VirTekmovanja vir) {
        Turnir turnir = new Turnir();
        turnir.setIme("Popravek turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnir.setUstvaril(lastnik);
        turnir.setVir(vir);
        return turnirRepozitorij.save(turnir).getId();
    }

    /* Potrjen igralec mora biti povezan z igralcem (CHECK v shemi), zato igralec
       ostane v cakanju - za pot skozi verigo je pomembna vloga. */
    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        StatusRacuna status = vloga == Vloga.IGRALEC ? StatusRacuna.CAKA : StatusRacuna.POTRJEN;
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(status);
        return uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        String par = prijavnoIme + ":" + GESLO;
        return "Basic " + Base64.getEncoder().encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
