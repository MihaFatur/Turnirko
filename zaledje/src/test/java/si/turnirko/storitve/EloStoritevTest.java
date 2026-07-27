/* Testi cistega ELO izracuna - brez baze in brez Springa.
   Pokrivajo tri izboljsave: dinamicni K, nicvsotno zaokrozevanje (Math.rint)
   in set-margino po presenecenju. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EloStoritevTest {

    private final EloStoritev elo = new EloStoritev();

    /* Dinamicni K: velik za novinca, manjsi za ustaljenega igralca. */
    @Test
    void kFaktorPadaSStevilomTekem() {
        assertEquals(48, elo.kFaktor(0));
        assertEquals(48, elo.kFaktor(9));
        assertEquals(32, elo.kFaktor(10));
        assertEquals(32, elo.kFaktor(29));
        assertEquals(20, elo.kFaktor(30));
        assertEquals(20, elo.kFaktor(100));
    }

    @Test
    void novincaEnakRatingGladkaZmaga() {
        // dva novinca (0 tekem -> K=48), gladka zmaga 3:0 pri najboljsem od 5
        EloStoritev.IzracunElo izracun = elo.izracunaj(1000, 1000, 0, 0, true, 3, 0, 5);
        assertEquals(1028, izracun.ratingPo1());
        assertEquals(972, izracun.ratingPo2());
        assertEquals(28, izracun.sprememba1());
        assertEquals(-28, izracun.sprememba2());
    }

    @Test
    void tesnaZmagaPrineseManjKotGladka() {
        EloStoritev.IzracunElo tesna = elo.izracunaj(1000, 1000, 0, 0, true, 3, 2, 5);
        EloStoritev.IzracunElo gladka = elo.izracunaj(1000, 1000, 0, 0, true, 3, 0, 5);
        assertTrue(tesna.sprememba1() < gladka.sprememba1(),
                "tesna zmaga mora prinesti manj tock kot gladka");
    }

    /* Nicvsotno zaokrozevanje: pri ENAKEM K gubitnik izgubi natanko toliko,
       kot zmagovalec pridobi - tudi pri izenaceni napovedi (rint, ne round). */
    @Test
    void spremembiStaNicvsotniPriEnakemK() {
        for (int[] izid : new int[][] { {3, 0}, {3, 1}, {3, 2} }) {
            EloStoritev.IzracunElo iz = elo.izracunaj(1000, 1000, 0, 0, true, izid[0], izid[1], 5);
            assertEquals(iz.sprememba1(), -iz.sprememba2(),
                    "pri enakem K je vsota sprememb 0 (izid " + izid[0] + ":" + izid[1] + ")");
        }
        // tudi pri razlicnih ratingih, dokler je K enak
        EloStoritev.IzracunElo iz = elo.izracunaj(1234, 1100, 40, 40, true, 3, 1, 5);
        assertEquals(iz.sprememba1(), -iz.sprememba2());
    }

    /* Dinamicni K: novinec se ob isti tekmi giblje bolj kot ustaljen nasprotnik. */
    @Test
    void novinecSeGibljeVecKotUstaljen() {
        EloStoritev.IzracunElo iz = elo.izracunaj(1000, 1000, 0, 40, true, 3, 0, 5);
        assertTrue(Math.abs(iz.sprememba1()) > Math.abs(iz.sprememba2()),
                "novinec (K=48) se mora gibati bolj kot ustaljen igralec (K=20)");
    }

    @Test
    void favoritZmagoMaloPridobiPresenecenjeVelikoIzgubi() {
        EloStoritev.IzracunElo izracun = elo.izracunaj(1400, 1000, 0, 0, true, 3, 0, 5);
        assertTrue(izracun.sprememba1() <= 8, "favorit ob zmagi pridobi malo");

        EloStoritev.IzracunElo presenecenje = elo.izracunaj(1400, 1000, 0, 0, false, 0, 3, 5);
        assertTrue(presenecenje.sprememba1() <= -40, "favorit ob porazu izgubi veliko");
    }

    /* Set-margina po presenecenju: gladka zmaga avtsajderja prinese vec kot
       gladka zmaga favorita (poleg osnovne razlike verjetnosti nosi tudi
       presenetljivo prepricljiv izid). */
    @Test
    void presenetljivaGladkaZmagaPrineseVec() {
        int avtsajder = elo.izracunaj(1000, 1600, 0, 0, true, 3, 0, 5).sprememba1();
        int favorit = elo.izracunaj(1600, 1000, 0, 0, true, 3, 0, 5).sprememba1();
        assertTrue(avtsajder > favorit,
                "presenetljiva gladka zmaga mora prinesti vec kot pricakovana");
    }

    @Test
    void ratingNikoliPodSpodnjoMejo() {
        EloStoritev.IzracunElo izracun = elo.izracunaj(105, 1000, 0, 0, false, 0, 3, 5);
        assertTrue(izracun.ratingPo1() >= 100, "rating ne sme pod spodnjo mejo 100");
    }

    @Test
    void zmagovalecJeLahkoTudiZManjNizi() {
        // predaja pri izenacenju: zmagovalca doloci parameter, ne nizi
        EloStoritev.IzracunElo izracun = elo.izracunaj(1000, 1000, 0, 0, true, 1, 1, 5);
        assertTrue(izracun.sprememba1() > 0);
    }

    @Test
    void neveljavniVnosiSprozijoIzjemo() {
        assertThrows(IllegalArgumentException.class, () -> elo.izracunaj(1000, 1000, 0, 0, true, -1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> elo.izracunaj(1000, 1000, 0, 0, true, 3, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> elo.izracunaj(1000, 1000, 0, 0, true, 3, 0, 0));
    }
}
