/* Testi prepoznavanja vrnitve po odsotnosti - cisto zaporedje casov, brez baze.
   Pravilo je isto za redni obracun in za ponovni preracun, zato mora biti
   natancno doloceno tudi v robnih primerih (tekma brez datuma, vnos za nazaj). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SledilnikVrnitveTest {

    private static LocalDateTime dne(int leto, int mesec, int dan) {
        return LocalDate.of(leto, mesec, dan).atStartOfDay();
    }

    /* Nov igralec ni "vrnjen" - njegovo negotovost pokrijeta ze pribitka za
       manj kot 10 oziroma 30 tekem. */
    @Test
    void prvaTekmaNiVrnitev() {
        SledilnikVrnitve sledilnik = new SledilnikVrnitve();
        assertFalse(sledilnik.obracunaj(dne(2026, 3, 1)));
        assertEquals(dne(2026, 3, 1), sledilnik.zadnja());
    }

    /* Redno igranje: presledek pod letom dni ni vrnitev. */
    @Test
    void krajsiPremorNiVrnitev() {
        SledilnikVrnitve sledilnik = new SledilnikVrnitve();
        sledilnik.obracunaj(dne(2025, 1, 10));
        assertFalse(sledilnik.obracunaj(dne(2025, 12, 20)));
        assertEquals(0, sledilnik.preostanek());
    }

    /* Natanko na meji dvanajstih mesecev se vrnitev se NE sprozi - sele cez. */
    @Test
    void mejaJeVecKotDvanajstMesecev() {
        SledilnikVrnitve naMeji = new SledilnikVrnitve();
        naMeji.obracunaj(dne(2025, 1, 10));
        assertFalse(naMeji.obracunaj(dne(2026, 1, 10)));

        SledilnikVrnitve cezMejo = new SledilnikVrnitve();
        cezMejo.obracunaj(dne(2025, 1, 10));
        assertTrue(cezMejo.obracunaj(dne(2026, 1, 11)));
    }

    /* Po vrnitvi ima igralec povisan K natanko petnajst tekem, vkljucno s
       tekmo, ki je vrnitev sprozila. */
    @Test
    void povisanKTrajaPetnajstTekem() {
        SledilnikVrnitve sledilnik = new SledilnikVrnitve();
        sledilnik.obracunaj(dne(2024, 5, 1));

        assertTrue(sledilnik.obracunaj(dne(2026, 5, 1)), "tekma vrnitve se steje kot prva");
        assertEquals(TurnirkoRatingStoritev.TEKEM_PO_VRNITVI - 1, sledilnik.preostanek());

        for (int i = 2; i <= TurnirkoRatingStoritev.TEKEM_PO_VRNITVI; i++) {
            assertTrue(sledilnik.obracunaj(dne(2026, 5, 2)), "tekma " + i + " se mora steti");
        }
        assertEquals(0, sledilnik.preostanek());
        assertFalse(sledilnik.obracunaj(dne(2026, 5, 3)), "sestnajsta tekma je ze navadna");
    }

    /* Tekma brez znanega datuma ne sme sprozati vrnitve in ne sme premakniti
       zadnjega termina - sicer bi vsaka uvozena tekma brez datuma naslednjo
       pravo tekmo oznacila za vrnitev po stotih letih. */
    @Test
    void tekmaBrezDatumaNeSprozaVrnitve() {
        SledilnikVrnitve sledilnik = new SledilnikVrnitve();
        assertFalse(sledilnik.obracunaj(VrstaRatinskeTekme.BREZ_DATUMA));
        assertFalse(sledilnik.obracunaj(dne(2026, 1, 1)), "prva znana tekma ni vrnitev");

        SledilnikVrnitve zZnanoZadnjo = new SledilnikVrnitve();
        zZnanoZadnjo.obracunaj(dne(2026, 1, 1));
        zZnanoZadnjo.obracunaj(VrstaRatinskeTekme.BREZ_DATUMA);
        assertEquals(dne(2026, 1, 1), zZnanoZadnjo.zadnja(), "termin ostane zadnji znani");
        assertFalse(zZnanoZadnjo.obracunaj(dne(2026, 2, 1)));
    }

    /* Rezultat se lahko vnese tudi za nazaj; zadnji termin sme samo naprej,
       sicer bi pozen vnos stare tekme naslednjo tekmo naredil za vrnitev. */
    @Test
    void zadnjiTerminSmeSamoNaprej() {
        SledilnikVrnitve sledilnik = new SledilnikVrnitve();
        sledilnik.obracunaj(dne(2026, 6, 1));
        sledilnik.obracunaj(dne(2024, 1, 1)); // pozno vnesena stara tekma
        assertEquals(dne(2026, 6, 1), sledilnik.zadnja());
        assertFalse(sledilnik.obracunaj(dne(2026, 7, 1)));
    }
}
