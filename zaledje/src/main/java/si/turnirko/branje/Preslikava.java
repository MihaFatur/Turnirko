/* Preslikava iz koordinat listka (mm) v koordinate fotografije (px): homografija
   (stiri pari tock - popravi tudi perspektivo telefona, ki listka ne snema
   natanko od zgoraj) ali afina preslikava (trije pari - zasuk, merilo, nagib). */
package si.turnirko.branje;

public final class Preslikava {

    /* h11 h12 h13 / h21 h22 h23 / h31 h32 1 */
    private final double[] h;

    private Preslikava(double[] h) {
        this.h = h;
    }

    public double[] uporabi(double x, double y) {
        double w = h[6] * x + h[7] * y + 1;
        return new double[] {(h[0] * x + h[1] * y + h[2]) / w, (h[3] * x + h[4] * y + h[5]) / w};
    }

    /* Homografija iz natanko stirih parov (iz[i] -> v[i]). */
    public static Preslikava homografija(double[][] iz, double[][] v) {
        double[][] a = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = iz[i][0];
            double y = iz[i][1];
            double u = v[i][0];
            double w = v[i][1];
            a[2 * i] = new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
            a[2 * i + 1] = new double[] {0, 0, 0, x, y, 1, -w * x, -w * y, w};
        }
        double[] r = resi(a, 8);
        return r == null ? null : new Preslikava(r);
    }

    /* Homografija iz stirih ali vec parov po metodi najmanjsih kvadratov (normalne
       enacbe; tocke so v mm in px, zato se pred racunom prestavijo v tezisce in
       pomanjsajo, da sistem ostane dobro pogojen). */
    public static Preslikava homografijaNajmanjsihKvadratov(double[][] iz, double[][] v) {
        int n = iz.length;
        if (n < 4 || v.length != n) {
            return null;
        }
        double[] ni = normalizacija(iz);
        double[] nv = normalizacija(v);
        double[][] ata = new double[8][9];
        for (int i = 0; i < n; i++) {
            double x = (iz[i][0] - ni[0]) * ni[2];
            double y = (iz[i][1] - ni[1]) * ni[2];
            double u = (v[i][0] - nv[0]) * nv[2];
            double w = (v[i][1] - nv[1]) * nv[2];
            dodaj(ata, new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y}, u);
            dodaj(ata, new double[] {0, 0, 0, x, y, 1, -w * x, -w * y}, w);
        }
        double[] r = resi(ata, 8);
        if (r == null) {
            return null;
        }
        // H = T_v^-1 * Hn * T_iz (T: premik v tezisce in merilo s)
        double[] hn = {r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], 1};
        double[][] tIz = {{ni[2], 0, -ni[2] * ni[0]}, {0, ni[2], -ni[2] * ni[1]}, {0, 0, 1}};
        double[][] tVInv = {{1 / nv[2], 0, nv[0]}, {0, 1 / nv[2], nv[1]}, {0, 0, 1}};
        double[][] h = zmnozi(tVInv, zmnozi(new double[][] {
                {hn[0], hn[1], hn[2]}, {hn[3], hn[4], hn[5]}, {hn[6], hn[7], hn[8]}}, tIz));
        if (Math.abs(h[2][2]) < 1e-12) {
            return null;
        }
        double k = h[2][2];
        return new Preslikava(new double[] {
                h[0][0] / k, h[0][1] / k, h[0][2] / k, h[1][0] / k, h[1][1] / k, h[1][2] / k, h[2][0] / k, h[2][1] / k});
    }

    /* Tezisce (x, y) in merilo, ki povprecno razdaljo do tezisca naredi sqrt(2). */
    private static double[] normalizacija(double[][] t) {
        double cx = 0;
        double cy = 0;
        for (double[] p : t) {
            cx += p[0] / t.length;
            cy += p[1] / t.length;
        }
        double d = 0;
        for (double[] p : t) {
            d += Math.hypot(p[0] - cx, p[1] - cy) / t.length;
        }
        return new double[] {cx, cy, d > 0 ? Math.sqrt(2) / d : 1};
    }

    /* Prispevek ene enacbe a·h = b k normalnim enacbam (A^T A | A^T b). */
    private static void dodaj(double[][] ata, double[] a, double b) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ata[i][j] += a[i] * a[j];
            }
            ata[i][8] += a[i] * b;
        }
    }

    private static double[][] zmnozi(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    c[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return c;
    }

    /* Afina preslikava iz treh parov. */
    public static Preslikava afina(double[][] iz, double[][] v) {
        double[][] a = new double[6][7];
        for (int i = 0; i < 3; i++) {
            double x = iz[i][0];
            double y = iz[i][1];
            a[2 * i] = new double[] {x, y, 1, 0, 0, 0, v[i][0]};
            a[2 * i + 1] = new double[] {0, 0, 0, x, y, 1, v[i][1]};
        }
        double[] r = resi(a, 6);
        if (r == null) {
            return null;
        }
        return new Preslikava(new double[] {r[0], r[1], r[2], r[3], r[4], r[5], 0, 0});
    }

    /* Gaussova eliminacija z delnim pivotiranjem; zadnji stolpec je desna stran. */
    private static double[] resi(double[][] a, int n) {
        for (int k = 0; k < n; k++) {
            int pivot = k;
            for (int i = k + 1; i < n; i++) {
                if (Math.abs(a[i][k]) > Math.abs(a[pivot][k])) {
                    pivot = i;
                }
            }
            if (Math.abs(a[pivot][k]) < 1e-12) {
                return null;
            }
            double[] t = a[k];
            a[k] = a[pivot];
            a[pivot] = t;
            for (int i = k + 1; i < n; i++) {
                double f = a[i][k] / a[k][k];
                for (int j = k; j <= n; j++) {
                    a[i][j] -= f * a[k][j];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = a[i][n];
            for (int j = i + 1; j < n; j++) {
                s -= a[i][j] * x[j];
            }
            x[i] = s / a[i][i];
        }
        return x;
    }
}
