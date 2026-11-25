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
       fotografija, stevke cez crto). Strozji je prvi, ker blazji iz sence naredi crto.
       Pri vsakem pragu najprej postavitev novih listkov, nato starih (GeometrijaListka.TABELE). */
    public static Tabela najdi(Temnost listek) {
        boolean[] strogo = listek.crnilo(8 * GeometrijaListka.PX_NA_MM, 0.10f, 0.15f);
        for (GeometrijaListka.Stolpca s : GeometrijaListka.TABELE) {
            Tabela t = najdi(listek, strogo, s, 0.55, 0.7);
            if (t != null) {
                return t;
            }
        }
        boolean[] blago = listek.crnilo(8 * GeometrijaListka.PX_NA_MM, 0.06f, 0.15f);
        for (GeometrijaListka.Stolpca s : GeometrijaListka.TABELE) {
            Tabela t = najdi(listek, blago, s, 0.45, 0.6);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Tabela najdi(Temnost listek, boolean[] crnilo, GeometrijaListka.Stolpca stolpca,
                                double pokritostCrte, double pokritostRoba) {
        int r = GeometrijaListka.PX_NA_MM;
        int w = listek.sirina;
        int[] levi = {(int) Math.round((stolpca.levi()[0] + 0.8) * r),
                (int) Math.round((stolpca.levi()[1] - 0.8) * r)};
        int[] desni = {(int) Math.round((stolpca.desni()[0] + 0.8) * r),
                (int) Math.round((stolpca.desni()[1] - 0.8) * r)};
        int od = (int) Math.round(GeometrijaListka.TABELA_OD * r);
        int doo = Math.min(listek.visina - 2, (int) Math.round(GeometrijaListka.TABELA_DO * r));

        // Crte in celice vsakega stolpca posebej: na prepognjenem listku (F2.6) je
        // desni stolpec lahko za nekaj px zamaknjen ali nagnjen proti levemu, zato
        // ena vodoravna crta cez oba stolpca hkrati ne bi obstajala.
        List<Celica> leve = celiceStolpca(crnilo, w, od, doo, levi, stolpca.levi(), r,
                pokritostCrte, pokritostRoba, stolpca.najmanjVisina());
        List<Celica> desne = celiceStolpca(crnilo, w, od, doo, desni, stolpca.desni(), r,
                pokritostCrte, pokritostRoba, stolpca.najmanjVisina());

        // vrstica = leva in desna celica z bliznjim srediscem (vrstice so vsaj 2,5 mm visoke)
        List<Vrstica> celice = new ArrayList<>();
        int j = 0;
        for (Celica l : leve) {
            while (j < desne.size() && sredina(desne.get(j)) < sredina(l) - NAJVEC_ZAMIK_STOLPCEV * r) {
                j++;
            }
            if (j < desne.size() && Math.abs(sredina(desne.get(j)) - sredina(l)) <= NAJVEC_ZAMIK_STOLPCEV * r) {
                celice.add(new Vrstica(l, desne.get(j)));
                j++;
            }
        }
        if (celice.size() < 2) {
            return null;
        }
        return new Tabela(List.copyOf(celice.subList(0, celice.size() - 1)), celice.getLast());
    }

    /* Najvecji navpicni zamik med levo in desno celico iste vrstice (mm). */
    private static final double NAJVEC_ZAMIK_STOLPCEV = 1.2;
    /* Toleranca crte navzgor in navzdol (px): nagnjena crta na prepognjenem listku. */
    private static final int TOLERANCA_CRTE = 2;
    /* Delez sirine stolpca na vsakem koncu, kjer mora biti tudi crta (precrtanje ni crta). */
    private static final double DELEZ_KONCA = 0.12;
    /* Koliko px nad in pod crto se isceta njena konca (nagnjena crta). */
    private static final int ZDRS_KONCA = 5;

    private static double sredina(Celica c) {
        return (c.y0() + c.y1()) / 2.0;
    }

    /* Celice enega stolpca od zgoraj navzdol: pasovi med zaporednima vodoravnima
       crtama, ki imajo oba navpicna robova. */
    private static List<Celica> celiceStolpca(boolean[] crnilo, int w, int od, int doo, int[] x, double[] stolpec, int r,
                                              double pokritostCrte, double pokritostRoba, double najmanjVisina) {
        int t = TOLERANCA_CRTE;
        // konca stolpca: crta tabele sega od roba do roba celice, precrtana vrednost
        // (sodnik je napacno stevilko precrtal) pa ne - v ozki celici (17 mm, od
        // 24. 11. 2026) bi precrtanje sicer pokrilo dovolj sirine, da bi celico
        // razdelilo na dve in zamaknilo vse vrstice stolpca
        int konec = Math.max(3, (int) Math.round((x[1] - x[0] + 1) * DELEZ_KONCA));
        int[] levi = {x[0], x[0] + konec - 1};
        int[] desni = {x[1] - konec + 1, x[1]};
        List<int[]> crte = new ArrayList<>();
        for (int y = Math.max(t, od); y < doo && y < crnilo.length / w - t; y++) {
            if (pokritost(crnilo, w, y, x, t) <= pokritostCrte) {
                continue;
            }
            int z = y;
            while (y + 1 < doo && pokritost(crnilo, w, y + 1, x, t) > pokritostCrte) {
                y++;
            }
            // konca iscemo malo sire: na prepognjenem ali nagnjenem listku crta proti
            // koncu zdrsne za nekaj px
            boolean levo = false;
            boolean desno = false;
            int ymax = crnilo.length / w - t - 1;
            for (int v = Math.max(t, z - ZDRS_KONCA); v <= Math.min(ymax, y + ZDRS_KONCA); v++) {
                levo |= pokritost(crnilo, w, v, levi, t) > pokritostCrte;
                desno |= pokritost(crnilo, w, v, desni, t) > pokritostCrte;
            }
            if (levo && desno) {
                crte.add(new int[] {z, y});
            }
        }
        // pasovi med zaporednima crtama; toleranca razsiri crto za t px na vsako
        // stran: notranjost se zacne za njo
        int n = crte.size() - 1;
        int[][] pasovi = new int[Math.max(0, n)][];
        boolean[] zRoboma = new boolean[Math.max(0, n)];
        for (int k = 0; k < n; k++) {
            int y0 = crte.get(k)[1] + 1 - (t - 1);
            int y1 = crte.get(k + 1)[0] - 1 + (t - 1);
            pasovi[k] = new int[] {y0, y1};
            zRoboma[k] = y1 - y0 + 1 >= NAJMANJ_PAS * r
                    && celicaStolpca(crnilo, w, y0, y1, stolpec, r, pokritostRoba) != null;
        }
        // Pregib (guba prepognjenega listka) cez celico je videti kot crta in celico
        // razdeli na dva pasova. Zaporedna pasova z navpicnima robovoma sta zato ena
        // celica: med vrsticama nizov je vedno razmik brez navpicnih robov.
        List<Celica> celice = new ArrayList<>();
        int najmanj = (int) Math.round(najmanjVisina * r);
        for (int i = 0; i < n; i++) {
            if (!zRoboma[i]) {
                continue;
            }
            int j = i;
            while (j + 1 < n && zRoboma[j + 1]) {
                j++;
            }
            int y0 = pasovi[i][0];
            int y1 = pasovi[j][1];
            int h = y1 - y0 + 1;
            if (h >= najmanj && h <= 12 * r) {
                Celica c = celicaStolpca(crnilo, w, y0, y1, stolpec, r, pokritostRoba);
                if (c != null) {
                    celice.add(c);
                }
            }
            i = j;
        }
        return celice;
    }

    /* Najnizji pas med crtama, ki je lahko del celice (mm); nizji je razmik med
       vrsticama ali dvojna crta, ki se ne zdruzuje. */
    private static final double NAJMANJ_PAS = 1.5;

    /* Delez stolpca, ki ima v vrstici y (s toleranco t px navzgor in navzdol) crnilo. */
    private static double pokritost(boolean[] crnilo, int w, int y, int[] x, int t) {
        int zadetkov = 0;
        for (int i = x[0]; i <= x[1]; i++) {
            for (int d = -t; d <= t; d++) {
                if (crnilo[(y + d) * w + i]) {
                    zadetkov++;
                    break;
                }
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
