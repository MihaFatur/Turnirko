/* Diagnostika branja (DiagnozaBranja, 24. 11. 2026).

   Pravila, ki jih test varuje (docs/pravila/branje-listkov.md, »Diagnostika«):
   - vzrok napacne celice sledi korakom bralnika: brez crnila -> crnilo v prazni ->
     razrez brez pravega stevila stevk -> mreza (nobena taka razlaga ne da prave
     vrednosti) -> izbira razlage (porazdelitev celice ima na vrhu drugo) -> pravila;
   - tiha napaka je napacna celica, ki ni oznacena za pregled;
   - stevke se merijo na razlagi, ki jo vzame ucenje (pravo stevilo stevk, najvecja
     verjetnost); celica brez nje se steje posebej (v ucenje ne pride nikoli);
   - umerjenost: mreza po najvecji verjetnosti porazdelitve, koncno po zaupanju razlage;
   - galerija samo, ce je podan izrez, in najvec NAJVEC_NAPAK. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import si.turnirko.branje.RazlagaListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.dto.DiagnozaNaboraDto;
import si.turnirko.storitve.DiagnozaBranja.Celica;
import si.turnirko.storitve.DiagnozaBranja.Vzrok;

class DiagnozaBranjaTest {

    /* Navidezna mreza: stevka je zapisana v prvem pikslu izreza. */
    private static final ToIntFunction<float[]> MREZA = v -> (int) v[0];

    @Test
    void vzrokSlediKorakomBralnika() {
        // »11« v eni potezi: razrez ponudi samo eno stevko
        assertEquals(Vzrok.RAZREZ, DiagnozaBranja.vzrok(
                celica(11, 1, false, vsebina(razlaga(0.9, 1)), p(1, 0.9), 0.9), MREZA));
        // mreza sedmico prebere kot enico
        assertEquals(Vzrok.MREZA, DiagnozaBranja.vzrok(
                celica(7, 1, false, vsebina(razlaga(1, 1)), p(1, 0.95), 0.95), MREZA));
        // mreza bi »11« prebrala prav, a je obveljala razlaga z eno stevko (4)
        assertEquals(Vzrok.IZBIRA_RAZREZA, DiagnozaBranja.vzrok(
                celica(11, 4, true, vsebina(razlaga(0.3, 1, 1), razlaga(0.7, 4)), p(4, 0.7), 0.6), MREZA));
        // mreza in porazdelitev imata 9, pravila (pogostost) sta izbrala 8
        assertEquals(Vzrok.PRAVILA, DiagnozaBranja.vzrok(
                celica(9, 8, true, vsebina(razlaga(1, 9)), p(9, 0.6), 0.55), MREZA));
        // napisano 5, razrez ni nasel crnila
        assertEquals(Vzrok.NI_CRNILA, DiagnozaBranja.vzrok(
                celica(5, null, true, prazna(), p(RazlagaListka.PRAZNO, 1), 0.9), MREZA));
        // prazna celica (za zadnjim nizom), v njej crnilo (crta, sum)
        assertEquals(Vzrok.CRNILO_V_PRAZNI, DiagnozaBranja.vzrok(
                celica(null, 1, false, vsebina(razlaga(1, 1)), p(1, 0.9), 0.9), MREZA));
    }

    @Test
    void zbirnikSteje() {
        DiagnozaBranja.Zbirnik z = new DiagnozaBranja.Zbirnik(MREZA);
        List<Celica> listek = List.of(
                celica(11, 11, false, vsebina(razlaga(1, 1, 1)), p(11, 0.995), 0.995),   // prav
                celica(7, 1, false, vsebina(razlaga(1, 1)), p(1, 0.95), 0.96),           // mreza, tiha
                celica(11, 1, true, vsebina(razlaga(1, 1)), p(1, 0.7), 0.7),             // razrez, ujeta
                celica(9, 9, false, vsebina(razlaga(1, 9)), p(9, 0.85), 0.92));          // prav
        z.dodaj(listek, (niz, stran) -> "slika");
        DiagnozaNaboraDto.Del d = z.dto();

        assertEquals(1, d.listkov());
        assertEquals(4, d.celic());
        assertEquals(2, d.napacnih());
        assertEquals(new DiagnozaNaboraDto.Vzrok("MREZA", 1, 0), vzrok(d, "MREZA"));
        assertEquals(new DiagnozaNaboraDto.Vzrok("RAZREZ", 0, 1), vzrok(d, "RAZREZ"));

        // stevke: 11 (dve prav), 7 (narobe), 9 (prav); celica »11« brez dveh stevk ni zraven
        assertEquals(4, d.stevke().celicZZapisom());
        assertEquals(1, d.stevke().brezRazreza());
        assertEquals(4, d.stevke().stevk());
        assertEquals(3, d.stevke().prav());
        DiagnozaNaboraDto.PoStevki sedem = d.stevke().poStevkah().get(7);
        assertEquals(1, sedem.vseh());
        assertEquals(0, sedem.prav());
        assertEquals(1, sedem.zamenjave().getFirst().prebrano());

        // umerjenost mreze: 0,995 -> [0,99, 1], 0,95 -> [0,95, 0,99), 0,7 -> [0,5, 0,8), 0,85 -> [0,8, 0,9)
        assertEquals(new DiagnozaNaboraDto.Razred(0.99, 1, 1, 1), d.umerjenostMreze().get(5));
        assertEquals(new DiagnozaNaboraDto.Razred(0.95, 0.99, 1, 0), d.umerjenostMreze().get(4));
        assertEquals(new DiagnozaNaboraDto.Razred(0.5, 0.8, 1, 0), d.umerjenostMreze().get(1));
        // koncno: 0,96 in 0,995 in 0,92 in 0,7
        assertEquals(1, d.umerjenostKoncna().get(4).celic());
        assertEquals(0, d.umerjenostKoncna().get(4).prav());

        assertEquals(2, d.napake().size());
        DiagnozaNaboraDto.Napaka prva = d.napake().getFirst();
        assertEquals("MREZA", prva.vzrok());
        assertTrue(prva.tiha());
        assertEquals(7, prva.napisano());
        assertEquals(1, prva.prebrano());
        assertEquals("slika", prva.slika());
    }

    @Test
    void galerijaSamoZIzrezomInOmejena() {
        DiagnozaBranja.Zbirnik brez = new DiagnozaBranja.Zbirnik(MREZA);
        brez.dodaj(List.of(celica(7, 1, false, vsebina(razlaga(1, 1)), p(1, 0.9), 0.9)), null);
        assertTrue(brez.dto().napake().isEmpty());
        assertEquals(1, brez.dto().napacnih());

        DiagnozaBranja.Zbirnik polna = new DiagnozaBranja.Zbirnik(MREZA);
        List<Celica> veliko = new ArrayList<>();
        for (int i = 0; i < DiagnozaBranja.NAJVEC_NAPAK + 5; i++) {
            veliko.add(celica(7, 1, false, vsebina(razlaga(1, 1)), p(1, 0.9), 0.9));
        }
        polna.dodaj(veliko, (niz, stran) -> "s");
        assertEquals(DiagnozaBranja.NAJVEC_NAPAK, polna.dto().napake().size());
        assertEquals(DiagnozaBranja.NAJVEC_NAPAK + 5, polna.dto().napacnih());
    }

    // ---------- pomozno ----------

    private static DiagnozaNaboraDto.Vzrok vzrok(DiagnozaNaboraDto.Del d, String ime) {
        return d.vzroki().stream().filter(v -> v.vzrok().equals(ime)).findFirst().orElseThrow();
    }

    private static Celica celica(Integer napisano, Integer prebrano, boolean oznacena, RazrezCelice.Vsebina v,
                                 double[] porazdelitev, double zaupanje) {
        return new Celica(0, 0, napisano, prebrano, oznacena, v, porazdelitev, zaupanje);
    }

    private static RazrezCelice.Vsebina vsebina(RazrezCelice.Razlaga... razlage) {
        return new RazrezCelice.Vsebina(false, List.of(razlage), 0.05, razlage[0].stevke().size(), 0.2);
    }

    private static RazrezCelice.Vsebina prazna() {
        return new RazrezCelice.Vsebina(true, List.of(), 0, 0, 0);
    }

    /* Razlaga z danimi stevkami (navidezna mreza jih prebere natanko tako). */
    private static RazrezCelice.Razlaga razlaga(double verjetnost, int... stevke) {
        List<RazrezCelice.Stevka> s = new ArrayList<>();
        for (int st : stevke) {
            float[] vhod = new float[784];
            vhod[0] = st;
            s.add(new RazrezCelice.Stevka(vhod));
        }
        return new RazrezCelice.Razlaga(s, verjetnost);
    }

    /* Porazdelitev celice z verjetnostjo na eni vrednosti, ostanek enakomerno. */
    private static double[] p(int vrednost, double verjetnost) {
        double[] p = new double[RazlagaListka.VELIKOST];
        double ostanek = (1 - verjetnost) / (p.length - 1);
        java.util.Arrays.fill(p, ostanek);
        p[vrednost] = verjetnost;
        return p;
    }
}
