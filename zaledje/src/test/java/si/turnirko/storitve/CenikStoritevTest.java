/* Cistega izracuna cene - brez baze in brez Springa (isti vzorec kot
   TurnirkoRatingStoritevTest). Stevilke so odlocitev lastnika: Premium
   1,99/2,99 EUR mesecno po starosti, letno fiksno 9,99/19,99 (21. 10. 2026);
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
        assertEquals(1.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, false));
        assertEquals(2.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, true));
    }

    /* Letno je fiksna cena, ne izracun, in mora biti pri OBEH pasovih cenejsa
       od dvanajstih mesecnih - vmesnik ob njej pokaze oznako prihranka,
       izpeljano iz prikazanih cen (zdaj -58 % za U21, -44 % sicer). */
    @Test
    void premiumLetnoJeFiksnaInCenejsaOdDvanajstihMesecnih() {
        assertEquals(9.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, false));
        assertEquals(19.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, true));
        for (boolean starejsi : new boolean[] {false, true}) {
            double dvanajstMesecnih = 12 * cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, starejsi);
            assertTrue(cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, starejsi) < dvanajstMesecnih);
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
