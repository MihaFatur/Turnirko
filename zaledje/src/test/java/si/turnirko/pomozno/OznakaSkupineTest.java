/* Oznaka skupine po vrsti (OznakaSkupine).

   Domenska pravila, ki jih ta test varuje:
   - skupine so oštevilčene od 1 do števila skupin (odločitev lastnika, 23. 11. 2026);
   - SV regija ima znotraj nivoja še vedno črke, po slovenski abecedi brez Č, Š, Ž:
     po P pride R, ne Q; do 16 skupin je enako kot v angleški abecedi;
   - ko črk zmanjka, se oznaka nadaljuje z ZA, ZB …, vrstni red po abecedi pa
     ostane enak vrstnemu redu skupin;
   - nobena oznaka ne preseže 3 znakov, kolikor jih dovoli stolpec skupina.oznaka. */
package si.turnirko.pomozno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import si.turnirko.izjeme.DomenskaIzjema;

class OznakaSkupineTest {

    @Test
    void skupineSoOstevilceneOdEne() {
        assertEquals("1", OznakaSkupine.zaIndeks(0));
        assertEquals("2", OznakaSkupine.zaIndeks(1));
        assertEquals("17", OznakaSkupine.zaIndeks(16));
        assertEquals("999", OznakaSkupine.zaIndeks(998));
    }

    @Test
    void stevilkeSoEnolicneInNajvecTriZnake() {
        Set<String> videne = new HashSet<>();
        for (int i = 0; i < OznakaSkupine.NAJVEC; i++) {
            String oznaka = OznakaSkupine.zaIndeks(i);
            assertTrue(oznaka.length() <= 3, oznaka);
            assertTrue(videne.add(oznaka), "podvojena oznaka " + oznaka);
        }
    }

    @Test
    void prevecSkupinInNegativenIndeks() {
        assertThrows(DomenskaIzjema.class, () -> OznakaSkupine.zaIndeks(OznakaSkupine.NAJVEC));
        assertThrows(IllegalArgumentException.class, () -> OznakaSkupine.zaIndeks(-1));
    }

    // ---------------- črke (SV regija) ----------------

    @Test
    void poPJeR_neQ() {
        assertEquals("P", OznakaSkupine.crkaPoVrsti(15));
        assertEquals("R", OznakaSkupine.crkaPoVrsti(16));
        assertEquals("S", OznakaSkupine.crkaPoVrsti(17));
    }

    @Test
    void celotnaAbecedaBrezSumnikovInTujihCrk() {
        StringBuilder vse = new StringBuilder();
        for (int i = 0; i < 22; i++) {
            vse.append(OznakaSkupine.crkaPoVrsti(i));
        }
        assertEquals("ABCDEFGHIJKLMNOPRSTUVZ", vse.toString());
    }

    @Test
    void doSestnajstSkupinJeKotAngleskaAbeceda() {
        for (int i = 0; i < 16; i++) {
            assertEquals(String.valueOf((char) ('A' + i)), OznakaSkupine.crkaPoVrsti(i));
        }
    }

    @Test
    void zaZJeZA() {
        assertEquals("Z", OznakaSkupine.crkaPoVrsti(21));
        assertEquals("ZA", OznakaSkupine.crkaPoVrsti(22));
        assertEquals("ZB", OznakaSkupine.crkaPoVrsti(23));
        assertEquals("ZZ", OznakaSkupine.crkaPoVrsti(43));
        assertEquals("ZZA", OznakaSkupine.crkaPoVrsti(44));
    }

    @Test
    void vrstniRedCrkPoAbecediJeVrstniRedSkupin() {
        List<String> oznake = new ArrayList<>();
        for (int i = 0; i < OznakaSkupine.NAJVEC_CRK; i++) {
            oznake.add(OznakaSkupine.crkaPoVrsti(i));
        }
        assertEquals(oznake, oznake.stream().sorted().toList(),
                "ORDER BY oznaka mora dati skupine po vrsti");
    }

    @Test
    void crkeNajvecTriZnakeInPrevecJeDomenskaNapaka() {
        for (int i = 0; i < OznakaSkupine.NAJVEC_CRK; i++) {
            assertTrue(OznakaSkupine.crkaPoVrsti(i).length() <= 3);
        }
        assertThrows(DomenskaIzjema.class, () -> OznakaSkupine.crkaPoVrsti(OznakaSkupine.NAJVEC_CRK));
    }
}
