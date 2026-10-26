package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class PostavkaZanimivostiTest {

    /* Liga ima srečanja (na nož, gostje, nosilci), turnir pa prve naslove. */
    @Test
    void razdelkiLocijoLigoInTurnir() {
        List<PostavkaZanimivosti> liga = PostavkaZanimivosti.zaTekmovanje(true, true);
        assertTrue(liga.contains(PostavkaZanimivosti.NA_NOZ));
        assertTrue(liga.contains(PostavkaZanimivosti.GOSTJE));
        assertTrue(liga.contains(PostavkaZanimivosti.NOSILCI));
        assertFalse(liga.contains(PostavkaZanimivosti.PRVI_NASLOVI));

        List<PostavkaZanimivosti> turnir = PostavkaZanimivosti.zaTekmovanje(false, true);
        assertTrue(turnir.contains(PostavkaZanimivosti.PRVI_NASLOVI));
        assertFalse(turnir.contains(PostavkaZanimivosti.NA_NOZ));
        assertFalse(turnir.contains(PostavkaZanimivosti.NOSILCI));
    }

    /* Vzpon in presenečenje merita rating; tekmovanje, ki ne šteje, ga nima, zato
       ti razdelki niti ne nastanejo in organizatorju ne smejo biti ponujeni. */
    @Test
    void razdelkaZRatingomOdpadeta() {
        for (boolean jeLiga : new boolean[] {true, false}) {
            List<PostavkaZanimivosti> brezRatinga = PostavkaZanimivosti.zaTekmovanje(jeLiga, false);
            assertFalse(brezRatinga.contains(PostavkaZanimivosti.VZPONI));
            assertFalse(brezRatinga.contains(PostavkaZanimivosti.PRESENECENJE));
            assertTrue(brezRatinga.contains(PostavkaZanimivosti.ZID));
        }
    }

    @Test
    void zapisJeNabornBrezPodvojitevInVrstnegaReda() {
        assertNull(PostavkaZanimivosti.vZapis(List.of()));
        assertNull(PostavkaZanimivosti.vZapis(null));
        assertEquals("ZID,KLUBI",
                PostavkaZanimivosti.vZapis(List.of(PostavkaZanimivosti.ZID, PostavkaZanimivosti.KLUBI,
                        PostavkaZanimivosti.ZID)));
    }

    /* Razdelek, ki je bil v bazi, a je zdaj odstranjen, ne sme zrušiti branja. */
    @Test
    void zapisSeBereNazajInNeznanoImeSeIzpusti() {
        assertEquals(EnumSet.of(PostavkaZanimivosti.KLUBI, PostavkaZanimivosti.ZID),
                PostavkaZanimivosti.izZapisa("ZID,KLUBI"));
        assertEquals(EnumSet.of(PostavkaZanimivosti.KLUBI),
                PostavkaZanimivosti.izZapisa("KLUBI,ODSTRANJEN"));
        assertTrue(PostavkaZanimivosti.izZapisa(null).isEmpty());
        assertTrue(PostavkaZanimivosti.izZapisa(" ").isEmpty());
    }
}
