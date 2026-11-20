/* Ucenje mreze stevk: zacetno na MNIST in dotreniranje na stevkah potrjenih listkov.

   Zacetna mreza (resources/branje/stevke.bin) je naucena s tem razredom na javnem
   naboru MNIST (60.000 rocno napisanih stevk, CC BY-SA 3.0, Y. LeCun idr.):
     java -cp target/classes si.turnirko.branje.UcenjeStevk <mapa MNIST> <izhod.bin> [epohe]
   Ukaz izpise tudi natancnost na 10.000 preizkusnih stevkah in zapise izbor
   (branje/mnist-izbor.bin.gz), ki ga nocno dotreniranje mesa med stevke listkov,
   da mreza ne pozabi splosnih oblik (UcenjeIzListkov).

   Pisava na listku ni MNIST: sodniki pisejo s kemicnim svincnikom, evropsko enico
   z nosom in precrtano sedmico. Zato se vsak vzorec pri ucenju nakljucno zasuce,
   razpotegne, nagne, premakne in odebeli ali stanjsa - mreza se nauci oblike, ne
   tocne pisave enega pisca. */
package si.turnirko.branje;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class UcenjeStevk {

    private UcenjeStevk() {}

    /* Ena stevka: 28 x 28 sivin (0 = papir, 255 = crnilo) in njena vrednost. */
    public record Vzorec(byte[] slika, int oznaka) {}

    // ---------- Podatki ----------

    /* Slike in oznake MNIST (idx, stisnjeno z gzip). */
    public static List<Vzorec> beriMnist(Path slike, Path oznake) throws IOException {
        try (DataInputStream s = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(slike.toFile()))));
             DataInputStream o = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(oznake.toFile()))))) {
            if (s.readInt() != 2051 || o.readInt() != 2049) {
                throw new IOException("Datoteki nista v obliki MNIST idx.");
            }
            int n = s.readInt();
            if (o.readInt() != n || s.readInt() != 28 || s.readInt() != 28) {
                throw new IOException("Datoteki MNIST se ne ujemata.");
            }
            List<Vzorec> vzorci = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[784];
                s.readFully(b);
                vzorci.add(new Vzorec(b, o.readUnsignedByte()));
            }
            return vzorci;
        }
    }

    /* Strnjen zapis vzorcev (izbor MNIST v virih): stevilo, nato oznaka + 784 bajtov. */
    public static void zapisiIzbor(List<Vzorec> vzorci, OutputStream izhod) throws IOException {
        DataOutputStream o = new DataOutputStream(new GZIPOutputStream(izhod));
        o.writeInt(vzorci.size());
        for (Vzorec v : vzorci) {
            o.writeByte(v.oznaka());
            o.write(v.slika());
        }
        o.close();
    }

    public static List<Vzorec> beriIzbor(InputStream vhod) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(vhod)))) {
            int n = in.readInt();
            List<Vzorec> vzorci = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int oznaka = in.readUnsignedByte();
                byte[] b = new byte[784];
                in.readFully(b);
                vzorci.add(new Vzorec(b, oznaka));
            }
            return vzorci;
        }
    }

    /* Enakomeren izbor: po »naRazred« vzorcev vsake stevke. */
    public static List<Vzorec> enakomerno(List<Vzorec> vzorci, int naRazred, long seme) {
        List<Vzorec> premesani = new ArrayList<>(vzorci);
        Collections.shuffle(premesani, new Random(seme));
        int[] stevec = new int[10];
        List<Vzorec> izbor = new ArrayList<>();
        for (Vzorec v : premesani) {
            if (stevec[v.oznaka()]++ < naRazred) {
                izbor.add(v);
            }
        }
        return izbor;
    }

    // ---------- Vhod mreze ----------

    public static float[] vhod(byte[] slika) {
        float[] x = new float[784];
        for (int i = 0; i < 784; i++) {
            x[i] = (slika[i] & 0xFF) / 255f;
        }
        return x;
    }

    /* Vhod mreze nazaj v bajte (za shranjevanje stevk listkov). */
    public static byte[] vBajte(float[] vhod) {
        byte[] b = new byte[784];
        for (int i = 0; i < 784; i++) {
            b[i] = (byte) Math.round(Math.max(0, Math.min(1, vhod[i])) * 255);
        }
        return b;
    }

    /* Nakljucno popacenje: zasuk +-15 st., merilo 0,85-1,15 (razlicno po oseh),
       nagib +-0,25, premik +-2 px, obcasno debelejsa ali tanjsa poteza. */
    static float[] popaci(byte[] slika, Random r) {
        float[] x = vhod(slika);
        double kot = Math.toRadians((r.nextDouble() * 2 - 1) * 15);
        double merilo = 0.85 + r.nextDouble() * 0.3;
        double sx = merilo * (0.9 + r.nextDouble() * 0.2);
        double sy = merilo * (0.9 + r.nextDouble() * 0.2);
        double nagib = (r.nextDouble() * 2 - 1) * 0.25;
        double tx = (r.nextDouble() * 2 - 1) * 2;
        double ty = (r.nextDouble() * 2 - 1) * 2;
        double c = Math.cos(kot);
        double s = Math.sin(kot);
        // A = R * Nagib * Merilo; vzorcimo z inverzom
        double a = c * sx;
        double b = (c * nagib - s) * sy;
        double cc = s * sx;
        double d = (s * nagib + c) * sy;
        double det = a * d - b * cc;
        float[] y = new float[784];
        for (int i = 0; i < 28; i++) {
            for (int j = 0; j < 28; j++) {
                double px = j - 13.5 - tx;
                double py = i - 13.5 - ty;
                double qx = (d * px - b * py) / det + 13.5;
                double qy = (-cc * px + a * py) / det + 13.5;
                y[i * 28 + j] = bilinearno(x, qx, qy);
            }
        }
        double debelina = r.nextDouble();
        if (debelina < 0.2) {
            return debelejse(y);
        }
        if (debelina < 0.3) {
            return tanjse(y);
        }
        return y;
    }

    private static float bilinearno(float[] x, double qx, double qy) {
        int x0 = (int) Math.floor(qx);
        int y0 = (int) Math.floor(qy);
        if (x0 < 0 || y0 < 0 || x0 >= 27 || y0 >= 27) {
            return 0;
        }
        double fx = qx - x0;
        double fy = qy - y0;
        int i = y0 * 28 + x0;
        return (float) ((x[i] * (1 - fx) + x[i + 1] * fx) * (1 - fy) + (x[i + 28] * (1 - fx) + x[i + 29] * fx) * fy);
    }

    private static float[] debelejse(float[] x) {
        float[] y = new float[784];
        for (int i = 0; i < 28; i++) {
            for (int j = 0; j < 28; j++) {
                float m = x[i * 28 + j];
                if (i > 0) m = Math.max(m, 0.8f * x[(i - 1) * 28 + j]);
                if (i < 27) m = Math.max(m, 0.8f * x[(i + 1) * 28 + j]);
                if (j > 0) m = Math.max(m, 0.8f * x[i * 28 + j - 1]);
                if (j < 27) m = Math.max(m, 0.8f * x[i * 28 + j + 1]);
                y[i * 28 + j] = m;
            }
        }
        return y;
    }

    private static float[] tanjse(float[] x) {
        float[] y = new float[784];
        for (int i = 1; i < 27; i++) {
            for (int j = 1; j < 27; j++) {
                float povprecje = (x[(i - 1) * 28 + j] + x[(i + 1) * 28 + j] + x[i * 28 + j - 1] + x[i * 28 + j + 1]) / 4;
                y[i * 28 + j] = Math.min(x[i * 28 + j], povprecje + 0.15f);
            }
        }
        return y;
    }

    // ---------- Ucenje ----------

    /* Mini-paketi po 64, Adam, popacenje vsakega vzorca. Uci na »niti« nitih. */
    public static void uci(MrezaStevk mreza, List<Vzorec> vzorci, int epoh, float korak, int niti, long seme,
                           Consumer<String> dnevnik) {
        int paket = 64;
        Random r = new Random(seme);
        Adam adam = new Adam(mreza, korak);
        ExecutorService izvajalec = Executors.newFixedThreadPool(niti);
        try {
            MrezaStevk[] gradienti = new MrezaStevk[niti];
            MrezaStevk.Prostor[] prostori = new MrezaStevk.Prostor[niti];
            for (int t = 0; t < niti; t++) {
                gradienti[t] = new MrezaStevk();
                prostori[t] = new MrezaStevk.Prostor();
            }
            List<Vzorec> vrsta = new ArrayList<>(vzorci);
            for (int e = 0; e < epoh; e++) {
                Collections.shuffle(vrsta, r);
                double izguba = 0;
                for (int z = 0; z < vrsta.size(); z += paket) {
                    int konec = Math.min(vrsta.size(), z + paket);
                    List<Future<Double>> opravila = new ArrayList<>();
                    for (int t = 0; t < niti; t++) {
                        int nit = t;
                        long semeNiti = r.nextLong();
                        int zacetek = z;
                        opravila.add(izvajalec.submit(() -> {
                            Random rn = new Random(semeNiti);
                            MrezaStevk g = gradienti[nit];
                            g.pocisti();
                            double vsota = 0;
                            for (int i = zacetek + nit; i < konec; i += niti) {
                                Vzorec v = vrsta.get(i);
                                vsota += mreza.nazaj(popaci(v.slika(), rn), v.oznaka(), prostori[nit], g);
                            }
                            return vsota;
                        }));
                    }
                    for (Future<Double> o : opravila) {
                        izguba += o.get();
                    }
                    for (int t = 1; t < niti; t++) {
                        gradienti[0].pristej(gradienti[t]);
                    }
                    adam.korak(gradienti[0], konec - z);
                }
                adam.zmanjsaj(0.7f);
                if (dnevnik != null) {
                    dnevnik.accept(String.format("epoha %d: izguba %.4f", e + 1, izguba / vrsta.size()));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Ucenje mreze stevk ni uspelo.", e);
        } finally {
            izvajalec.shutdown();
        }
    }

    /* Delez pravilno prepoznanih vzorcev. */
    public static double natancnost(MrezaStevk mreza, List<Vzorec> vzorci) {
        if (vzorci.isEmpty()) {
            return 0;
        }
        MrezaStevk.Prostor s = new MrezaStevk.Prostor();
        int prav = 0;
        for (Vzorec v : vzorci) {
            mreza.naprej(vhod(v.slika()), s);
            int najboljsi = 0;
            for (int o = 1; o < MrezaStevk.RAZREDI; o++) {
                if (s.z[o] > s.z[najboljsi]) {
                    najboljsi = o;
                }
            }
            if (najboljsi == v.oznaka()) {
                prav++;
            }
        }
        return (double) prav / vzorci.size();
    }

    /* Adam (Kingma in Ba): korak se prilagodi vsaki utezi posebej. */
    private static final class Adam {
        private final List<float[]> parametri;
        private final List<float[]> m = new ArrayList<>();
        private final List<float[]> v = new ArrayList<>();
        private float korak;
        private int t;

        Adam(MrezaStevk mreza, float korak) {
            this.parametri = mreza.parametri();
            this.korak = korak;
            for (float[] p : parametri) {
                m.add(new float[p.length]);
                v.add(new float[p.length]);
            }
        }

        void korak(MrezaStevk gradient, int velikost) {
            t++;
            double b1t = 1 - Math.pow(0.9, t);
            double b2t = 1 - Math.pow(0.999, t);
            List<float[]> g = gradient.parametri();
            for (int i = 0; i < parametri.size(); i++) {
                float[] p = parametri.get(i);
                float[] gi = g.get(i);
                float[] mi = m.get(i);
                float[] vi = v.get(i);
                for (int j = 0; j < p.length; j++) {
                    float d = gi[j] / velikost;
                    mi[j] = 0.9f * mi[j] + 0.1f * d;
                    vi[j] = 0.999f * vi[j] + 0.001f * d * d;
                    p[j] -= (float) (korak * (mi[j] / b1t) / (Math.sqrt(vi[j] / b2t) + 1e-8));
                }
            }
        }

        void zmanjsaj(float faktor) {
            korak *= faktor;
        }
    }

    // ---------- Zacetno ucenje ----------

    public static void main(String[] args) throws Exception {
        Path mapa = Path.of(args[0]);
        Path izhod = Path.of(args[1]);
        int epoh = args.length > 2 ? Integer.parseInt(args[2]) : 6;
        List<Vzorec> ucni = beriMnist(mapa.resolve("train-images-idx3-ubyte.gz"), mapa.resolve("train-labels-idx1-ubyte.gz"));
        List<Vzorec> preizkusni = beriMnist(mapa.resolve("t10k-images-idx3-ubyte.gz"), mapa.resolve("t10k-labels-idx1-ubyte.gz"));
        MrezaStevk mreza = MrezaStevk.nakljucna(new Random(19112026L));
        int niti = Math.max(1, Runtime.getRuntime().availableProcessors());
        long zacetek = System.currentTimeMillis();
        uci(mreza, ucni, epoh, 0.001f, niti, 1L, System.out::println);
        System.out.printf("Natancnost na preizkusnih MNIST: %.4f (%d s)%n",
                natancnost(mreza, preizkusni), (System.currentTimeMillis() - zacetek) / 1000);
        try (OutputStream o = new FileOutputStream(izhod.toFile())) {
            mreza.zapisi(o);
        }
        Path izbor = izhod.resolveSibling("mnist-izbor.bin.gz");
        try (OutputStream o = Files.newOutputStream(izbor)) {
            zapisiIzbor(enakomerno(ucni, 300, 7L), o);
        }
        Path preizkus = izhod.resolveSibling("mnist-preizkus.bin.gz");
        try (OutputStream o = Files.newOutputStream(preizkus)) {
            zapisiIzbor(enakomerno(preizkusni, 100, 11L), o);
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        mreza.zapisi(b);
        System.out.println("Zapisano: " + izhod + " (" + b.size() / 1024 + " kB), " + izbor + ", " + preizkus);
    }
}
