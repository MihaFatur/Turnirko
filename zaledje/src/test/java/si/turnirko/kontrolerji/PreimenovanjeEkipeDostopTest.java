/* Preimenovanje ekipe (PUT /lige/ekipe/{id}/ime in /dogodki/ekipe/{id}/ime)
   skozi varnostno verigo: gost in igralec ne smeta, organizator, ki
   tekmovanja ni ustvaril, tudi ne, admin pa ime spremeni. Pravila imena so v
   PreimenovanjeEkipeTest; tu je pot in to, da je veriga ne pogoltne ali pusti
   skozi (pot je PUT, zato je ne pokrije splosno pravilo "GET je javen"). */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.EkipeDogodkaStoritev;
import si.turnirko.storitve.LigaStoritev;
import si.turnirko.storitve.TurnirjiStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreimenovanjeEkipeDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String TELO = "{\"ime\":\"Zeleni zmaji\"}";

    @Autowired MockMvc mockMvc;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired EkipeDogodkaStoritev ekipeDogodka;
    @Autowired TurnirjiStoritev turnirjiStoritev;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    private record EkipaLige(Long liga, Long ekipa) {}

    @Test
    void gostEkipeLigeNePreimenuje() throws Exception {
        EkipaLige e = ekipaLige();

        mockMvc.perform(put("/api/v1/lige/ekipe/" + e.ekipa() + "/ime")
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isUnauthorized());

        assertEquals("Kuhinja", ligaStoritev.ekipe(e.liga()).get(0).prikazanoIme(), "ime je ostalo");
    }

    @Test
    void igralecEkipeLigeNePreimenuje() throws Exception {
        EkipaLige e = ekipaLige();
        racun("igralec-ekipa", Vloga.IGRALEC);

        mockMvc.perform(put("/api/v1/lige/ekipe/" + e.ekipa() + "/ime")
                        .header("Authorization", basic("igralec-ekipa"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isForbidden());
    }

    @Test
    void tujOrganizatorEkipeLigeNePreimenuje() throws Exception {
        EkipaLige e = ekipaLige();
        racun("tuj-organizator-ekipa", Vloga.ORGANIZATOR);

        mockMvc.perform(put("/api/v1/lige/ekipe/" + e.ekipa() + "/ime")
                        .header("Authorization", basic("tuj-organizator-ekipa"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isForbidden());

        assertEquals("Kuhinja", ligaStoritev.ekipe(e.liga()).get(0).prikazanoIme(), "ime je ostalo");
    }

    @Test
    void adminPreimenujeEkipoLige() throws Exception {
        EkipaLige e = ekipaLige();
        racun("admin-ekipa", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/lige/ekipe/" + e.ekipa() + "/ime")
                        .header("Authorization", basic("admin-ekipa"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e.ekipa()))
                .andExpect(jsonPath("$.prikazanoIme").value("Zeleni zmaji"));
    }

    /* Trk imena je poslovna napaka s sporocilom (409), ne 500. */
    @Test
    void trkImenaVrneKonflikt() throws Exception {
        EkipaLige e = ekipaLige();
        ligaStoritev.dodajEkipo(e.liga(), new EkipaVnos(null, null, "Pisarna"));
        racun("admin-trk", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/lige/ekipe/" + e.ekipa() + "/ime")
                        .header("Authorization", basic("admin-trk"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"pisarna\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void gostEkipeDogodkaNePreimenuje() throws Exception {
        Long ekipa = ekipaDogodka();

        mockMvc.perform(put("/api/v1/dogodki/ekipe/" + ekipa + "/ime")
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tujOrganizatorEkipeDogodkaNePreimenuje() throws Exception {
        Long ekipa = ekipaDogodka();
        racun("tuj-organizator-dogodek", Vloga.ORGANIZATOR);

        mockMvc.perform(put("/api/v1/dogodki/ekipe/" + ekipa + "/ime")
                        .header("Authorization", basic("tuj-organizator-dogodek"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPreimenujeEkipoDogodka() throws Exception {
        Long ekipa = ekipaDogodka();
        racun("admin-dogodek", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/dogodki/ekipe/" + ekipa + "/ime")
                        .header("Authorization", basic("admin-dogodek"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ekipa))
                .andExpect(jsonPath("$.prikazanoIme").value("Zeleni zmaji"));
    }

    /* Pot za ekipo dogodka ne sme pustiti ekipe lige skozi. */
    @Test
    void potDogodkaNePoznaEkipeLige() throws Exception {
        EkipaLige e = ekipaLige();
        racun("admin-napacna-pot", Vloga.ADMIN);

        mockMvc.perform(put("/api/v1/dogodki/ekipe/" + e.ekipa() + "/ime")
                        .header("Authorization", basic("admin-napacna-pot"))
                        .contentType(MediaType.APPLICATION_JSON).content(TELO))
                .andExpect(status().isNotFound());
    }

    // ---------- Pomozno ----------

    /* Liga in ekipni turnir brez lastnika (ustvarjena brez prijave): tuj
       organizator ju ne sme urejati, admin sme vse. */
    private EkipaLige ekipaLige() {
        Long liga = ligaStoritev.ustvari(new LigaVnos("Dostop ekipa", "2026/27", SpolKategorija.MESANO,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false,
                RavenTekmovanja.KLUBSKO, false, null, null, null)).id();
        return new EkipaLige(liga,
                ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja")).id());
    }

    private Long ekipaDogodka() {
        Turnir turnir = new Turnir();
        turnir.setIme("Dostop ekipni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnir = turnirRepozitorij.save(turnir);
        Dogodek dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(),
                new DogodekVnos("Ekipno", SpolKategorija.MOSKI, null, 5, null, null,
                        Disciplina.EKIPNO, SistemTekmovanja.IZLOCILNI, null, null,
                        FormatSrecanja.EKIPNI_DP, null, null));
        return ekipeDogodka.dodajEkipo(dogodek.getId(), new EkipaVnos(null, null, "Kuhinja")).id();
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        /* Potrjen igralec mora biti povezan z igralcem (CHECK v shemi), zato
           igralec ostane v cakanju - za pot skozi verigo je pomembna vloga. */
        racun(prijavnoIme, vloga, vloga == Vloga.IGRALEC ? StatusRacuna.CAKA : StatusRacuna.POTRJEN);
    }

    private void racun(String prijavnoIme, Vloga vloga, StatusRacuna status) {
        Uporabnik u = new Uporabnik(prijavnoIme, kodirnik.encode(GESLO), vloga);
        u.setStatus(status);
        uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijavnoIme) {
        String par = prijavnoIme + ":" + GESLO;
        return "Basic " + Base64.getEncoder().encodeToString(par.getBytes(StandardCharsets.UTF_8));
    }
}
