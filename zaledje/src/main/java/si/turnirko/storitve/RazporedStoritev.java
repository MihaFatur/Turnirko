/* Generator razporeda lige po kroznem (round-robin) sistemu - "circle method".
   Deluje z indeksi ekip (0..stEkip-1) in vrne pare po kolih; preslikavo v
   dejanske ekipe opravi LigaStoritev. Cista logika brez baze - lahko se testira
   neposredno.

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

    /* Celoten razpored za dano stevilo ekip. */
    public List<Par> razpored(int stEkip, boolean dvokrozno) {
        if (stEkip < 2) {
            return List.of();
        }
        List<Par> prvicikel = enokrozno(stEkip);
        if (!dvokrozno) {
            return prvicikel;
        }
        // drugi cikel: isti pari, zamenjana doma/gost, kola nadaljujejo naprej
        int kolVCiklu = stEkip % 2 == 0 ? stEkip - 1 : stEkip;
        List<Par> vsi = new ArrayList<>(prvicikel);
        for (Par p : prvicikel) {
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

    /* Rotacija: mesto 0 ostane, ostala se premaknejo za eno (circle method). */
    private void zavrtiMesta(int[] mesta) {
        int zadnji = mesta[mesta.length - 1];
        for (int i = mesta.length - 1; i > 1; i--) {
            mesta[i] = mesta[i - 1];
        }
        mesta[1] = zadnji;
    }
}
