/* Opozorilo »v bazi ze obstaja podoben igralec« pri vpisu novega (POST
   /igralci/podobni in /igralci/podobni/podrobno).

   Test drzi tri stvari, ki jih obrazec sam ne zagotavlja:
   - kdo sme vprasati (gost ne, organizator javni izpis, admin poln izpis),
   - da organizator iz odgovora NE izve datuma rojstva - ne zapisanega in ne
     tega, ali se vpisani ujema (sicer bi datum ugotovil s poskusanjem),
   - da administrator vidi primerjavo datuma, ki jo opozorilo potrebuje
     (»Miha Fatur z drugim datumom rojstva«). */
package si.turnirko.kontrolerji;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class PodobniIgralciTest {

    private static final String GESLO = "preizkusnoGeslo123";
    private static final String ORGANIZATOR = "podobni-organizator";
    private static final String ADMIN = "podobni-admin";

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
    void gostPodobnihNeVidi() throws Exception {
        mockMvc.perform(post("/api/v1/igralci/podobni").contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "1900-01-01")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno").contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "1900-01-01")))
                .andExpect(status().isUnauthorized());
    }

    /* Primer iz zahteve: isti Miha Fatur z drugim datumom rojstva. Admin dobi
       zapis z datumom in oznako, da se datum razlikuje. */
    @Test
    void adminVidiIstoImeInDrugDatum() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));
        igralec("Maja", "Fatur", LocalDate.of(2010, 1, 13));

        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "1900-01-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].igralec.ime").value("Miha"))
                .andExpect(jsonPath("$[0].igralec.datumRojstva").value("2007-01-09"))
                .andExpect(jsonPath("$[0].ujemanje").value("ISTO"))
                .andExpect(jsonPath("$[0].datum").value("DRUG"))
                .andExpect(jsonPath("$[0].arhiviran").value(false));
    }

    @Test
    void adminVidiTudiEnakDatumInTipkarskoNapako() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));

        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatar", "2007-01-09")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ujemanje").value("PODOBNO"))
                .andExpect(jsonPath("$[0].datum").value("ENAK"));
    }

    /* »Mihael Fatur« z enakim datumom je verjetnejsi dvojnik kot »Miha Fatur«
       z drugim, zato stoji pred njim, cetudi je ime le podobno. */
    @Test
    void adminDobiNajverjetnejsegaNajprej() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(1990, 5, 5));
        igralec("Mihael", "Fatur", LocalDate.of(2007, 1, 9));

        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "2007-01-09")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].igralec.ime").value("Mihael"))
                .andExpect(jsonPath("$[0].datum").value("ENAK"))
                .andExpect(jsonPath("$[1].igralec.ime").value("Miha"));
    }

    /* Organizator vidi isto ime, klub in pas - datuma rojstva pa ne, ne
       zapisanega in ne primerjave z vpisanim. */
    @Test
    void organizatorDobiJavniIzpisBrezDatumaRojstva() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));

        String telo = mockMvc.perform(post("/api/v1/igralci/podobni")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "2007-01-09")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].igralec.ime").value("Miha"))
                .andExpect(jsonPath("$[0].igralec.starostniPas").exists())
                .andExpect(jsonPath("$[0].ujemanje").value("ISTO"))
                .andReturn().getResponse().getContentAsString();

        for (String polje : new String[] {"datumRojstva", "datum\"", "email", "telefonskaSt",
                "naslov", "ntzsLicenca", "2007-01-09"}) {
            assertFalse(telo.contains(polje),
                    "organizatorjev izpis vsebuje \"" + polje + "\": " + telo);
        }
    }

    /* Zadetki javnega izpisa ne smejo biti odvisni od datuma: sicer bi
       organizator z njim iskal datum rojstva znanega igralca. */
    @Test
    void javniIzpisNeOdvisiOdDatuma() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));

        String enakDatum = podobniOrganizatorju(vnos("Miha", "Fatur", "2007-01-09"));
        String drugDatum = podobniOrganizatorju(vnos("Miha", "Fatur", "1900-01-01"));
        String brezDatuma = podobniOrganizatorju("{\"ime\":\"Miha\",\"priimek\":\"Fatur\"}");

        assertTrue(enakDatum.equals(drugDatum) && drugDatum.equals(brezDatuma),
                "javni izpis se razlikuje po datumu: " + enakDatum + " / " + drugDatum
                        + " / " + brezDatuma);
    }

    @Test
    void organizatorPodrobnegaIzpisaNeSme() throws Exception {
        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "2007-01-09")))
                .andExpect(status().isForbidden());
    }

    @Test
    void arhiviranIgralecJeZadetekInJeOznacen() throws Exception {
        Igralec arhiviran = igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));
        arhiviran.setArhiviran(true);
        igralecRepozitorij.save(arhiviran);

        mockMvc.perform(post("/api/v1/igralci/podobni")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Miha", "Fatur", "2007-01-09")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].arhiviran").value(true));
    }

    @Test
    void brezPodobnegaVrneSeprazenSeznam() throws Exception {
        igralec("Miha", "Fatur", LocalDate.of(2007, 1, 9));

        mockMvc.perform(post("/api/v1/igralci/podobni")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Zdenka", "Kovacic", "2007-01-09")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /* Pri pogostem priimku opozorilo ne sme zapolniti zaslona. */
    @Test
    void zadetkovJeNajvecDesetInNajmocnejsiPrvi() throws Exception {
        for (int i = 0; i < 12; i++) {
            igralec("Janez", "Novak", LocalDate.of(1980 + i, 3, 3));
        }
        mockMvc.perform(post("/api/v1/igralci/podobni/podrobno")
                        .header("Authorization", basic(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vnos("Janez", "Novak", "1985-03-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].igralec.datumRojstva").value("1985-03-03"))
                .andExpect(jsonPath("$[0].datum").value("ENAK"));
    }

    @Test
    void prazenVpisJeNapakaZahteve() throws Exception {
        mockMvc.perform(post("/api/v1/igralci/podobni")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ime\":\" \",\"priimek\":\"Fatur\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Pomozno ----------

    private String podobniOrganizatorju(String telo) throws Exception {
        return mockMvc.perform(post("/api/v1/igralci/podobni")
                        .header("Authorization", basic(ORGANIZATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(telo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String vnos(String ime, String priimek, String datum) {
        return "{\"ime\":\"" + ime + "\",\"priimek\":\"" + priimek
                + "\",\"datumRojstva\":\"" + datum + "\"}";
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
