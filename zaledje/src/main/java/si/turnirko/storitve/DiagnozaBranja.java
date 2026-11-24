/* Diagnostika branja (24. 11. 2026, lastnik: »zakaj je zaznavanje na pravih listkih
   obcutno slabse, kot kaze meritev?«). Cist izracun brez baze; MeritevNabora ga
   pozene nad zlatim naborom, locen za preizkusne in ucne listke.

   Za vsako celico tock listka (vrstice nizov, kot jih steje MeritevBranja) ugotovi:
   - ali je prebrana prav in, ce ni, ZAKAJ (Vzrok). Vzroki si sledijo po korakih
     bralnika: najprej razrez (je crnilo, je razlaga s pravim stevilom stevk), nato
     mreza (ali bi katera taka razlaga dala pravo vrednost), nato izbira razlage
     (porazdelitev celice) in na koncu pravila (Viterbi s pogostostjo nizov);
   - kako dobro mreza bere STEVKE z listkov, izrezane natanko kot za ucenje
     (UcenjeIzListkov.stevkeCelice: razlaga s pravim stevilom stevk z najvecjo
     verjetnostjo). Celice, kjer take razlage ni, se v ucenje in med preizkusne
     stevke nikoli ne zapisejo - zato jih tu stejemo posebej;
   - umerjenost: zaupanje celice proti delezu prav, za mrezo samo (najvecja
     verjetnost porazdelitve) in za koncno branje (robna verjetnost razlage). */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

import si.turnirko.branje.RazlagaListka;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.dto.DiagnozaNaboraDto;
import si.turnirko.dto.NapakeMrezeDto;

public final class DiagnozaBranja {

    private DiagnozaBranja() {}

    public enum Vzrok { NI_CRNILA, CRNILO_V_PRAZNI, RAZREZ, MREZA, IZBIRA_RAZREZA, PRAVILA }

    /* Meje razredov zaupanja (zadnja malo nad 1, da zaupanje 1,0 pade v zadnji razred). */
    static final double[] MEJE = {0, 0.5, 0.8, 0.9, 0.95, 0.99, 1.000001};

    /* Najvec napacnih celic v galeriji. */
    static final int NAJVEC_NAPAK = 40;

    /* Celica listka za diagnozo. stran 0 = levo, 1 = desno; napisano/prebrano null =
       prazna; oznacena = organizator jo vidi oznaceno (kot v MeritevBranja);
       porazdelitev = mreza na tej celici (101 vrednosti, zadnja je prazno);
       zaupanje = robna verjetnost izbrane vrednosti po pravilih. */
    public record Celica(int niz, int stran, Integer napisano, Integer prebrano, boolean oznacena,
                         RazrezCelice.Vsebina vsebina, double[] porazdelitev, double zaupanje) {}

