/* Testi generatorja razporeda (krozni sistem) - cista logika brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
        assertTrue(razpored.razpored(1, true, true).isEmpty());
    }

    // ---------- enakomerna razvrstitev (razpored po parih) ----------

    /* Ne glede na velikost lige mora tudi razpored po parih ostati regularen
       krozni sistem: vsak z vsakim natanko enkrat in nihce dvakrat v istem
       kolu. Sodo in liho stevilo parov sta razlicni veji, zato so v seznamu
       obe (8 in 12 ekip = sodo parov, 6, 10 in 14 = liho) in liho stevilo ekip
       (navidezna ekipa). */
    @Test
    void poParihOstaneRegularenKrozniSistem() {
        for (int stEkip : new int[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16 }) {
            List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
            preveriBrezPonovitevVKolu(pari);
            preveriVsakParEnkrat(pari, stEkip, false);
        }
    }

    @Test
    void poParihDvokroznoObaObracuna() {
        List<RazporedStoritev.Par> pari = razpored.razpored(10, true, true);
        assertEquals(90, pari.size(), "10 ekip, dvokrozno: 90 srecanj");
        assertEquals(20, steviloKol(pari), "10 kol na cikel");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 10, true);
    }

    /* Pri sodem stevilu parov (8 ekip = 4 pari) noben par ne ostane brez
       nasprotnega para, zato se liga konca s kolom, v katerem vsaka ekipa
       igra samo s svojim parom - in kol je toliko kot pri navadnem sistemu. */
    @Test
    void poParihSodoParovZadnjeKoloSoDvobojiParov() {
        List<RazporedStoritev.Par> pari = razpored.razpored(8, false, true);
        assertEquals(7, steviloKol(pari), "8 ekip: 7 kol kot pri navadnem sistemu");

        List<RazporedStoritev.Par> zadnje = vKolu(pari, 7);
        assertEquals(4, zadnje.size(), "v zadnjem kolu igrajo vsi stirje pari");
        for (RazporedStoritev.Par p : zadnje) {
            assertEquals(4, Math.abs(p.domaci() - p.gost()),
                    "zadnje kolo je dvoboj para: i proti i+4");
        }
    }

    /* Bistvo enakomerne razvrstitve: par igra v vsakem krogu (dve koli) proti
       ISTEMU nasprotnemu paru, torej ena njegova ekipa proti zgornji in druga
       proti spodnji ekipi nasprotnika. Preverjeno na primeru iz zahteve -
       10 ekip, pari A-F, B-G, C-H, D-I, E-J. */
    @Test
    void poParihParVKroguIgraIsteNasprotnike() {
        int stEkip = 10;
        int parov = stEkip / 2;
        List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
        assertEquals(stEkip, steviloKol(pari),
                "pri lihem stevilu parov ima liga eno kolo vec (vsaka ekipa enkrat pociva)");

        for (int krog = 1; krog <= parov; krog++) {
            List<RazporedStoritev.Par> vKrogu = new ArrayList<>(vKolu(pari, 2 * krog - 1));
            vKrogu.addAll(vKolu(pari, 2 * krog));
            for (int p = 0; p < parov; p++) {
                Set<Integer> zgornja = nasprotniki(vKrogu, p);
                Set<Integer> spodnja = nasprotniki(vKrogu, p + parov);
                if (zgornja.contains(p + parov)) {
                    // krog, v katerem par odigra svoj notranji dvoboj
                    assertEquals(Set.of(p + parov), zgornja);
                    assertEquals(Set.of(p), spodnja);
                    continue;
                }
                assertEquals(zgornja, spodnja,
                        "obe ekipi para imata v krogu " + krog + " iste nasprotnike");
                assertEquals(2, zgornja.size(), "par v krogu igra z obema ekipama nasprotnega para");
                assertEquals(1, zgornja.stream().filter(n -> n < parov).count(),
                        "en nasprotnik je iz zgornje polovice");
                assertEquals(1, zgornja.stream().filter(n -> n >= parov).count(),
                        "en nasprotnik je iz spodnje polovice");
            }
        }
    }

    /* Gostitelj je posamezna ekipa in ne cel par: sicer bi ena ekipa gostila
       vse stiri dvoboje kroga in bi ji ob koncu sezone pripadlo bistveno vec
       (ali manj) domacih srecanj kot ostalim. Pri sodem stevilu ekip mora biti
       delitev natancna - polovica navzdol oz. navzgor. */
    @Test
    void poParihDomacaSrecanjaSoRazdeljena() {
        for (int stEkip : new int[] { 4, 6, 8, 10, 12, 14 }) {
            List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
            int srecanj = stEkip - 1;
            for (int ekipa = 0; ekipa < stEkip; ekipa++) {
                final int e = ekipa;
                long doma = pari.stream().filter(p -> p.domaci() == e).count();
                assertTrue(doma == srecanj / 2 || doma == (srecanj + 1) / 2,
                        "ekipa " + e + " pri " + stEkip + " ekipah igra " + doma
                                + " srecanj doma, pricakovano " + (srecanj / 2) + " ali "
                                + ((srecanj + 1) / 2));
            }
        }
    }

    private List<RazporedStoritev.Par> vKolu(List<RazporedStoritev.Par> pari, int kolo) {
        return pari.stream().filter(p -> p.kolo() == kolo).toList();
    }

    private Set<Integer> nasprotniki(List<RazporedStoritev.Par> pari, int ekipa) {
        Set<Integer> nasprotniki = new HashSet<>();
        for (RazporedStoritev.Par p : pari) {
            if (p.domaci() == ekipa) nasprotniki.add(p.gost());
            if (p.gost() == ekipa) nasprotniki.add(p.domaci());
        }
        return nasprotniki;
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
