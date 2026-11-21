/* Rocni zapis v eni celici: prazno, ena stevka (»7«) ali dve (»11«, »13«).

   Celica je prosta (sodnik pise, kakor je navajen), zato razrez ni vedno
   enolicen: »11« sta lahko dve loceni crti ali ena sklenjena poteza, »10« je
   lahko ena zanka. Namesto ene odlocitve vrne vec RAZLAG z ocenjeno
   verjetnostjo (ena ali dve stevki); katera je prava, odlocita mreza stevk in
   pravila namiznega tenisa (RazlagaListka).

   Vsaka stevka gre v obliko, v kateri je bila naucena mreza (MNIST): crnilo,
   pomanjsano v okvir 20 x 20 z ohranjenim razmerjem in postavljeno v sliko
   28 x 28 tako, da je tezisce v sredini. */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class RazrezCelice {

    private RazrezCelice() {}

    public static final int MNIST = 28;

    /* Stevka kot vhod mreze (28 x 28, 0 = papir, 1 = crnilo). */
    public record Stevka(float[] vhod) {}

    /* Ena razlaga zapisa: stevke od leve proti desni in njena verjetnost. */
    public record Razlaga(List<Stevka> stevke, double verjetnost) {}

    /* prazna = v celici ni zapisa; sicer razlage (vsota verjetnosti 1).
       crnilo = delez notranjosti celice s crnilom, skupin = skupine potez pred
       zdruzevanjem, razpon = delez sirine celice od prve do zadnje poteze. */
    public record Vsebina(boolean prazna, List<Razlaga> razlage, double crnilo, int skupin, double razpon) {

        /* Popravljena celica (F2.5): sodnik je vrednost precrtal ali prepisal in
           napisal novo. Pozna se po nenavadno veliko crnila, po vec kot treh
           skupinah potez ali po zapisu, ki se razteza cez vecino celice (ena
           vrednost ima najvec dve stevki). Taka celica je vedno negotova, njene
           stevke pa ne gredo v ucenje. */
        public boolean popravljena() {
            return !prazna && (crnilo > NAJVEC_CRNILA || skupin > 3 || razpon > NAJVEC_RAZPONA);
        }
    }

    /* Najvec delov (komponent) celice, ki se se obdelajo (varnost: cas branja). */
    static final int NAJVEC_DELOV = 40;

    /* Meje popravljene celice, umerjene na simulaciji (PopravljenaCelicaPoskus). */
    static final double NAJVEC_CRNILA = 0.12;
    static final double NAJVEC_RAZPONA = 0.35;

    private static final Vsebina PRAZNA = new Vsebina(true, List.of(), 0, 0, 0);

    /* Odmik od roba celice: crta tabele in njen rob ne gresta v razrez (tudi ne v
       zlati nabor, ZlatiNabor.slikaCelic). */
    public static final int ODMIK = 4;
    /* Delez notranjosti celice, ki je zagotovo papir (percentil ravni papirja). */
    private static final double PERCENTIL_PAPIRJA = 0.35;

    /* Temnost papirja v celici, kot jo vzame razrez; NaN, ce je celica premajhna.
       Zlati nabor jo odsteje, da se papir shrani kot cisto belo (ZlatiNabor). */
    public static float papir(Temnost listek, CeliceListka.Celica c) {
        float[] d = notranjost(listek, c);
        if (d == null) {
            return Float.NaN;
        }
        Arrays.sort(d);
        return d[(int) (d.length * PERCENTIL_PAPIRJA)];
    }

    /* Temnost notranjosti celice brez roba; null, ce je celica premajhna. */
    private static float[] notranjost(Temnost listek, CeliceListka.Celica c) {
        int x0 = c.x0() + ODMIK;
        int y0 = c.y0() + ODMIK;
        int w = c.sirina() - 2 * ODMIK;
        int h = c.visina() - 2 * ODMIK;
        if (w < 10 || h < 10) {
            return null;
        }
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y * w + x] = listek.vrni(x0 + x, y0 + y);
            }
        }
        return d;
    }

    public static Vsebina razrezi(Temnost listek, CeliceListka.Celica c) {
        float[] d = notranjost(listek, c);
        if (d == null) {
            return PRAZNA;
        }
        int w = c.sirina() - 2 * ODMIK;
        int h = c.visina() - 2 * ODMIK;
        float[] urejeno = d.clone();
        Arrays.sort(urejeno);
        float papir = urejeno[(int) (urejeno.length * PERCENTIL_PAPIRJA)];
        float crnilo = urejeno[Math.min(urejeno.length - 1, (int) (urejeno.length * 0.995))];
        if (crnilo - papir < 0.18f) {
            return PRAZNA;
        }
        float prag = Math.max(papir + 0.12f, papir + 0.5f * (crnilo - papir));
        boolean[] maska = new boolean[w * h];
        for (int i = 0; i < maska.length; i++) {
            maska[i] = d[i] > prag;
        }

        List<Temnost.Komponenta> deli = new ArrayList<>();
        int skupaj = 0;
        for (Temnost.Komponenta k : Temnost.komponente(maska, w, h, true)) {
            boolean ostanekVodoravneCrte = k.visina() <= 4 && k.sirina() > 0.4 * w;
            boolean ostanekNavpicneCrte = k.sirina() <= 4 && k.visina() > 0.6 * h
                    && (k.minX <= 1 || k.maxX >= w - 2);
            if (k.ploscina < 10 || ostanekVodoravneCrte || ostanekNavpicneCrte) {
                continue;
            }
            deli.add(k);
            skupaj += k.ploscina;
        }
        if (skupaj < 25) {
            return PRAZNA;
        }
        // Omejitev racunanja (zdruzevanje skupin je kubicno): celica z vec kot
        // NAJVEC_DELOV deli je umazana ali precrtana - obdrzi najvecje dele, celica je
        // popravljena (skupin = vseh delov).
        int vsehDelov = deli.size();
        if (vsehDelov > NAJVEC_DELOV) {
            deli.sort(Comparator.comparingInt((Temnost.Komponenta k) -> k.ploscina).reversed());
            deli = new ArrayList<>(deli.subList(0, NAJVEC_DELOV));
        }

        List<List<Temnost.Komponenta>> skupine = zdruziPoStolpcih(deli);
        int skupin = vsehDelov > NAJVEC_DELOV ? vsehDelov : skupine.size();
        int levo = Integer.MAX_VALUE;
        int desno = -1;
        for (Temnost.Komponenta k : deli) {
            levo = Math.min(levo, k.minX);
            desno = Math.max(desno, k.maxX);
        }
        double delezCrnila = (double) skupaj / (w * h);
        double razpon = (double) (desno - levo + 1) / w;
        while (skupine.size() > 2) {
            zdruziNajblizji(skupine);
        }
        List<Razlaga> razlage = new ArrayList<>();
        if (skupine.size() == 2) {
            razlage.add(new Razlaga(List.of(stevka(skupine.get(0), w), stevka(skupine.get(1), w)),
                    deli.size() > 2 ? 0.8 : 0.9));
            razlage.add(new Razlaga(List.of(stevka(vse(skupine), w)), deli.size() > 2 ? 0.2 : 0.1));
        } else {
            List<Temnost.Komponenta> g = skupine.getFirst();
            int[] okvir = okvir(g);
            double razmerje = (double) (okvir[2] - okvir[0] + 1) / (okvir[3] - okvir[1] + 1);
            double ena = razmerje < 0.75 ? 0.95 : razmerje < 1.0 ? 0.75 : razmerje < 1.3 ? 0.45 : 0.2;
            razlage.add(new Razlaga(List.of(stevka(g, w)), ena));
            List<Stevka> razdeljeno = razdeli(g, w, okvir);
            if (razdeljeno != null) {
                razlage.add(new Razlaga(razdeljeno, 1 - ena));
            }
        }
        return new Vsebina(false, normaliziraj(razlage), delezCrnila, skupin, razpon);
    }

    /* Deli, ki se v vodoravni smeri prekrivajo (precka sedmice, odtrgan zgornji
       lok petice), so ena stevka. */
    private static List<List<Temnost.Komponenta>> zdruziPoStolpcih(List<Temnost.Komponenta> deli) {
        List<Temnost.Komponenta> po = new ArrayList<>(deli);
        po.sort(Comparator.comparingInt(k -> k.minX));
        List<List<Temnost.Komponenta>> skupine = new ArrayList<>();
        List<int[]> meje = new ArrayList<>();
        for (Temnost.Komponenta k : po) {
            boolean dodana = false;
            for (int i = 0; i < skupine.size(); i++) {
                int[] m = meje.get(i);
                int prekrivanje = Math.min(m[1], k.maxX) - Math.max(m[0], k.minX) + 1;
                int ozja = Math.min(m[1] - m[0] + 1, k.sirina());
                if (prekrivanje >= 0.5 * ozja) {
                    skupine.get(i).add(k);
                    m[0] = Math.min(m[0], k.minX);
                    m[1] = Math.max(m[1], k.maxX);
                    dodana = true;
                    break;
                }
            }
            if (!dodana) {
                skupine.add(new ArrayList<>(List.of(k)));
                meje.add(new int[] {k.minX, k.maxX});
            }
        }
        return skupine;
    }

    /* Tri ali vec skupin (raztrgana poteza): zdruzi sosednji z najmanjso vrzeljo. */
    private static void zdruziNajblizji(List<List<Temnost.Komponenta>> skupine) {
        skupine.sort(Comparator.comparingInt(g -> okvir(g)[0]));
        int najboljsi = 0;
        int najmanjsaVrzel = Integer.MAX_VALUE;
        for (int i = 0; i + 1 < skupine.size(); i++) {
            int vrzel = okvir(skupine.get(i + 1))[0] - okvir(skupine.get(i))[2];
            if (vrzel < najmanjsaVrzel) {
                najmanjsaVrzel = vrzel;
                najboljsi = i;
            }
        }
        skupine.get(najboljsi).addAll(skupine.remove(najboljsi + 1));
    }

    private static List<Temnost.Komponenta> vse(List<List<Temnost.Komponenta>> skupine) {
        List<Temnost.Komponenta> vse = new ArrayList<>();
        skupine.forEach(vse::addAll);
        return vse;
    }

    /* minX, minY, maxX, maxY skupine. */
    private static int[] okvir(List<Temnost.Komponenta> g) {
        int[] o = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (Temnost.Komponenta k : g) {
            o[0] = Math.min(o[0], k.minX);
            o[1] = Math.min(o[1], k.minY);
            o[2] = Math.max(o[2], k.maxX);
            o[3] = Math.max(o[3], k.maxY);
        }
        return o;
    }

    /* Sklenjen zapis dveh stevk (»11« v eni potezi): razrez na stolpcu z najmanj
       crnila v srednjem delu. null, ce bi bil kateri del prazen. */
    private static List<Stevka> razdeli(List<Temnost.Komponenta> g, int w, int[] okvir) {
        int sirina = okvir[2] - okvir[0] + 1;
        int[] stolpci = new int[sirina];
        for (Temnost.Komponenta k : g) {
            for (int i : k.tocke) {
                stolpci[i % w - okvir[0]]++;
            }
        }
        int rez = -1;
        int najmanj = Integer.MAX_VALUE;
        for (int x = (int) (sirina * 0.3); x <= (int) (sirina * 0.7); x++) {
            if (stolpci[x] < najmanj) {
                najmanj = stolpci[x];
                rez = x + okvir[0];
            }
        }
        if (rez < 0) {
            return null;
        }
        List<Integer> levo = new ArrayList<>();
        List<Integer> desno = new ArrayList<>();
        for (Temnost.Komponenta k : g) {
            for (int i : k.tocke) {
                (i % w < rez ? levo : desno).add(i);
            }
        }
        if (levo.size() < 10 || desno.size() < 10) {
            return null;
        }
        return List.of(vMnist(levo, w), vMnist(desno, w));
    }

    private static Stevka stevka(List<Temnost.Komponenta> g, int w) {
        List<Integer> tocke = new ArrayList<>();
        for (Temnost.Komponenta k : g) {
            for (int i : k.tocke) {
                tocke.add(i);
            }
        }
        return vMnist(tocke, w);
    }

    /* Tocke crnila v sliko 28 x 28 po postopku MNIST: okvir 20 x 20 z ohranjenim
       razmerjem (ploscinsko povprecenje da sivine robov), tezisce v sredini. */
    static Stevka vMnist(List<Integer> tocke, int w) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int i : tocke) {
            minX = Math.min(minX, i % w);
            maxX = Math.max(maxX, i % w);
            minY = Math.min(minY, i / w);
            maxY = Math.max(maxY, i / w);
        }
        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;
        boolean[] m = new boolean[bw * bh];
        for (int i : tocke) {
            m[(i / w - minY) * bw + (i % w - minX)] = true;
        }
        double f = 20.0 / Math.max(bw, bh);
        int tw = Math.max(1, (int) Math.round(bw * f));
        int th = Math.max(1, (int) Math.round(bh * f));
        float[] mala = new float[tw * th];
        int vzorcev = 4;
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int zadetkov = 0;
                for (int j = 0; j < vzorcev; j++) {
                    for (int i = 0; i < vzorcev; i++) {
                        int sx = (int) ((tx + (i + 0.5) / vzorcev) / f);
                        int sy = (int) ((ty + (j + 0.5) / vzorcev) / f);
                        if (sx < bw && sy < bh && m[sy * bw + sx]) {
                            zadetkov++;
                        }
                    }
                }
                mala[ty * tw + tx] = (float) zadetkov / (vzorcev * vzorcev);
            }
        }
        double vsota = 0;
        double cx = 0;
        double cy = 0;
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                float v = mala[y * tw + x];
                vsota += v;
                cx += v * x;
                cy += v * y;
            }
        }
        int ox = vsota > 0 ? (int) Math.round(MNIST / 2.0 - 0.5 - cx / vsota) : (MNIST - tw) / 2;
        int oy = vsota > 0 ? (int) Math.round(MNIST / 2.0 - 0.5 - cy / vsota) : (MNIST - th) / 2;
        float[] vhod = new float[MNIST * MNIST];
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int X = x + ox;
                int Y = y + oy;
                if (X >= 0 && X < MNIST && Y >= 0 && Y < MNIST) {
                    vhod[Y * MNIST + X] = mala[y * tw + x];
                }
            }
        }
        return new Stevka(vhod);
    }

    private static List<Razlaga> normaliziraj(List<Razlaga> razlage) {
        double vsota = razlage.stream().mapToDouble(Razlaga::verjetnost).sum();
        return razlage.stream().map(r -> new Razlaga(r.stevke(), r.verjetnost() / vsota)).toList();
    }
}
