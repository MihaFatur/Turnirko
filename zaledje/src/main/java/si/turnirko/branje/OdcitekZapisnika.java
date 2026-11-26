/* Odcitek poravnanega ligaskega zapisnika (GeometrijaZapisnika): stanje vsakega
   kvadratka za krizec in razrez vsake celice s tockami. Pravil (koliko krizcev
   sodi k tekmi, kdo je zmagal niz) tu ni - to je PredlogZapisnika in RazlagaListka.

   Postopek:
   1. vsak pas (vrsta) ima na koncih mali polni kvadrat; poisce se blizu
      pricakovane lege in pove, za koliko je vrsta na poravnani sliki zamaknjena
      (list A4 ni raven: prepognjen, dvignjen rob). Zamik vmesnih elementov se
      linearno razdeli med obe oznaki;
   2. rob kvadratka in celice se poisce po crti blizu popravljene lege
      (CeliceVadnice.okence, isto kot okenca vadnice in miza na listku);
   3. kvadratek: delez crnila v notranjosti (brez crte in 0,5 mm ob njej, kamor
      seze rob krizca iz sosednjega kvadratka); celica: RazrezCelice. */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.List;

public final class OdcitekZapisnika {

    private OdcitekZapisnika() {}

    /* Kaj je v kvadratku: prazno, krizec (kljukica, poteza), pocrnjen (lastnik:
       »napacen krizec: kvadratek zapolni do crnega« - preklic) ali nejasno. */
    public enum StanjeKvadratka { PRAZEN, KRIZEC, POCRNJEN, NEJASNO }

    /* Meje deleza crnila v notranjosti kvadratka (umerjeno s simulacijo,
       SimulacijaZapisnikaTest): krizec z dvema potezama kemicnega svincnika je
       15-40 %, kljukica 8-20 %, zapolnjen kvadratek nad 75 %, prazen ~0-2 %
       (sum, zrnat papir). Vmes je nejasno: odloci pravilo stevila krizcev ali
       organizator. */
    static final double PRAZEN_DO = 0.035;
    static final double KRIZEC_OD = 0.06;
    static final double KRIZEC_DO = 0.55;
    static final double POCRNJEN_OD = 0.70;
    /* Od roba notranjosti navznoter (px pri 10 px/mm), ki se ne steje. */
    static final int ROB_KVADRATKA = 5;
    /* Iskanje oznake vrste okrog pricakovane lege (mm). */
    static final double ISKANJE_OZNAKE_VRSTE = 4;

    public record Kvadratek(GeometrijaZapisnika.Kvadratek mesto, double crnilo, StanjeKvadratka stanje,
                            CeliceListka.Celica celica) {}

    public record Celica(GeometrijaZapisnika.Celica mesto, RazrezCelice.Vsebina vsebina, CeliceListka.Celica celica) {}

    /* najdenihOznak: oznake vrst, ki so bile najdene (od 2 * pasov). */
    public record Odcitek(List<Kvadratek> kvadratki, List<Celica> celice, int najdenihOznak, int vsehOznak) {}

    public static Odcitek odcitaj(Temnost list, GeometrijaZapisnika.PostavitevZapisnika p) {
        boolean[] crnilo = CeliceVadnice.crnilo(list);
        int r = GeometrijaListka.PX_NA_MM;
        double velikostOznake = p.stranicaOznakeVrste() * r;
        int najdenih = 0;
        List<double[][]> zamiki = new ArrayList<>();
        for (GeometrijaZapisnika.Pas pas : p.pasovi()) {
            double[] levo = oznakaVrste(list, crnilo, pas.levo(), velikostOznake);
            double[] desno = oznakaVrste(list, crnilo, pas.desno(), velikostOznake);
            najdenih += (levo != null ? 1 : 0) + (desno != null ? 1 : 0);
            if (levo == null) {
                levo = desno;
            }
            if (desno == null) {
                desno = levo;
            }
            if (levo == null) {
                levo = new double[] {0, 0};
                desno = new double[] {0, 0};
            }
            zamiki.add(new double[][] {levo, desno});
        }

        List<Kvadratek> kvadratki = new ArrayList<>();
        for (GeometrijaZapisnika.Kvadratek k : p.kvadratki()) {
            double[] mm = premakni(k.p(), p.pasovi().get(k.pas()), zamiki.get(k.pas()));
            CeliceListka.Celica c = CeliceVadnice.okence(list, crnilo, mm).celica();
            double d = delezCrnila(crnilo, list.sirina, list.visina, c);
            kvadratki.add(new Kvadratek(k, d, stanje(d), c));
        }
        List<Celica> celice = new ArrayList<>();
        for (GeometrijaZapisnika.Celica c : p.celice()) {
            double[] mm = premakni(c.p(), p.pasovi().get(c.pas()), zamiki.get(c.pas()));
            CeliceListka.Celica notranjost = CeliceVadnice.okence(list, crnilo, mm).celica();
            celice.add(new Celica(c, RazrezCelice.razrezi(list, notranjost), notranjost));
        }
        return new Odcitek(kvadratki, celice, najdenih, 2 * p.pasovi().size());
    }

