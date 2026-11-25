/* Stalna mesta na listu vadnice stevk (A4 pokoncno, 210 x 297 mm, mm od
   zgornjega levega roba).

   List natisne vmesnik (komponente/ListVadnice.tsx, pomozno/vadnica.ts, slog.css
   razdelek »Vadnica stevk«): trije polni crni kvadrati 6 x 6 mm (zgoraj levo,
   spodaj levo, spodaj desno), QR koda 22 x 22 mm zgoraj desno (»TKV:<razlicica>:<id
   lista>«, verzija 1) in vrstice okenc s crto 0,45 mm. Vrstica i ima vzorec
   vrednosti levo od okenc. Oznake in koda so pri vseh razlicicah na istem mestu,
   okenca pa ne (Okenca).
   OB SPREMEMBI LISTA SPREMENI OBOJE in dodaj RAZLICICO (tudi v vmesniku), sicer se
   natisnjeni listi preberejo narobe. Bralnik pozna vse razlicice v RAZLICICE, zato
   se ze natisnjeni listi prejsnje se preberejo (BranjeVadnice). */
package si.turnirko.branje;

import java.util.List;

public final class GeometrijaVadnice {

    public static final double SIRINA = 210;
    public static final double VISINA = 297;

    /* Oznake 6 x 6 mm, 12 mm od levega roba in 12 mm od zgornjega/spodnjega,
       desna 12 mm od desnega: tiskalnik jih ne odreze tudi pri robu 10 mm. */
    public static final double STRANICA_OZNAKE = 6;
    public static final double[] ZGORAJ_LEVO = {15, 15};
    public static final double[] SPODAJ_LEVO = {15, 282};
    public static final double[] SPODAJ_DESNO = {195, 282};

    /* QR: zgornji levi kot (173, 9), stranica 22 mm (21 modulov po ~1 mm). */
    public static final double QR_X = 173;
    public static final double QR_Y = 9;
    public static final double QR_STRANICA = 22;
    public static final int QR_MODULOV = 21;

    /* Afina napoved iskalnega vzorca QR (178 mm od oznake zgoraj levo) se pri
       nagnjenem telefonu na A4 zmoti za vec kot na listku. */
    public static final Postavitev POSTAVITEV = new Postavitev(SIRINA, VISINA, ZGORAJ_LEVO, SPODAJ_LEVO,
            SPODAJ_DESNO, STRANICA_OZNAKE, QR_X, QR_Y, QR_STRANICA, QR_MODULOV, 40);

    /* Okenca ene razlicice lista: levi zgornji rob prvega okenca (x, y), mere
       okenca, razmik med okenci (korak), stevilo okenc v vrstici in vrednost
       vsake vrstice. Okence i je v vrstici i / stolpcev, stolpcu i % stolpcev. */
    public record Okenca(int razlicica, double x, double y, double sirina, double visina,
                         double korakX, double korakY, int stolpcev, int[] vrednosti) {

        public int vrstic() {
            return vrednosti.length;
        }

        public int okenc() {
            return vrednosti.length * stolpcev;
        }

        /* Vrednost, ki je napisana v okencu (vrednost njegove vrstice). */
        public int vrednost(int okence) {
            return vrednosti[okence / stolpcev];
        }

        /* Zunanji rob okenca (mm): x0, y0, x1, y1. */
        public double[] okence(int okence) {
            int vrstica = okence / stolpcev;
            int stolpec = okence % stolpcev;
            double x0 = x + stolpec * korakX;
            double y0 = y + vrstica * korakY;
            return new double[] {x0, y0, x0 + sirina, y0 + visina};
        }
    }

    /* Razlicica 1 (20. 11. 2026): 14 vrstic po 10 okenc 14 x 10 mm. Stevke in
       dvomestna stevila, ki jih je na listkih najvec in jih je najtezje prebrati
       sklenjene (10-13). */
    public static final Okenca V1 = new Okenca(1, 38, 46, 14, 10, 16, 16, 10,
            new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});

    /* Razlicica 2 (24. 11. 2026): okenca enake oblike kot celica za tocke na
       sodniskem listku (17 x 9 mm, GeometrijaListka.TABELA), da se mreza uci na
       pisavi v istih pogojih. 16 vrstic po 8 okenc; zadnji vrstici sta se enkrat
       11 in 10, ki sta na listkih najpogostejsi dvomestni vrednosti. */
    public static final Okenca V2 = new Okenca(2, 38, 46, 17, 9, 19, 13, 8,
            new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 11, 10});

    public static final List<Okenca> RAZLICICE = List.of(V1, V2);

    /* Razlicica, ki jo vmesnik zdaj natisne (pomozno/vadnica.ts RAZLICICA). */
    public static final int RAZLICICA = V2.razlicica();

    /* Okenca razlicice ali null, ce je bralnik ne pozna. */
    public static Okenca za(int razlicica) {
        return RAZLICICE.stream().filter(o -> o.razlicica() == razlicica).findFirst().orElse(null);
    }

    private GeometrijaVadnice() {}
}
