/* Testi cistega izracuna Turnirko ratinga - brez baze in brez Springa.
   Pokrivajo K po negotovosti (vkljucno s pribitkom za vrnitev), nicvsotno
   zaokrozevanje (Math.rint), tezo tekmovanja in pravilo "zmaga je zmaga":
   izid v nizih na spremembo ne vpliva (tega se ne da vec niti podati - obracun
   iz RatingStoritev pa drzi RazclenitevSpremembeTest). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import si.turnirko.modeli.RavenTekmovanja;

class TurnirkoRatingStoritevTest {

    private final TurnirkoRatingStoritev rating = new TurnirkoRatingStoritev();

    /* K po negotovosti: osnova 48, pribitka 10 + 10, dokler o igralcu vemo malo. */
    @Test
    void kFaktorPadaSStevilomTekem() {
        assertEquals(68, rating.kFaktor(0));
        assertEquals(68, rating.kFaktor(9));
        assertEquals(58, rating.kFaktor(10));
        assertEquals(58, rating.kFaktor(29));
        assertEquals(48, rating.kFaktor(30));
        assertEquals(48, rating.kFaktor(100));
    }

    /* Pribitek za vrnitev se PRISTEJE ostalim - vrnjeni novinec ima najvec. */
    @Test
    void vrnitevPovisaKZaDesetTock() {
        assertEquals(58, rating.kFaktor(30, true));
        assertEquals(68, rating.kFaktor(10, true));
        assertEquals(78, rating.kFaktor(0, true));
    }

    /* Vrnjeni igralec se ob isti tekmi giblje bolj kot njegov nasprotnik,
       ki je ves cas igral - njegova stara stevilka o njem pove manj. */
    @Test
    void poVrnitviSeIgralecGibljeBolj() {
        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(
                new TurnirkoRatingStoritev.StanjeIgralca(1000, 40, true),
                new TurnirkoRatingStoritev.StanjeIgralca(1000, 40, false),
                true, 1.0);
        assertEquals(29, iz.sprememba1(), "K 58 (48 + 10 za vrnitev), polovica");
        assertEquals(-24, iz.sprememba2(), "nasprotnik ostane pri K 48");
    }

    /* Med enakima igralcema je pricakovani izid 1/2, zato zmaga prinese
       natanko polovico K - brez vsakega mnozitelja. */
    @Test
    void novincaZEnakimRatingom() {
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(1000, 1000, 0, 0, true);
        assertEquals(1034, izracun.ratingPo1());
        assertEquals(966, izracun.ratingPo2());
        assertEquals(34, izracun.sprememba1());
        assertEquals(-34, izracun.sprememba2());
        assertEquals(0.5, izracun.pricakovanaVerjetnost1(), 1e-12);
    }

    /* Nicvsotno zaokrozevanje: pri ENAKEM K gubitnik izgubi natanko toliko,
       kot zmagovalec pridobi - tudi pri izenaceni napovedi (rint, ne round). */
    @Test
    void spremembiStaNicvsotniPriEnakemK() {
        for (int razlika : new int[] { 0, 1, 7, 134, 400, 999 }) {
            TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(1000 + razlika, 1000, 40, 40, true);
            assertEquals(iz.sprememba1(), -iz.sprememba2(),
                    "pri enakem K je vsota sprememb 0 (razlika " + razlika + ")");
            TurnirkoRatingStoritev.Izracun poraz = rating.izracunaj(1000 + razlika, 1000, 40, 40, false);
            assertEquals(poraz.sprememba1(), -poraz.sprememba2());
        }
        // tudi pri tezi, ki da lihi K (0,5 x K 68 = 34 -> pol tega je 17)
        TurnirkoRatingStoritev.StanjeIgralca novinec = TurnirkoRatingStoritev.StanjeIgralca.ustaljeno(1000, 0);
        TurnirkoRatingStoritev.Izracun pol = rating.izracunaj(novinec, novinec, true, 0.5);
        assertEquals(17, pol.sprememba1());
        assertEquals(-17, pol.sprememba2());
    }

    /* Dinamicni K: novinec se ob isti tekmi giblje bolj kot ustaljen nasprotnik. */
    @Test
    void novinecSeGibljeVecKotUstaljen() {
        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(1000, 1000, 0, 40, true);
        assertEquals(34, iz.sprememba1(), "K 68");
        assertEquals(-24, iz.sprememba2(), "K 48");
    }

    @Test
    void favoritZmagoMaloPridobiPresenecenjeVelikoIzgubi() {
        // 400 tock razlike: pricakovano 10/11 = 0,909
        TurnirkoRatingStoritev.Izracun zmaga = rating.izracunaj(1400, 1000, 0, 0, true);
        assertEquals(6, zmaga.sprememba1(), "68 x (1 - 0,909)");

        TurnirkoRatingStoritev.Izracun presenecenje = rating.izracunaj(1400, 1000, 0, 0, false);
        assertEquals(-62, presenecenje.sprememba1(), "68 x (0 - 0,909)");
        assertEquals(62, presenecenje.sprememba2());
    }

    /* Avtsajderjeva zmaga je vredna vec od favoritove - samo zaradi
       pricakovanega izida, ne zaradi nizov. */
    @Test
    void zmagaProtiMocnejsemuPrineseVec() {
        int avtsajder = rating.izracunaj(1000, 1600, 0, 0, true).sprememba1();
        int favorit = rating.izracunaj(1600, 1000, 0, 0, true).sprememba1();
        assertEquals(66, avtsajder, "68 x (1 - 0,031)");
        assertEquals(2, favorit, "68 x 0,031");
        assertEquals(68, avtsajder + favorit, "vsota obeh je K: E + (1 - E) = 1");
    }

    @Test
    void ratingNikoliPodSpodnjoMejo() {
        // poraz proti 300 bi odnesel 17 tock (68 x 0,25), do meje pa jih je 5
        TurnirkoRatingStoritev.Izracun izracun = rating.izracunaj(105, 300, 0, 0, false);
        assertEquals(100, izracun.ratingPo1(), "rating ne sme pod spodnjo mejo 100");
        assertEquals(-5, izracun.sprememba1(), "sprememba je razlika do meje, ne izracunana");
    }

    /* TEZA TEKMOVANJA: ista tekma na klubskem turnirju premakne rating za tri
       cetrtine, na rekreativnem za polovico. Teza je last tekme, zato je enaka
       za oba igralca in vsota sprememb pri enakem K ostane 0. */
    @Test
    void tezaTekmovanjaSorazmernoZmanjsaSpremembo() {
        TurnirkoRatingStoritev.StanjeIgralca novinec = TurnirkoRatingStoritev.StanjeIgralca.ustaljeno(1000, 0);

        int uradno = rating.izracunaj(novinec, novinec, true, 1.00).sprememba1();
        int klubsko = rating.izracunaj(novinec, novinec, true, 0.75).sprememba1();
        int rekreativno = rating.izracunaj(novinec, novinec, true, 0.50).sprememba1();

        assertEquals(34, uradno, "K 68, polovica");
        assertEquals(26, klubsko, "tri cetrtine istega (25,5 -> 26, pol na sodo)");
        assertEquals(17, rekreativno, "polovica istega");

        TurnirkoRatingStoritev.Izracun iz = rating.izracunaj(novinec, novinec, true, 0.50);
        assertEquals(iz.sprememba1(), -iz.sprememba2(), "teza je enaka za oba, vsota ostane 0");
        assertEquals(0.50, iz.teza());
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

    /* Vrstni red igralcev v klicu ne sme spremeniti izracuna. */
    @Test
    void vrstniRedIgralcevNeSpremeniIzracuna() {
        TurnirkoRatingStoritev.Izracun prvi = rating.izracunaj(1200, 1000, 5, 40, true);
        TurnirkoRatingStoritev.Izracun drugi = rating.izracunaj(1000, 1200, 40, 5, false);
        assertEquals(prvi.sprememba1(), drugi.sprememba2());
        assertEquals(prvi.sprememba2(), drugi.sprememba1());
    }
}
