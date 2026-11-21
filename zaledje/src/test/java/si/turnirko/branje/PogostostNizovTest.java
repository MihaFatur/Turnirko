/* Pogostost izidov nizov (F2.1): zgodovina loci pogoste izide od redkih, redek
   izid pa nikoli ni nemogoc. */
package si.turnirko.branje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PogostostNizovTest {

    private final PogostostNizov p = PogostostNizov.VGRAJENA;

    @Test
    void brezZgodovineSoVsiNiziEnakovredni() {
        assertEquals(1, p.utez(11, 1, 0));
        assertEquals(1, p.utez(25, 23, 0));
    }

    @Test
    void pogostIzidImaVecjoUtezOdRedkega() {
        assertTrue(p.utez(11, 7, 0.5) > 2 * p.utez(11, 1, 0.5));
        assertTrue(p.utez(13, 11, 0.5) > 4 * p.utez(18, 16, 0.5));
        // utezi 11:a so v povprecju 1 (razmerje do prazne vrstice ostane kot brez zgodovine)
        double vsota = 0;
        for (int a = 0; a <= 9; a++) {
            vsota += p.utez(11, a, 1);
        }
        assertEquals(10, vsota, 1e-9);
    }

    @Test
    void redekInNevidenIzidNistaNemogoca() {
        assertTrue(p.verjetnost(11, 0) > 0);
        assertTrue(p.verjetnost(30, 28) > 0, "podaljsek brez stetja dobi verjetnost iz glajenja");
        assertTrue(p.verjetnost(30, 28) < p.verjetnost(25, 23));
    }

    @Test
    void neveljavenNizNimaVerjetnosti() {
        assertEquals(0, p.verjetnost(11, 10));
        assertEquals(0, p.verjetnost(13, 10));
        assertEquals(0, p.verjetnost(10, 8));
    }

    @Test
    void stetjeIzBazeZamenjaObliko() {
        long[] samo117 = new long[10];
        samo117[7] = 100_000;
        PogostostNizov enostranska = new PogostostNizov(samo117, new long[0]);
        assertEquals(100_000, enostranska.nizov());
        assertTrue(enostranska.verjetnost(11, 7) > 0.99);
        assertTrue(enostranska.verjetnost(11, 3) > 0, "glajenje");
    }
}
