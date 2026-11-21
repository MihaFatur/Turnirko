/* Razlaga listka po pravilih namiznega tenisa (RazlagaListka).

   Domenska pravila, ki jih ta test varuje:
   - jasno prebran listek ostane, kot je, in ni negotov;
   - dvoumna stevka (1 ali 7) se razresi po pravilu niza (11:7 je veljaven, 17:7 ne)
     in je oznacena kot negotova;
   - pravila izberejo tisto razlago, pri kateri se tekma konca pravilno in se
     ujema s celico Rezultat;
   - po odlocitvi tekme so vrstice prazne;
   - prazna vrstica pred odlocitvijo je nepopolna tekma (predaja, brez boja). */
package si.turnirko.branje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RazlagaListkaTest {

    @Test
    void jasenListekOstaneKotJe() {
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(gotovo(11), gotovo(7)),
                vrstica(gotovo(9), gotovo(11)),
                vrstica(gotovo(11), gotovo(5)),
                vrstica(gotovo(11), gotovo(8)),
                vrstica(prazno(), prazno())), gotovo(3), gotovo(1));
        assertEquals(11, izid.nizi().get(0).levo());
        assertEquals(7, izid.nizi().get(0).desno());
        assertNull(izid.nizi().get(4).levo());
        assertTrue(izid.nizi().stream().noneMatch(n -> n.negotovoLevo() || n.negotovoDesno()));
        assertEquals(3, izid.rezultatLevo());
        assertNull(izid.opomba());
    }

    @Test
    void dvoumnaStevkaSeRazresiPoPraviluNiza() {
        // mreza bolj verjame, da je levo 17, a 17:7 ni niz; 11:7 je
        double[] levo = verjetnosti(17, 0.6, 11, 0.4);
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(levo, gotovo(7)),
                vrstica(gotovo(11), gotovo(4)),
                vrstica(prazno(), prazno())), prazno(), prazno());
        assertEquals(11, izid.nizi().get(0).levo());
        assertTrue(izid.nizi().get(0).negotovoLevo(), "pravila so preglasila mrezo");
        assertFalse(izid.nizi().get(0).negotovoDesno());
    }

    @Test
    void rezultatOdlociMedVeljavnimaRazlagama() {
        // 3. niz: 11:9 ali 11:4 sta oba veljavna, a samo 11:9 da 2:1 - in zmagovalec ostane isti;
        // odloca smer: celica rezultata 1:2 pomeni, da je 3. niz dobil desni
        double[] levo = verjetnosti(11, 0.55, 9, 0.45);
        double[] desno = verjetnosti(9, 0.55, 11, 0.45);
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(gotovo(11), gotovo(5)),
                vrstica(gotovo(6), gotovo(11)),
                vrstica(levo, desno)), gotovo(1), gotovo(2));
        assertEquals(9, izid.nizi().get(2).levo());
        assertEquals(11, izid.nizi().get(2).desno());
    }

    @Test
    void poOdlocitviSoVrsticePrazne() {
        // po 2:0 (tekma na 3 nize) se 3. niz ne igra; sum v vrstici ne postane niz
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(gotovo(11), gotovo(3)),
                vrstica(gotovo(11), gotovo(6)),
                vrstica(verjetnosti(11, 0.3, RazlagaListka.PRAZNO, 0.7), verjetnosti(1, 0.3, RazlagaListka.PRAZNO, 0.7))),
                prazno(), prazno());
        assertNull(izid.nizi().get(2).levo());
    }

    @Test
    void praznaVrsticaPredOdlocitvijoJeNepopolnaTekma() {
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(gotovo(11), gotovo(3)),
                vrstica(prazno(), prazno()),
                vrstica(prazno(), prazno())), prazno(), prazno());
        assertEquals(11, izid.nizi().get(0).levo());
        assertNull(izid.nizi().get(1).levo());
    }

    @Test
    void podaljsanNiz() {
        RazlagaListka.Izid izid = RazlagaListka.razlozi(List.of(
                vrstica(gotovo(13), gotovo(11)),
                vrstica(gotovo(10), gotovo(12)),
                vrstica(gotovo(11), gotovo(2))), gotovo(2), gotovo(1));
        assertEquals(13, izid.nizi().get(0).levo());
        assertEquals(12, izid.nizi().get(1).desno());
    }

    @Test
    void zgodovinaOdlociMedVeljavnimaNizomaInCelicoOznaciNegotovo() {
        // mreza rahlo bolj verjame 11:1, a 11:7 je v zgodovini dvanajstkrat pogostejsi
        double[] desno = verjetnosti(1, 0.55, 7, 0.45);
        List<RazlagaListka.Vrstica> vrstice = List.of(
                vrstica(gotovo(11), desno),
                vrstica(gotovo(11), gotovo(4)),
                vrstica(prazno(), prazno()));
        RazlagaListka.Izid brez = RazlagaListka.razlozi(vrstice, prazno(), prazno(), PogostostNizov.VGRAJENA, 0);
        assertEquals(1, brez.nizi().get(0).desno());
        RazlagaListka.Izid z = RazlagaListka.razlozi(vrstice, prazno(), prazno(), PogostostNizov.VGRAJENA, 0.5);
        assertEquals(7, z.nizi().get(0).desno());
        assertTrue(z.nizi().get(0).negotovoDesno(), "odlocila je zgodovina, ne pisava");
        assertFalse(z.nizi().get(1).negotovoDesno(), "jasna celica ostane gotova");
    }

    // ---------- Pomozno ----------

    private static RazlagaListka.Vrstica vrstica(double[] levo, double[] desno) {
        return new RazlagaListka.Vrstica(levo, desno);
    }

    private static double[] gotovo(int vrednost) {
        return verjetnosti(vrednost, 1.0);
    }

    private static double[] prazno() {
        return gotovo(RazlagaListka.PRAZNO);
    }

    /* Porazdelitev: pari (vrednost, verjetnost); ostalo dobi drobtino. */
    private static double[] verjetnosti(double... pari) {
        double[] p = new double[RazlagaListka.VELIKOST];
        java.util.Arrays.fill(p, 1e-6);
        List<Double> seznam = new ArrayList<>();
        for (double v : pari) {
            seznam.add(v);
        }
        for (int i = 0; i + 1 < seznam.size(); i += 2) {
            p[(int) (double) seznam.get(i)] = seznam.get(i + 1);
        }
        return p;
    }
}
