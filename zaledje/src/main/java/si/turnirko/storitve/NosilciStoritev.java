/* Zreb z nosilci: kdo pade v katero skupino in kdo na katero mesto v
   izlocilni mrezi. Cista logika nad ze urejenim jakostnim seznamom
   (najmocnejsi prvi) - brez baze, zato jo je mogoce testirati neposredno.

   PRAVILA ZREBA (21. in 22. clen PST NTZS)

   Skupine (N skupin):
   - prvi jakostni pas se NE zreba: 1. nosilec je prvi zapisan v skupini A,
     2. v skupini B ... N-ti v N-ti skupini;
   - vsak naslednji pas (N+1..2N, 2N+1..3N, 3N+1..4N ...) se zreba naklujucno,
     po enega igralca v vsako skupino - v skupinah torej stojijo drug pod
     drugim po jakostnih pasovih;
   - ce zadnji pas ni poln, ga dobijo NAKLJUCNE skupine (te so za enega
     igralca vecje).

   Izlocilna mreza:
   - 1. nosilec gre na vrh, 2. na dno mreze (ta dva se ne zrebata);
   - 3. in 4. nosilec se zrebata na svoji cetrtini - s prvima dvema se lahko
     srecata sele v polfinalu;
   - 5. do 8. nosilec se zrebajo na svoje osmine - cetrtfinale;
   - 9. do 16., 17. do 32. ... enako po pasovih, ki se podvajajo.

   Izlocilni del po skupinah:
   - zmagovalci skupin so nosilci 1..N (zmagovalec skupine A je 1. nosilec)
     in se zrebajo po zgornjem pravilu pasov;
   - drugouvrsceni se zrebajo na preostala mesta, vsak v NASPROTNO polovico
     od zmagovalca svoje skupine: igralca iz iste skupine se tako lahko
     srecata sele v finalu;
   - iz tega samo od sebe sledi, da zmagovalec skupine v 1. kolu nikoli ne
     igra z drugim zmagovalcem skupine - ali je prost ali igra
     drugouvrscenega iz druge skupine.

   Klubska locitev (v 1. kolu mreze in znotraj skupine) je MEHKO pravilo: ce
   je igralcev enega kluba prevec, se ji ni mogoce izogniti. Zato zreb ni
   iskanje po pravilih, ampak vec nakljucnih poskusov, med katerimi obvelja
   tisti z najmanj trki - in prvi brez trka konca iskanje. */
package si.turnirko.storitve;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;

@Service
public class NosilciStoritev {

    /* Koliko nakljucnih zrebov poskusimo, preden obvelja najboljsi. Meja je
       varovalka za primer, ko zreba brez klubskega trka sploh ni. */
    private static final int POSKUSOV = 300;

    /* Zreb po vrsti (SV regija) vsak poskus se izboljsa z zamenjavami, zato jih
       je dovolj manj; skupinska kazen pretehta vse klubske trke. */
    private static final int POSKUSOV_PO_VRSTI = 60;
    private static final long SKUPINSKA_KAZEN = 1000;

    /* Vir nakljucnosti je zamenljiv, da so testi lahko deterministicni.
       Isti vir uporablja tudi ZrebStoritev, da je zreb v celoti ponovljiv. */
    private Random nakljucje = new SecureRandom();

    void nastaviNakljucje(Random nakljucje) {
        this.nakljucje = nakljucje;
    }

    Random nakljucje() {
        return nakljucje;
    }

    // ---------------------------------------------------------------------
    // Skupine
    // ---------------------------------------------------------------------

