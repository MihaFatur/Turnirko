/* Branje listkov na simuliranih fotografijah (LokalniBralnikListka od fotografije
   do prepisa).

   Na listek, kot ga natisne vmesnik (resources/branje/listek-5-nizov.png, posnetek
   iz brskalnika, 11,3 px/mm), se v celice narisejo rocne stevke iz preizkusnega
   dela MNIST - teh mreza pri ucenju ni videla. Listek se nato »fotografira«:
   zasuk, perspektiva telefona, neenakomerna svetloba, sum in stiskanje JPEG.
   Zahtevne fotografije imajo se stevke, ki se dotikajo ali silijo cez celico,
   vecji zasuk in perspektivo ter zamegljeno sliko.

   Domenska pravila, ki jih ta test varuje:
   - bralnik najde oznake in celice tudi na zasukani fotografiji s perspektivo;
   - prazen listek nima nobenega prebranega niza;
   - velika vecina celic je prebrana prav, napacna celica pa je skoraj vedno
     oznacena kot negotova (organizator jo pogleda) - tiha napaka je redka.
   Meje so postavljene pod izmerjene vrednosti (pisava iz MNIST ni pisava v
   dvorani); pravo merilo je stevilo potrjenih listkov brez popravka na strani
   Branje listkov. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.GeometrijaListka;
import si.turnirko.branje.PoravnavaListka;
import si.turnirko.branje.Preslikava;
import si.turnirko.branje.UcenjeStevk;

class SimuliraneFotografijeTest {

    /* Pogoji simulacije: razmik med stevkama (mm, negativno = prekrivanje),
       visina stevke (delez celice), zasuk (st.), perspektiva (delez), zamegljenost. */
    record Pogoji(double razmikOd, double razmikDo, double visinaOd, double visinaDo,
                  double kot, double perspektiva, boolean zamegli) {}

    static final Pogoji OBICAJNO = new Pogoji(0.1, 1.0, 0.6, 0.85, 12, 0.08, false);
    static final Pogoji ZAHTEVNO = new Pogoji(-0.3, 0.8, 0.6, 1.05, 20, 0.14, true);

    record Meritev(int listkov, int neprebranih, double prav, double tihe, int brezPopravka) {}

    private static BufferedImage listek;
    private static double pxNaMm;
    private static List<UcenjeStevk.Vzorec> stevke;
    private static CeliceListka.Tabela celice;
    private static LokalniBralnikListka bralnik;

    /* Fotografija listka z danim izidom (za druge teste: ucenje iz potrjenih listkov). */
    static byte[] fotografijaIzida(int[][] izid, Random r) throws IOException {
        if (listek == null) {
            pripravi();
        }
        BufferedImage izpolnjen = kopija(listek);
        for (int i = 0; i < izid.length; i++) {
            napisi(izpolnjen, celice.nizi().get(i).levo(), izid[i][0], r, OBICAJNO);
            napisi(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, OBICAJNO);
        }
        int[] d = dobljeni(izid);
        napisi(izpolnjen, celice.rezultat().levo(), d[0], r, OBICAJNO);
        napisi(izpolnjen, celice.rezultat().desno(), d[1], r, OBICAJNO);
        return fotografija(izpolnjen, r, OBICAJNO);
    }

    @BeforeAll
    static void pripravi() throws IOException {
        try (InputStream v = SimuliraneFotografijeTest.class.getResourceAsStream("/branje/listek-5-nizov.png")) {
            listek = ImageIO.read(v);
        }
        pxNaMm = listek.getWidth() / GeometrijaListka.SIRINA;
        try (InputStream v = SimuliraneFotografijeTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            stevke = UcenjeStevk.beriIzbor(v);
        }
        // lega celic na cistem listku (iz istega postopka, ki ga uporablja bralnik)
        double[][] qr = KodaListka.najdi(listek).getFirst().tocke();
        celice = CeliceListka.najdi(PoravnavaListka.poravnaj(listek, qr).slika());
        VirMrezeStevk vir = new VirMrezeStevk(null);
        VirMrezeStevk.Nalozena vgrajena = VirMrezeStevk.vgrajena();
        vir.nastavi(vgrajena.mreza(), vgrajena.oznaka());
        bralnik = new LokalniBralnikListka(vir);
    }

    @Test
    void cistListekImaPetVrsticInRezultat() {
        assertNotNull(celice);
        assertEquals(5, celice.nizi().size());
    }

    @Test
    void prazenListekNimaPrebranihNizov() throws IOException {
        Random r = new Random(1);
        PrebranListek p = bralnik.preberi(fotografija(kopija(listek), r, OBICAJNO)).listek();
        assertEquals(5, p.nizi().size());
        assertTrue(p.nizi().stream().allMatch(n -> n.levo() == null && n.desno() == null), p.nizi().toString());
        assertNull(p.rezultatLevo());
    }

    @Test
    void obicajneFotografijeSoPrebranePrav() throws IOException {
        Meritev m = izmeri(new Random(19112026L), 30, OBICAJNO);
        assertEquals(0, m.neprebranih(), "vsak listek mora biti poravnan");
        assertTrue(m.prav() >= 0.95, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.01, "prevec tihih napak: " + m.tihe());
    }

    @Test
    void zahtevneFotografijeImajoRedkeTiheNapake() throws IOException {
        Meritev m = izmeri(new Random(20112026L), 30, ZAHTEVNO);
        assertEquals(0, m.neprebranih(), "vsak listek mora biti poravnan");
        assertTrue(m.prav() >= 0.85, "premalo pravilnih celic: " + m.prav());
        assertTrue(m.tihe() <= 0.03, "prevec tihih napak: " + m.tihe());
    }

    private static Meritev izmeri(Random r, int listkov, Pogoji pogoji) throws IOException {
        int celic = 0;
        int prav = 0;
        int tihih = 0;
        int brezPopravka = 0;
        int neprebranih = 0;
        for (int k = 0; k < listkov; k++) {
            int[][] izid = nakljucnaTekma(r, 3);
            BufferedImage izpolnjen = kopija(listek);
            for (int i = 0; i < izid.length; i++) {
                napisi(izpolnjen, celice.nizi().get(i).levo(), izid[i][0], r, pogoji);
                napisi(izpolnjen, celice.nizi().get(i).desno(), izid[i][1], r, pogoji);
            }
            int[] dobljeni = dobljeni(izid);
            if (r.nextDouble() < 0.8) {
                napisi(izpolnjen, celice.rezultat().levo(), dobljeni[0], r, pogoji);
                napisi(izpolnjen, celice.rezultat().desno(), dobljeni[1], r, pogoji);
            }
            PrebranListek p;
            byte[] foto = fotografija(izpolnjen, r, pogoji);
            try {
                p = bralnik.preberi(foto).listek();
            } catch (BralnikListka.NapakaBranjaListka e) {
                neprebranih++;
                System.out.println("Listek " + k + " ni prebran: " + e.getMessage());
                continue;
            }
            boolean vsePrav = true;
            for (int i = 0; i < izid.length; i++) {
                PrebranListek.Niz n = p.nizi().get(i);
                for (int s = 0; s < 2; s++) {
                    Integer prebrano = s == 0 ? n.levo() : n.desno();
                    boolean negotovo = s == 0 ? n.negotovoLevo() : n.negotovoDesno();
                    celic++;
                    if (prebrano != null && prebrano == izid[i][s]) {
                        prav++;
                    } else {
                        vsePrav = false;
                        if (!negotovo) {
                            tihih++;
                        }
                    }
                }
            }
            if (vsePrav && PredlogIzidaListka.sestavi(p, 5, false).pripravljen()) {
                brezPopravka++;
            }
        }
        Meritev m = new Meritev(listkov, neprebranih, (double) prav / Math.max(1, celic),
                (double) tihih / Math.max(1, celic), brezPopravka);
        System.out.printf("Simulirane fotografije %s: %s%n", pogoji == ZAHTEVNO ? "zahtevne" : "obicajne", m);
        return m;
    }

    // ---------- Simulacija ----------

    /* Nakljucna veljavna tekma na »zaZmago« nizov (vecinoma 11:x, obcasno podaljsek). */
    private static int[][] nakljucnaTekma(Random r, int zaZmago) {
        List<int[]> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < zaZmago && b < zaZmago) {
            boolean prvi = r.nextBoolean();
            int porazenec;
            int zmagovalec;
            if (r.nextDouble() < 0.2) {
                zmagovalec = 12 + r.nextInt(4);
                porazenec = zmagovalec - 2;
            } else {
                zmagovalec = 11;
                porazenec = r.nextInt(10);
            }
            nizi.add(prvi ? new int[] {zmagovalec, porazenec} : new int[] {porazenec, zmagovalec});
            if (prvi) {
                a++;
            } else {
                b++;
            }
        }
        return nizi.toArray(new int[0][]);
    }

    private static int[] dobljeni(int[][] izid) {
        int[] d = new int[2];
        for (int[] n : izid) {
            d[n[0] > n[1] ? 0 : 1]++;
        }
        return d;
    }

    /* Vrednost z rocnimi stevkami MNIST v celico (kemicni svincnik: temno modra). */
    private static void napisi(BufferedImage slika, CeliceListka.Celica c, int vrednost, Random r, Pogoji pogoji) {
        String zapis = String.valueOf(vrednost);
        double r10 = GeometrijaListka.PX_NA_MM;
        double x0 = c.x0() / r10 * pxNaMm;
        double y0 = c.y0() / r10 * pxNaMm;
        double w = c.sirina() / r10 * pxNaMm;
        double h = c.visina() / r10 * pxNaMm;
        double visina = h * (pogoji.visinaOd() + r.nextDouble() * (pogoji.visinaDo() - pogoji.visinaOd()));
        double razmik = pxNaMm * (pogoji.razmikOd() + r.nextDouble() * (pogoji.razmikDo() - pogoji.razmikOd()));
        double x = x0 + w * (0.3 + r.nextDouble() * 0.2);
        double y = y0 + (h - visina) / 2 + (r.nextDouble() - 0.5) * h * 0.1;
        for (char znak : zapis.toCharArray()) {
            int stevka = znak - '0';
            UcenjeStevk.Vzorec v;
            do {
                v = stevke.get(r.nextInt(stevke.size()));
            } while (v.oznaka() != stevka);
            x += narisiStevko(slika, v.slika(), x, y, visina) + razmik;
        }
    }

    /* Narise stevko (obrezano na crnilo) z visino »visina«; vrne njeno sirino. */
    private static double narisiStevko(BufferedImage slika, byte[] mnist, double x, double y, double visina) {
        int minX = 28;
        int maxX = -1;
        int minY = 28;
        int maxY = -1;
        for (int i = 0; i < 784; i++) {
            if ((mnist[i] & 0xFF) > 50) {
                minX = Math.min(minX, i % 28);
                maxX = Math.max(maxX, i % 28);
                minY = Math.min(minY, i / 28);
                maxY = Math.max(maxY, i / 28);
            }
        }
        double merilo = visina / (maxY - minY + 1);
        double sirina = (maxX - minX + 1) * merilo;
        for (int py = (int) y; py < y + visina; py++) {
            for (int px = (int) x; px < x + sirina; px++) {
                double mx = minX + (px - x) / merilo;
                double my = minY + (py - y) / merilo;
                int ix = (int) mx;
                int iy = (int) my;
                if (ix < 0 || iy < 0 || ix >= 27 || iy >= 27 || px < 0 || py < 0
                        || px >= slika.getWidth() || py >= slika.getHeight()) {
                    continue;
                }
                double fx = mx - ix;
                double fy = my - iy;
                double a = ((mnist[iy * 28 + ix] & 0xFF) * (1 - fx) + (mnist[iy * 28 + ix + 1] & 0xFF) * fx) * (1 - fy)
                        + ((mnist[(iy + 1) * 28 + ix] & 0xFF) * (1 - fx) + (mnist[(iy + 1) * 28 + ix + 1] & 0xFF) * fx) * fy;
                a /= 255;
                if (a <= 0.02) {
                    continue;
                }
                int p = slika.getRGB(px, py);
                int rr = (int) (((p >> 16) & 0xFF) * (1 - a) + 25 * a);
                int gg = (int) (((p >> 8) & 0xFF) * (1 - a) + 35 * a);
                int bb = (int) ((p & 0xFF) * (1 - a) + 110 * a);
                slika.setRGB(px, py, (rr << 16) | (gg << 8) | bb);
            }
        }
        return sirina;
    }

    /* Fotografija telefona: listek v platnu 2000 x 1500 na mizi, zasukan, s
       perspektivo, svetlobnim prelivom, sumom (po zelji zamegljen) in JPEG 85 %. */
    static byte[] fotografija(BufferedImage listek, Random r, Pogoji pogoji) throws IOException {
        int W = 2000;
        int H = 1500;
        double w = listek.getWidth();
        double h = listek.getHeight();
        // organizator posname cel listek: zasede vecino slike, a ne cez rob
        double sirina = 950 + r.nextDouble() * 300;
        double visina = sirina * h / w;
        double kot = Math.toRadians((r.nextDouble() * 2 - 1) * pogoji.kot());
        double cx = W / 2.0 + (r.nextDouble() - 0.5) * 150;
        double cy = H / 2.0 + (r.nextDouble() - 0.5) * 100;
        double[][] koti = {{-sirina / 2, -visina / 2}, {sirina / 2, -visina / 2}, {sirina / 2, visina / 2}, {-sirina / 2, visina / 2}};
        double[][] platno = new double[4][];
        for (int i = 0; i < 4; i++) {
            double px = koti[i][0] * (1 + (r.nextDouble() - 0.5) * pogoji.perspektiva());
            double py = koti[i][1] * (1 + (r.nextDouble() - 0.5) * pogoji.perspektiva());
            platno[i] = new double[] {cx + px * Math.cos(kot) - py * Math.sin(kot), cy + px * Math.sin(kot) + py * Math.cos(kot)};
        }
        Preslikava vListek = Preslikava.homografija(platno,
                new double[][] {{0, 0}, {w - 1, 0}, {w - 1, h - 1}, {0, h - 1}});
        double svetloba = 0.7 + r.nextDouble() * 0.2;
        int[] rgb = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double[] p = vListek.uporabi(x, y);
                double luc = svetloba + (1 - svetloba) * x / W;
                int c = p[0] >= 0 && p[1] >= 0 && p[0] < w - 1 && p[1] < h - 1
                        ? listek.getRGB((int) p[0], (int) p[1]) : (120 << 16) | (96 << 8) | 70;
                double sum = r.nextGaussian() * 5;
                rgb[y * W + x] = (omeji(((c >> 16) & 0xFF) * luc + sum) << 16)
                        | (omeji(((c >> 8) & 0xFF) * luc + sum) << 8) | omeji((c & 0xFF) * luc + sum);
            }
        }
        if (pogoji.zamegli()) {
            rgb = zamegli(rgb, W, H);
        }
        BufferedImage foto = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        foto.setRGB(0, 0, W, H, rgb, 0, W);
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        Iterator<ImageWriter> pisci = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter pisec = pisci.next();
        ImageWriteParam nastavitve = pisec.getDefaultWriteParam();
        nastavitve.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        nastavitve.setCompressionQuality(0.85f);
        try (ImageOutputStream o = ImageIO.createImageOutputStream(izhod)) {
            pisec.setOutput(o);
            pisec.write(null, new IIOImage(foto, null, null), nastavitve);
        } finally {
            pisec.dispose();
        }
        return izhod.toByteArray();
    }

    /* Povprecje 3 x 3 (rahlo neostra fotografija). */
    private static int[] zamegli(int[] rgb, int W, int H) {
        int[] izhod = rgb.clone();
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int rr = 0;
                int gg = 0;
                int bb = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int c = rgb[(y + dy) * W + x + dx];
                        rr += (c >> 16) & 0xFF;
                        gg += (c >> 8) & 0xFF;
                        bb += c & 0xFF;
                    }
                }
                izhod[y * W + x] = ((rr / 9) << 16) | ((gg / 9) << 8) | (bb / 9);
            }
        }
        return izhod;
    }

    private static int omeji(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static BufferedImage kopija(BufferedImage izvor) {
        BufferedImage k = new BufferedImage(izvor.getWidth(), izvor.getHeight(), BufferedImage.TYPE_INT_RGB);
        k.getGraphics().drawImage(izvor, 0, 0, null);
        return k;
    }
}
