/* Testi regularizirane fiksne tocke - cista matematika, brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class UvrstitevNovincaTest {

    private static UvrstitevNovinca.Izid zmaga(int nasprotnik) {
        return new UvrstitevNovinca.Izid(nasprotnik, true);
    }

    private static UvrstitevNovinca.Izid poraz(int nasprotnik) {
        return new UvrstitevNovinca.Izid(nasprotnik, false);
    }

    /* Brez izidov ni kaj razlagati - ostane sidro. */
    @Test
    void brezIzidovOstaneSidro() {
        assertEquals(1263, UvrstitevNovinca.izracunaj(List.of(), 1263));
    }

    /* Izenacen izid proti enako mocnim pomeni, da je sidro pravo. */
    @Test
    void izenacenIzidPotrdiSidro() {
        assertEquals(1263, UvrstitevNovinca.izracunaj(
                List.of(zmaga(1263), poraz(1263)), 1263));
    }

    /* Zmage dvignejo, porazi spustijo - simetricno okoli sidra. */
    @Test
    void zmageDvignejoPoraziSpustijo() {
        int zZmagami = UvrstitevNovinca.izracunaj(List.of(zmaga(1263), zmaga(1304)), 1263);
        int sPorazi = UvrstitevNovinca.izracunaj(List.of(poraz(1263), poraz(1222)), 1263);

        assertEquals(1465, zZmagami, "dve zmagi proti ~1280 in dve navidezni izenaceni");
        assertEquals(1061, sPorazi, "dva poraza proti ~1240 in dve navidezni izenaceni");
        assertTrue(zZmagami > 1263 && sPorazi < 1263);
    }

    /* Regularizacija: dve navidezni izenaceni tekmi proti sidru poskrbita, da
       stoodstotni izkupicek ne odnese novinca v neskoncnost, in da vec tekem
       nosi vec teze kot manj. */
    @Test
    void regularizacijaDrziStoodstotniIzkupicekPriTlehZemlje() {
        int dveZmagi = UvrstitevNovinca.izracunaj(List.of(zmaga(1000), zmaga(1000)), 1000);
        int petZmag = UvrstitevNovinca.izracunaj(
                List.of(zmaga(1000), zmaga(1000), zmaga(1000), zmaga(1000), zmaga(1000)), 1000);

        assertTrue(dveZmagi < petZmag, "vec zmag je mocnejsi dokaz in dvigne vec");
        assertTrue(petZmag < 1700, "brez regularizacije bi bil neomejen");
    }

    /* Kdo so bili nasprotniki, steje: zmaga proti mocnejsemu dvigne bolj. */
    @Test
    void mocnejsiNasprotnikiDvignejoVec() {
        int protiSibkim = UvrstitevNovinca.izracunaj(List.of(zmaga(900), zmaga(900)), 1000);
        int protiMocnim = UvrstitevNovinca.izracunaj(List.of(zmaga(1500), zmaga(1500)), 1000);
        assertTrue(protiMocnim > protiSibkim + 300);
    }

    /* Sidro poteguje: isti izidi pri visjem sidru dajo visjo uvrstitev. */
    @Test
    void sidroPotegujeRezultat() {
        int nizkoSidro = UvrstitevNovinca.izracunaj(List.of(zmaga(1000), poraz(1000)), 800);
        int visokoSidro = UvrstitevNovinca.izracunaj(List.of(zmaga(1000), poraz(1000)), 1600);
        assertTrue(nizkoSidro < visokoSidro);
    }

    /* Meji sta isti kot pri postavitvenem ratingu - cez njiju ne gre. */
    @Test
    void rezultatOstaneVMejah() {
        int zelovisoko = UvrstitevNovinca.izracunaj(
                List.of(zmaga(2900), zmaga(2900), zmaga(2900), zmaga(2900), zmaga(2900),
                        zmaga(2900), zmaga(2900), zmaga(2900), zmaga(2900), zmaga(2900)), 2900);
        assertTrue(zelovisoko <= 3000);

        int zelonizko = UvrstitevNovinca.izracunaj(
                List.of(poraz(100), poraz(100), poraz(100), poraz(100), poraz(100),
                        poraz(100), poraz(100), poraz(100), poraz(100), poraz(100)), 100);
        assertTrue(zelonizko >= 100);
    }
}
