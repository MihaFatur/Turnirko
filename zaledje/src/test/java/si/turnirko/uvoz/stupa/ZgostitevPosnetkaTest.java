/* Zgostitev posnetka Stupe meri VSEBINO, ne bajtov odgovora.

   Stupa je septembra 2026 spremenila obliko odgovorov (vrstni red kljucev,
   novo prazno polje) in bajtna zgostitev je vse dogodke razglasila za
   spremenjene - prava sprememba (uradna mesta 245/246) se v tem ni locila.
   Spremenjen popravek datuma pa JE sprememba uvoza, ceprav vir ostane isti. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ZgostitevPosnetkaTest {

    private final ObjectMapper json = new ObjectMapper();

    private String k(String besedilo) throws Exception {
        return PosnetekDogodka.kanonicno(json.readTree(besedilo));
    }

    @Test
    void oblikaOdgovoraNeSpremeniZgostitve() throws Exception {
        String prej = k("""
                {"data": [{"id": 7, "name": "Kadeti", "points": 3, "updated_at": "2026-09-13T10:00:00"}]}""");
        // drug vrstni red kljucev, novo prazno polje, 3.0 namesto 3, nov cas posodobitve
        String potem = k("""
                {"data": [{"points": 3.0, "point_restriction_mode": null, "name": "Kadeti",
                           "updated_at": "2026-09-20T08:00:00", "id": 7}]}""");
        assertEquals(prej, potem);
    }

    @Test
    void spremembaVsebineSpremeniZgostitev() throws Exception {
        assertNotEquals(k("""
                {"data": [{"id": 7, "rank": 1}, {"id": 8, "rank": 2}]}"""), k("""
                {"data": [{"id": 7, "rank": 1}, {"id": 8, "rank": 3}]}"""));
        assertNotEquals(k("""
                {"data": [{"id": 7, "rank": null}]}"""), k("""
                {"data": [{"id": 7, "rank": 4}]}"""), "vrednost, ki se pojavi, je sprememba");
    }

    @Test
    void popravekDatumaJeDelZgostitve() {
        PopravkiStupe popravki = PopravkiStupe.privzeti();
        // 61: dogodek s popravljenim datumom; 169: casi tekem 1. SNTL zensk 2024/25
        assertTrue(popravki.opisZa(61, List.of()).contains("2025-05-31"));
        assertTrue(popravki.opisZa(169, List.of(42749L)).contains("2024-10-05T17:00"));
        assertEquals("", popravki.opisZa(999_999, List.of(1L, 2L)), "dogodek brez popravkov ne prinese nicesar");
        assertEquals(popravki.opisZa(169, List.of(42751L, 42749L)), popravki.opisZa(169, List.of(42749L, 42751L)),
                "vrstni red tekem v posnetku ne sme spremeniti zgostitve");
    }
}
