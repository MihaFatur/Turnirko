/* Slike in logotip tekmovanja skozi pravo varnostno verigo (V54).

   Nalaganje in knjiznica sta urejevalceva, slika sama pa je javna in se hrani
   za vedno (zgostitev je naslov) - telefon v dvorani jo prenese enkrat. Pravila
   lastnistva preverja SlikeStoritevTest. */
package si.turnirko.kontrolerji;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.modeli.Liga;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.TestneSlike;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.storitve.OmejevalnikPoskusov;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SlikeDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";

    @Autowired MockMvc mockMvc;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired PasswordEncoder kodirnik;
    @Autowired OmejevalnikPoskusov omejevalnik;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void pocisti() {
        omejevalnik.pocistiVse();
    }

    @Test
    void nalagaSamoUrejevalec() throws Exception {
        racun("igralec-slike", Vloga.IGRALEC);

        mockMvc.perform(multipart("/api/v1/slike").file(datoteka(300)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/slike").file(datoteka(300))
                        .header("Authorization", basic("igralec-slike")))
                .andExpect(status().isForbidden());
    }

    /* Knjiznica je GET, a ni javna - neobjavljene slike niso za goste. */
    @Test
    void knjiznicaNiJavna() throws Exception {
        racun("igralec-knjiznica", Vloga.IGRALEC);

        mockMvc.perform(get("/api/v1/slike")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/slike").header("Authorization", basic("igralec-knjiznica")))
                .andExpect(status().isForbidden());
    }

    /* Organizator nalozi sliko; gost jo prebere z dolgim predpomnjenjem, ob
       ponovnem vprasanju z oznako pa dobi 304. */
    @Test
    void slikaJeJavnaInSeHraniZaVedno() throws Exception {
        racun("org-slike", Vloga.ORGANIZATOR);
        String zgostitev = nalozi("org-slike", 500);

        mockMvc.perform(get("/api/v1/slike").header("Authorization", basic("org-slike")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zgostitev").value(zgostitev));

        mockMvc.perform(get("/api/v1/slike/" + zgostitev + "?velikost=TISK"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("ETag", "\"" + zgostitev + "-tisk\""));
        mockMvc.perform(get("/api/v1/slike/" + zgostitev))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + zgostitev + "-zaslon\""));
        mockMvc.perform(get("/api/v1/slike/" + zgostitev + "?velikost=DELJENJE")
                        .header("If-None-Match", "\"" + zgostitev + "-deljenje\""))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/v1/slike/" + "a".repeat(64)))
                .andExpect(status().isNotFound());
    }

    @Test
    void neslikaSeZavrneZRazlago() throws Exception {
        racun("org-neslika", Vloga.ORGANIZATOR);

        mockMvc.perform(multipart("/api/v1/slike")
                        .file(new MockMultipartFile("datoteka", "logo.png", "image/png",
                                "<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", basic("org-neslika")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Datoteka ni slika PNG ali JPEG."));
    }

    /* Lastnik izbere logotip; liga in turnir ga nosita v javnem izpisu (tudi
       seznamu), tuj organizator ga ne more nastaviti ne odstraniti. */
    @Test
    void logotipLigeInTurnirjaVJavnemIzpisu() throws Exception {
        Uporabnik lastnik = racun("lastnik-logotip", Vloga.ORGANIZATOR);
        racun("tuj-logotip", Vloga.ORGANIZATOR);
        Long liga = liga(lastnik);
        Long turnir = turnir(lastnik);
        String zgostitev = nalozi("lastnik-logotip", 400);
        long idSlika = idSlike("lastnik-logotip", zgostitev);
        String vnos = "{\"idSlika\":" + idSlika + "}";

        mockMvc.perform(put("/api/v1/lige/" + liga + "/logotip")
                        .header("Authorization", basic("tuj-logotip"))
                        .contentType(MediaType.APPLICATION_JSON).content(vnos))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/lige/" + liga + "/logotip").contentType(MediaType.APPLICATION_JSON).content(vnos))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/lige/" + liga + "/logotip")
                        .header("Authorization", basic("lastnik-logotip"))
                        .contentType(MediaType.APPLICATION_JSON).content(vnos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zgostitev").value(zgostitev));
        mockMvc.perform(put("/api/v1/turnirji/" + turnir + "/logotip")
                        .header("Authorization", basic("lastnik-logotip"))
                        .contentType(MediaType.APPLICATION_JSON).content(vnos))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/lige/" + liga))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logotip.zgostitev").value(zgostitev))
                .andExpect(jsonPath("$.logotip.sirina").value(400));
        mockMvc.perform(get("/api/v1/turnirji/" + turnir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logotip.zgostitev").value(zgostitev));
        mockMvc.perform(get("/api/v1/turnirji"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + turnir + ")].logotip.zgostitev").value(zgostitev));

        mockMvc.perform(delete("/api/v1/lige/" + liga + "/logotip").header("Authorization", basic("tuj-logotip")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/lige/" + liga + "/logotip").header("Authorization", basic("lastnik-logotip")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/lige/" + liga))
                .andExpect(jsonPath("$.logotip").doesNotExist());
    }

    // ---------- Pomozno ----------

    private String nalozi(String prijavnoIme, int sirina) throws Exception {
        String odgovor = mockMvc.perform(multipart("/api/v1/slike").file(datoteka(sirina))
                        .header("Authorization", basic(prijavnoIme)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(odgovor).get("zgostitev").asText();
    }

    private long idSlike(String prijavnoIme, String zgostitev) throws Exception {
        String odgovor = mockMvc.perform(get("/api/v1/slike").header("Authorization", basic(prijavnoIme)))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode slika : json.readTree(odgovor)) {
            if (slika.get("zgostitev").asText().equals(zgostitev)) {
                return slika.get("id").asLong();
            }
        }
        throw new AssertionError("slike ni v knjiznici");
    }

    private static MockMultipartFile datoteka(int sirina) {
        return new MockMultipartFile("datoteka", "logotip.png", "image/png", TestneSlike.png(sirina, 200));
    }

    private Long liga(Uporabnik lastnik) {
        Liga l = new Liga();
        l.setIme("Liga z logotipom");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        return ligaRepozitorij.save(l).getId();
    }

    private Long turnir(Uporabnik lastnik) {
        Turnir t = new Turnir();
        t.setIme("Turnir z logotipom");
        t.setDatumZacetka(LocalDate.now());
        t.setRaven(RavenTekmovanja.KLUBSKO);
        t.setUstvaril(lastnik);
        return turnirRepozitorij.save(t).getId();
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
