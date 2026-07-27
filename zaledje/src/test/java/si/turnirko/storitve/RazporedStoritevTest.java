/* Testi generatorja razporeda (krozni sistem) - cista logika brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RazporedStoritevTest {

    private final RazporedStoritev razpored = new RazporedStoritev();

    @Test
    void sodoSteviloEnokroznoVsakZVsakimEnkrat() {
        List<RazporedStoritev.Par> pari = razpored.razpored(4, false);
        assertEquals(6, pari.size(), "4 ekipe, enokrozno: 6 srecanj");
        assertEquals(3, steviloKol(pari), "3 kola");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 4, false);
    }

    @Test
    void sodoSteviloDvokroznoObaObracuna() {
        List<RazporedStoritev.Par> pari = razpored.razpored(4, true);
        assertEquals(12, pari.size(), "4 ekipe, dvokrozno: 12 srecanj");
        assertEquals(6, steviloKol(pari), "6 kol");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 4, true);
    }

    @Test
    void lihoSteviloVsakoKoloEnaEkipaPociva() {
        List<RazporedStoritev.Par> pari = razpored.razpored(5, false);
        assertEquals(10, pari.size(), "5 ekip, enokrozno: 10 srecanj");
        assertEquals(5, steviloKol(pari), "5 kol");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 5, false);
        // v vsakem kolu igrata najvec 2 para (ena ekipa pociva)
        for (int kolo = 1; kolo <= 5; kolo++) {
            final int k = kolo;
            long v = pari.stream().filter(p -> p.kolo() == k).count();
            assertEquals(2, v, "v kolu " + kolo + " naj bosta 2 srecanji");
        }
    }

    @Test
    void triEkipeDvokrozno() {
        List<RazporedStoritev.Par> pari = razpored.razpored(3, true);
        assertEquals(6, pari.size(), "3 ekipe, dvokrozno: 6 srecanj");
        preveriVsakParEnkrat(pari, 3, true);
    }

    @Test
    void premaloEkipPrazenRazpored() {
        assertTrue(razpored.razpored(1, true).isEmpty());
        assertTrue(razpored.razpored(0, false).isEmpty());
    }

    // ---------- pomozne preverbe ----------

    private int steviloKol(List<RazporedStoritev.Par> pari) {
        return pari.stream().mapToInt(RazporedStoritev.Par::kolo).max().orElse(0);
    }

    private void preveriBrezPonovitevVKolu(List<RazporedStoritev.Par> pari) {
        int maxKolo = steviloKol(pari);
        for (int kolo = 1; kolo <= maxKolo; kolo++) {
            Set<Integer> nastopajoce = new HashSet<>();
            final int k = kolo;
            pari.stream().filter(p -> p.kolo() == k).forEach(p -> {
                assertNotEquals(p.domaci(), p.gost(), "ekipa ne igra sama s sabo");
                assertTrue(nastopajoce.add(p.domaci()), "ekipa " + p.domaci() + " dvakrat v kolu " + k);
                assertTrue(nastopajoce.add(p.gost()), "ekipa " + p.gost() + " dvakrat v kolu " + k);
            });
        }
    }

    /* Enokrozno: vsak neurejeni par natanko enkrat. Dvokrozno: vsak urejeni
       par (doma-gost) natanko enkrat. */
    private void preveriVsakParEnkrat(List<RazporedStoritev.Par> pari, int stEkip, boolean dvokrozno) {
        Set<String> videni = new HashSet<>();
        for (RazporedStoritev.Par p : pari) {
            String kljuc = dvokrozno
                    ? p.domaci() + ">" + p.gost()
                    : Math.min(p.domaci(), p.gost()) + "-" + Math.max(p.domaci(), p.gost());
            assertTrue(videni.add(kljuc), "par " + kljuc + " se ponovi");
        }
        int pricakovano = dvokrozno ? stEkip * (stEkip - 1) : stEkip * (stEkip - 1) / 2;
        assertEquals(pricakovano, videni.size());
        for (RazporedStoritev.Par p : pari) {
            assertTrue(p.domaci() >= 0 && p.domaci() < stEkip);
            assertTrue(p.gost() >= 0 && p.gost() < stEkip);
        }
        assertFalse(videni.isEmpty());
    }
}