    /* Razdeli jakostno urejen seznam v skupine po jakostnih pasovih.
       Vrne seznam skupin (prva je skupina A); clani vsake skupine so urejeni
       po pasovih, torej po jakosti navzdol. */
    public List<List<Prijava>> vSkupine(List<Prijava> poJakosti, int stSkupin) {
        List<List<Prijava>> najboljsi = null;
        int najmanjTrkov = Integer.MAX_VALUE;
        for (int poskus = 0; poskus < POSKUSOV && najmanjTrkov > 0; poskus++) {
            List<List<Prijava>> razpored = enZrebSkupin(poJakosti, stSkupin);
            int trkov = 0;
            for (List<Prijava> skupina : razpored) {
                trkov += klubskihTrkov(skupina);
            }
            if (trkov < najmanjTrkov) {
                najmanjTrkov = trkov;
                najboljsi = razpored;
            }
        }
        return najboljsi;
    }

    private List<List<Prijava>> enZrebSkupin(List<Prijava> poJakosti, int stSkupin) {
        List<List<Prijava>> skupine = new ArrayList<>();
        for (int i = 0; i < stSkupin; i++) {
            skupine.add(new ArrayList<>());
        }
        for (int od = 0; od < poJakosti.size(); od += stSkupin) {
            List<Prijava> pas = poJakosti.subList(od, Math.min(od + stSkupin, poJakosti.size()));
            if (od == 0) {
                // prvi pas se ne zreba: 1. nosilec v A, 2. v B ...
                for (int i = 0; i < pas.size(); i++) {
                    skupine.get(i).add(pas.get(i));
                }
            } else {
                zrebajPas(pas, skupine);
            }
        }
        return skupine;
    }

    /* En jakostni pas: vsak igralec v svojo skupino, izbrano nakljucno.
       Kadar pas ni poln, ostanejo nakljucne skupine brez igralca tega pasu. */
    private void zrebajPas(List<Prijava> pas, List<List<Prijava>> skupine) {
        List<Prijava> igralci = new ArrayList<>(pas);
        Collections.shuffle(igralci, nakljucje);

        List<Integer> proste = zaporedje(skupine.size());
        Collections.shuffle(proste, nakljucje);

        for (Prijava igralec : igralci) {
            int izbrana = 0; // ce nobena ne ustreza, obvelja prva - pravilo popusti
            for (int i = 0; i < proste.size(); i++) {
                if (!istiKlubVSkupini(igralec, skupine.get(proste.get(i)))) {
                    izbrana = i;
                    break;
                }
            }
            skupine.get(proste.remove(izbrana)).add(igralec);
        }
    }

    // ---------------------------------------------------------------------
    // Izlocilna mreza
    // ---------------------------------------------------------------------

    /* Jakostni seznam razporedi na nosilska mesta mreze: 1. in 2. ostaneta na
       svojem mestu, vsak naslednji pas (3-4, 5-8, 9-16 ...) se premesa.
       Vrne seznam po nosilskih mestih, kot ga pricakuje zgradiIzlocilnoMrezo. */
    public List<Prijava> vMrezo(List<Prijava> poJakosti) {
        int velikostMreze = najblizjaPotencaDve(poJakosti.size());
        List<Prijava> najboljsi = null;
        int najmanjTrkov = Integer.MAX_VALUE;
        for (int poskus = 0; poskus < POSKUSOV && najmanjTrkov > 0; poskus++) {
            List<Prijava> nosilci = premesajPasove(poJakosti);
            int trkov = klubskihTrkovVPrvemKolu(nosilci, velikostMreze);
            if (trkov < najmanjTrkov) {
                najmanjTrkov = trkov;
                najboljsi = nosilci;
            }
        }
        return najboljsi;
    }

