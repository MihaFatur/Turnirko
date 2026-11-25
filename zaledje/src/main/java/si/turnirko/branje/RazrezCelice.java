/* Rocni zapis v eni celici: prazno, ena stevka (»7«) ali dve (»11«, »13«).

   Celica je prosta (sodnik pise, kakor je navajen), zato razrez ni vedno
   enolicen: »11« sta lahko dve loceni crti ali ena sklenjena poteza, »10« je
   lahko ena zanka. Namesto ene odlocitve vrne vec RAZLAG z ocenjeno
   verjetnostjo (ena ali dve stevki); katera je prava, odlocita mreza stevk in
   pravila namiznega tenisa (RazlagaListka).

   Vsaka stevka gre v obliko, v kateri je bila naucena mreza (MNIST): crnilo,
   pomanjsano v okvir 20 x 20 z ohranjenim razmerjem in postavljeno v sliko
   28 x 28 tako, da je tezisce v sredini.

   Stevka cez crto ostane cela (F2.4, 24. 11. 2026): na pravih listkih z generalke
   so sodniki pogosto pisali cez rob nizke celice, razrez pa je vzel samo
   notranjost - sedmici je odrezal streho, devetki vrh. Zdaj se pregleda tudi
   OKOLICA_MM okrog celice; crta celice se izbrise povsod, razen kjer jo poteza
   precka (crnilo tik nad in pod njo), dolge crte sosednjih celic in podcrtaj imen
   prav tako, obdrzijo pa se samo deli, ki so vecinoma v celici (sosednja stevka,
   ki sega k nam, ostane sosedu). */
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
       zdruzevanjem, razpon = delez sirine celice od prve do zadnje poteze,
       najvecSkupin = koliko skupin ima se navadna celica te oblike. */
    public record Vsebina(boolean prazna, List<Razlaga> razlage, double crnilo, int skupin, double razpon,
                          int najvecSkupin) {

        public Vsebina(boolean prazna, List<Razlaga> razlage, double crnilo, int skupin, double razpon) {
            this(prazna, razlage, crnilo, skupin, razpon, NAJVEC_SKUPIN_SIROKE);
        }

        /* Popravljena celica (F2.5): sodnik je vrednost precrtal ali prepisal in
           napisal novo. Pozna se po nenavadno veliko crnila, po vec skupinah potez,
           kot jih ima navadna vrednost, ali po zapisu, ki se razteza cez vecino
           celice (ena vrednost ima najvec dve stevki). Taka celica je vedno
           negotova, njene stevke pa ne gredo v ucenje. Crnilo in razpon sta merjena
           glede na primerjalno sirino celice (primerjalnaSirina), zato meji veljata
           za siroke in za ozke celice. */
        public boolean popravljena() {
            return !prazna && (crnilo > NAJVEC_CRNILA || skupin > najvecSkupin || razpon > NAJVEC_RAZPONA);
        }
    }

    /* Navadna vrednost ima najvec dve skupini potez (dve stevki). V siroki celici
       je ob njej se prostor za odtrgano potezo (tri), v ozki (17 mm) ga ni: tretja
       skupina je tam skoraj vedno precrtana vrednost ob novi (PopravljenaCelicaPoskus,
       24. 11. 2026: navadne celice nikoli vec kot dve). */
    static final int NAJVEC_SKUPIN_SIROKE = 3;
    static final int NAJVEC_SKUPIN_OZKE = 2;

    /* Meji popravljene celice sta umerjeni na sirokih nizkih celicah (35 x 6 mm,
       notranjost ~6,9-krat sirsa od visine). Ozka visoka celica (17 x 9 mm) ima
       isto vrednost napisano cez vecji del sirine, zato se crnilo in razpon merita
       glede na sirino RAZMERJE_SIROKE_CELICE x visina (velikost pisave sledi visini
       celice). Pri sirokih celicah je vecja njihova sirina (meji ostaneta natanko
       kot prej). 6,0 je umerjeno na ozkih celicah (PopravljenaCelicaPoskus, 24. 11.
       2026): pri 5,2 je bilo 3 % navadnih celic po krivem popravljenih (cetrtina
       listkov bi sla v pregled brez razloga), pri 6,0 0,6 % pri obicajnih
       fotografijah. Precrtano vrednost v ozki celici zato pogosteje ujame tretja
       skupina potez ali neujemanje s pravili kot crnilo. */
    static final double RAZMERJE_SIROKE_CELICE = 6.0;

    /* Ozka celica: sirina notranjosti manj kot 4-kratna visina (nova 17 x 9 mm). */
    static boolean ozka(int sirina, int visina) {
        return sirina < 4 * visina;
    }

    static double primerjalnaSirina(int sirina, int visina) {
        return Math.max(sirina, RAZMERJE_SIROKE_CELICE * visina);
    }

    /* Koliko okrog celice se iscejo poteze, ki sezejo cez crto (mm). Na listku je
       razmik med vrsticami nizov 1 mm, oznaka niza in dvopicje sta vsaj 2 mm od
       celice; poteza sosednje celice se izloci po tem, da je vecinoma zunaj nase. */
    static final double OKOLICA_MM = 1.5;
    /* Pas crte od jedra navzven (px pri 10 px/mm): odmik ODMIK, crta 0,3-0,45 mm,
       zameglitev in toleranca iskanja robov (CeliceListka, CeliceVadnice). */
    static final int PAS_CRTE = 12;
    /* Poteza precka crto, ce je crnilo na obeh straneh pasu najvec toliko px
       vstran (posevna poteza cez 1 mm pasu). */
    static final int PRECKANJE_VSTRAN = 3;
    /* Del je stevka te celice, ce ima v jedru vsaj toliko svojega crnila. */
    static final double DELEZ_V_CELICI = 0.5;

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

    /* Obmocje razreza: notranjost celice z okolico, v px slike (x0, y0 levo zgoraj). */
    record Obmocje(int x0, int y0, int sirina, int visina) {}

    static Obmocje obmocje(Temnost listek, CeliceListka.Celica c) {
        int e = (int) Math.round(OKOLICA_MM * GeometrijaListka.PX_NA_MM);
        int x0 = Math.max(0, c.x0() - e);
        int y0 = Math.max(0, c.y0() - e);
        int x1 = Math.min(listek.sirina - 1, c.x1() + e);
        int y1 = Math.min(listek.visina - 1, c.y1() + e);
        return new Obmocje(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /* Deli zapisa celice: komponente crnila v obmocju (brez crt), ki so vecinoma v
       notranjosti. null, ce je celica premajhna ali prazna (premalo kontrasta). */
    private record Zapis(Obmocje o, List<Temnost.Komponenta> deli) {}

    private static Zapis zapis(Temnost listek, CeliceListka.Celica c) {
        float[] d = notranjost(listek, c);
        if (d == null) {
            return null;
        }
        int w = c.sirina() - 2 * ODMIK;
        int h = c.visina() - 2 * ODMIK;
        float[] urejeno = d.clone();
        Arrays.sort(urejeno);
        float papir = urejeno[(int) (urejeno.length * PERCENTIL_PAPIRJA)];
        float crnilo = urejeno[Math.min(urejeno.length - 1, (int) (urejeno.length * 0.995))];
        if (crnilo - papir < 0.18f) {
            return null;
        }
        float prag = Math.max(papir + 0.12f, papir + 0.5f * (crnilo - papir));
        Obmocje o = obmocje(listek, c);
        boolean[] maska = new boolean[o.sirina() * o.visina()];
        for (int y = 0; y < o.visina(); y++) {
            for (int x = 0; x < o.sirina(); x++) {
                maska[y * o.sirina() + x] = listek.vrni(o.x0() + x, o.y0() + y) > prag;
            }
        }
        // jedro: notranjost celice brez odmika ob crti (kar je razrez bral prej), v
        // koordinatah obmocja. Okence vadnice in mize (CeliceVadnice) sega v crto za
        // ODMIK - 1 px, celica listka se zacne za crto - jedro je v obeh brez crte.
        int cx0 = c.x0() - o.x0() + ODMIK;
        int cy0 = c.y0() - o.y0() + ODMIK;
        int cx1 = c.x1() - o.x0() - ODMIK;
        int cy1 = c.y1() - o.y0() - ODMIK;
        izbrisiCrte(maska, o.sirina(), o.visina(), cx0, cy0, cx1, cy1, w, h);

        List<Temnost.Komponenta> deli = new ArrayList<>();
        for (Temnost.Komponenta k : Temnost.komponente(maska, o.sirina(), o.visina(), true)) {
            boolean ostanekVodoravneCrte = k.visina() <= 4 && k.sirina() > 0.4 * w;
            boolean ostanekNavpicneCrte = k.sirina() <= 4 && k.visina() > 0.6 * h
                    && (k.minX <= cx0 + 1 || k.maxX >= cx1 - 1);
            if (k.ploscina < 10 || ostanekVodoravneCrte || ostanekNavpicneCrte
                    || vNotranjosti(k, o.sirina(), cx0, cy0, cx1, cy1) < DELEZ_V_CELICI * k.ploscina) {
                continue;
            }
            deli.add(k);
        }
        return new Zapis(o, deli);
    }

    private static int vNotranjosti(Temnost.Komponenta k, int sirina, int cx0, int cy0, int cx1, int cy1) {
        int n = 0;
        for (int i : k.tocke) {
            int x = i % sirina;
            int y = i / sirina;
            if (x >= cx0 && x <= cx1 && y >= cy0 && y <= cy1) {
                n++;
            }
        }
        return n;
    }

    /* Izbrise crte okrog celice. V pasu PAS_CRTE ob notranjosti (crte te celice)
       ostane samo crnilo, ki crto precka - ima crnilo tik pred pasom in tik za njim
       (stevka, napisana cez rob, tako ostane povezana). Zunaj pasu se izbrisejo
       dolgi vodoravni in navpicni nizi crnila (crte sosednjih celic, podcrtaj imen). */
    static void izbrisiCrte(boolean[] m, int sirina, int visina, int cx0, int cy0, int cx1, int cy1,
                            int w, int h) {
        int b = PAS_CRTE;
        // vodoravni crti (zgoraj, spodaj): po stolpcih
        pasVodoravno(m, sirina, visina, cy0 - b, cy0 - 1, cx0 - b, cx1 + b);
        pasVodoravno(m, sirina, visina, cy1 + 1, cy1 + b, cx0 - b, cx1 + b);
        // navpicni crti (levo, desno): po vrsticah
        pasNavpicno(m, sirina, visina, cx0 - b, cx0 - 1, cy0 - b, cy1 + b);
        pasNavpicno(m, sirina, visina, cx1 + 1, cx1 + b, cy0 - b, cy1 + b);
        // dolge crte zunaj notranjosti in pasu
        int najmanjVodoravno = (int) Math.round(0.4 * w);
        int najmanjNavpicno = (int) Math.round(0.6 * h);
        for (int y = 0; y < visina; y++) {
            if (y >= cy0 - b && y <= cy1 + b) {
                continue;
            }
            izbrisiDolgeNize(m, y * sirina, 1, sirina, najmanjVodoravno);
        }
        for (int x = 0; x < sirina; x++) {
            if (x >= cx0 - b && x <= cx1 + b) {
                continue;
            }
            izbrisiDolgeNize(m, x, sirina, visina, najmanjNavpicno);
        }
    }

    /* Pas vrstic ya..yb (vodoravna crta) med stolpcema xa..xb. */
    private static void pasVodoravno(boolean[] m, int sirina, int visina, int ya, int yb, int xa, int xb) {
        ya = Math.max(0, ya);
        yb = Math.min(visina - 1, yb);
        xa = Math.max(0, xa);
        xb = Math.min(sirina - 1, xb);
        if (ya > yb) {
            return;
        }
        boolean[] precka = new boolean[sirina];
        for (int x = xa; x <= xb; x++) {
            precka[x] = crnilo(m, sirina, visina, x, ya - 2, ya - 1, true)
                    && crnilo(m, sirina, visina, x, yb + 1, yb + 2, true);
        }
        for (int x = xa; x <= xb; x++) {
            if (!precka[x]) {
                for (int y = ya; y <= yb; y++) {
                    m[y * sirina + x] = false;
                }
            }
        }
    }

    /* Pas stolpcev xa..xb (navpicna crta) med vrsticama ya..yb. */
    private static void pasNavpicno(boolean[] m, int sirina, int visina, int xa, int xb, int ya, int yb) {
        xa = Math.max(0, xa);
        xb = Math.min(sirina - 1, xb);
        ya = Math.max(0, ya);
        yb = Math.min(visina - 1, yb);
        if (xa > xb) {
            return;
        }
        boolean[] precka = new boolean[visina];
        for (int y = ya; y <= yb; y++) {
            precka[y] = crnilo(m, sirina, visina, y, xa - 2, xa - 1, false)
                    && crnilo(m, sirina, visina, y, xb + 1, xb + 2, false);
        }
        for (int y = ya; y <= yb; y++) {
            if (!precka[y]) {
                for (int x = xa; x <= xb; x++) {
                    m[y * sirina + x] = false;
                }
            }
        }
    }

    /* Ali je crnilo ob pasu: v vrsticah (ali stolpcih) od..do, na mestu p +- PRECKANJE_VSTRAN. */
    private static boolean crnilo(boolean[] m, int sirina, int visina, int p, int od, int doo, boolean vodoravno) {
        for (int q = od; q <= doo; q++) {
            for (int dp = -PRECKANJE_VSTRAN; dp <= PRECKANJE_VSTRAN; dp++) {
                int x = vodoravno ? p + dp : q;
                int y = vodoravno ? q : p + dp;
                if (x >= 0 && x < sirina && y >= 0 && y < visina && m[y * sirina + x]) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Izbrise nize crnila, dolge vsaj »najmanj«, v vrstici ali stolpcu (zacetek, korak, dolzina). */
    private static void izbrisiDolgeNize(boolean[] m, int zacetek, int korak, int dolzina, int najmanj) {
        int i = 0;
        while (i < dolzina) {
            if (!m[zacetek + i * korak]) {
                i++;
                continue;
            }
            int j = i;
            while (j < dolzina && m[zacetek + j * korak]) {
                j++;
            }
            if (j - i >= najmanj) {
                for (int q = i; q < j; q++) {
                    m[zacetek + q * korak] = false;
                }
            }
            i = j;
        }
    }

    /* Kako dalec zunaj celice (px) zlati nabor se hrani poteze zapisa: do konca pasu
       crte (PAS_CRTE od jedra, jedro je ODMIK v celici). Ime nad tabelo je vec kot 2 mm
       nad prvo celico, sodnikovo rocno ime pod Rezultatom pa lahko seze blizje - poteza
       stevke, ki se ga dotakne, bi sicer v trajni nabor prinesla delcek crke (varnostni
       pregled V4, 24. 11. 2026). */
    static final int ZUNAJ_ZA_NABOR = PAS_CRTE - ODMIK;

    /* Tocke zapisa celice v koordinatah slike (y * sirina + x): kar razrez vzame za
       stevke, tudi deli cez crto, a najvec ZUNAJ_ZA_NABOR px zunaj celice. Zlati nabor
       jih shrani (ZlatiNabor.slikaCelic), da meritev nad naborom vidi skoraj isto kot
       bralnik (stevka, ki sega dalec cez crto, je v naboru prirezana). */
    public static List<Integer> tockeZapisa(Temnost listek, CeliceListka.Celica c) {
        Zapis z = zapis(listek, c);
        List<Integer> tocke = new ArrayList<>();
        if (z == null) {
            return tocke;
        }
        Obmocje o = z.o();
        int e = ZUNAJ_ZA_NABOR;
        for (Temnost.Komponenta k : z.deli()) {
            for (int i : k.tocke) {
                int x = o.x0() + i % o.sirina();
                int y = o.y0() + i / o.sirina();
                if (x >= c.x0() - e && x <= c.x1() + e && y >= c.y0() - e && y <= c.y1() + e) {
                    tocke.add(y * listek.sirina + x);
                }
            }
        }
        return tocke;
    }

    /* Celica za tocke na listku: meje popravljene celice upostevajo obliko (ozka
       visoka celica novega listka). */
    public static Vsebina razrezi(Temnost listek, CeliceListka.Celica c) {
        return razrezi(listek, c, true);
    }

    /* Okence (miza na listku, okence vadnice): meje popravljene celice ostanejo kot
       pred novim listkom (sirina okenca, najvec tri skupine) - okence mize ni bilo
       spremenjeno, pregled kode 24. 11. 2026. */
    public static Vsebina razreziOkence(Temnost listek, CeliceListka.Celica c) {
        return razrezi(listek, c, false);
    }

    private static Vsebina razrezi(Temnost listek, CeliceListka.Celica c, boolean poObliki) {
        Zapis z = zapis(listek, c);
        if (z == null) {
            return PRAZNA;
        }
        int w = c.sirina() - 2 * ODMIK;
        int h = c.visina() - 2 * ODMIK;
        int ws = z.o().sirina();
        List<Temnost.Komponenta> deli = new ArrayList<>(z.deli());
        int skupaj = 0;
        for (Temnost.Komponenta k : deli) {
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
        double primerjalna = poObliki ? primerjalnaSirina(w, h) : w;
        double delezCrnila = skupaj / (primerjalna * h);
        double razpon = (desno - levo + 1) / primerjalna;
        while (skupine.size() > 2) {
            zdruziNajblizji(skupine);
        }
        List<Razlaga> razlage = new ArrayList<>();
        if (skupine.size() == 2) {
            razlage.add(new Razlaga(List.of(stevka(skupine.get(0), ws), stevka(skupine.get(1), ws)),
                    deli.size() > 2 ? 0.8 : 0.9));
            razlage.add(new Razlaga(List.of(stevka(vse(skupine), ws)), deli.size() > 2 ? 0.2 : 0.1));
        } else {
            List<Temnost.Komponenta> g = skupine.getFirst();
            int[] okvir = okvir(g);
            double razmerje = (double) (okvir[2] - okvir[0] + 1) / (okvir[3] - okvir[1] + 1);
            double ena = razmerje < 0.75 ? 0.95 : razmerje < 1.0 ? 0.75 : razmerje < 1.3 ? 0.45 : 0.2;
            razlage.add(new Razlaga(List.of(stevka(g, ws)), ena));
            // sklenjen zapis dveh stevk: vec moznih rezov (F2.3), vsak svoja razlaga;
            // katerega mreza prebere najbolj zanesljivo, odloci LokalniBralnikListka
            List<Rez> rezi = razdelitve(g, ws, okvir);
            double tez = rezi.stream().mapToDouble(Rez::tez).sum();
            for (Rez rez : rezi) {
                razlage.add(new Razlaga(rez.stevki(), (1 - ena) * rez.tez() / tez));
            }
        }
        return new Vsebina(false, normaliziraj(razlage), delezCrnila, skupin, razpon,
                poObliki && ozka(w, h) ? NAJVEC_SKUPIN_OZKE : NAJVEC_SKUPIN_SIROKE);
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

    /* Najvec rezov sklenjenega zapisa (F2.3; meja racunanja, V3). */
    static final int NAJVEC_REZOV = 3;

    /* Rez sklenjenega zapisa: stevki levo in desno od reza in tez (vecja, ce rez
       gre cez manj crnila). */
    private record Rez(List<Stevka> stevki, double tez) {}

    /* Sklenjen zapis dveh stevk (»11« v eni potezi, »10« z zanko, ki se dotika
       enice): razrezi na stolpcih z najmanj crnila v srednjem delu (30-70 %
       sirine). Najtanjsi stolpec ni vedno pravi (poteza enice se nagne v nicto),
       zato do NAJVEC_REZOV lokalnih minimumov, vsaj 12 % sirine narazen; tez
       1 / (1 + crnilo v stolpcu reza). Prazen seznam, ce bi bil kateri del prazen. */
    private static List<Rez> razdelitve(List<Temnost.Komponenta> g, int w, int[] okvir) {
        int sirina = okvir[2] - okvir[0] + 1;
        int[] stolpci = new int[sirina];
        for (Temnost.Komponenta k : g) {
            for (int i : k.tocke) {
                stolpci[i % w - okvir[0]]++;
            }
        }
        int od = (int) (sirina * 0.3);
        int doo = (int) (sirina * 0.7);
        List<Integer> kandidati = new ArrayList<>();
        for (int x = od; x <= doo; x++) {
            kandidati.add(x);
        }
        kandidati.sort(Comparator.comparingInt((Integer x) -> stolpci[x]).thenComparingInt(x -> x));
        int razmik = Math.max(2, (int) Math.round(0.12 * sirina));
        List<Integer> izbrani = new ArrayList<>();
        for (int x : kandidati) {
            if (izbrani.size() >= NAJVEC_REZOV) {
                break;
            }
            if (izbrani.stream().allMatch(y -> Math.abs(y - x) >= razmik)) {
                izbrani.add(x);
            }
        }
        List<Rez> rezi = new ArrayList<>();
        for (int x : izbrani) {
            int rez = x + okvir[0];
            List<Integer> levo = new ArrayList<>();
            List<Integer> desno = new ArrayList<>();
            for (Temnost.Komponenta k : g) {
                for (int i : k.tocke) {
                    (i % w < rez ? levo : desno).add(i);
                }
            }
            if (levo.size() >= 10 && desno.size() >= 10) {
                rezi.add(new Rez(List.of(vMnist(levo, w), vMnist(desno, w)), 1.0 / (1 + stolpci[x])));
            }
        }
        return rezi;
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
