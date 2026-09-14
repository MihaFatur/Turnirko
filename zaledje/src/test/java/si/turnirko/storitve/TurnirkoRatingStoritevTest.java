/* Testi cistega izracuna Turnirko ratinga - brez baze in brez Springa.
   Pokrivajo stiri izboljsave: K po negotovosti (vkljucno s pribitkom za
   vrnitev), nicvsotno zaokrozevanje (Math.rint), set-margino po presenecenju
   in odsotnost margine pri predaji. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import si.turnirko.modeli.RavenTekmovanja;

class TurnirkoRatingStoritevTest {

    private final TurnirkoRatingStoritev rating = new TurnirkoRatingStoritev();

    /* K po negotovosti: osnova 40, pribitka 8 + 8, dokler o igralcu vemo malo. */
    @Test
    void kFaktorPadaSStevilomTekem() {
        assertEquals(56, rating.kFaktor(0));
        assertEquals(56, rating.kFaktor(9));
        assertEquals(48, rating.kFaktor(10));
        assertEquals(48, rating.kFaktor(29));
        assertEquals(40, rating.kFaktor(30));
        assertEquals(40, rating.kFaktor(100));
    }

    /* Pribitek za vrnitev se PRISTEJE ostalim - vrnjeni novinec ima najvec. */
    @Test
    void vrnitevPovisaKZaOsemTock() {
        assertEquals(48, rating.kFaktor(30, true));
        assertEquals(56, rating.kFaktor(10, true));
        assertEquals(64, rating.kFaktor(0, true));
    }

    /* Vrnjeni igralec se ob isti tekmi giblje bolj kot njegov nasprotnik,
       ki je ves cas igral - njegova stara stevilka o njem pove manj. */
    @Test
    void poVrnitviSeIgralecGibljeBolj() {
        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(
                new TurnirkoRatingStoritev.StanjeIgralca(1000, 40, true),
                new TurnirkoRatingStoritev.StanjeIgralca(1000, 40, false),
                true, 3, 0, 5, false);
        assertEquals(35, iz.sprememba1(), "K 48 (40 + 8 za vrnitev) krat margina 1,469, polovica");
        assertEquals(-29, iz.sprememba2(), "nasprotnik ostane pri K 40");
    }

    @Test
    void novincaEnakRatingGladkaZmaga() {
        // dva novinca (0 tekem -> K=56), gladka zmaga 3:0 pri najboljsem od 5
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(1000, 1000, 0, 0, true, 3, 0, 5);
        assertEquals(1041, izracun.ratingPo1());
        assertEquals(959, izracun.ratingPo2());
        assertEquals(41, izracun.sprememba1());
        assertEquals(-41, izracun.sprememba2());
    }

    @Test
    void tesnaZmagaPrineseManjKotGladka() {
        TurnirkoRatingStoritev.Izracun tesna = rating.izracunaj(1000, 1000, 0, 0, true, 3, 2, 5);
        TurnirkoRatingStoritev.Izracun gladka = rating.izracunaj(1000, 1000, 0, 0, true, 3, 0, 5);
        assertTrue(tesna.sprememba1() < gladka.sprememba1(),
                "tesna zmaga mora prinesti manj tock kot gladka");
    }

    /* Nicvsotno zaokrozevanje: pri ENAKEM K gubitnik izgubi natanko toliko,
       kot zmagovalec pridobi - tudi pri izenaceni napovedi (rint, ne round). */
    @Test
    void spremembiStaNicvsotniPriEnakemK() {
        for (int[] izid : new int[][] { {3, 0}, {3, 1}, {3, 2} }) {
            TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(1000, 1000, 0, 0, true, izid[0], izid[1], 5);
            assertEquals(iz.sprememba1(), -iz.sprememba2(),
                    "pri enakem K je vsota sprememb 0 (izid " + izid[0] + ":" + izid[1] + ")");
        }
        // tudi pri razlicnih ratingih, dokler je K enak
        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(1234, 1100, 40, 40, true, 3, 1, 5);
        assertEquals(iz.sprememba1(), -iz.sprememba2());
    }

    /* Dinamicni K: novinec se ob isti tekmi giblje bolj kot ustaljen nasprotnik. */
    @Test
    void novinecSeGibljeVecKotUstaljen() {
        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(1000, 1000, 0, 40, true, 3, 0, 5);
        assertTrue(Math.abs(iz.sprememba1()) > Math.abs(iz.sprememba2()),
                "novinec (K=56) se mora gibati bolj kot ustaljen igralec (K=40)");
    }

    @Test
    void favoritZmagoMaloPridobiPresenecenjeVelikoIzgubi() {
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(1400, 1000, 0, 0, true, 3, 0, 5);
        assertTrue(izracun.sprememba1() <= 8, "favorit ob zmagi pridobi malo");

        TurnirkoRatingStoritev.Izracun presenecenje = rating.izracunaj(1400, 1000, 0, 0, false, 0, 3, 5);
        assertTrue(presenecenje.sprememba1() <= -40, "favorit ob porazu izgubi veliko");
    }

    /* Set-margina po presenecenju: gladka zmaga avtsajderja prinese vec kot
       gladka zmaga favorita (poleg osnovne razlike verjetnosti nosi tudi
       presenetljivo prepricljiv izid). */
    @Test
    void presenetljivaGladkaZmagaPrineseVec() {
        int avtsajder = rating.izracunaj(1000, 1600, 0, 0, true, 3, 0, 5).sprememba1();
        int favorit = rating.izracunaj(1600, 1000, 0, 0, true, 3, 0, 5).sprememba1();
        assertTrue(avtsajder > favorit,
                "presenetljiva gladka zmaga mora prinesti vec kot pricakovana");
    }

    @Test
    void ratingNikoliPodSpodnjoMejo() {
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(105, 1000, 0, 0, false, 0, 3, 5);
        assertTrue(izracun.ratingPo1() >= 100, "rating ne sme pod spodnjo mejo 100");
    }

    @Test
    void zmagovalecJeLahkoTudiZManjNizi() {
        // predaja pri izenacenju: zmagovalca doloci parameter, ne nizi
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(1000, 1000, 0, 0, true, 1, 1, 5);
        assertTrue(izracun.sprememba1() > 0);
    }

    @Test
    void neveljavniVnosiSprozijoIzjemo() {
        assertThrows(IllegalArgumentException.class, () -> rating.izracunaj(1000, 1000, 0, 0, true, -1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> rating.izracunaj(1000, 1000, 0, 0, true, 3, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> rating.izracunaj(1000, 1000, 0, 0, true, 3, 0, 0));
    }

    /* PREDAJA: kdor preda pri vodstvu 2:0, je "zmagovalcu" pustil nic dobljenih
       nizov. Prejsnja koda je nize porazenca brala kot manjsega od obeh stevil
       in je tak izid ocenila kot gladko razbitje 3:0 - zmagovalec je za tekmo,
       ki jo je na mizi izgubljal, dobil najvisji mozni bonus. Pri predaji zato
       margine ne uporabimo: steje samo, kdo je zmagal. */
    @Test
    void predajaPriVodstvuNeDaBonusaZaRazbitje() {
        // igralec 1 vodi 2:0 in preda -> zmagovalec je igralec 2
        TurnirkoRatingStoritev.Izracun predaja = rating.izracunaj(1000, 1000, 0, 0, false, 2, 0, 5, true);
        TurnirkoRatingStoritev.Izracun gladko = rating.izracunaj(1000, 1000, 0, 0, false, 0, 3, 5);

        assertEquals(28, predaja.sprememba2(),
                "pri predaji je mnozitelj margine 1, zato polovica K (56)");
        assertTrue(predaja.sprememba2() < gladko.sprememba2(),
                "predaja ne sme prinesti vec kot dejansko odigrano razbitje 3:0");
        assertEquals(predaja.sprememba1(), -predaja.sprememba2(),
                "pri enakem K je vsota sprememb tudi pri predaji 0");
    }

    /* TEZA TEKMOVANJA: ista tekma na klubskem turnirju premakne rating za tri
       cetrtine, na rekreativnem za polovico. Teza je last tekme, zato je enaka
       za oba igralca in vsota sprememb pri enakem K ostane 0. */
    @Test
    void tezaTekmovanjaSorazmernoZmanjsaSpremembo() {
        TurnirkoRatingStoritev.StanjeIgralca novinec = TurnirkoRatingStoritev.StanjeIgralca.ustaljeno(1000, 0);

        int uradno = rating.izracunaj(novinec, novinec, true, 3, 0, 5, false, 1.00).sprememba1();
        int klubsko = rating.izracunaj(novinec, novinec, true, 3, 0, 5, false, 0.75).sprememba1();
        int rekreativno = rating.izracunaj(novinec, novinec, true, 3, 0, 5, false, 0.50).sprememba1();

        assertEquals(41, uradno, "K 56 krat margina 1,469, polovica");
        assertEquals(31, klubsko, "tri cetrtine istega");
        assertEquals(21, rekreativno, "polovica istega");

        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(novinec, novinec, true, 3, 0, 5, false, 0.50);
        assertEquals(iz.sprememba1(), -iz.sprememba2(), "teza je enaka za oba, vsota ostane 0");
    }

    /* Raven tekmovanja in njena teza sta en podatek - da se ne razideta. */
    @Test
    void ravniImajoDogovorjeneTeze() {
        assertEquals(1.00, RavenTekmovanja.URADNO.getTeza());
        assertEquals(0.75, RavenTekmovanja.KLUBSKO.getTeza());
        assertEquals(0.50, RavenTekmovanja.REKREATIVNO.getTeza());
        assertEquals(0.00, RavenTekmovanja.NE_STEJE.getTeza());
        assertTrue(RavenTekmovanja.REKREATIVNO.steje());
        assertFalse(RavenTekmovanja.NE_STEJE.steje(), "tekmovanje brez teze se ne obracuna");
    }

    /* Nize porazenca vzamemo po ZMAGOVALCU in ne kot manjsega od obeh stevil.
       Pri koncani tekmi je to isto - ta test to zaklene, da popravek predaje ne
       spremeni obracuna odigranih tekem. */
    @Test
    void koncaneTekmeSeObracunajoEnako() {
        for (int[] izid : new int[][] { {3, 0}, {3, 1}, {3, 2} }) {
            TurnirkoRatingStoritev.Izracun prvi = rating.izracunaj(1200, 1000, 5, 40, true, izid[0], izid[1], 5);
            TurnirkoRatingStoritev.Izracun drugi = rating.izracunaj(1000, 1200, 40, 5, false, izid[1], izid[0], 5);
            assertEquals(prvi.sprememba1(), drugi.sprememba2(),
                    "vrstni red igralcev v klicu ne sme spremeniti izracuna");
        }
    }
}