    /* Izboljsana poravnava A4 (SimulacijaZapisnikaTest: pri zahtevnih fotografijah je
       bila poravnava iz treh oznak in majhne QR na drugem koncu lista zamaknjena za vec
       mm, oznake vrst niso bile najdene). Oznake vrst so desetine tock na znanih mestih
       po vsem listu: najdene (v oknu ISKANJE_OZNAKE_VRSTE okrog lege po trenutni
       poravnavi) skupaj z oznakami v vogalih dolocijo novo homografijo po najmanjsih
       kvadratih, tocke z ostankom nad NAJVEC_OSTANEK se izlocijo (sosednja vrsta pri
       velikem zamiku). Dva kroga: v drugem je zamik manjsi in najdenih oznak vec.
       List se vsakic preslika znova iz FOTOGRAFIJE (brez dvojnega vzorcenja). Vrne
       izboljsan list ali prvotnega, ce oznak ni dovolj. */
    public static Temnost izboljsaj(PoravnavaListka.PoravnavaSPreslikavo prva,
                                    GeometrijaZapisnika.PostavitevZapisnika p, Postavitev postavitev) {
        Temnost list = prva.slika();
        Preslikava h = prva.preslikava();
        int r = GeometrijaListka.PX_NA_MM;
        double velikost = p.stranicaOznakeVrste() * r;
        for (int krog = 0; krog < 2; krog++) {
            boolean[] crnilo = CeliceVadnice.crnilo(list);
            List<double[]> mm = new ArrayList<>();
            List<double[]> slika = new ArrayList<>();
            for (double[] o : p.oznake()) {
                mm.add(o);
                slika.add(h.uporabi(o[0], o[1]));
            }
            int vogalov = mm.size();
            for (GeometrijaZapisnika.Pas pas : p.pasovi()) {
                for (double[] o : new double[][] {pas.levo(), pas.desno()}) {
                    double[] zamik = oznakaVrste(list, crnilo, o, velikost);
                    if (zamik != null) {
                        mm.add(o);
                        slika.add(h.uporabi(o[0] + zamik[0] / r, o[1] + zamik[1] / r));
                    }
                }
            }
            if (mm.size() - vogalov < 6) {
                return list;
            }
            Preslikava nova = prileganje(mm, slika, h);
            if (nova == null) {
                return list;
            }
            h = nova;
            list = PoravnavaListka.prerisi(prva.fotografija(), h, postavitev);
        }
        return list;
    }

    /* Najvecji ostanek tocke (mm) po prileganju; vecji je napacno najdena oznaka. */
    static final double NAJVEC_OSTANEK = 1.5;

    /* Homografija po najmanjsih kvadratih z enkratnim izlocanjem tock z velikim
       ostankom; null, ce prileganje ni zanesljivo. */
    private static Preslikava prileganje(List<double[]> mm, List<double[]> slika, Preslikava stara) {
        double pxNaMm = Math.hypot(stara.uporabi(10, 0)[0] - stara.uporabi(0, 0)[0],
                stara.uporabi(10, 0)[1] - stara.uporabi(0, 0)[1]) / 10;
        List<double[]> iz = new ArrayList<>(mm);
        List<double[]> v = new ArrayList<>(slika);
        for (int krog = 0; krog < 2; krog++) {
            Preslikava h = Preslikava.homografijaNajmanjsihKvadratov(iz.toArray(new double[0][]), v.toArray(new double[0][]));
            if (h == null) {
                return null;
            }
            List<double[]> ostaliIz = new ArrayList<>();
            List<double[]> ostaliV = new ArrayList<>();
            for (int i = 0; i < iz.size(); i++) {
                double[] q = h.uporabi(iz.get(i)[0], iz.get(i)[1]);
                if (Math.hypot(q[0] - v.get(i)[0], q[1] - v.get(i)[1]) <= NAJVEC_OSTANEK * pxNaMm) {
                    ostaliIz.add(iz.get(i));
                    ostaliV.add(v.get(i));
                }
            }
            if (ostaliIz.size() == iz.size()) {
                return h;
            }
            if (ostaliIz.size() < 8) {
                return null;
            }
            iz = ostaliIz;
            v = ostaliV;
        }
        return Preslikava.homografijaNajmanjsihKvadratov(iz.toArray(new double[0][]), v.toArray(new double[0][]));
    }

