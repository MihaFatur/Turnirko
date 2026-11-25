/* Razrez celice na umetni sliki (10 px/mm): stevka cez crto ostane cela (F2.4),
   poteza sosednje celice ni nasa, zlati nabor pa ne shrani nicesar dlje od pasu crte
   (zasebnost: rocno ime pod Rezultatom, varnostni pregled V4, 24. 11. 2026). */
package si.turnirko.branje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RazrezCeliceTest {

    /* Celica z notranjostjo (65, 55)-(236, 136): crte 3 px, 2 px pred notranjostjo
       (kot jih najde CeliceListka). */
    private static final CeliceListka.Celica CELICA = new CeliceListka.Celica(65, 55, 236, 136);

    private static Temnost listekSCelico() {
        Temnost t = new Temnost(300, 200);
        pravokotnik(t, 60, 50, 241, 52);     // zgoraj
        pravokotnik(t, 60, 139, 241, 141);   // spodaj
        pravokotnik(t, 60, 50, 62, 141);     // levo
        pravokotnik(t, 239, 50, 241, 141);   // desno
        return t;
    }

    private static void pravokotnik(Temnost t, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                t.t[y * t.sirina + x] = 0.9f;
            }
        }
    }

    @Test
    void stevkaCezCrtoOstaneEnaPoteza() {
        Temnost t = listekSCelico();
        pravokotnik(t, 150, 38, 154, 120);  // »1«, ki sega 1,2 mm nad zgornjo crto
        RazrezCelice.Vsebina v = RazrezCelice.razrezi(t, CELICA);
        assertFalse(v.prazna());
        assertEquals(1, v.skupin(), "ena poteza, crta je ne razdeli");
        List<Integer> tocke = RazrezCelice.tockeZapisa(t, CELICA);
        // poteza je ohranjena tudi v pasu crte (precka jo), crta sama pa ne
        assertTrue(tocke.contains(51 * t.sirina + 152), "poteza cez crto ostane povezana");
        assertFalse(tocke.contains(51 * t.sirina + 100), "crta ni del zapisa");
        assertFalse(tocke.contains(80 * t.sirina + 62), "leva crta ni del zapisa");
    }

    @Test
    void potezaSosedaNiNasa() {
        Temnost t = listekSCelico();
        // stevka zgornje celice, ki seze 0,6 mm v nas rob (vecinoma zunaj)
        pravokotnik(t, 150, 10, 154, 57);
        // in nasa stevka drugje v celici
        pravokotnik(t, 100, 80, 104, 125);
        List<Integer> tocke = RazrezCelice.tockeZapisa(t, CELICA);
        assertFalse(tocke.isEmpty());
        assertTrue(tocke.stream().noneMatch(i -> i % t.sirina >= 150 && i % t.sirina <= 154),
                "poteza sosednje celice ni v zapisu");
        assertEquals(1, RazrezCelice.razrezi(t, CELICA).skupin());
    }

    @Test
    void zlatiNaborNeShraniNicesarDljeOdPasuCrte() {
        Temnost t = listekSCelico();
        // stevka, ki seze 1,5 mm cez spodnjo crto (proti rocnemu imenu sodnika)
        pravokotnik(t, 150, 70, 154, 157);
        List<Integer> tocke = RazrezCelice.tockeZapisa(t, CELICA);
        int e = RazrezCelice.ZUNAJ_ZA_NABOR;
        assertFalse(tocke.isEmpty());
        for (int i : tocke) {
            int x = i % t.sirina;
            int y = i / t.sirina;
            assertTrue(y <= CELICA.y1() + e && y >= CELICA.y0() - e && x >= CELICA.x0() - e && x <= CELICA.x1() + e,
                    "tocka (" + x + ", " + y + ") je dlje od pasu crte");
        }
        // bralnik sam vidi celo potezo (ena skupina, ne prazna)
        assertFalse(RazrezCelice.razrezi(t, CELICA).prazna());
    }

    @Test
    void primerjalnaSirinaJeSirinaPriSirokiCeliciInVisinaPriOzki() {
        // siroka nizka celica (stari listek): meje kot prej
        assertEquals(332, RazrezCelice.primerjalnaSirina(332, 48), 1e-9);
        // ozka visoka (nov listek): sirina, kot bi jo imela siroka celica iste visine
        assertEquals(RazrezCelice.RAZMERJE_SIROKE_CELICE * 76, RazrezCelice.primerjalnaSirina(156, 76), 1e-9);
        assertTrue(RazrezCelice.ozka(156, 76));
        assertFalse(RazrezCelice.ozka(332, 48));
    }
}
