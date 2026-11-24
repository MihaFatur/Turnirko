/* Iz verjetnosti stevk sestavi najverjetnejsi VELJAVEN izid celega listka.

   Mreza stevk vsako celico prebere sama zase in se zmoti (1 ali 7, 4 ali 9).
   Pravila namiznega tenisa pa celice povezejo:
   - niz je veljaven samo kot 11 proti najvec 9 ali, od 10:10 naprej, z razliko 2;
   - tekma se konca, ko eden doseze dovolj nizov (2, 3 ali 4) - po tem so vrstice
     prazne;
   - celica »Rezultat« (ce jo je sodnik izpolnil) mora biti stevilo dobljenih nizov.
   Med vsemi zaporedji nizov, ki ta pravila izpolnijo, je izbrano tisto z najvecjo
   skupno verjetnostjo (Viterbi po vrsticah). Ker so stanja majhna (dobljeni nizi
   obeh), je to natancno in hitro.

   Zaupanje celice je njena robna verjetnost cez VSA veljavna zaporedja
   (algoritem naprej-nazaj). Celica z nizkim zaupanjem, ali taka, kjer so pravila
   preglasila mrezo, je »negotova« in jo organizator pogleda.

   Pot brez zmagovalca (prazna vrstica, preden je tekma odlocena) je dovoljena z
   majhno verjetnostjo: tako se izrazi predaja ali brez boja, ki ju bralnik ne
   zna prebrati - predlog jo nato oznaci kot nepopolno tekmo. */
