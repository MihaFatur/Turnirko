/* Nastavitev zavihka »Zanimivosti« skozi pravo varnostno verigo.

   Branje je javno kot ostali pogledi tekmovanja, pisanje pa samo lastniku in
   administratorju. Veriga pozna samo vlogo, lastništvo pa preveri
   LastnistvoStoritev - zato test prehodi obe plasti in ne samo storitve. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.LigaStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ZanimivostiNastavitveDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String SKRIJ_KLUBI = "{\"skrite\":[\"KLUBI\"]}";

    @Autowired MockMvc mockMvc;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostNastavitevBereJavno() throws Exception {
        Long turnir = turnir(null, null);

        mockMvc.perform(get("/api/v1/turnirji/" + turnir + "/zanimivosti/nastavitve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razdelki[0]").value("STEVILKE"))
                .andExpect(jsonPath("$.skrite.length()").value(0));
    }

    @Test
    void gostInIgralecNastavitevNePiseta() throws Exception {
        Long turnir = turnir(null, null);
        racun("igralec-zanimivosti", Vloga.IGRALEC);

        mockMvc.perform(put(url(turnir))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("igralec-zanimivosti"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isForbidden());
    }

    @Test
    void tujOrganizatorNastavitveNePise() throws Exception {
        Long turnir = turnir(racun("lastnik-zanimivosti", Vloga.ORGANIZATOR), null);
        racun("tuj-zanimivosti", Vloga.ORGANIZATOR);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("tuj-zanimivosti"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isForbidden());
    }

    /* Lastnik skrije razdelek in ga vidi tudi v javnem branju - ista številka. */
    @Test
    void lastnikTurnirjaSkrijeRazdelek() throws Exception {
        Long turnir = turnir(racun("lastnik-turnir", Vloga.ORGANIZATOR), null);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-turnir"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skrite[0]").value("KLUBI"));

        mockMvc.perform(get(url(turnir)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skrite[0]").value("KLUBI"));
    }

    @Test
    void adminSmeVsakemuTurnirju() throws Exception {
        Long turnir = turnir(racun("lastnik-admin", Vloga.ORGANIZATOR), null);
        racun("admin-zanimivosti", Vloga.ADMIN);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("admin-zanimivosti"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isOk());
    }

    /* Razdelka, ki na tekmovanju ne nastane (liga na nož ima, turnir ne), in
       neznanega imena zavrnemo kot napako vnosa. */
    @Test
    void razdelkaZaNapacnoTekmovanjeInNeznanegaNiMogoceShraniti() throws Exception {
        Long turnir = turnir(racun("lastnik-napaka", Vloga.ORGANIZATOR), null);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-napaka"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skrite\":[\"NA_NOZ\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-napaka"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skrite\":[\"NEOBSTOJECI\"]}"))
                .andExpect(status().isBadRequest());
    }

    /* Uvoz je zveza, pravila tekmovanja ostanejo zaklenjena, zavihek pa je prikaz
       in ga lastnik sme urediti kot pri prehodih lige. */
    @Test
    void lastnikUvozenegaTurnirjaSmeUrediti() throws Exception {
        Long turnir = turnir(racun("lastnik-uvoz", Vloga.ORGANIZATOR), VirTekmovanja.STUPA);

        mockMvc.perform(put(url(turnir))
                        .header("Authorization", basic("lastnik-uvoz"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isOk());
    }

    @Test
    void lastnikLigeSkrijeRazdelekTujNe() throws Exception {
        Long liga = liga(racun("lastnik-liga", Vloga.ORGANIZATOR));
        racun("tuj-liga", Vloga.ORGANIZATOR);
        String url = "/api/v1/lige/" + liga + "/zanimivosti/nastavitve";

        mockMvc.perform(put(url)
                        .header("Authorization", basic("tuj-liga"))
                        .contentType(MediaType.APPLICATION_JSON).content(SKRIJ_KLUBI))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url)
                        .header("Authorization", basic("lastnik-liga"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skrite\":[\"NA_NOZ\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skrite[0]").value("NA_NOZ"));
    }

    // ---------- Pomozno ----------

    private static String url(Long turnir) {
        return "/api/v1/turnirji/" + turnir + "/zanimivosti/nastavitve";
    }

    private Long turnir(Uporabnik lastnik, VirTekmovanja vir) {
        Turnir turnir = new Turnir();
        turnir.setIme("Zanimivosti turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnir.setUstvaril(lastnik);
        turnir.setVir(vir);
        return turnirRepozitorij.save(turnir).getId();
    }

    private Long liga(Uporabnik lastnik) {
        Long id = ligaStoritev.ustvari(new LigaVnos("Zanimivosti liga", "2026/27",
                SpolKategorija.MESANO, FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false,
                RavenTekmovanja.KLUBSKO, false, null, null, null)).id();
        Liga liga = ligaRepozitorij.findById(id).orElseThrow();
        liga.setUstvaril(lastnik);
        return id;
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
