/* Cistega izracuna cene - brez baze in brez Springa (isti vzorec kot
   TurnirkoRatingStoritevTest). Stevilke so odlocitev lastnika (17.-23. 9.
   2026): Premium 3,99/4,99 EUR mesecno po starosti, letno = 11x mesecna;
   organizatorski paketi so samo letni. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(3.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, false));
        assertEquals(4.99, cenik.cena(Paket.PREMIUM, CiklusPlacila.MESECNO, true));
    }

    /* Letno = tocno 11x mesecna cena, brez navideznega popusta (ZVPot-1). */
    @Test
    void premiumLetnoJeEnajstkratMesecno() {
        assertEquals(43.89, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, false));
        assertEquals(54.89, cenik.cena(Paket.PREMIUM, CiklusPlacila.LETNO, true));
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
