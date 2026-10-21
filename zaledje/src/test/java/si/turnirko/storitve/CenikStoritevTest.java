/* Cistega izracuna cene - brez baze in brez Springa (isti vzorec kot
   TurnirkoRatingStoritevTest). Stevilke so odlocitev lastnika: Premium
   0,99/1,99 EUR mesecno po starosti, letno fiksno 9,99/19,99 (21. 10. 2026);
   organizatorski paketi so samo letni. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;

class CenikStoritevTest {

    private final CenikStoritev cenik = new CenikStoritev();

    @Test
    void brezplacnoJeVedno0() {
        assertEquals(0.0, cenik.cena(Paket.BREZPLACNO, null, null));
    }

    @Test
    void premiumMesecnoPoStarosti() {
        assertEquals(0.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, false));
        assertEquals(1.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, true));
    }

    /* Letno je fiksna cena, ne izracun; vmesnik ob njej pokaze oznako
       prihranka, izpeljano iz istih stevilk in zaokrozeno navzdol (zdaj
       "-15 %"). Ta test drzi, da letno pri OBEH pasovih res prihrani vsaj
       toliko - sicer bi oznaka obljubljala popust, ki ga ni (ZVPot-1). */
    @Test
    void premiumLetnoJeFiksnaInVsaj15OdstotkovCenejeOdMesecne() {
        assertEquals(9.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, false));
        assertEquals(19.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, true));
        for (boolean starejsi : new boolean[] {false, true}) {
            double dvanajstMesecnih = 12 * cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, starejsi);
            double prihranek = 1 - cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, starejsi) / dvanajstMesecnih;
            assertTrue(prihranek >= 0.15, "prihranek " + prihranek);
        }
    }

    @Test
    void premiumBrezCiklusaZavrne() {
        assertThrows(NeveljavenVnosIzjema.class, () -> cenik.cena(Paket.PREMIUM, null, false));
    }

    @Test
    void organizatorskiPaketiSoLetniPoObsegu() {
        assertEquals(89.99, cenik.cena(Paket.ORGANIZATOR_BASIC, CiklusPlacila.LETNO, null));
        assertEquals(169.99, cenik.cena(Paket.ORGANIZATOR_PLUS, CiklusPlacila.LETNO, null));
        assertEquals(249.99, cenik.cena(Paket.ORGANIZATOR_PRO, CiklusPlacila.LETNO, null));
    }

    @Test
    void organizatorMesecnoZavrne() {
        assertThrows(NeveljavenVnosIzjema.class,
                () -> cenik.cena(Paket.ORGANIZATOR_BASIC, CiklusPlacila.MESECNO, null));
    }
}
