/* Slika kot temnost (0 = papir, 1 = crnilo) in osnovna obdelava, ki jo bralnik
   listka potrebuje: vzorcenje, prilagodljiv prag in povezane komponente.

   Temnost je 1 - min(R, G, B): modro in crno pisalo sta temni v vsaj enem kanalu,
   papir je svetel v vseh, zato se crnilo loci od papirja bolje kot pri sivini. */
package si.turnirko.branje;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class Temnost {

    public final int sirina;
    public final int visina;
    public final float[] t;

    public Temnost(int sirina, int visina) {
        this.sirina = sirina;
        this.visina = visina;
        this.t = new float[sirina * visina];
    }

    public static Temnost iz(BufferedImage slika) {
        Temnost d = new Temnost(slika.getWidth(), slika.getHeight());
        int[] vrstica = new int[slika.getWidth()];
        for (int y = 0; y < d.visina; y++) {
            slika.getRGB(0, y, d.sirina, 1, vrstica, 0, d.sirina);
            for (int x = 0; x < d.sirina; x++) {
                int p = vrstica[x];
                int min = Math.min((p >> 16) & 0xFF, Math.min((p >> 8) & 0xFF, p & 0xFF));
                d.t[y * d.sirina + x] = 1f - min / 255f;
            }
        }
        return d;
    }

    public float vrni(int x, int y) {
        return t[y * sirina + x];
    }

    /* Bilinearno vzorcenje; zunaj slike je papir. */
    public float vzorec(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        if (x0 < 0 || y0 < 0 || x0 >= sirina - 1 || y0 >= visina - 1) {
            return 0f;
        }
        double fx = x - x0;
        double fy = y - y0;
        int i = y0 * sirina + x0;
        double zgoraj = t[i] * (1 - fx) + t[i + 1] * fx;
        double spodaj = t[i + sirina] * (1 - fx) + t[i + sirina + 1] * fx;
        return (float) (zgoraj * (1 - fy) + spodaj * fy);
    }

    /* Crnilo s prilagodljivim pragom (Bradley): tocka je crnilo, ce je za
       »razlika« temnejsa od povprecja okolice. Senca telefona in neenakomerna luc
       v dvorani tako ne postaneta crnilo, tanka crta pa ostane. */
    public boolean[] crnilo(int okno, float razlika, float najmanj) {
        double[] integral = new double[(sirina + 1) * (visina + 1)];
        for (int y = 0; y < visina; y++) {
            double vsota = 0;
            for (int x = 0; x < sirina; x++) {
                vsota += t[y * sirina + x];
                integral[(y + 1) * (sirina + 1) + x + 1] = integral[y * (sirina + 1) + x + 1] + vsota;
            }
        }
        int pol = Math.max(1, okno / 2);
        boolean[] maska = new boolean[sirina * visina];
        for (int y = 0; y < visina; y++) {
            int y1 = Math.max(0, y - pol);
            int y2 = Math.min(visina, y + pol + 1);
            for (int x = 0; x < sirina; x++) {
                int x1 = Math.max(0, x - pol);
                int x2 = Math.min(sirina, x + pol + 1);
                double vsota = integral[y2 * (sirina + 1) + x2] - integral[y1 * (sirina + 1) + x2]
                        - integral[y2 * (sirina + 1) + x1] + integral[y1 * (sirina + 1) + x1];
                double povprecje = vsota / ((double) (x2 - x1) * (y2 - y1));
                float v = t[y * sirina + x];
                maska[y * sirina + x] = v > povprecje + razlika && v > najmanj;
            }
        }
        return maska;
    }

    /* Povezana komponenta crnila (8-sosednost) s statistiko za razpoznavo oblike. */
    public static final class Komponenta {
        public int ploscina;
        public int minX = Integer.MAX_VALUE;
        public int minY = Integer.MAX_VALUE;
        public int maxX = -1;
        public int maxY = -1;
        double sx;
        double sy;
        double sxx;
        double syy;
        double sxy;
        /* Indeksi tock (y * sirina + x), samo ce so bile zahtevane. */
        public int[] tocke;

        public double cx() {
            return sx / ploscina;
        }

        public double cy() {
            return sy / ploscina;
        }

        public int sirina() {
            return maxX - minX + 1;
        }

        public int visina() {
            return maxY - minY + 1;
        }

        /* Lastni vrednosti kovariancne matrike (razprsenost v smeri osi elipse). */
        public double[] lastniVrednosti() {
            double n = ploscina;
            double vx = sxx / n - cx() * cx();
            double vy = syy / n - cy() * cy();
            double vxy = sxy / n - cx() * cy();
            double sled = vx + vy;
            double koren = Math.sqrt(Math.max(0, (vx - vy) * (vx - vy) / 4 + vxy * vxy));
            return new double[] {sled / 2 + koren, sled / 2 - koren};
        }

        /* Poln kvadrat (oznaka listka): enaka razprsenost v vseh smereh in
           ploscina, kot jo ima polna ploskev te razprsenosti (za kvadrat s
           stranico a je varianca a^2/12). Obroc (iskalni vzorec QR) ima premalo
           ploscine, crka ali stevka je podolgovata. Neodvisno od zasuka. */
        public boolean jePolnKvadrat() {
            double[] l = lastniVrednosti();
            if (l[1] <= 0) {
                return false;
            }
            double podolgovatost = l[0] / l[1];
            double polnost = ploscina / (12 * Math.sqrt(l[0] * l[1]));
            return podolgovatost < 1.8 && polnost > 0.8 && polnost < 1.2;
        }
    }

    /* Povezane komponente maske (8-sosednost). Z zbiranjem tock samo, kadar jih
       klicatelj potrebuje (celice so majhne, cela fotografija ni). */
    public static List<Komponenta> komponente(boolean[] maska, int sirina, int visina, boolean zTockami) {
        int[] oznaka = new int[maska.length];
        int[] sklad = new int[maska.length];
        List<Komponenta> vse = new ArrayList<>();
        int naslednja = 1;
        for (int z = 0; z < maska.length; z++) {
            if (!maska[z] || oznaka[z] != 0) {
                continue;
            }
            Komponenta k = new Komponenta();
            List<Integer> tocke = zTockami ? new ArrayList<>() : null;
            int vrh = 0;
            sklad[vrh++] = z;
            oznaka[z] = naslednja;
            while (vrh > 0) {
                int i = sklad[--vrh];
                int x = i % sirina;
                int y = i / sirina;
                k.ploscina++;
                k.sx += x;
                k.sy += y;
                k.sxx += (double) x * x;
                k.syy += (double) y * y;
                k.sxy += (double) x * y;
                k.minX = Math.min(k.minX, x);
                k.maxX = Math.max(k.maxX, x);
                k.minY = Math.min(k.minY, y);
                k.maxY = Math.max(k.maxY, y);
                if (tocke != null) {
                    tocke.add(i);
                }
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= visina) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= sirina) {
                            continue;
                        }
                        int j = ny * sirina + nx;
                        if (maska[j] && oznaka[j] == 0) {
                            oznaka[j] = naslednja;
                            sklad[vrh++] = j;
                        }
                    }
                }
            }
            if (tocke != null) {
                k.tocke = tocke.stream().mapToInt(Integer::intValue).toArray();
            }
            vse.add(k);
            naslednja++;
        }
        return vse;
    }
}
