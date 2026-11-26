/* Branje ligaskega zapisnika na simuliranih fotografijah (od fotografije do
   predloga), za obe obliki (VRSTICE, KADER).

   List se narise iz iste postavitve, kot jo natisne vmesnik (RisalnikZapisnika),
   v kvadratke se narisejo krizci kemicnega svincnika (tudi kljukice in kaksen
   pocrnjen - preklican - kvadratek), v celice pa rocne stevke iz preizkusnega
   dela MNIST. Srecanje je nakljucno: postava iz kadra, menjave, nizi po pravilih.
   List se nato »fotografira« (SimuliraneFotografijeTest.fotografija: zasuk,
   perspektiva, svetloba, sum, JPEG).

   Domenska pravila, ki jih test varuje:
   - krizci se preberejo, preklican (pocrnjen) kvadratek ni krizec;
   - predlog doloci, kdo je igral (tudi menjave), in izid vseh tekem;
   - zapisnik, ki ga predlog oznaci kot zanesljiv (samodejni zapis), je skoraj
     vedno pravilen - tiha napaka je redka;
   - pri pragu zmag so tekme po odlocitvi prazne in predlog to sprejme.
   Vecji vzorec: -Dsimulacija.zapisnikov=40. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;

class SimulacijaZapisnikaTest {

    static final double PX_NA_MM = 12;
    static final int NIZOV = 5;

    private static List<UcenjeStevk.Vzorec> stevke;
    static LokalniBralnikZapisnika bralnik;

    @BeforeAll
    static void pripravi() throws IOException {
        try (InputStream v = SimulacijaZapisnikaTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            stevke = UcenjeStevk.beriIzbor(v);
        }
        VirMrezeStevk vir = new VirMrezeStevk(null);
        VirMrezeStevk.Nalozena vgrajena = VirMrezeStevk.vgrajena();
        vir.nastavi(vgrajena.mreza(), vgrajena.oznaka());
        bralnik = new LokalniBralnikZapisnika(vir);
    }

    static List<GeometrijaZapisnika.TekmaRazporeda> tekme(FormatSrecanja f) {
        return f.razpored().stream()
                .map(m -> new GeometrijaZapisnika.TekmaRazporeda(m.oznaka(), m.tip() == TipTekmeSrecanja.DVOJICE))
                .toList();
    }

    /* Resnica enega srecanja: igralci vsake tekme (vrstice kadra) in nizi (null = neodigrana). */
    record Resnica(List<List<Integer>> domaci, List<List<Integer>> gost, List<int[][]> nizi,
                   List<int[]> preklicani) {}

    static Resnica nakljucnoSrecanje(Random r, FormatSrecanja f, int kaderD, int kaderG, Integer prag) {
        int zaZmago = NIZOV / 2 + 1;
        int[] mestaD = razlicni(r, kaderD, 2);
        int[] mestaG = razlicni(r, kaderG, 2);
        List<List<Integer>> d = new ArrayList<>();
        List<List<Integer>> g = new ArrayList<>();
        List<int[][]> nizi = new ArrayList<>();
        int zmD = 0;
        int zmG = 0;
        // prvi nastop mesta doloci zacetno postavo: tam menjave ni
        java.util.Set<String> videni = new java.util.HashSet<>();
        for (FormatSrecanja.MestoTekme m : f.razpored()) {
            if (prag != null && (zmD >= prag || zmG >= prag)) {
                d.add(List.of());
                g.add(List.of());
                nizi.add(null);
                continue;
            }
            if (m.tip() == TipTekmeSrecanja.DVOJICE) {
                d.add(par(r, mestaD, kaderD));
                g.add(par(r, mestaG, kaderG));
            } else {
                int a = mestaD[m.domaci().charAt(0) - 'A'];
                int x = mestaG[m.gost().charAt(0) - 'X'];
                d.add(List.of(videni.add("D" + m.domaci()) ? a : igralec(r, a, kaderD)));
                g.add(List.of(videni.add("G" + m.gost()) ? x : igralec(r, x, kaderG)));
            }
            int[][] t = tekma(r, zaZmago);
            nizi.add(t);
            if (t[t.length - 1][0] > t[t.length - 1][1]) {
                zmD++;
            } else {
                zmG++;
            }
        }
        return new Resnica(d, g, nizi, new ArrayList<>());
    }

    private static int[] razlicni(Random r, int n, int k) {
        List<Integer> vsi = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            vsi.add(i);
        }
        java.util.Collections.shuffle(vsi, r);
        int[] izbrani = new int[k];
        for (int i = 0; i < k; i++) {
            izbrani[i] = vsi.get(i);
        }
        return izbrani;
    }

    /* Igralec mesta, ob menjavi (15 %) drug iz kadra. */
    private static int igralec(Random r, int mesto, int kader) {
        if (kader > 2 && r.nextDouble() < 0.15) {
            int drug;
            do {
                drug = r.nextInt(kader);
            } while (drug == mesto);
            return drug;
        }
        return mesto;
    }

    private static List<Integer> par(Random r, int[] mesta, int kader) {
        int a = mesta[0];
        int b = mesta[1];
        if (kader > 2 && r.nextDouble() < 0.15) {
            do {
                b = r.nextInt(kader);
            } while (b == a);
        }
        return a < b ? List.of(a, b) : List.of(b, a);
    }

    /* Nakljucna tekma do zaZmago nizov (nizi kot {domaci, gost}). */
    static int[][] tekma(Random r, int zaZmago) {
        List<int[]> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < zaZmago && b < zaZmago) {
            boolean prvi = r.nextBoolean();
            int porazenec = r.nextDouble() < 0.12 ? 10 + r.nextInt(4) : r.nextInt(10);
            int zmagovalec = porazenec >= 10 ? porazenec + 2 : 11;
            nizi.add(prvi ? new int[] {zmagovalec, porazenec} : new int[] {porazenec, zmagovalec});
            if (prvi) {
                a++;
            } else {
                b++;
            }
        }
        return nizi.toArray(new int[0][]);
    }

    /* Izpolni list: krizci (10 % kljukic, ob 8 % tekem se preklican pocrnjen
       kvadratek drugega igralca) in tocke. */
    static BufferedImage izpolni(GeometrijaZapisnika.PostavitevZapisnika p, Resnica res, Random r) {
        return izpolni(p, res, r, "TZ:4711:ABCDEFGH");
    }

    static BufferedImage izpolni(GeometrijaZapisnika.PostavitevZapisnika p, Resnica res, Random r, String qr) {
        BufferedImage list = RisalnikZapisnika.narisi(p, qr, PX_NA_MM);
        napolni(list, p, res, r);
        return list;
    }

    /* Krizci in tocke na ze narisan list (12 px/mm) - tudi na posnetek lista iz vmesnika. */
    static void napolni(BufferedImage list, GeometrijaZapisnika.PostavitevZapisnika p, Resnica res, Random r) {
        for (GeometrijaZapisnika.Kvadratek k : p.kvadratki()) {
            List<Integer> igrali = (k.domaci() ? res.domaci() : res.gost()).get(k.tekma());
            if (igrali.contains(k.igralec())) {
                RisalnikZapisnika.krizec(list, k.p(), PX_NA_MM, r, r.nextDouble() < 0.1);
            }
        }
        // preklic: pri nekaterih odigranih tekmah je bil najprej prekrizan napacen igralec
        for (int t = 0; t < res.nizi().size(); t++) {
            if (res.nizi().get(t) == null || r.nextDouble() >= 0.08) {
                continue;
            }
            final int tekma = t;
            List<GeometrijaZapisnika.Kvadratek> prosti = p.kvadratki().stream()
                    .filter(k -> k.tekma() == tekma && k.domaci()
                            && !res.domaci().get(tekma).contains(k.igralec()))
                    .toList();
            if (!prosti.isEmpty()) {
                GeometrijaZapisnika.Kvadratek k = prosti.get(r.nextInt(prosti.size()));
                RisalnikZapisnika.pocrni(list, k.p(), PX_NA_MM, r);
                res.preklicani().add(new int[] {t, k.igralec()});
            }
        }
        for (GeometrijaZapisnika.Celica c : p.celice()) {
            int[][] nizi = res.nizi().get(c.tekma());
            if (nizi == null || c.niz() >= nizi.length) {
                continue;
            }
            napisi(list, c.p(), nizi[c.niz()][c.domaci() ? 0 : 1], r);
        }
    }

    private static void napisi(BufferedImage list, GeometrijaZapisnika.Pravokotnik c, int vrednost, Random r) {
        String zapis = String.valueOf(vrednost);
        double w = c.sirina() * PX_NA_MM;
        double h = c.visina() * PX_NA_MM;
        double visina = h * (0.5 + r.nextDouble() * 0.25);
        if (zapis.length() == 2) {
            visina = Math.min(visina, w * 0.55);
        }
        double x = c.x() * PX_NA_MM + w * (0.08 + r.nextDouble() * 0.15);
        double y = c.y() * PX_NA_MM + (h - visina) / 2 + (r.nextDouble() - 0.5) * h * 0.1;
        for (char znak : zapis.toCharArray()) {
            int stevka = znak - '0';
            UcenjeStevk.Vzorec v;
            do {
                v = stevke.get(r.nextInt(stevke.size()));
            } while (v.oznaka() != stevka);
            x += SimuliraneFotografijeTest.narisiStevko(list, v.slika(), x, y, visina) + PX_NA_MM * (0.2 + r.nextDouble() * 0.6);
        }
    }

    record Meritev(int zapisnikov, int neprebranih, int pravilnih, int zanesljivih, int tihih, int pravihIgralcev,
                   int igralcev, int pravihCelic, int celic) {
        @Override
        public String toString() {
            return String.format("zapisnikov %d, neprebranih %d, brez napake %d, zanesljivih %d, tihih %d, "
                            + "igralci %.1f %%, celice %.1f %%", zapisnikov, neprebranih, pravilnih, zanesljivih, tihih,
                    100.0 * pravihIgralcev / Math.max(1, igralcev), 100.0 * pravihCelic / Math.max(1, celic));
        }
    }

    static Meritev izmeri(Random r, int n, GeometrijaZapisnika.Oblika oblika, FormatSrecanja f, int kaderD,
                          int kaderG, Integer prag, SimuliraneFotografijeTest.Pogoji pogoji) throws IOException {
        GeometrijaZapisnika.PostavitevZapisnika p = GeometrijaZapisnika.izracunaj(GeometrijaZapisnika.RAZLICICA,
                oblika, tekme(f), NIZOV, kaderD + 1, kaderG + 1);
        LokalniBralnikZapisnika.NatisZaBranje natis = new LokalniBralnikZapisnika.NatisZaBranje(4711, p, NIZOV);
        int neprebranih = 0;
        int pravilnih = 0;
        int zanesljivih = 0;
        int tihih = 0;
        int pravihIgralcev = 0;
        int igralcev = 0;
        int pravihCelic = 0;
        int celic = 0;
        boolean zahtevno = pogoji.zamegli();
        for (int i = 0; i < n; i++) {
            Resnica res = nakljucnoSrecanje(r, f, kaderD, kaderG, prag);
            BufferedImage list = izpolni(p, res, r);
            byte[] foto = SimuliraneFotografijeTest.fotografija(list, r, pogoji, 3200, 2400,
                    zahtevno ? 2000 : 2300, zahtevno ? 250 : 350);
            LokalniBralnikZapisnika.Branje b;
            try {
                b = bralnik.preberi(foto, k -> natis);
            } catch (LokalniBralnikZapisnika.NapakaBranjaZapisnika e) {
                neprebranih++;
                continue;
            }
            PredlogZapisnika.Predlog predlog = PredlogZapisnika.izracunaj(b.zapisnik(), f, NIZOV, prag, kaderD, kaderG);
            boolean prav = true;
            for (int t = 0; t < res.nizi().size(); t++) {
                PredlogZapisnika.Tekma pt = predlog.tekme().get(t);
                igralcev += 2;
                boolean igralciD = pt.domaci().equals(res.domaci().get(t));
                boolean igralciG = pt.gost().equals(res.gost().get(t));
                pravihIgralcev += (igralciD ? 1 : 0) + (igralciG ? 1 : 0);
                prav &= igralciD && igralciG;
                int[][] nizi = res.nizi().get(t);
                for (int s = 0; s < NIZOV; s++) {
                    PrebranListek.Niz niz = s < pt.nizi().size() ? pt.nizi().get(s) : null;
                    Integer d = niz == null ? null : niz.levo();
                    Integer g = niz == null ? null : niz.desno();
                    Integer pd = nizi != null && s < nizi.length ? nizi[s][0] : null;
                    Integer pg = nizi != null && s < nizi.length ? nizi[s][1] : null;
                    celic += 2;
                    boolean cd = java.util.Objects.equals(d, pd);
                    boolean cg = java.util.Objects.equals(g, pg);
                    pravihCelic += (cd ? 1 : 0) + (cg ? 1 : 0);
                    prav &= cd && cg;
                }
            }
            pravilnih += prav ? 1 : 0;
            zanesljivih += predlog.zanesljiv() ? 1 : 0;
            if (Boolean.getBoolean("simulacija.razlogi") && !predlog.zanesljiv()) {
                StringBuilder sb = new StringBuilder("  ni zanesljiv (prav=" + prav + "): " + predlog.opozorila());
                for (PredlogZapisnika.Tekma pt : predlog.tekme()) {
                    if (pt.negotovoDomaci() || pt.negotovoGost() || pt.negotoveTocke()) {
                        sb.append(" | ").append(pt.oznaka()).append(pt.negotovoDomaci() ? " igralecD" : "")
                                .append(pt.negotovoGost() ? " igralecG" : "").append(pt.negotoveTocke() ? " tocke" : "");
                    }
                }
                for (PrebranZapisnik.Tekma t : b.zapisnik().tekme()) {
                    for (PrebranZapisnik.Krizec kr : t.domaci()) {
                        if (!kr.stanje().equals("PRAZEN") && !kr.stanje().equals("KRIZEC")) {
                            sb.append(" [").append(kr.stanje()).append(' ').append(kr.crnilo()).append(']');
                        }
                    }
                }
                System.out.println(sb);
            }
            if (predlog.zanesljiv() && !prav) {
                tihih++;
            }
        }
        return new Meritev(n, neprebranih, pravilnih, zanesljivih, tihih, pravihIgralcev, igralcev, pravihCelic, celic);
    }

    private static int stevilo() {
        return Integer.getInteger("simulacija.zapisnikov", 6);
    }

    @Test
    void vrsticeObicajneFotografije() throws IOException {
        Meritev m = izmeri(new Random(11), stevilo(), GeometrijaZapisnika.Oblika.VRSTICE, FormatSrecanja.SAVINJA,
                3, 4, null, SimuliraneFotografijeTest.OBICAJNO);
        System.out.println("VRSTICE obicajno: " + m);
        assertEquals(0, m.neprebranih());
        assertEquals(0, m.tihih());
        assertTrue(m.pravihCelic() >= 0.97 * m.celic(), m.toString());
        assertTrue(m.pravihIgralcev() >= 0.98 * m.igralcev(), m.toString());
    }

    @Test
    void kaderObicajneFotografije() throws IOException {
        Meritev m = izmeri(new Random(12), stevilo(), GeometrijaZapisnika.Oblika.KADER, FormatSrecanja.SAVINJA,
                8, 6, null, SimuliraneFotografijeTest.OBICAJNO);
        System.out.println("KADER obicajno: " + m);
        assertEquals(0, m.neprebranih());
        assertEquals(0, m.tihih());
        assertTrue(m.pravihCelic() >= 0.97 * m.celic(), m.toString());
        assertTrue(m.pravihIgralcev() >= 0.98 * m.igralcev(), m.toString());
    }

    @Test
    void zahtevneFotografijeBrezTihihNapak() throws IOException {
        Meritev v = izmeri(new Random(13), stevilo(), GeometrijaZapisnika.Oblika.VRSTICE, FormatSrecanja.CORBILLON,
                4, 3, 3, SimuliraneFotografijeTest.ZAHTEVNO);
        Meritev k = izmeri(new Random(14), stevilo(), GeometrijaZapisnika.Oblika.KADER, FormatSrecanja.DSI,
                9, 9, 2, SimuliraneFotografijeTest.ZAHTEVNO);
        System.out.println("VRSTICE zahtevno: " + v);
        System.out.println("KADER zahtevno: " + k);
        assertTrue(v.neprebranih() + k.neprebranih() <= 1);
        assertEquals(0, v.tihih() + k.tihih());
        assertTrue(v.pravihIgralcev() >= 0.95 * v.igralcev(), v.toString());
        assertTrue(k.pravihIgralcev() >= 0.95 * k.igralcev(), k.toString());
    }

    @Test
    void obeOblikiGrestaNaListInSamodejnaIzbira() {
        // vrstice: do 6 igralcev + prazna vrstica na ekipo pri 5 nizih
        assertTrue(GeometrijaZapisnika.greVVrstice(7, 7, 5));
        assertFalse(GeometrijaZapisnika.greVVrstice(8, 8, 5));
        assertEquals(GeometrijaZapisnika.Oblika.VRSTICE, GeometrijaZapisnika.samodejna(4, 5, 5, 5));
        assertEquals(GeometrijaZapisnika.Oblika.KADER, GeometrijaZapisnika.samodejna(9, 6, 5, 5));
        for (GeometrijaZapisnika.Oblika o : GeometrijaZapisnika.Oblika.values()) {
            GeometrijaZapisnika.PostavitevZapisnika p = GeometrijaZapisnika.izracunaj(GeometrijaZapisnika.RAZLICICA,
                    o, tekme(FormatSrecanja.SAVINJA), 5, 7, 7);
            for (GeometrijaZapisnika.Kvadratek k : p.kvadratki()) {
                assertTrue(znotraj(k.p()), "kvadratek zunaj lista " + k);
            }
            for (GeometrijaZapisnika.Celica c : p.celice()) {
                assertTrue(znotraj(c.p()), "celica zunaj lista " + c);
            }
            assertEquals(5 * 7 * 2, p.kvadratki().size());
            assertEquals(5 * 5 * 2, p.celice().size());
        }
    }

    private static boolean znotraj(GeometrijaZapisnika.Pravokotnik p) {
        return p.x() >= 18 && p.y() >= 26 && p.x() + p.sirina() <= 279 && p.y() + p.visina() <= 190;
    }
}
