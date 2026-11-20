/* Stalna mesta na listu vadnice stevk (A4 pokoncno, 210 x 297 mm, mm od
   zgornjega levega roba).

   List natisne vmesnik (komponente/ListVadnice.tsx, slog.css razdelek »Vadnica
   stevk«): trije polni crni kvadrati 6 x 6 mm (zgoraj levo, spodaj levo, spodaj
   desno), QR koda 22 x 22 mm zgoraj desno (»TKV:1:<id lista>«, verzija 1) in 14
   vrstic po 10 okenc 14 x 10 mm s crto 0,45 mm. Vrstica i ima vzorec VREDNOSTI[i]
   levo od okenc.
   OB SPREMEMBI LISTA SPREMENI OBOJE in povecaj RAZLICICA (tudi v vmesniku), sicer
   se natisnjeni listi preberejo narobe. Ze natisnjene liste stare razlicice
   bralnik zavrne (BranjeVadnice). */
package si.turnirko.branje;

public final class GeometrijaVadnice {

    /* Razlicica postavitve v QR kodi. */
    public static final int RAZLICICA = 1;

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

    /* Okenca: 10 v vrstici, 14 x 10 mm, 2 mm narazen; vrstice na 16 mm. */
    public static final int STOLPCEV = 10;
    public static final double OKENCE_X = 38;
    public static final double OKENCE_Y = 46;
    public static final double OKENCE_SIRINA = 14;
    public static final double OKENCE_VISINA = 10;
    public static final double KORAK_X = 16;
    public static final double KORAK_Y = 16;

    /* Kaj se pise v vrstico: stevke in dvomestna stevila, ki jih je na listkih
       najvec in jih je najtezje prebrati sklenjene (10-13). */
    public static final int[] VREDNOSTI = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
    public static final int VRSTIC = VREDNOSTI.length;
    public static final int OKENC = VRSTIC * STOLPCEV;

    /* Afina napoved iskalnega vzorca QR (178 mm od oznake zgoraj levo) se pri
       nagnjenem telefonu na A4 zmoti za vec kot na listku. */
    public static final Postavitev POSTAVITEV = new Postavitev(SIRINA, VISINA, ZGORAJ_LEVO, SPODAJ_LEVO,
            SPODAJ_DESNO, STRANICA_OZNAKE, QR_X, QR_Y, QR_STRANICA, QR_MODULOV, 40);

    /* Zunanji rob okenca (mm): x0, y0, x1, y1; okence = vrstica * STOLPCEV + stolpec. */
    public static double[] okence(int okence) {
        int vrstica = okence / STOLPCEV;
        int stolpec = okence % STOLPCEV;
        double x0 = OKENCE_X + stolpec * KORAK_X;
        double y0 = OKENCE_Y + vrstica * KORAK_Y;
        return new double[] {x0, y0, x0 + OKENCE_SIRINA, y0 + OKENCE_VISINA};
    }

    private GeometrijaVadnice() {}
}
