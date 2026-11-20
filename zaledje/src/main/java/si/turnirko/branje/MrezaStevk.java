/* Majhna konvolucijska mreza za rocno napisane stevke 0-9 (slika 28 x 28).

   Zgradba (LeNet): konvolucija 5x5 (16 filtrov) -> ReLU -> zdruzevanje 2x2 ->
   konvolucija 5x5 (32 filtrov) -> ReLU -> zdruzevanje 2x2 -> polno povezana
   plast 128 -> ReLU -> 10 razredov. Skupaj ~80.000 utezi (320 kB).

   Napisana je v cisti Javi brez knjiznic, ker je majhna: ena stevka je ~1 milijon
   mnozenj (manj kot milisekunda), listek ima do 30 stevk. Tako branje ne stane
   nic in tece na istem strezniku kot aplikacija, brez omejitve stevila listkov.
   Ista koda tudi uci (vzvratno razsirjanje, Adam), da se mreza lahko dotrenira
   na stevkah potrjenih listkov (UcenjeStevk). */
package si.turnirko.branje;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;

public final class MrezaStevk {

    static final int VHOD = 28;
    static final int C1 = 16;
    static final int C2 = 32;
    static final int SKRITI = 128;
    static final int RAZREDI = 10;
    private static final int A1 = 24;   // 28 - 5 + 1
    private static final int P1 = 12;
    private static final int A2 = 8;    // 12 - 5 + 1
    private static final int P2 = 4;
    static final int PLOSCATO = C2 * P2 * P2;
    private static final int ZNAK = 0x544B5331; // "TKS1"

    final float[] w1 = new float[C1 * 25];
    final float[] b1 = new float[C1];
    final float[] w2 = new float[C2 * C1 * 25];
    final float[] b2 = new float[C2];
    final float[] w3 = new float[SKRITI * PLOSCATO];
    final float[] b3 = new float[SKRITI];
    final float[] w4 = new float[RAZREDI * SKRITI];
    final float[] b4 = new float[RAZREDI];

    List<float[]> parametri() {
        return List.of(w1, b1, w2, b2, w3, b3, w4, b4);
    }

    /* Zacetne utezi po He (ReLU). */
    public static MrezaStevk nakljucna(Random r) {
        MrezaStevk m = new MrezaStevk();
        napolni(m.w1, r, 25);
        napolni(m.w2, r, C1 * 25);
        napolni(m.w3, r, PLOSCATO);
        napolni(m.w4, r, SKRITI);
        return m;
    }