    /* Nosilska mesta izlocilnega dela po skupinah. Oba seznama sta po
       skupinah: zmagovalci.get(i) in drugi.get(i) sta iz iste skupine,
       skupina 0 (A) je najmocnejsa.

       drugi.get(i) je lahko prazen (null): skupina z manj igralci (npr. tri
       namesto stirih) drugouvrscenega za ta zreb nima. SV_REGIJA isto metodo
       uporabi za tolazilni zreb, kjer sta "zmagovalec" in "drugi" tretje- in
       cetrtouvrsceni. Seznam, ki ga vrne, ima toliko nosilcev, kolikor je
       udelezencev. */
    public List<Prijava> vMrezoIzSkupin(List<Prijava> zmagovalci, List<Prijava> drugi) {
        int stDrugih = (int) drugi.stream().filter(java.util.Objects::nonNull).count();
        int velikostMreze = najblizjaPotencaDve(zmagovalci.size() + stDrugih);
        int[] indeksMesta = indeksiNosilskihMest(velikostMreze);

        List<Prijava> najboljsi = null;
        int najmanjTrkov = Integer.MAX_VALUE;
        for (int poskus = 0; poskus < POSKUSOV && najmanjTrkov > 0; poskus++) {
            List<Prijava> nosilci = enZrebIzSkupin(zmagovalci, drugi, velikostMreze, indeksMesta);
            int trkov = klubskihTrkovVPrvemKolu(nosilci, velikostMreze);
            if (trkov < najmanjTrkov) {
                najmanjTrkov = trkov;
                najboljsi = nosilci;
            }
        }
        return najboljsi;
    }

    /* Nosilska mesta zreba, v katerega pridejo igralci iz vec skupin PO VRSTI
       (SV regija: A1, B1, ..., E1, A2, B2 ...). Visje v seznamu je boljse mesto
       v skupini, zato visji nosilec: 1. in 2. ostaneta, pasovi (3-4, 5-8, 9-16
       ...) se zrebajo kot v vsaki mrezi. Igralca iz iste skupine naj se srecata
       cim pozneje - najraje sele v finalu zreba (pri dveh iz skupine to pomeni
       nasprotni polovici, kot pri vMrezoIzSkupin); klubska locitev v 1. kolu je
       drugotna. Vsak nakljucni zreb pasov se izboljsa z zamenjavami znotraj
       pasov, obvelja tisti z najmanjso kaznijo. skupine.get(i) je skupina
       i-tega igralca. Vrne seznam po nosilskih mestih (prvi = 1. nosilec). */
    public List<Prijava> vMrezoPoVrsti(List<Prijava> poVrsti, List<Integer> skupine) {
        int stevilo = poVrsti.size();
        if (stevilo <= 2) {
            return new ArrayList<>(poVrsti);
        }
        int velikostMreze = najblizjaPotencaDve(stevilo);
        int[] indeksMesta = indeksiNosilskihMest(velikostMreze);
        List<int[]> pasovi = pasovi(stevilo);

        List<Integer> najboljsi = null;
        long najmanjsaKazen = Long.MAX_VALUE;
        for (int poskus = 0; poskus < POSKUSOV_PO_VRSTI && najmanjsaKazen > 0; poskus++) {
            // red.get(nosilec - 1) = indeks igralca v poVrsti
            List<Integer> red = premesajPasove(zaporedje(stevilo));
            long kazen = izboljsajZamenjavami(red, pasovi, poVrsti, skupine, indeksMesta, velikostMreze);
            if (kazen < najmanjsaKazen) {
                najmanjsaKazen = kazen;
                najboljsi = red;
            }
        }
        List<Prijava> nosilci = new ArrayList<>(stevilo);
        for (int indeks : najboljsi) {
            nosilci.add(poVrsti.get(indeks));
        }
        return nosilci;
    }

    /* Zamenjave znotraj pasov, dokler katera zmanjsa kazen. */
    private static long izboljsajZamenjavami(List<Integer> red, List<int[]> pasovi, List<Prijava> poVrsti,
                                             List<Integer> skupine, int[] indeksMesta, int velikostMreze) {
        long kazen = kazenPoVrsti(red, poVrsti, skupine, indeksMesta, velikostMreze);
        boolean izboljsano = true;
        while (izboljsano && kazen > 0) {
            izboljsano = false;
            for (int[] pas : pasovi) {
                for (int a = pas[0]; a < pas[1]; a++) {
                    for (int b = a + 1; b < pas[1]; b++) {
                        Collections.swap(red, a, b);
                        long nova = kazenPoVrsti(red, poVrsti, skupine, indeksMesta, velikostMreze);
                        if (nova < kazen) {
                            kazen = nova;
                            izboljsano = true;
                        } else {
                            Collections.swap(red, a, b);
                        }
                    }
                }
            }
        }
        return kazen;
    }