    static StanjeKvadratka stanje(double delez) {
        if (delez <= PRAZEN_DO) {
            return StanjeKvadratka.PRAZEN;
        }
        if (delez >= POCRNJEN_OD) {
            return StanjeKvadratka.POCRNJEN;
        }
        if (delez >= KRIZEC_OD && delez <= KRIZEC_DO) {
            return StanjeKvadratka.KRIZEC;
        }
        return StanjeKvadratka.NEJASNO;
    }

    /* Robovi elementa v mm, premaknjeni za zamik vrste (linearno med oznakama). */
    private static double[] premakni(GeometrijaZapisnika.Pravokotnik p, GeometrijaZapisnika.Pas pas, double[][] zamik) {
        double r = GeometrijaListka.PX_NA_MM;
        double razpon = pas.desno()[0] - pas.levo()[0];
        double t = razpon <= 0 ? 0 : Math.clamp((p.sredinaX() - pas.levo()[0]) / razpon, 0.0, 1.0);
        double dx = (zamik[0][0] + t * (zamik[1][0] - zamik[0][0])) / r;
        double dy = (zamik[0][1] + t * (zamik[1][1] - zamik[0][1])) / r;
        double[] b = p.robovi();
        return new double[] {b[0] + dx, b[1] + dy, b[2] + dx, b[3] + dy};
    }

    /* Zamik (px) najdene oznake vrste od pricakovane lege; null, ce je ni. Oznaka
       je polna komponenta priblizno pricakovane velikosti, najblizja pricakovani legi. */
    static double[] oznakaVrste(Temnost list, boolean[] crnilo, double[] mm, double velikost) {
        int r = GeometrijaListka.PX_NA_MM;
        double ex = mm[0] * r;
        double ey = mm[1] * r;
        int pol = (int) Math.round(ISKANJE_OZNAKE_VRSTE * r + velikost / 2);
        int x0 = Math.max(0, (int) ex - pol);
        int y0 = Math.max(0, (int) ey - pol);
        int x1 = Math.min(list.sirina - 1, (int) ex + pol);
        int y1 = Math.min(list.visina - 1, (int) ey + pol);
        if (x1 <= x0 || y1 <= y0) {
            return null;
        }
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        boolean[] izrez = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(crnilo, (y0 + y) * list.sirina + x0, izrez, y * w, w);
        }
        double pricakovana = velikost * velikost;
        double[] najboljsi = null;
        double najmanj = Double.MAX_VALUE;
        for (Temnost.Komponenta k : Temnost.komponente(izrez, w, h, false)) {
            double razmerje = k.ploscina / pricakovana;
            int sk = k.sirina();
            int vk = k.visina();
            if (razmerje < 0.45 || razmerje > 1.9 || sk < 0.6 * velikost || vk < 0.6 * velikost
                    || sk > 1.6 * velikost || vk > 1.6 * velikost
                    || k.ploscina < 0.7 * sk * vk) {
                continue;
            }
            double cx = x0 + k.cx();
            double cy = y0 + k.cy();
            double d = Math.hypot(cx - ex, cy - ey);
            if (d < najmanj) {
                najmanj = d;
                najboljsi = new double[] {cx - ex, cy - ey};
            }
        }
        return najboljsi;
    }

    /* Delez crnila v notranjosti kvadratka brez roba ROB_KVADRATKA. Celica
       (CeliceVadnice) je notranjost, razsirjena za ODMIK - 1 v crto. */
    static double delezCrnila(boolean[] crnilo, int w, int h, CeliceListka.Celica c) {
        int rob = RazrezCelice.ODMIK - 1 + ROB_KVADRATKA;
        int x0 = Math.max(0, c.x0() + rob);
        int y0 = Math.max(0, c.y0() + rob);
        int x1 = Math.min(w - 1, c.x1() - rob);
        int y1 = Math.min(h - 1, c.y1() - rob);
        if (x1 <= x0 || y1 <= y0) {
            return 0;
        }
        int crnih = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (crnilo[y * w + x]) {
                    crnih++;
                }
            }
        }
        return (double) crnih / ((double) (x1 - x0 + 1) * (y1 - y0 + 1));
    }
}