package si.turnirko.branje;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RazlagaListka {

    /* Indeks »prazne celice« v porazdelitvi (vrednosti 0-99 so indeksi 0-99). */
    public static final int PRAZNO = 100;
    public static final int VELIKOST = 101;

    /* Verjetnost, da se tekma na listku ne konca (predaja, brez boja, pozabljen niz). */
    private static final double NEPOPOLNO = 0.02;
    /* Celica je negotova pod tem zaupanjem. */
    private static final double ZAUPANJE = 0.8;

    private RazlagaListka() {}

    public record Vrstica(double[] levo, double[] desno) {}

    /* zaupanjeLevo/Desno = robna verjetnost izbrane vrednosti celice cez vsa veljavna
       zaporedja (0-1); diagnostika branja (DiagnozaBranja) iz nje meri umerjenost. */
    public record Niz(Integer levo, Integer desno, boolean negotovoLevo, boolean negotovoDesno,
                      double zaupanjeLevo, double zaupanjeDesno) {

        public Niz(Integer levo, Integer desno, boolean negotovoLevo, boolean negotovoDesno) {
            this(levo, desno, negotovoLevo, negotovoDesno, 0, 0);
        }
    }

    public record Izid(List<Niz> nizi, Integer rezultatLevo, Integer rezultatDesno,
                       boolean negotovRezultat, String opomba) {}

    /* Izbira vrstice: prazna ali veljaven niz (levo, desno). */
    private record Moznost(int levo, int desno) {
        boolean prazna() {
            return levo == PRAZNO;
        }

        boolean levaZmaga() {
            return levo > desno;
        }
    }

    private static final List<Moznost> MOZNOSTI = moznosti();

    private static List<Moznost> moznosti() {
        List<Moznost> m = new ArrayList<>();
        m.add(new Moznost(PRAZNO, PRAZNO));
        for (int a = 0; a <= 9; a++) {
            m.add(new Moznost(11, a));
            m.add(new Moznost(a, 11));
        }
        for (int k = 12; k <= PogostostNizov.NAJVEC_PODALJSEK; k++) {
            m.add(new Moznost(k, k - 2));
            m.add(new Moznost(k - 2, k));
        }
        return List.copyOf(m);
    }

    /* Utez vsake moznosti po pogostosti v zgodovini (prazna vrstica 1; njeno
       verjetnost doloca stanje tekme). */
    private static double[] utezi(PogostostNizov pogostost, double beta) {
        double[] u = new double[MOZNOSTI.size()];
        for (int m = 0; m < u.length; m++) {
            Moznost mo = MOZNOSTI.get(m);
            u[m] = mo.prazna() ? 1 : pogostost.utez(Math.max(mo.levo(), mo.desno()), Math.min(mo.levo(), mo.desno()), beta);
        }
        return u;
    }

    /* Stanje: tekma tece (0), koncana (1) ali nepopolna (2), z dobljenimi nizi. */
    private record Stanje(int vrsta, int prvi, int drugi) {}

    /* Z vgrajeno pogostostjo nizov in privzeto beto (testi, meritve brez baze). */
    public static Izid razlozi(List<Vrstica> vrstice, double[] rezultatLevo, double[] rezultatDesno) {
        return razlozi(vrstice, rezultatLevo, rezultatDesno, PogostostNizov.VGRAJENA, PogostostNizov.PRIVZETA_BETA);
    }

    /* Najverjetnejsi veljaven izid; pogostost nizov iz zgodovine vstopi z beto
       (0 = brez zgodovine). Celica je negotova tudi, ce bi brez zgodovine izbrali
       drugo vrednost: tam je odlocila zgodovina, ne pisava, zato jo organizator
       pogleda. */
    public static Izid razlozi(List<Vrstica> vrstice, double[] rezultatLevo, double[] rezultatDesno,
                               PogostostNizov pogostost, double beta) {
        int n = vrstice.size();
        int zaZmago = n / 2 + 1;

        List<Stanje> stanja = new ArrayList<>();
        Map<Stanje, Integer> indeks = new HashMap<>();
        for (int vrsta = 0; vrsta < 3; vrsta++) {
            for (int a = 0; a <= zaZmago; a++) {
                for (int b = 0; b <= zaZmago; b++) {
                    boolean konec = a == zaZmago || b == zaZmago;
                    if (a == zaZmago && b == zaZmago) {
                        continue;
                    }
                    if ((vrsta == 1) != konec) {
                        continue;
                    }
                    Stanje s = new Stanje(vrsta, a, b);
                    indeks.put(s, stanja.size());
                    stanja.add(s);
                }
            }
        }
        int S = stanja.size();
        int M = MOZNOSTI.size();
        // prehod[s][m] = naslednje stanje ali -1; osnova = utez prehoda brez pogostosti nizov
        int[][] prehod = new int[S][M];
        double[][] osnova = new double[S][M];
        for (int s = 0; s < S; s++) {
            Stanje st = stanja.get(s);
            for (int m = 0; m < M; m++) {
                Moznost mo = MOZNOSTI.get(m);
                prehod[s][m] = -1;
                if (mo.prazna()) {
                    int cilj = st.vrsta() == 0 ? indeks.get(new Stanje(2, st.prvi(), st.drugi())) : s;
                    prehod[s][m] = cilj;
                    osnova[s][m] = st.vrsta() == 0 ? NEPOPOLNO : 1;
                } else if (st.vrsta() == 0) {
                    int a = st.prvi() + (mo.levaZmaga() ? 1 : 0);
                    int b = st.drugi() + (mo.levaZmaga() ? 0 : 1);
                    int vrsta = a == zaZmago || b == zaZmago ? 1 : 0;
                    prehod[s][m] = indeks.get(new Stanje(vrsta, a, b));
                    osnova[s][m] = 1;
                }
            }
        }
        double[][] utez = utezPrehodov(osnova, utezi(pogostost, beta));
        double[][] oddaja = new double[n][M];
        for (int i = 0; i < n; i++) {
            for (int m = 0; m < M; m++) {
                Moznost mo = MOZNOSTI.get(m);
                oddaja[i][m] = vrstice.get(i).levo()[mo.levo()] * vrstice.get(i).desno()[mo.desno()];
            }
        }
        double[] koncna = new double[S];
        for (int s = 0; s < S; s++) {
            Stanje st = stanja.get(s);
            double r = rezultat(rezultatLevo, st.prvi()) * rezultat(rezultatDesno, st.drugi());
            koncna[s] = st.vrsta() == 0 ? NEPOPOLNO * r : r;
        }
        int zacetek = indeks.get(new Stanje(0, 0, 0));

        double[] najvecOut = new double[1];
        int[] pot = viterbi(prehod, utez, oddaja, koncna, zacetek, najvecOut);
        if (pot == null) {
            return neodvisno(vrstice, rezultatLevo, rezultatDesno);
        }
        // ista razlaga brez zgodovine: kjer se razlikuje, je odlocila pogostost
        double[] najvecBrez = beta == 0 ? najvecOut : new double[1];
        int[] potBrezZgodovine = beta == 0 ? pot
                : viterbi(prehod, utezPrehodov(osnova, utezi(pogostost, 0)), oddaja, koncna, zacetek, najvecBrez);
        // koliko so morala pravila popraviti mrezo, se meri brez zgodovine (redek, a
        // pravilno prebran podaljsek ni neujemanje s pravili)
        double najvec = potBrezZgodovine == null ? najvecOut[0] : najvecBrez[0];

        // naprej-nazaj: robne verjetnosti celic cez vsa veljavna zaporedja
        double[][] naprej = new double[n + 1][S];
        naprej[0][zacetek] = 1;
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < S; a++) {
                if (naprej[i][a] == 0) {
                    continue;
                }
                for (int m = 0; m < M; m++) {
                    int c = prehod[a][m];
                    if (c >= 0) {
                        naprej[i + 1][c] += naprej[i][a] * utez[a][m] * oddaja[i][m];
                    }
                }
            }
        }
        double[][] nazaj = new double[n + 1][S];
        System.arraycopy(koncna, 0, nazaj[n], 0, S);
        for (int i = n - 1; i >= 0; i--) {
            for (int a = 0; a < S; a++) {
                double v = 0;
                for (int m = 0; m < M; m++) {
                    int c = prehod[a][m];
                    if (c >= 0) {
                        v += utez[a][m] * oddaja[i][m] * nazaj[i + 1][c];
                    }
                }
                nazaj[i][a] = v;
            }
        }
        double z = 0;
        for (int a = 0; a < S; a++) {
            z += naprej[n][a] * koncna[a];
        }
        List<Niz> nizi = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Moznost izbrana = MOZNOSTI.get(pot[i]);
            double robLevo = 0;
            double robDesno = 0;
            for (int a = 0; a < S; a++) {
                if (naprej[i][a] == 0) {
                    continue;
                }
                for (int m = 0; m < M; m++) {
                    int c = prehod[a][m];
                    if (c < 0) {
                        continue;
                    }
                    Moznost mo = MOZNOSTI.get(m);
                    double p = naprej[i][a] * utez[a][m] * oddaja[i][m] * nazaj[i + 1][c] / z;
                    if (mo.levo() == izbrana.levo()) {
                        robLevo += p;
                    }
                    if (mo.desno() == izbrana.desno()) {
                        robDesno += p;
                    }
                }
            }
            Vrstica v = vrstice.get(i);
            Moznost brez = potBrezZgodovine == null ? null : MOZNOSTI.get(potBrezZgodovine[i]);
            boolean negLevo = robLevo < ZAUPANJE || najverjetnejsa(v.levo()) != izbrana.levo()
                    || brez == null || brez.levo() != izbrana.levo();
            boolean negDesno = robDesno < ZAUPANJE || najverjetnejsa(v.desno()) != izbrana.desno()
                    || brez == null || brez.desno() != izbrana.desno();
            nizi.add(izbrana.prazna()
                    ? new Niz(null, null, negLevo, negDesno, robLevo, robDesno)
                    : new Niz(izbrana.levo(), izbrana.desno(), negLevo, negDesno, robLevo, robDesno));
        }
        Integer[] rez = rezultatCelic(rezultatLevo, rezultatDesno);
        // skupna verjetnost izbranega proti najboljsemu branju brez pravil: pove,
        // koliko so morala pravila popraviti mrezo
        double brezPravil = 0;
        for (Vrstica v : vrstice) {
            brezPravil += Math.log(najvecja(v.levo())) + Math.log(najvecja(v.desno()));
        }
        String opomba = najvec - brezPravil < Math.log(1e-4)
                ? "Številke na listku se slabo ujemajo s pravili. Preveri vse nize." : null;
        return new Izid(nizi, rez[0], rez[1], negotovRezultat(rezultatLevo, rezultatDesno), opomba);
    }

    /* utez[s][m] = osnova prehoda * utez moznosti m. */
    private static double[][] utezPrehodov(double[][] osnova, double[] uteziMoznosti) {
        double[][] u = new double[osnova.length][uteziMoznosti.length];
        for (int s = 0; s < osnova.length; s++) {
            for (int m = 0; m < uteziMoznosti.length; m++) {
                u[s][m] = osnova[s][m] * uteziMoznosti[m];
            }
        }
        return u;
    }

    /* Viterbi (najverjetnejse veljavno zaporedje) v logaritmih: indeksi moznosti
       po vrsticah ali null, ce veljavnega zaporedja ni; najvecOut[0] = log verjetnosti. */
    private static int[] viterbi(int[][] prehod, double[][] utez, double[][] oddaja, double[] koncna, int zacetek,
                                 double[] najvecOut) {
        int n = oddaja.length;
        int S = prehod.length;
        int M = MOZNOSTI.size();
        double[][] najbolje = new double[n + 1][S];
        int[][] odkod = new int[n + 1][S];
        int[][] izbira = new int[n + 1][S];
        for (double[] v : najbolje) {
            java.util.Arrays.fill(v, Double.NEGATIVE_INFINITY);
        }
        najbolje[0][zacetek] = 0;
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < S; s++) {
                if (najbolje[i][s] == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                for (int m = 0; m < M; m++) {
                    int c = prehod[s][m];
                    double w = utez[s][m] * oddaja[i][m];
                    if (c < 0 || w <= 0) {
                        continue;
                    }
                    double v = najbolje[i][s] + Math.log(w);
                    if (v > najbolje[i + 1][c]) {
                        najbolje[i + 1][c] = v;
                        odkod[i + 1][c] = s;
                        izbira[i + 1][c] = m;
                    }
                }
            }
        }
        int konec = -1;
        double najvec = Double.NEGATIVE_INFINITY;
        for (int s = 0; s < S; s++) {
            if (koncna[s] > 0 && najbolje[n][s] + Math.log(koncna[s]) > najvec) {
                najvec = najbolje[n][s] + Math.log(koncna[s]);
                konec = s;
            }
        }
        if (konec < 0) {
            return null;
        }
        int[] pot = new int[n];
        int s = konec;
        for (int i = n; i > 0; i--) {
            pot[i - 1] = izbira[i][s];
            s = odkod[i][s];
        }
        najvecOut[0] = najvec;
        return pot;
    }

    /* Rezultat celice: verjetnost, da je napisano w, ali da je celica prazna (ni izpolnjena). */
    private static double rezultat(double[] p, int w) {
        return p == null ? 1 : p[w] + p[PRAZNO];
    }

    private static Integer[] rezultatCelic(double[] levo, double[] desno) {
        return new Integer[] {vrednost(levo), vrednost(desno)};
    }

    private static Integer vrednost(double[] p) {
        if (p == null) {
            return null;
        }
        int m = najverjetnejsa(p);
        return m == PRAZNO ? null : m;
    }

    private static boolean negotovRezultat(double[] levo, double[] desno) {
        return (levo != null && najvecja(levo) < ZAUPANJE) || (desno != null && najvecja(desno) < ZAUPANJE);
    }

    /* Ce nobeno veljavno zaporedje ni mogoce: vsaka celica zase, vse negotovo. */
    private static Izid neodvisno(List<Vrstica> vrstice, double[] rezLevo, double[] rezDesno) {
        List<Niz> nizi = new ArrayList<>();
        for (Vrstica v : vrstice) {
            int l = najverjetnejsa(v.levo());
            int d = najverjetnejsa(v.desno());
            nizi.add(new Niz(l == PRAZNO ? null : l, d == PRAZNO ? null : d, true, true,
                    najvecja(v.levo()), najvecja(v.desno())));
        }
        Integer[] rez = rezultatCelic(rezLevo, rezDesno);
        return new Izid(nizi, rez[0], rez[1], true, "Številke na listku se ne ujemajo s pravili. Preveri vse nize.");
    }

    static int najverjetnejsa(double[] p) {
        int m = 0;
        for (int i = 1; i < p.length; i++) {
            if (p[i] > p[m]) {
                m = i;
            }
        }
        return m;
    }

    private static double najvecja(double[] p) {
        return p[najverjetnejsa(p)];
    }
}
