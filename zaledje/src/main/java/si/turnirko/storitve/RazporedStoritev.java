/* Generator razporeda lige - "circle method" (krozni sistem) in razlicica po
   PARIH (enakomerna razvrstitev). Deluje z indeksi ekip (0..stEkip-1) in vrne
   pare po kolih; preslikavo v dejanske ekipe opravi LigaStoritev. Cista logika
   brez baze - lahko se testira neposredno.

   Pri lihem stevilu ekip se doda navidezni nasprotnik (prosto): ekipa, ki bi
   igrala z njim, v tem kolu pocitka. Pri dvokroznem sistemu drugi krog ponovi
   pare z zamenjano vlogo doma/gost. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class RazporedStoritev {

    /* En par srecanja: kolo (1..), indeksa domace in gostujoce ekipe. */
    public record Par(int kolo, int domaci, int gost) {}

    /* Celoten razpored za dano stevilo ekip po navadnem kroznem sistemu.
       Indeksi so vrstni red vpisa ekip in ne pomenijo jakosti. */
    public List<Par> razpored(int stEkip, boolean dvokrozno) {
        return razpored(stEkip, dvokrozno, false);
    }

    /* Celoten razpored; "poParih" preklopi na enakomerno razvrstitev, kjer so
       indeksi JAKOSTNI vrstni red (0 = najmocnejsa ekipa). */
    public List<Par> razpored(int stEkip, boolean dvokrozno, boolean poParih) {
        if (stEkip < 2) {
            return List.of();
        }
        List<Par> prviCikel = poParih ? enokroznoPoParih(stEkip) : enokrozno(stEkip);
        if (!dvokrozno) {
            return prviCikel;
        }
        /* Drugi cikel: isti pari, zamenjana doma/gost, kola nadaljujejo naprej.
           Dolzino cikla preberemo iz zgeneriranega razporeda - razlicici imata
           razlicno stevilo kol in izracun iz stevila ekip bi veljal le za eno. */
        int kolVCiklu = prviCikel.stream().mapToInt(Par::kolo).max().orElse(0);
        List<Par> vsi = new ArrayList<>(prviCikel);
        for (Par p : prviCikel) {
            vsi.add(new Par(p.kolo() + kolVCiklu, p.gost(), p.domaci()));
        }
        return vsi;
    }

    /* En cikel (vsak z vsakim enkrat) po circle metodi. */
    private List<Par> enokrozno(int stEkip) {
        boolean liho = stEkip % 2 != 0;
        int m = liho ? stEkip + 1 : stEkip;   // po potrebi dodamo navideznega
        int navidezni = m - 1;                 // indeks "prosto" (samo ce liho)
        int polovica = m / 2;
        int stKol = m - 1;

        // razpored mest v krogu; mesto 0 je fiksno, ostala rotiramo
        int[] mesta = new int[m];
        for (int i = 0; i < m; i++) {
            mesta[i] = i;
        }

        List<Par> pari = new ArrayList<>();
        for (int kolo = 1; kolo <= stKol; kolo++) {
            for (int i = 0; i < polovica; i++) {
                int a = mesta[i];
                int b = mesta[m - 1 - i];
                if (liho && (a == navidezni || b == navidezni)) {
                    continue; // ta ekipa v tem kolu pocitka (prosto)
                }
                // izmenicno doma/gost, da se obremenitev razporedi
                boolean aDoma = ((kolo + i) % 2 == 0);
                if (aDoma) {
                    pari.add(new Par(kolo, a, b));
                } else {
                    pari.add(new Par(kolo, b, a));
                }
            }
            zavrtiMesta(mesta);
        }
        return pari;
    }

    // ---------- Enakomerna razvrstitev (razpored po parih) ----------

    /* En cikel po parih. Indeksi so jakostna lestvica: 0 je najmocnejsa ekipa.

       Ekipe se zvezejo v PARE - i-ta ekipa zgornje polovice z i-to ekipo
       spodnje (pri 10 ekipah: A-F, B-G, C-H, D-I, E-J). Par nastopa kot celota:
       v enem KROGU odigra oba svoja dvoboja proti istemu nasprotnemu paru, zato
       vsaka ekipa v krogu dobi enega nasprotnika iz zgornje in enega iz spodnje
       polovice. Sezona se za vsak par konca z dvobojem med njegovima ekipama.

       Krog parov se zapise kot DVE koli, ker je kolo v aplikaciji en igralni dan
       in ekipa v njem odigra eno srecanje:
         prvo kolo  - zgornja proti zgornji, spodnja proti spodnji (doma pri
                      prvem paru),
         drugo kolo - navzkrizno (doma pri drugem paru).
       Vsaka ekipa torej v krogu odigra eno srecanje doma in eno v gosteh.

       Stevilo kol:
         * sodo stevilo parov - pari se v vsakem krogu razdelijo brez ostanka,
           zato dvoboji znotraj parov dobijo svoje sklepno kolo. Skupaj
           2*(parov-1) + 1 = stEkip-1 kol, torej enako kot navadni krozni sistem.
         * liho stevilo parov (10, 6, 14 ekip ...) - v vsakem krogu en par ostane
           brez nasprotnega para; takrat odigra svoj notranji dvoboj (v prvem
           kolu kroga) in v drugem kolu pocitka. Skupaj 2*parov = stEkip kol,
           torej eno vec kot navadni krozni sistem, in vsaka ekipa ima eno prosto
           kolo. To ni izbira, ampak nujnost: razporeda, v katerem bi pri lihem
           stevilu parov vsak par v vsakem kolu nastopil kot celota, ni - lihega
           stevila parov ni mogoce razdeliti na dvoboje parov. */
    private List<Par> enokroznoPoParih(int stEkip) {
        boolean liho = stEkip % 2 != 0;
        int m = liho ? stEkip + 1 : stEkip;   // navidezna ekipa je najsibkejsa
        int navidezna = m - 1;
        int parov = m / 2;

        List<Par> pari = new ArrayList<>();
        int kolo = 1;
        for (int[][] krog : krogiParov(parov)) {
            for (int[] dvoboj : krog) {
                int domaciPar = dvoboj[0];
                int gostujociPar = dvoboj[1];
                if (gostujociPar < 0) {
                    // par je brez nasprotnika: odigra svoj notranji dvoboj
                    dodaj(pari, kolo, domaciPar, domaciPar + parov, navidezna, liho);
                    continue;
                }
                /* Prvo kolo kroga doma pri prvem paru (zgornja proti zgornji,
                   spodnja proti spodnji), drugo pri drugem (navzkrizno). Vsaka
                   od stirih ekip tako v krogu odigra eno srecanje doma in eno v
                   gosteh - gostitelj ni cel par, ker bi sicer ena ekipa gostila
                   vse stiri dvoboje kroga in bi se domace pravice cez sezono
                   razsle. */
                dodaj(pari, kolo, domaciPar, gostujociPar, navidezna, liho);
                dodaj(pari, kolo, domaciPar + parov, gostujociPar + parov, navidezna, liho);
                dodaj(pari, kolo + 1, gostujociPar, domaciPar + parov, navidezna, liho);
                dodaj(pari, kolo + 1, gostujociPar + parov, domaciPar, navidezna, liho);
            }
            kolo += 2;
        }
        /* Pri sodem stevilu parov nihce ne ostane brez nasprotnega para, zato
           notranji dvoboji dobijo svoje sklepno kolo - zadnje kolo lige je
           takrat srecanje vsake ekipe s svojim parom. */
        if (parov % 2 == 0) {
            for (int p = 0; p < parov; p++) {
                dodaj(pari, kolo, p, p + parov, navidezna, liho);
            }
        }
        return pari;
    }

    /* Doda srecanje, razen ce v njem nastopa navidezna ekipa (takrat nasprotnik
       v tem kolu pocitka). */
    private static void dodaj(List<Par> pari, int kolo, int domaci, int gost,
                              int navidezna, boolean liho) {
        if (liho && (domaci == navidezna || gost == navidezna)) {
            return;
        }
        pari.add(new Par(kolo, domaci, gost));
    }

    /* Krogi dvobojev med PARI po isti circle metodi kot enokrozno, le da so
       vozlisca pari in ne ekipe. Vsak krog je seznam dvobojev {domaciPar,
       gostujociPar}; pri lihem stevilu parov eden ostane brez nasprotnika in ga
       oznacuje {par, -1}. */
    private List<int[][]> krogiParov(int parov) {
        boolean liho = parov % 2 != 0;
        int m = liho ? parov + 1 : parov;
        int navidezni = m - 1;
        int polovica = m / 2;

        int[] mesta = new int[m];
        for (int i = 0; i < m; i++) {
            mesta[i] = i;
        }

        List<int[][]> krogi = new ArrayList<>();
        for (int krog = 1; krog <= m - 1; krog++) {
            List<int[]> dvoboji = new ArrayList<>();
            for (int i = 0; i < polovica; i++) {
                int a = mesta[i];
                int b = mesta[m - 1 - i];
                if (liho && (a == navidezni || b == navidezni)) {
                    dvoboji.add(new int[] { a == navidezni ? b : a, -1 });
                    continue;
                }
                // gostitelj se izmenjuje, da se domaci krogi porazdelijo
                boolean aDoma = ((krog + i) % 2 == 0);
                dvoboji.add(aDoma ? new int[] { a, b } : new int[] { b, a });
            }
            krogi.add(dvoboji.toArray(new int[0][]));
            zavrtiMesta(mesta);
        }
        return krogi;
    }

    /* Rotacija: mesto 0 ostane, ostala se premaknejo za eno (circle method). */
    private void zavrtiMesta(int[] mesta) {
        int zadnji = mesta[mesta.length - 1];
        for (int i = mesta.length - 1; i > 1; i--) {
            mesta[i] = mesta[i - 1];
        }
        mesta[1] = zadnji;
    }
}
