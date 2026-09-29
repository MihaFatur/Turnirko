/* Organizatorski pregled skozi pravo varnostno verigo (HTTP Basic).

   Pregled je GET, »GET je javen« pa je splosno pravilo verige: ce bi pravilo za
   /api/v1/organizator stalo za njim, bi gost dobil 200 in videl porabo paketa
   in lastnino tujega organizatorja. Storitveni test (OrganizatorPregledTest)
   klice storitev z rocno postavljenim kontekstom in tega ne pokrije. */
package si.turnirko.kontrolerji;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizatorPregledDostopTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String PREGLED = "/api/v1/organizator/pregled";

    @Autowired MockMvc mockMvc;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired PasswordEncoder kodirnik;

    @Test
    void gostPregledaNeVidi() throws Exception {
        mockMvc.perform(get(PREGLED)).andExpect(status().isUnauthorized());
    }

    @Test
    void igralecInAdminPregledaNimataSamoOrganizator() throws Exception {
        racun("igralec-pregled@test.si", Vloga.IGRALEC, StatusRacuna.CAKA);
        racun("admin-pregled@test.si", Vloga.ADMIN, StatusRacuna.POTRJEN);

        mockMvc.perform(get(PREGLED).header("Authorization", basic("igralec-pregled@test.si")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PREGLED).header("Authorization", basic("admin-pregled@test.si")))
                .andExpect(status().isForbidden());
    }

    /* Odgovor nosi ime in klub iz registracije, mejo paketa in samo lastne
       turnirje - datumi kot ISO niz, ne kot tabela stevil. */
    @Test
    void organizatorVidiSvojPregled() throws Exception {
        Uporabnik jaz = racun("organizator-pregled@test.si", Vloga.ORGANIZATOR, StatusRacuna.POTRJEN);
        jaz.setPrijavljenoIme("Marko");
        jaz.setPrijavljeniPriimek("Kovač");
        Narocnina n = new Narocnina(jaz, Paket.ORGANIZATOR_PLUS);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        Turnir t = new Turnir();
        t.setIme("Moj turnir");
        t.setUstvaril(jaz);
        t.setDatumZacetka(java.time.LocalDate.now().plusDays(3));
        turnirRepozitorij.save(t);

        mockMvc.perform(get(PREGLED).header("Authorization", basic("organizator-pregled@test.si")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ime").value("Marko Kovač"))
                .andExpect(jsonPath("$.paket").value("ORGANIZATOR_PLUS"))
                .andExpect(jsonPath("$.turnirji.meja").value(5))
                .andExpect(jsonPath("$.turnirji.uporabljeno").value(1))
                .andExpect(jsonPath("$.lige.meja").value(3))
                .andExpect(jsonPath("$.tekmovanja[0].ime").value("Moj turnir"))
                .andExpect(jsonPath("$.tekmovanja[0].datumZacetka")
                        .value(java.time.LocalDate.now().plusDays(3).toString()))
                .andExpect(jsonPath("$.naslednje.imeTekmovanja").value("Moj turnir"));
    }

    /* Glava vmesnika bere ime organizatorja iz /auth/me, ne e-poste. */
    @Test
    void prijavljeniOrganizatorDobiPolnoImeZaGlavo() throws Exception {
        Uporabnik jaz = racun("glava@test.si", Vloga.ORGANIZATOR, StatusRacuna.POTRJEN);
        jaz.setPrijavljenoIme("Marko");
        jaz.setPrijavljeniPriimek("Kovač");
        uporabnikRepozitorij.save(jaz);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", basic("glava@test.si")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polnoIme").value("Marko Kovač"));
    }

    private Uporabnik racun(String prijava, Vloga vloga, StatusRacuna statusRacuna) {
        Uporabnik u = new Uporabnik(prijava, kodirnik.encode(GESLO), vloga);
        u.setStatus(statusRacuna);
        return uporabnikRepozitorij.save(u);
    }

    private static String basic(String prijava) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((prijava + ":" + GESLO).getBytes(StandardCharsets.UTF_8));
    }
}
