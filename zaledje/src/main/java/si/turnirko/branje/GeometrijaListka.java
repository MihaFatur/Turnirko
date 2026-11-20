/* Stalna mesta na sodniskem listku (mm od zgornjega levega roba listka 105 x 99 mm),
   po katerih bralnik poravna fotografijo.

   Listek natisne vmesnik (komponente/Listek.tsx, slog.css razdelek »Sodniski
   listek«): trije polni crni kvadrati 4 x 4 mm (zgoraj levo, spodaj levo, spodaj
   desno) in QR koda 13 x 13 mm zgoraj desno. Stolpca tock sledita mrezi
   --listek-stolpci (15 mm | 1fr | 5,5 mm | 1fr) v vsebini med 7 mm in 98 mm.
   OB SPREMEMBI LISTKA SPREMENI OBOJE - sicer bralnik isce celice na napacnem mestu.

   Navpicna lega celic NI stalna (vrstice nizov se raztegnejo glede na stevilo
   nizov in dolzino imen), zato jih CeliceListka poisce po crtah. */
package si.turnirko.branje;

public final class GeometrijaListka {

    public static final double SIRINA = 105;
    public static final double VISINA = 99;

    /* Sredisca oznak (stranica 4 mm; 7 mm od levega/desnega in 5 mm od zgornjega/spodnjega roba). */
    public static final double[] ZGORAJ_LEVO = {9, 7};
    public static final double[] SPODAJ_LEVO = {9, 92};
    public static final double[] SPODAJ_DESNO = {96, 92};
    public static final double STRANICA_OZNAKE = 4;

    /* QR koda: zgornji levi kot (85, 5), stranica 13 mm, verzija 1 (21 modulov). */
    public static final double QR_X = 85;
    public static final double QR_Y = 5;
    public static final double QR_STRANICA = 13;
    public static final int QR_MODULOV = 21;

    /* Sredisce iskalnega vzorca QR zgoraj desno: 3,5 modula od zgornjega in desnega roba. */
    public static final double[] QR_ZGORAJ_DESNO = {
            QR_X + QR_STRANICA - 3.5 * QR_STRANICA / QR_MODULOV,
            QR_Y + 3.5 * QR_STRANICA / QR_MODULOV};
    /* Sredisci ostalih dveh iskalnih vzorcev (ZXing vrne vse tri). */
    public static final double[] QR_ZGORAJ_LEVO = {
            QR_X + 3.5 * QR_STRANICA / QR_MODULOV, QR_Y + 3.5 * QR_STRANICA / QR_MODULOV};
    public static final double[] QR_SPODAJ_LEVO = {
            QR_X + 3.5 * QR_STRANICA / QR_MODULOV, QR_Y + QR_STRANICA - 3.5 * QR_STRANICA / QR_MODULOV};

    /* Stolpca tock: vsebina 7-98 mm, mreza 15 | 1fr | 5,5 | 1fr. */
    private static final double VSEBINA_OD = 7;
    private static final double VSEBINA_DO = 98;
    private static final double STOLPEC = (VSEBINA_DO - VSEBINA_OD - 15 - 5.5) / 2;
    public static final double[] LEVI_STOLPEC = {VSEBINA_OD + 15, VSEBINA_OD + 15 + STOLPEC};
    public static final double[] DESNI_STOLPEC = {VSEBINA_DO - STOLPEC, VSEBINA_DO};

    /* Navpicno obmocje, v katerem so celice (pod kontekstom, nad nogo). */
    public static final double TABELA_OD = 16;
    public static final double TABELA_DO = 90;

    /* Locljivost poravnane slike listka. 10 px/mm: crta 0,3 mm je 3 px, stevka
       visoka 5 mm ima 50 px - dovolj za razrez in mrezo 28 x 28. */
    public static final int PX_NA_MM = 10;

    private GeometrijaListka() {}
}
