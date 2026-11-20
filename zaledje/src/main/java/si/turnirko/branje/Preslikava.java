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
