/* Stevilka mize iz porazdelitve okenca (S1, LokalniBralnikListka.miza).

   Domenska pravila, ki jih ta test varuje:
   - prazno okence ni miza in ni negotovo;
   - zanesljivo prebrana stevilka 1-99 je miza;
   - mize 0 ni: nicla ni kandidat, vendar steje v vsoto - kar mreza vidi kot 0,
     ni zanesljiva druga stevilka;
   - pod mejo zaupanja ali pri nezanesljivem okencu (pregib, precrtano) je miza
     negotova, nikoli tiho napacna. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import si.turnirko.branje.RazlagaListka;

class MizaListkaTest {

    private static double[] porazdelitev(double... pari) {
        double[] p = new double[RazlagaListka.VELIKOST];
        for (int i = 0; i < pari.length; i += 2) {
            p[(int) pari[i]] = pari[i + 1];
        }
        return p;
    }

    @Test
    void praznoOkenceNiMiza() {
        LokalniBralnikListka.Miza m = LokalniBralnikListka.miza(porazdelitev(RazlagaListka.PRAZNO, 1), false);
        assertNull(m.stevilka());
        assertFalse(m.negotova());
    }

    @Test
    void zanesljivaStevilka() {
        LokalniBralnikListka.Miza m = LokalniBralnikListka.miza(porazdelitev(12, 0.95, 17, 0.05), false);
        assertEquals(12, m.stevilka());
        assertFalse(m.negotova());
    }

    @Test
    void niclaStejeVVsoto() {
        LokalniBralnikListka.Miza m = LokalniBralnikListka.miza(porazdelitev(0, 0.9, 7, 0.1), false);
        assertEquals(7, m.stevilka());
        assertTrue(m.negotova(), "90 % nicla ne sme postati zanesljiva sedmica");
    }

    @Test
    void podMejoJeNegotova() {
        LokalniBralnikListka.Miza m = LokalniBralnikListka.miza(porazdelitev(4, 0.6, 9, 0.4), false);
        assertEquals(4, m.stevilka());
        assertTrue(m.negotova());
    }

    /* Miza gre z branjem v JSON (branje_listka.prebrano) in nazaj; branja pred S1
       polj nimajo in dajo prazno mizo. */
    @Test
    void mizaPrezivJson() {
        PrebranListek z = new PrebranListek(true, null, null, null, false, null, null, java.util.List.of(),
                3, 1, false, "NI", null, null, 7, true);
        PrebranListek nazaj = JsonBranja.preberi(JsonBranja.zapisi(z), PrebranListek.class);
        assertEquals(7, nazaj.miza());
        assertTrue(nazaj.negotovaMiza());
        PrebranListek star = JsonBranja.preberi("{\"jeListek\":true,\"nizi\":[],\"negotovRezultat\":false}",
                PrebranListek.class);
        assertNull(star.miza());
        assertFalse(star.negotovaMiza());
    }

    @Test
    void nezanesljivoOkenceJeVednoNegotovo() {
        LokalniBralnikListka.Miza m = LokalniBralnikListka.miza(porazdelitev(1, 0.99), true);
        assertEquals(1, m.stevilka());
        assertTrue(m.negotova());
    }
}
