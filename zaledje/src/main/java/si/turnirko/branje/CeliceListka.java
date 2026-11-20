/* Celice za tocke na poravnanem listku: vrstice nizov in vrstica »Rezultat«.

   Vodoravna lega stolpcev je stalna (GeometrijaListka), navpicna pa ne - vrstice
   nizov se raztegnejo po stevilu nizov in dolzini imen. Zato se celice poiscejo
   po crtah: vodoravna crta prek obeh stolpcev je rob celice, celica pa je samo
   pas med dvema crtama, ki ima na obeh straneh obeh stolpcev tudi navpicni rob.
   Pas pod imeni (okvircka »Prvi servis«, »Timeout«) navpicnih robov nima in
   zato ni celica. Zadnja celica je »Rezultat«. */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.List;

public final class CeliceListka {

    private CeliceListka() {}

    /* Notranjost celice v px poravnane slike (brez crt). */
    public record Celica(int x0, int y0, int x1, int y1) {
        public int sirina() {
            return x1 - x0 + 1;
        }

        public int visina() {
            return y1 - y0 + 1;
        }
    }

    public record Vrstica(Celica levo, Celica desno) {}

    /* nizi po vrsti in vrstica Rezultat; null, ce tabele ni. */
    public record Tabela(List<Vrstica> nizi, Vrstica rezultat) {}

    /* Najprej strozji prag (cist listek), nato blazji (zamegljena ali temna
       fotografija, stevke cez crto). Strozji je prvi, ker blazji iz sence naredi crto. */
    public static Tabela najdi(Temnost listek) {
        Tabela t = najdi(listek, 0.10f, 0.55, 0.7);
        return t != null ? t : najdi(listek, 0.06f, 0.45, 0.6);
    }

    private static Tabela najdi(Temnost listek, float razlika, double pokritostCrte, double pokritostRoba) {
        int r = GeometrijaListka.PX_NA_MM;
        boolean[] crnilo = listek.crnilo(8 * r, razlika, 0.15f);
        int w = listek.sirina;
        int[] levi = {(int) Math.round((GeometrijaListka.LEVI_STOLPEC[0] + 0.8) * r),
                (int) Math.round((GeometrijaListka.LEVI_STOLPEC[1] - 0.8) * r)};
        int[] desni = {(int) Math.round((GeometrijaListka.DESNI_STOLPEC[0] + 0.8) * r),
                (int) Math.round((GeometrijaListka.DESNI_STOLPEC[1] - 0.8) * r)};
        int od = (int) Math.round(GeometrijaListka.TABELA_OD * r);
        int doo = Math.min(listek.visina - 2, (int) Math.round(GeometrijaListka.TABELA_DO * r));

        // vrstica je crta, ce je crnilo (z enim px tolerance navzgor in navzdol) cez vecino obeh stolpcev
        boolean[] jeCrta = new boolean[listek.visina];
        for (int y = Math.max(1, od); y < doo; y++) {
            jeCrta[y] = pokritost(crnilo, w, y, levi) > pokritostCrte && pokritost(crnilo, w, y, desni) > pokritostCrte;
        }
        List<int[]> crte = new ArrayList<>();
        for (int y = od; y < doo; y++) {
            if (!jeCrta[y]) {
                continue;
            }
            int z = y;
            while (y + 1 < doo && jeCrta[y + 1]) {
                y++;
            }
            crte.add(new int[] {z, y});
        }

        List<Vrstica> celice = new ArrayList<>();
        for (int k = 0; k + 1 < crte.size(); k++) {
            int y0 = crte.get(k)[1] + 1;
            int y1 = crte.get(k + 1)[0] - 1;
            int h = y1 - y0 + 1;
            if (h < 2.5 * r || h > 12 * r) {
                continue;
            }
            Celica l = celicaStolpca(crnilo, w, y0, y1, GeometrijaListka.LEVI_STOLPEC, r, pokritostRoba);
            Celica d = celicaStolpca(crnilo, w, y0, y1, GeometrijaListka.DESNI_STOLPEC, r, pokritostRoba);
            if (l != null && d != null) {
                celice.add(new Vrstica(l, d));
            }
        }
        if (celice.size() < 2) {
            return null;
        }
        return new Tabela(List.copyOf(celice.subList(0, celice.size() - 1)), celice.getLast());
    }

    private static double pokritost(boolean[] crnilo, int w, int y, int[] x) {
        int zadetkov = 0;
        for (int i = x[0]; i <= x[1]; i++) {
            if (crnilo[(y - 1) * w + i] || crnilo[y * w + i] || crnilo[(y + 1) * w + i]) {
                zadetkov++;
            }
        }
        return (double) zadetkov / (x[1] - x[0] + 1);
    }

    /* Celica stolpca v pasu y0..y1: oba navpicna robova morata obstajati blizu
       pricakovanih leg; notranjost se zacne za robom. */
    private static Celica celicaStolpca(boolean[] crnilo, int w, int y0, int y1, double[] stolpec, int r,
                                        double pokritost) {
        int levo = rob(crnilo, w, y0, y1, (int) Math.round(stolpec[0] * r), r, true, pokritost);
        int desno = rob(crnilo, w, y0, y1, (int) Math.round(stolpec[1] * r), r, false, pokritost);
        if (levo < 0 || desno < 0 || desno - levo < 10 * r) {
            return null;
        }
        return new Celica(levo, y0, desno, y1);
    }

    /* Prvi stolpec notranjosti ob robu (levem ali desnem); -1, ce roba ni. Rob je
       stolpec px, ki je crnilo v vecini pasu (ob robu lahko seze tudi pisava). */
    private static int rob(boolean[] crnilo, int w, int y0, int y1, int x, int r, boolean levi, double pokritost) {
        int okno = (int) Math.round(1.5 * r);
        int najdenRob = -1;
        for (int i = Math.max(1, x - okno); i <= Math.min(w - 2, x + okno); i++) {
            int zadetkov = 0;
            for (int y = y0; y <= y1; y++) {
                if (crnilo[y * w + i - 1] || crnilo[y * w + i] || crnilo[y * w + i + 1]) {
                    zadetkov++;
                }
            }
            if (zadetkov > pokritost * (y1 - y0 + 1)) {
                if (levi) {
                    najdenRob = i;        // skrajno desni stolpec levega roba
                } else if (najdenRob < 0) {
                    najdenRob = i;        // skrajno levi stolpec desnega roba
                }
            }
        }
        if (najdenRob < 0) {
            return -1;
        }
        return levi ? najdenRob + 2 : najdenRob - 2;
    }
}