    /* Celice tock enega listka v istem obsegu kot MeritevBranja.primerjaj: vse vrstice
       do zadnje prebrane ali potrjene (prazne vrstice na koncu niso celice). */
    public static List<Celica> celice(LokalniBralnikListka.Izrez izrez, LokalniBralnikListka.Porazdelitve porazdelitve,
                                      LokalniBralnikListka.Razlozen razlozen, MeritevBranja.Resnica resnica) {
        List<PrebranListek.Niz> vrstice = razlozen.listek().niziAliPrazno();
        int zadnjaPolna = -1;
        for (int i = 0; i < vrstice.size(); i++) {
            PrebranListek.Niz v = vrstice.get(i);
            if (v != null && (v.levo() != null || v.desno() != null)) {
                zadnjaPolna = i;
            }
        }
        int n = Math.min(Math.max(zadnjaPolna + 1, resnica.nizi().size()), izrez.nizi().size());
        List<Celica> celice = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PrebranListek.Niz v = i < vrstice.size() ? vrstice.get(i) : null;
            RazlagaListka.Niz r = i < razlozen.razlaga().nizi().size() ? razlozen.razlaga().nizi().get(i) : null;
            Integer levo = v == null ? null : v.levo();
            Integer desno = v == null ? null : v.desno();
            boolean vidna = i <= zadnjaPolna;
            boolean opozoriloNiza = vidna && (levo == null || desno == null
                    || !PredlogIzidaListka.veljavenNiz(levo, desno));
            int[] prava = i < resnica.nizi().size() ? resnica.nizi().get(i) : null;
            celice.add(new Celica(i, 0, prava == null ? null : prava[0], levo,
                    opozoriloNiza || (vidna && v.negotovoLevo()), izrez.nizi().get(i)[0],
                    porazdelitve.nizi().get(i)[0], r == null ? 0 : r.zaupanjeLevo()));
            celice.add(new Celica(i, 1, prava == null ? null : prava[1], desno,
                    opozoriloNiza || (vidna && v.negotovoDesno()), izrez.nizi().get(i)[1],
                    porazdelitve.nizi().get(i)[1], r == null ? 0 : r.zaupanjeDesno()));
        }
        return celice;
    }

    /* Zakaj je celica narobe (klice se samo za napacne celice). stevka = mreza na eni
       izrezani stevki (najverjetnejsa 0-9). */
    static Vzrok vzrok(Celica c, ToIntFunction<float[]> stevka) {
        Integer napisano = c.napisano();
        if (napisano == null) {
            return c.vsebina().prazna() ? Vzrok.PRAVILA : Vzrok.CRNILO_V_PRAZNI;
        }
        if (c.vsebina().prazna()) {
            return Vzrok.NI_CRNILA;
        }
        List<RazrezCelice.Razlaga> ustrezne = ustrezne(c.vsebina(), napisano);
        if (ustrezne.isEmpty()) {
            return Vzrok.RAZREZ;
        }
        boolean mrezaPrav = ustrezne.stream().anyMatch(r -> vrednost(r, stevka) == napisano);
        if (!mrezaPrav) {
            return Vzrok.MREZA;
        }
        if (najverjetnejsa(c.porazdelitev()) != napisano) {
            return Vzrok.IZBIRA_RAZREZA;
        }
        return Vzrok.PRAVILA;
    }

    /* Razlage razreza z istim stevilom stevk kot potrjena vrednost. */
    private static List<RazrezCelice.Razlaga> ustrezne(RazrezCelice.Vsebina v, int vrednost) {
        int dolzina = String.valueOf(vrednost).length();
        return v.razlage().stream().filter(r -> r.stevke().size() == dolzina).toList();
    }

    private static int vrednost(RazrezCelice.Razlaga r, ToIntFunction<float[]> stevka) {
        int v = 0;
        for (RazrezCelice.Stevka s : r.stevke()) {
            v = v * 10 + stevka.applyAsInt(s.vhod());
        }
        return v;
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

    private static int razred(double zaupanje) {
        for (int i = 0; i < MEJE.length - 1; i++) {
            if (zaupanje < MEJE[i + 1]) {
                return i;
            }
        }
        return MEJE.length - 2;
    }

    /* Sesteje listke ene skupine (preizkusni ali ucni). */
    public static final class Zbirnik {
        private final ToIntFunction<float[]> stevka;
        private int listkov;
        private int celic;
        private int napacnih;
        private final int[][] vzroki = new int[Vzrok.values().length][2];
        private int celicZZapisom;
        private int brezRazreza;
        private int stevk;
        private int stevkPrav;
        private final int[][] zmeda = new int[10][10];
        private final int[][] umerjenostMreze = new int[MEJE.length - 1][2];
        private final int[][] umerjenostKoncna = new int[MEJE.length - 1][2];
        private final List<DiagnozaNaboraDto.Napaka> napake = new ArrayList<>();

        public Zbirnik(ToIntFunction<float[]> stevka) {
            this.stevka = stevka;
        }

        /* slika(niz, stran) da izrez celice za galerijo (PNG base64) ali null; klice se
           samo, dokler galerija ni polna. null = brez galerije. */
        public void dodaj(List<Celica> celiceListka, BiFunction<Integer, Integer, String> slika) {
            listkov++;
            for (Celica c : celiceListka) {
                celic++;
                boolean prav = Objects.equals(c.prebrano(), c.napisano());
                stevke(c);

                int naj = najverjetnejsa(c.porazdelitev());
                int pravaVrednost = c.napisano() == null ? RazlagaListka.PRAZNO : c.napisano();
                int[] rm = umerjenostMreze[razred(c.porazdelitev()[naj])];
                rm[0]++;
                if (naj == pravaVrednost) {
                    rm[1]++;
                }
                int[] rk = umerjenostKoncna[razred(c.zaupanje())];
                rk[0]++;
                if (prav) {
                    rk[1]++;
                }

                if (prav) {
                    continue;
                }
                napacnih++;
                Vzrok v = vzrok(c, stevka);
                boolean tiha = !c.oznacena();
                vzroki[v.ordinal()][tiha ? 0 : 1]++;
                if (slika != null && napake.size() < NAJVEC_NAPAK) {
                    napake.add(new DiagnozaNaboraDto.Napaka(c.niz() + 1, c.stran() == 0 ? "levo" : "desno",
                            c.napisano(), c.prebrano(), v.name(), tiha, c.zaupanje(),
                            slika.apply(c.niz(), c.stran())));
                }
            }
        }

        /* Stevke celice, izrezane kot za ucenje (najverjetnejsa razlaga s pravim stevilom stevk). */
        private void stevke(Celica c) {
            if (c.napisano() == null || c.vsebina().prazna()) {
                return;
            }
            celicZZapisom++;
            RazrezCelice.Razlaga najboljsa = ustrezne(c.vsebina(), c.napisano()).stream()
                    .max(java.util.Comparator.comparingDouble(RazrezCelice.Razlaga::verjetnost)).orElse(null);
            if (najboljsa == null) {
                brezRazreza++;
                return;
            }
            String napisano = String.valueOf(c.napisano());
            for (int k = 0; k < najboljsa.stevke().size(); k++) {
                int prava = napisano.charAt(k) - '0';
                int prebrana = stevka.applyAsInt(najboljsa.stevke().get(k).vhod());
                stevk++;
                zmeda[prava][prebrana]++;
                if (prava == prebrana) {
                    stevkPrav++;
                }
            }
        }

        public DiagnozaNaboraDto.Del dto() {
            List<DiagnozaNaboraDto.Vzrok> v = new ArrayList<>();
            for (Vzrok vz : Vzrok.values()) {
                v.add(new DiagnozaNaboraDto.Vzrok(vz.name(), vzroki[vz.ordinal()][0], vzroki[vz.ordinal()][1]));
            }
            List<DiagnozaNaboraDto.PoStevki> poStevkah = new ArrayList<>();
            for (int s = 0; s < 10; s++) {
                int vseh = 0;
                List<NapakeMrezeDto.Zamenjava> zamenjave = new ArrayList<>();
                for (int o = 0; o < 10; o++) {
                    vseh += zmeda[s][o];
                    if (o != s && zmeda[s][o] > 0) {
                        zamenjave.add(new NapakeMrezeDto.Zamenjava(o, zmeda[s][o]));
                    }
                }
                zamenjave.sort((a, b) -> Integer.compare(b.krat(), a.krat()));
                poStevkah.add(new DiagnozaNaboraDto.PoStevki(s, vseh, zmeda[s][s],
                        List.copyOf(zamenjave.subList(0, Math.min(3, zamenjave.size())))));
            }
            return new DiagnozaNaboraDto.Del(listkov, celic, napacnih, v,
                    new DiagnozaNaboraDto.Stevke(celicZZapisom, brezRazreza, stevk, stevkPrav, poStevkah),
                    razredi(umerjenostMreze), razredi(umerjenostKoncna), List.copyOf(napake));
        }

        private static List<DiagnozaNaboraDto.Razred> razredi(int[][] u) {
            List<DiagnozaNaboraDto.Razred> r = new ArrayList<>();
            for (int i = 0; i < u.length; i++) {
                r.add(new DiagnozaNaboraDto.Razred(MEJE[i], Math.min(1, MEJE[i + 1]), u[i][0], u[i][1]));
            }
            return r;
        }
    }
}
