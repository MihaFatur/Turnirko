/* Okenca na poravnanem listu vadnice stevk.

   Lega okenc je stalna (GeometrijaVadnice), poravnava pa ni popolna: list A4 na
   mizi ni cisto raven in iskalni vzorec QR je dolocen na pol modula natancno.
   Zato se vsak rob okenca poisce po crti v pasu +-1,5 mm okrog pricakovane lege
   (sosednje okence je 2 mm stran, njegov rob ne sme priti v pas); ce crte ni
   (zbledel tisk), velja pricakovana lega.

   Pasti, ki ju je pokazala simulacija (VadnicaTest):
   - crta 0,45 mm je na fotografiji pogosto pretrgana (pike), poteza »1« ob robu pa
     polna; zato je meja pokritosti nizka in velja crta, NAJBLIZJA pricakovani
     legi, ne najmocnejsa;
   - stevka se crte pogosto dotika (»1« v »11« tik ob levem robu) ali sega cez
     njo (»0« v »10« cez desni rob). Taka crta je sirsa od natisnjene; odreze se
     samo debelina crte, ne cela sirina, sicer prva stevka izgine in iz »10«
     nastane razpolovljena nicla.

   Vrnjena celica je NOTRANJOST, zmanjsana za RazrezCelice.ODMIK, tako da razrez
   (in slika v ZlatiNabor.slikaCelic), ki odmik spet odsteje, zacne takoj za crto. */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public final class CeliceVadnice {

    private CeliceVadnice() {}

    /* Pas iskanja crte okrog pricakovane lege (px pri 10 px/mm). Rob sosednjega
       okenca je 20 px stran in ne sme v pas niti pri zamiku poravnave. */
    private static final int ISKANJE = 12;
    /* Delez dolzine roba (srednje tri petine), ki mora biti crnilo, da je
       stolpec/vrstica crta; crnilo se steje z enim px tolerance precno na crto
       (pretrgana ali rahlo posevna crta). */
    private static final double POKRITOST = 0.35;
    /* Natisnjena crta 0,45 mm: po poravnavi s toleranco do ~7 px. Sirsa crta ima
       ob sebi stevko. */
    private static final int DEBELINA = 5;
    private static final int NAJVEC_SIRINA_CRTE = 10;

    /* Notranjost vseh okenc po vrsti (vrstica * STOLPCEV + stolpec), za razrez. */
    public static List<CeliceListka.Celica> najdi(Temnost list) {
        int r = GeometrijaListka.PX_NA_MM;
        boolean[] crnilo = list.crnilo(8 * r, 0.10f, 0.15f);
        int w = list.sirina;
        int h = list.visina;
        int o = RazrezCelice.ODMIK - 1;
        List<CeliceListka.Celica> okenca = new ArrayList<>(GeometrijaVadnice.OKENC);
        for (int i = 0; i < GeometrijaVadnice.OKENC; i++) {
            double[] mm = GeometrijaVadnice.okence(i);
            int x0 = (int) Math.round(mm[0] * r);
            int y0 = (int) Math.round(mm[1] * r);
            int x1 = (int) Math.round(mm[2] * r);
            int y1 = (int) Math.round(mm[3] * r);
            // crta se meri le na srednjih treh petinah roba: stevka v kotu ne naredi crte
            int sy0 = y0 + (y1 - y0) / 5;
            int sy1 = y1 - (y1 - y0) / 5;
            int sx0 = x0 + (x1 - x0) / 5;
            int sx1 = x1 - (x1 - x0) / 5;
            IntPredicate navpicna = x -> x >= 1 && x < w - 1 && pokrito(sy0, sy1, y -> y >= 0 && y < h
                    && (crnilo[y * w + x - 1] || crnilo[y * w + x] || crnilo[y * w + x + 1]));
            IntPredicate vodoravna = y -> y >= 1 && y < h - 1 && pokrito(sx0, sx1, x -> x >= 0 && x < w
                    && (crnilo[(y - 1) * w + x] || crnilo[y * w + x] || crnilo[(y + 1) * w + x]));
            okenca.add(new CeliceListka.Celica(
                    notranjiRob(navpicna, x0, true) - o,
                    notranjiRob(vodoravna, y0, true) - o,
                    notranjiRob(navpicna, x1, false) + o,
                    notranjiRob(vodoravna, y1, false) + o));
        }
        return okenca;
    }

    private static boolean pokrito(int od, int doo, IntPredicate crnilo) {
        int zadetkov = 0;
        for (int i = od; i <= doo; i++) {
            if (crnilo.test(i)) {
                zadetkov++;
            }
        }
        return zadetkov > POKRITOST * (doo - od + 1);
    }

    /* Prvi px notranjosti ob robu z zunanjim robom crte pri p (zacetni: levi ali
       zgornji rob, notranjost je vecja koordinata). Crta je zaporedje stolpcev
       (vrstic), ki so crta; velja tista, katere sredina je najblize p. */
    private static int notranjiRob(IntPredicate jeCrta, int p, boolean zacetni) {
        int najmanjsaRazdalja = Integer.MAX_VALUE;
        int zunaj = -1;
        int znotraj = -1;
        int i = p - ISKANJE;
        while (i <= p + ISKANJE) {
            if (!jeCrta.test(i)) {
                i++;
                continue;
            }
            int od = i;
            while (i + 1 <= p + ISKANJE && jeCrta.test(i + 1)) {
                i++;
            }
            int razdalja = Math.abs((od + i) / 2 - p);
            if (razdalja < najmanjsaRazdalja) {
                najmanjsaRazdalja = razdalja;
                zunaj = zacetni ? od : i;
                znotraj = zacetni ? i : od;
            }
            i++;
        }
        int smer = zacetni ? 1 : -1;
        if (zunaj < 0) {
            // crte ni: pricakovana lega in debelina natisnjene crte
            return p + smer * (DEBELINA + 1);
        }
        if (Math.abs(znotraj - zunaj) + 1 > NAJVEC_SIRINA_CRTE) {
            // stevka je zlita s crto: odreze se samo crta. Ce je zunanji rob zlitja
            // blizu pricakovanega, je stevka znotraj (dotika se crte) in zunanji rob
            // je rob crte; sicer sega stevka cez crto navzven in velja pricakovana lega.
            int zunanjiRobCrte = Math.abs(zunaj - p) <= 3 ? zunaj : p;
            return zunanjiRobCrte + smer * (DEBELINA + 1);
        }
        return znotraj + smer;
    }
}