    /* Kazen razporeda: za vsak par iz iste skupine, ki se lahko sreca pred
       finalom zreba, toliko huje, kolikor prej (polfinale 1, cetrtfinale 3,
       osmina 7 ...), in za vsak klubski trk v 1. kolu po 1. Skupinska kazen
       ima prednost pred vsemi klubskimi trki. */
    private static long kazenPoVrsti(List<Integer> red, List<Prijava> poVrsti, List<Integer> skupine,
                                     int[] indeksMesta, int velikostMreze) {
        int kol = Integer.numberOfTrailingZeros(velikostMreze);
        long kazen = 0;
        for (int a = 0; a < red.size(); a++) {
            for (int b = a + 1; b < red.size(); b++) {
                if (!skupine.get(red.get(a)).equals(skupine.get(red.get(b)))) {
                    continue;
                }
                int prvi = indeksMesta[a + 1];
                int drugi = indeksMesta[b + 1];
                int kolo = 1; // kolo, v katerem se lahko srecata (kol = finale)
                while ((prvi >> kolo) != (drugi >> kolo)) {
                    kolo++;
                }
                kazen += SKUPINSKA_KAZEN * ((1L << (kol - kolo)) - 1);
            }
        }
        List<Prijava> nosilci = new ArrayList<>(red.size());
        for (int indeks : red) {
            nosilci.add(poVrsti.get(indeks));
        }
        return kazen + klubskihTrkovVPrvemKolu(nosilci, velikostMreze);
    }

    private List<Prijava> enZrebIzSkupin(List<Prijava> zmagovalci, List<Prijava> drugi,
                                         int velikostMreze, int[] indeksMesta) {
        int stSkupin = zmagovalci.size();
        int stDrugih = (int) drugi.stream().filter(java.util.Objects::nonNull).count();
        Prijava[] nosilci = new Prijava[stSkupin + stDrugih];

        // zmagovalci skupin na nosilska mesta 1..N, premesani po pasovih
        List<Integer> skupinaNaMestu = premesajPasove(zaporedje(stSkupin));
        boolean[] zmagovalecJeZgoraj = new boolean[stSkupin];
        for (int mesto = 1; mesto <= stSkupin; mesto++) {
            int skupina = skupinaNaMestu.get(mesto - 1);
            nosilci[mesto - 1] = zmagovalci.get(skupina);
            zmagovalecJeZgoraj[skupina] = jeZgornjaPolovica(indeksMesta, mesto, velikostMreze);
        }

        // preostala nosilska mesta, locena po polovicah mreze
        List<Integer> zgoraj = new ArrayList<>();
        List<Integer> spodaj = new ArrayList<>();
        for (int mesto = stSkupin + 1; mesto <= nosilci.length; mesto++) {
            (jeZgornjaPolovica(indeksMesta, mesto, velikostMreze) ? zgoraj : spodaj).add(mesto);
        }
        Collections.shuffle(zgoraj, nakljucje);
        Collections.shuffle(spodaj, nakljucje);

        /* Drugouvrsceni v nasprotno polovico od zmagovalca svoje skupine.
           Mest je v vsaki polovici natanko toliko, kolikor jih pravilo
           zahteva: nosilski mesti 2k-1 in 2k sta vedno v razlicnih polovicah,
           zato se zmagovalci in drugouvrsceni po polovicah izidejo. */
        for (int skupina : premesano(zaporedje(stSkupin))) {
            Prijava drugouvrsceni = drugi.get(skupina);
            if (drugouvrsceni == null) {
                continue; // skupina drugouvrscenega nima
            }
            List<Integer> polovica = zmagovalecJeZgoraj[skupina] ? spodaj : zgoraj;
            if (polovica.isEmpty()) {
                // ob manjkajocih drugouvrscenih polovici nista vec uravnotezeni:
                // pravilo popusti in igralec gre v drugo polovico
                polovica = polovica == spodaj ? zgoraj : spodaj;
            }
            nosilci[vzemiMesto(polovica, drugouvrsceni, nosilci, velikostMreze) - 1] = drugouvrsceni;
        }
        return new ArrayList<>(Arrays.asList(nosilci));
    }

