/* Veckratno branje stevke (F2.2): mreza prebere pet rahlo spremenjenih razlicic
   iste stevke (izvirnik, premik za 1 px levo in desno, zasuk za 4 stopinje v obe
   smeri) in verjetnosti se povprecijo.

   Rocna stevka se od oblik, ki jih je mreza videla pri ucenju, pogosto razlikuje
   samo po legi ali nagibu; ena sama razlicica takrat da samozavesten napacen
   odgovor, povprecje petih pa je mehkejse in zato pravila (RazlagaListka) lazje
   popravijo napako ali celico oznacijo kot negotovo. Petkrat vec racunanja: pri
   ~30 stevkah listka je to se vedno precej pod sekundo. */
package si.turnirko.branje;

public final class VecBranj {

    private VecBranj() {}

    private static final int N = RazrezCelice.MNIST;
    private static final double ZASUK = Math.toRadians(4);

    /* Povprecne verjetnosti stevk 0-9 cez pet razlicic. */
    public static float[] verjetnosti(MrezaStevk mreza, float[] vhod, double temperatura) {
        float[][] razlicice = {
                vhod,
                premakni(vhod, 1),
                premakni(vhod, -1),
                zasukaj(vhod, ZASUK),
                zasukaj(vhod, -ZASUK)};
        float[] p = new float[10];
        for (float[] r : razlicice) {
            float[] q = mreza.verjetnosti(r, temperatura);
            for (int i = 0; i < 10; i++) {
                p[i] += q[i] / razlicice.length;
            }
        }
        return p;
    }

    /* Vodoravni premik za dx px (kar pade cez rob, se izgubi; rob je pri MNIST prazen). */
    static float[] premakni(float[] vhod, int dx) {
        float[] izhod = new float[N * N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int iz = x - dx;
                if (iz >= 0 && iz < N) {
                    izhod[y * N + x] = vhod[y * N + iz];
                }
            }
        }
        return izhod;
    }

    /* Zasuk okoli sredisca slike z bilinearnim vzorcenjem. */
    static float[] zasukaj(float[] vhod, double kot) {
        float[] izhod = new float[N * N];
        double c = (N - 1) / 2.0;
        double cos = Math.cos(kot);
        double sin = Math.sin(kot);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                // obratna preslikava: od kod v izvirniku pride ta tocka
                double sx = cos * (x - c) + sin * (y - c) + c;
                double sy = -sin * (x - c) + cos * (y - c) + c;
                izhod[y * N + x] = vzorec(vhod, sx, sy);
            }
        }
        return izhod;
    }

    private static float vzorec(float[] v, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        return (float) ((tocka(v, x0, y0) * (1 - fx) + tocka(v, x0 + 1, y0) * fx) * (1 - fy)
                + (tocka(v, x0, y0 + 1) * (1 - fx) + tocka(v, x0 + 1, y0 + 1) * fx) * fy);
    }

    private static float tocka(float[] v, int x, int y) {
        return x < 0 || y < 0 || x >= N || y >= N ? 0 : v[y * N + x];
    }
}