    private static void napolni(float[] w, Random r, int vhodov) {
        double s = Math.sqrt(2.0 / vhodov);
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (r.nextGaussian() * s);
        }
    }

    public MrezaStevk kopija() {
        MrezaStevk k = new MrezaStevk();
        List<float[]> iz = parametri();
        List<float[]> v = k.parametri();
        for (int i = 0; i < iz.size(); i++) {
            System.arraycopy(iz.get(i), 0, v.get(i), 0, iz.get(i).length);
        }
        return k;
    }

    /* Aktivacije enega vzorca; vsaka nit ima svoje. */
    static final class Prostor {
        final float[] a1 = new float[C1 * A1 * A1];
        final float[] p1 = new float[C1 * P1 * P1];
        final int[] i1 = new int[C1 * P1 * P1];
        final float[] a2 = new float[C2 * A2 * A2];
        final float[] p2 = new float[PLOSCATO];
        final int[] i2 = new int[PLOSCATO];
        final float[] h = new float[SKRITI];
        final float[] z = new float[RAZREDI];
        // vmesni odvodi
        final float[] dp1 = new float[C1 * P1 * P1];
        final float[] da1 = new float[C1 * A1 * A1];
        final float[] da2 = new float[C2 * A2 * A2];
        final float[] dp2 = new float[PLOSCATO];
        final float[] dh = new float[SKRITI];
        final float[] dz = new float[RAZREDI];
    }

    /* Verjetnosti razredov; temperatura > 1 jih zmehca (manj samozavestna mreza je
       za pravila namiznega tenisa boljsi vhod kot ostra napaka). */
    public float[] verjetnosti(float[] vhod, double temperatura) {
        Prostor s = new Prostor();
        naprej(vhod, s);
        return softmax(s.z, temperatura);
    }

    static float[] softmax(float[] z, double temperatura) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : z) {
            max = Math.max(max, v);
        }
        double vsota = 0;
        double[] e = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            e[i] = Math.exp((z[i] - max) / temperatura);
            vsota += e[i];
        }
        float[] p = new float[z.length];
        for (int i = 0; i < z.length; i++) {
            p[i] = (float) (e[i] / vsota);
        }
        return p;
    }

    void naprej(float[] x, Prostor s) {
        for (int k = 0; k < C1; k++) {
            int wb = k * 25;
            for (int i = 0; i < A1; i++) {
                for (int j = 0; j < A1; j++) {
                    float v = b1[k];
                    for (int u = 0; u < 5; u++) {
                        int vr = (i + u) * VHOD + j;
                        int wu = wb + u * 5;
                        for (int t = 0; t < 5; t++) {
                            v += w1[wu + t] * x[vr + t];
                        }
                    }
                    s.a1[(k * A1 + i) * A1 + j] = v > 0 ? v : 0;
                }
            }
        }
        zdruzi(s.a1, A1, C1, s.p1, s.i1);
        for (int k = 0; k < C2; k++) {
            for (int i = 0; i < A2; i++) {
                for (int j = 0; j < A2; j++) {
                    float v = b2[k];
                    for (int c = 0; c < C1; c++) {
                        int pb = c * P1 * P1;
                        int wb = (k * C1 + c) * 25;
                        for (int u = 0; u < 5; u++) {
                            int vr = pb + (i + u) * P1 + j;
                            int wu = wb + u * 5;
                            for (int t = 0; t < 5; t++) {
                                v += w2[wu + t] * s.p1[vr + t];
                            }
                        }
                    }
                    s.a2[(k * A2 + i) * A2 + j] = v > 0 ? v : 0;
                }
            }
        }
        zdruzi(s.a2, A2, C2, s.p2, s.i2);
        for (int n = 0; n < SKRITI; n++) {
            float v = b3[n];
            int wb = n * PLOSCATO;
            for (int m = 0; m < PLOSCATO; m++) {
                v += w3[wb + m] * s.p2[m];
            }
            s.h[n] = v > 0 ? v : 0;
        }
        for (int o = 0; o < RAZREDI; o++) {
            float v = b4[o];
            int wb = o * SKRITI;
            for (int n = 0; n < SKRITI; n++) {
                v += w4[wb + n] * s.h[n];
            }
            s.z[o] = v;
        }
    }

    /* Zdruzevanje 2x2 (maksimum); zapomni si, katera tocka je zmagala. */
    private static void zdruzi(float[] a, int stranica, int kanalov, float[] p, int[] indeksi) {
        int pol = stranica / 2;
        for (int k = 0; k < kanalov; k++) {
            int ab = k * stranica * stranica;
            for (int i = 0; i < pol; i++) {
                for (int j = 0; j < pol; j++) {
                    int najboljsi = ab + (2 * i) * stranica + 2 * j;
                    for (int di = 0; di < 2; di++) {
                        for (int dj = 0; dj < 2; dj++) {
                            int idx = ab + (2 * i + di) * stranica + 2 * j + dj;
                            if (a[idx] > a[najboljsi]) {
                                najboljsi = idx;
                            }
                        }
                    }
                    int pi = (k * pol + i) * pol + j;
                    p[pi] = a[najboljsi];
                    indeksi[pi] = najboljsi;
                }
            }
        }
    }

    /* Odvodi izgube (navzkrizna entropija) za en vzorec; pristeje jih v g in vrne izgubo. */
    double nazaj(float[] x, int oznaka, Prostor s, MrezaStevk g) {
        naprej(x, s);
        float[] p = softmax(s.z, 1);
        for (int o = 0; o < RAZREDI; o++) {
            s.dz[o] = p[o] - (o == oznaka ? 1 : 0);
        }
        java.util.Arrays.fill(s.dh, 0);
        for (int o = 0; o < RAZREDI; o++) {
            float d = s.dz[o];
            g.b4[o] += d;
            int wb = o * SKRITI;
            for (int n = 0; n < SKRITI; n++) {
                g.w4[wb + n] += d * s.h[n];
                s.dh[n] += w4[wb + n] * d;
            }
        }
        java.util.Arrays.fill(s.dp2, 0);
        for (int n = 0; n < SKRITI; n++) {
            if (s.h[n] <= 0) {
                continue;
            }
            float d = s.dh[n];
            g.b3[n] += d;
            int wb = n * PLOSCATO;
            for (int m = 0; m < PLOSCATO; m++) {
                g.w3[wb + m] += d * s.p2[m];
                s.dp2[m] += w3[wb + m] * d;
            }
        }
        java.util.Arrays.fill(s.da2, 0);
        for (int m = 0; m < PLOSCATO; m++) {
            int idx = s.i2[m];
            if (s.a2[idx] > 0) {
                s.da2[idx] += s.dp2[m];
            }
        }
        java.util.Arrays.fill(s.dp1, 0);
        for (int k = 0; k < C2; k++) {
            for (int i = 0; i < A2; i++) {
                for (int j = 0; j < A2; j++) {
                    float d = s.da2[(k * A2 + i) * A2 + j];
                    if (d == 0) {
                        continue;
                    }
                    g.b2[k] += d;
                    for (int c = 0; c < C1; c++) {
                        int pb = c * P1 * P1;
                        int wb = (k * C1 + c) * 25;
                        for (int u = 0; u < 5; u++) {
                            int vr = pb + (i + u) * P1 + j;
                            int wu = wb + u * 5;
                            for (int t = 0; t < 5; t++) {
                                g.w2[wu + t] += d * s.p1[vr + t];
                                s.dp1[vr + t] += d * w2[wu + t];
                            }
                        }
                    }
                }
            }
        }
        java.util.Arrays.fill(s.da1, 0);
        for (int m = 0; m < s.i1.length; m++) {
            int idx = s.i1[m];
            if (s.a1[idx] > 0) {
                s.da1[idx] += s.dp1[m];
            }
        }
        for (int k = 0; k < C1; k++) {
            int wb = k * 25;
            for (int i = 0; i < A1; i++) {
                for (int j = 0; j < A1; j++) {
                    float d = s.da1[(k * A1 + i) * A1 + j];
                    if (d == 0) {
                        continue;
                    }
                    g.b1[k] += d;
                    for (int u = 0; u < 5; u++) {
                        int vr = (i + u) * VHOD + j;
                        int wu = wb + u * 5;
                        for (int t = 0; t < 5; t++) {
                            g.w1[wu + t] += d * x[vr + t];
                        }
                    }
                }
            }
        }
        return -Math.log(Math.max(p[oznaka], 1e-12));
    }

    void pocisti() {
        for (float[] a : parametri()) {
            java.util.Arrays.fill(a, 0);
        }
    }

    void pristej(MrezaStevk g) {
        List<float[]> a = parametri();
        List<float[]> b = g.parametri();
        for (int i = 0; i < a.size(); i++) {
            float[] x = a.get(i);
            float[] y = b.get(i);
            for (int j = 0; j < x.length; j++) {
                x[j] += y[j];
            }
        }
    }

    public void zapisi(OutputStream izhod) throws IOException {
        DataOutputStream o = new DataOutputStream(izhod);
        o.writeInt(ZNAK);
        for (float[] a : parametri()) {
            o.writeInt(a.length);
            for (float v : a) {
                o.writeFloat(v);
            }
        }
        o.flush();
    }

    public static MrezaStevk preberi(InputStream vhod) throws IOException {
        DataInputStream in = new DataInputStream(vhod);
        if (in.readInt() != ZNAK) {
            throw new IOException("To ni zapis mreze stevk.");
        }
        MrezaStevk m = new MrezaStevk();
        for (float[] a : m.parametri()) {
            int n = in.readInt();
            if (n != a.length) {
                throw new IOException("Zapis mreze stevk ima drugacno zgradbo.");
            }
            for (int i = 0; i < n; i++) {
                a[i] = in.readFloat();
            }
        }
        return m;
    }
}