    /* Vzame (in odstrani) nosilsko mesto iz ponujenih. Prednost ima mesto,
       kjer nasprotnik v 1. kolu ni iz istega kluba; ce takega ni, obvelja
       prvo - klubske locitve v tem zrebu ni bilo mogoce doseci. */
    private int vzemiMesto(List<Integer> proste, Prijava igralec,
                           Prijava[] nosilci, int velikostMreze) {
        for (int i = 0; i < proste.size(); i++) {
            Prijava nasprotnik = naMestu(nosilci, velikostMreze + 1 - proste.get(i));
            if (nasprotnik == null || !istiKlub(igralec, nasprotnik)) {
                return proste.remove(i);
            }
        }
        return proste.remove(0);
    }

    // ---------------------------------------------------------------------
    // Geometrija mreze
    // ---------------------------------------------------------------------

    /* Standardni vrstni red nosilskih mest v mrezi.
       Za velikost 8 vrne [1, 8, 5, 4, 3, 6, 7, 2], kar pomeni pare 1-8, 5-4,
       3-6 in 7-2. Prvi nosilec je torej na VRHU, drugi na DNU mreze, 3. in 4.
       v razlicnih cetrtinah in tako naprej. Prosta mesta zasedejo najvisje
       stevilke, zato so enakomerno razprsena in se nikoli ne srecata.

       Vsak korak podvoji mrezo: mesto x se razcepi na par (x, 2n+1-x), pri
       lihem indeksu obrnjeno - ta obrat je tisto, kar drugega nosilca potisne
       na dno namesto na sredino. */
    public static int[] seedVrstniRed(int velikost) {
        int[] vrstniRed = {1};
        while (vrstniRed.length < velikost) {
            int dvojna = vrstniRed.length * 2;
            int[] novi = new int[dvojna];
            for (int i = 0; i < vrstniRed.length; i++) {
                int visji = dvojna + 1 - vrstniRed[i];
                boolean mocnejsiZgoraj = i % 2 == 0;
                novi[2 * i] = mocnejsiZgoraj ? vrstniRed[i] : visji;
                novi[2 * i + 1] = mocnejsiZgoraj ? visji : vrstniRed[i];
            }
            vrstniRed = novi;
        }
        return vrstniRed;
    }

    /* Najmanjsa potenca stevila 2, ki je >= n. */
    public static int najblizjaPotencaDve(int n) {
        int potenca = 1;
        while (potenca < n) {
            potenca *= 2;
        }
        return potenca;
    }

    /* Nosilsko mesto (1..velikost) -> njegov indeks v mrezi. */
    private static int[] indeksiNosilskihMest(int velikostMreze) {
        int[] polozaji = seedVrstniRed(velikostMreze);
        int[] indeks = new int[velikostMreze + 1];
        for (int i = 0; i < velikostMreze; i++) {
            indeks[polozaji[i]] = i;
        }
        return indeks;
    }

    private static boolean jeZgornjaPolovica(int[] indeksMesta, int mesto, int velikostMreze) {
        return indeksMesta[mesto] < velikostMreze / 2;
    }

