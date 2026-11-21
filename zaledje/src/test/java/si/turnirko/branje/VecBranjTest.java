/* Razlicice stevke za veckratno branje (F2.2): premik in zasuk ohranita stevko
   (crnilo ostane, sredisce se ne odpelje), izvirnik pa je med razlicicami. */
package si.turnirko.branje;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

class VecBranjTest {

    private static float[] stevka() {
        // navpicna crta (enica) v sredini slike 28 x 28
        float[] v = new float[28 * 28];
        for (int y = 4; y < 24; y++) {
            v[y * 28 + 13] = 1;
            v[y * 28 + 14] = 1;
        }
        return v;
    }

    private static double crnilo(float[] v) {
        double s = 0;
        for (float x : v) {
            s += x;
        }
        return s;
    }

    @Test
    void zasukZaNicJeIzvirnik() {
        float[] v = stevka();
        assertArrayEquals(v, VecBranj.zasukaj(v, 0), 1e-6f);
    }

    @Test
    void premikInZasukOhranitaCrnilo() {
        float[] v = stevka();
        assertEquals(crnilo(v), crnilo(VecBranj.premakni(v, 1)), 1e-6);
        assertEquals(crnilo(v), crnilo(VecBranj.zasukaj(v, Math.toRadians(4))), 0.05 * crnilo(v));
        assertEquals(1, VecBranj.premakni(v, 1)[10 * 28 + 15], 1e-6);
    }

    @Test
    void verjetnostiSoPorazdelitev() {
        float[] p = VecBranj.verjetnosti(MrezaStevk.nakljucna(new Random(1)), stevka(), 1.5);
        double vsota = 0;
        for (float x : p) {
            vsota += x;
        }
        assertEquals(10, p.length);
        assertEquals(1, vsota, 1e-5);
    }
}
