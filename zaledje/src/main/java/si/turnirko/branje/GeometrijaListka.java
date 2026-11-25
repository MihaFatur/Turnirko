/* Stalna mesta na sodniskem listku (mm od zgornjega levega roba listka 105 x 99 mm),
   po katerih bralnik poravna fotografijo.

   Listek natisne vmesnik (komponente/Listek.tsx, slog.css razdelek »Sodniski
   listek«): trije polni crni kvadrati 4 x 4 mm (zgoraj levo, spodaj levo, spodaj
   desno) in QR koda 13 x 13 mm zgoraj desno. Stolpca tock sledita mrezi
   --listek-stolpci (TABELA spodaj).
   OB SPREMEMBI LISTKA SPREMENI OBOJE - sicer bralnik isce celice na napacnem mestu.
   Ze natisnjeni listki prejsnje postavitve se se berejo (TABELE: bralnik poskusi
   najprej novo, nato staro).

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

    /* Postavitev tabele tock: zunanja robova celice levega in desnega igralca (mm, x)
       in najmanjsa visina celice (mm): nizji pas med crtama ni celica. */
    public record Stolpca(double[] levi, double[] desni, double najmanjVisina) {}

    /* Od 24. 11. 2026 (lastnik): visoke ozke celice 17 mm brez crte med deseticami
       in enicami, mreza 14,5 | 11,25 | 17 | 5,5 | 17 | 1fr od 7 mm. Visina je 9 mm
       pri petih nizih (prej okoli 6 mm), zato stevka redkeje sega cez crto.
       Dvopicje je na sredini listka: pregib na pol gre med celicama. Najnizja celica
       ima ~6,8 mm (sedem nizov): pregib cez celico jo razdeli na dva nizja pasova,
       ki se zdruzita nazaj (CeliceListka). */
    public static final Stolpca TABELA = new Stolpca(new double[] {32.75, 49.75}, new double[] {55.25, 72.25}, 5.0);

    /* Listki do 24. 11. 2026 (generalka): mreza 15 | 1fr | 5,5 | 1fr v vsebini 7-98 mm,
       celici 35 mm siroki, pri sedmih nizih vsaj 4,2 mm visoki. */
    public static final Stolpca TABELA_SIROKA = new Stolpca(
            new double[] {22, 22 + (98 - 7 - 15 - 5.5) / 2}, new double[] {98 - (98 - 7 - 15 - 5.5) / 2, 98}, 2.5);

    /* Vrstni red, v katerem jih bralnik poskusi: nova je pogostejsa. Stari stolpci
       na novem listku ne najdejo celic (crte so prekratke), novi na starem tudi ne
       (na stalnih legah nima navpicnih robov). */
    public static final java.util.List<Stolpca> TABELE = java.util.List.of(TABELA, TABELA_SIROKA);

    /* Navpicno obmocje, v katerem so celice (pod kontekstom, nad nogo). */
    public static final double TABELA_OD = 16;
    public static final double TABELA_DO = 90;

    /* Okence za stevilko mize (S1, od 22. 11. 2026): zunanji robovi {x0, y0, x1, y1},
       crta 0,45 mm kot okenca vadnice (CeliceVadnice isce robove). Lezi v glavi
       levo od QR (mirna cona QR je 3 mm: 80 + 3 < 85) in nad TABELA_OD, zato
       iskanja celic tabele ne moti. Listki pred S1 okenca nimajo (MizaListka vrne null). */
    public static final double[] MIZA = {62, 5, 80, 12};

    /* Locljivost poravnane slike listka. 10 px/mm: crta 0,3 mm je 3 px, stevka
       visoka 5 mm ima 50 px - dovolj za razrez in mrezo 28 x 28. */
    public static final int PX_NA_MM = 10;

    private GeometrijaListka() {}
}