    /* Meje jakostnih pasov v seznamu nosilcev (0-based, konec izkljucen):
       {1}, {2}, {3,4}, {5-8}, {9-16} ... Prva dva se ne zrebata, zato sta
       vsak svoj pas. */
    static List<int[]> pasovi(int stevilo) {
        List<int[]> pasovi = new ArrayList<>();
        int od = 0;
        int velikost = 1;
        while (od < stevilo) {
            int doIzkljucno = Math.min(od + velikost, stevilo);
            pasovi.add(new int[] {od, doIzkljucno});
            od = doIzkljucno;
            velikost = Math.max(1, od);
        }
        return pasovi;
    }

    /* Kopija seznama, v kateri je vsak jakostni pas premesan sam zase. */
    private <T> List<T> premesajPasove(List<T> poJakosti) {
        List<T> premesani = new ArrayList<>(poJakosti);
        for (int[] pas : pasovi(premesani.size())) {
            Collections.shuffle(premesani.subList(pas[0], pas[1]), nakljucje);
        }
        return premesani;
    }

    private <T> List<T> premesano(List<T> seznam) {
        Collections.shuffle(seznam, nakljucje);
        return seznam;
    }

    private static List<Integer> zaporedje(int koliko) {
        List<Integer> zaporedje = new ArrayList<>(koliko);
        for (int i = 0; i < koliko; i++) {
            zaporedje.add(i);
        }
        return zaporedje;
    }

    // ---------------------------------------------------------------------
    // Klubska locitev
    // ---------------------------------------------------------------------

    /* Koliko parov iz istega kluba bi se srecalo v 1. kolu. Pari 1. kola so
       (mesto, velikost+1-mesto); kjer je eno mesto prosto, tekme ni. */
    private static int klubskihTrkovVPrvemKolu(List<Prijava> nosilci, int velikostMreze) {
        Prijava[] mesta = nosilci.toArray(new Prijava[0]);
        int trkov = 0;
        for (int mesto = 1; 2 * mesto <= velikostMreze; mesto++) {
            Prijava prvi = naMestu(mesta, mesto);
            Prijava drugi = naMestu(mesta, velikostMreze + 1 - mesto);
            if (prvi != null && drugi != null && istiKlub(prvi, drugi)) {
                trkov++;
            }
        }
        return trkov;
    }

    /* Koliko parov iz istega kluba je v isti skupini. */
    private static int klubskihTrkov(List<Prijava> skupina) {
        int trkov = 0;
        for (int i = 0; i < skupina.size(); i++) {
            for (int j = i + 1; j < skupina.size(); j++) {
                if (istiKlub(skupina.get(i), skupina.get(j))) {
                    trkov++;
                }
            }
        }
        return trkov;
    }

    private static boolean istiKlubVSkupini(Prijava igralec, List<Prijava> skupina) {
        return skupina.stream().anyMatch(clan -> istiKlub(igralec, clan));
    }

    /* Igralca sta "iz istega kluba", ce imata kaksen klub skupen. Pri dvojicah
       ima prijava dva kluba; igralec BREZ kluba ni v klubu z nikomer. */
    private static boolean istiKlub(Prijava prva, Prijava druga) {
        Set<Long> klubi = klubi(prva);
        klubi.retainAll(klubi(druga));
        return !klubi.isEmpty();
    }

    private static Set<Long> klubi(Prijava prijava) {
        Set<Long> klubi = new HashSet<>();
        dodajKlub(klubi, prijava.getKlubObPrijavi());
        dodajKlub(klubi, prijava.getKlubObPrijavi2());
        return klubi;
    }

    private static void dodajKlub(Set<Long> klubi, Klub klub) {
        if (klub != null) {
            klubi.add(klub.getId());
        }
    }

    /* Igralec na danem nosilskem mestu; null pomeni prosto mesto (bye). */
    private static Prijava naMestu(Prijava[] nosilci, int mesto) {
        return mesto <= nosilci.length ? nosilci[mesto - 1] : null;
    }
}
