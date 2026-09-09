/* Osebni podatki igralcev ne smejo uiti neprijavljenemu obiskovalcu.

   To ni hipoteticno: javni seznam igralcev bere tudi domaca stran
   (pripomocek "1 na 1") in izbirniki ob prijavi na turnir, torej gre cez
   zico ob vsakem obisku. Ce kdo kdaj javno koncno tocko spet obesi na
   poln IgralecDto ali odstrani pravilo iz varnostne verige, mora pasti
   ta test, ne sele revizija po objavi. */
package si.turnirko.kontrolerji;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.modeli.Spol;
import si.turnirko.storitve.IgralciStoritev;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IgralciZasebnostTest {

    /* Polja, ki so osebni podatek in ne smejo nikoli v javni odgovor. */
    private static final String[] OSEBNA_POLJA = {
            "datumRojstva", "email", "telefonskaSt", "naslov", "kraj", "ntzsLicenca"
    };

    @Autowired MockMvc mockMvc;
    @Autowired IgralciStoritev igralciStoritev;

    private IgralecJavniDto igralec;

    @BeforeEach
    void pripravi() {
        igralec = igralciStoritev.ustvari(new IgralecVnos(
                "Zasebni", "Preizkus", Spol.MOSKI, LocalDate.of(2000, 1, 1),
                "zasebni@primer.si", "070 000 000", null, "L-123",
                "SLO", "Skrivna ulica 1", null, null));
    }

    @Test
    void javniSeznamNeVsebujeOsebnihPodatkov() throws Exception {
        String telo = mockMvc.perform(get("/api/v1/igralci"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        preveriBrezOsebnih(telo);
    }

    @Test
    void javniIzpisEnegaIgralcaNeVsebujeOsebnihPodatkov() throws Exception {
        String telo = mockMvc.perform(get("/api/v1/igralci/" + igralec.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        preveriBrezOsebnih(telo);
    }

    /* Starostni pas je izpeljanka iz letnice in gre v javni odgovor NAMERNO
       (organizator po njem filtrira prijave), datum rojstva pa ne. Test drzi
       oboje skupaj: kdor bi pas kdaj zamenjal za datum, mora tu pasti. */
    @Test
    void javniSeznamNosiStarostniPasBrezDatumaRojstva() throws Exception {
        String telo = mockMvc.perform(get("/api/v1/igralci"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(telo.contains("\"starostniPas\""), "javni odgovor nima starostnega pasu: " + telo);
        preveriBrezOsebnih(telo);
    }

    @Test
    void gostDoPodrobnegaSifrantaNeMore() throws Exception {
        mockMvc.perform(get("/api/v1/igralci/podrobno")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/igralci/" + igralec.id() + "/podrobno"))
                .andExpect(status().isUnauthorized());
    }

    /* Preverjamo surovo telo odgovora, ne DTO-ja: zanima nas, kaj gre cez
       zico, tudi ce se vmes zamenja tip. */
    private void preveriBrezOsebnih(String telo) {
        for (String polje : OSEBNA_POLJA) {
            assertFalse(telo.contains(polje),
                    "javni odgovor vsebuje osebni podatek \"" + polje + "\": " + telo);
        }
        assertFalse(telo.contains("zasebni@primer.si"), "javni odgovor vsebuje e-posto");
        assertFalse(telo.contains("Skrivna ulica"), "javni odgovor vsebuje naslov");
        assertFalse(telo.contains("2000-01-01"), "javni odgovor vsebuje datum rojstva");
    }
}
