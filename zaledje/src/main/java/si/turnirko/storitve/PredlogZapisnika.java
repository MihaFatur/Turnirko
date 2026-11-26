/* Predlog zapisnika srecanja iz prepisa (PrebranZapisnik): kdo je igral katero
   tekmo, izid vsake tekme in ali je vse skupaj dovolj zanesljivo, da se zapise
   brez organizatorja (lastnik 26. 11. 2026: »samodejno, ce je zanesljivo«).

   Pravila krizcev: posamicno tekmo igra en igralec na stran, dvojice dva.
   - natanko toliko jasnih krizcev in nic nejasnega: zanesljivo;
   - nic krizcev: tekma ni bila igrana (ali so krizec pozabili - to pokaze potek);
   - sicer: predlagajo se najverjetnejsi (najprej krizci, nato nejasni), stran
     je negotova in jo pogleda organizator;
   - prazna vrstica kadra (nov igralec) je vedno negotova: igralca, ki ga v kadru
     ni, mora izbrati ali dodati organizator.
   Pocrnjen kvadratek je preklic (lastnik: napacen krizec se zapolni do crnega).

   Potek srecanja: pri pragu zmag (prvi do N) mora biti vsaka tekma do odlocitve
   odigrana do konca, vse za njo pa prazne; brez praga se igrajo vse. Neskladje je
   opozorilo, ne napaka - organizator vidi fotografijo in odloci.

   Postava (zacetna) se ne bere, ampak izpelje (ZapisZapisnikaStoritev): mesto A je
   igralec prve posamicne tekme z mestom A. Predlog zato preveri samo, da je vsako
   mesto mogoce dolociti in da isti igralec ne zaseda dveh mest. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import si.turnirko.branje.OdcitekZapisnika.StanjeKvadratka;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;

public final class PredlogZapisnika {

    private PredlogZapisnika() {}

    public enum StanjeTekme { ODIGRANA, NEODIGRANA, NEPOPOLNA }

    /* domaci/gost: vrstice kadra natisa (indeks; == velikost kadra je nov igralec). */
    public record Tekma(String oznaka, boolean dvojice, List<Integer> domaci, List<Integer> gost,
                        boolean negotovoDomaci, boolean negotovoGost, List<PrebranListek.Niz> nizi,
                        StanjeTekme stanje, int niziDomaci, int niziGost, boolean negotoveTocke) {}

    public record Predlog(List<Tekma> tekme, boolean zanesljiv, List<String> opozorila) {}

    /* kaderDomaci/kaderGost: stevilo igralcev kadra na natisu (brez prazne vrstice). */
    public static Predlog izracunaj(PrebranZapisnik prebrano, FormatSrecanja format, int nizov, Integer prag,
                                    int kaderDomaci, int kaderGost) {
        List<FormatSrecanja.MestoTekme> razpored = format.razpored();
        int zaZmago = nizov / 2 + 1;
        List<String> opozorila = new ArrayList<>();
        List<Tekma> tekme = new ArrayList<>();
        boolean negotovo = false;

        int zmageD = 0;
        int zmageG = 0;
        boolean odloceno = false;
        for (int i = 0; i < razpored.size() && i < prebrano.tekme().size(); i++) {
            FormatSrecanja.MestoTekme mesto = razpored.get(i);
            PrebranZapisnik.Tekma pt = prebrano.tekme().get(i);
            boolean dvojice = mesto.tip() == TipTekmeSrecanja.DVOJICE;
            int k = dvojice ? 2 : 1;
            Izbira d = izberi(pt.domaci(), k, kaderDomaci);
            Izbira g = izberi(pt.gost(), k, kaderGost);

            int nd = 0;
            int ng = 0;
            boolean tocke = false;
            boolean negotoveTocke = false;
            for (PrebranListek.Niz n : pt.nizi()) {
                if (n.levo() == null || n.desno() == null) {
                    continue;
                }
                tocke = true;
                negotoveTocke |= n.negotovoLevo() || n.negotovoDesno();
                if (n.levo() > n.desno()) {
                    nd++;
                } else {
                    ng++;
                }
            }
            for (PrebranListek.Niz n : pt.nizi()) {
                // polovicno prebran niz (ena celica) je vedno za pregled
                if ((n.levo() == null) != (n.desno() == null)) {
                    negotoveTocke = true;
                }
            }
            StanjeTekme stanje;
            if (Math.max(nd, ng) >= zaZmago) {
                stanje = StanjeTekme.ODIGRANA;
            } else if (!tocke && d.izbrani().isEmpty() && g.izbrani().isEmpty() && !d.negotovo() && !g.negotovo()) {
                stanje = StanjeTekme.NEODIGRANA;
            } else {
                stanje = StanjeTekme.NEPOPOLNA;
            }
            String oznaka = mesto.oznaka();
            if (odloceno) {
                if (stanje != StanjeTekme.NEODIGRANA) {
                    opozorila.add("Tekma " + oznaka + " je vpisana, čeprav je bilo srečanje že odločeno.");
                }
            } else if (stanje == StanjeTekme.NEODIGRANA) {
                opozorila.add("Tekma " + oznaka + " ni vpisana.");
            } else if (stanje == StanjeTekme.NEPOPOLNA) {
                opozorila.add("Tekma " + oznaka + " ni vpisana do konca (manjka niz ali igralec).");
            }
            if (stanje == StanjeTekme.ODIGRANA) {
                if (d.izbrani().size() != k || g.izbrani().size() != k) {
                    opozorila.add("Pri tekmi " + oznaka + " ni označeno, kdo je igral.");
                }
                negotovo |= d.negotovo() || g.negotovo() || negotoveTocke;
                if (nd > ng) {
                    zmageD++;
                } else {
                    zmageG++;
                }
                if (prag != null && !odloceno && (zmageD >= prag || zmageG >= prag)) {
                    odloceno = true;
                }
            } else if (stanje == StanjeTekme.NEPOPOLNA) {
                negotovo = true;
            }
            if (pt.opomba() != null && stanje != StanjeTekme.NEODIGRANA) {
                opozorila.add("Tekma " + oznaka + ": " + pt.opomba());
            }
            tekme.add(new Tekma(oznaka, dvojice, d.izbrani(), g.izbrani(), d.negotovo(), g.negotovo(),
                    pt.nizi(), stanje, nd, ng, negotoveTocke));
        }
        if (prebrano.tekme().size() != razpored.size()) {
            opozorila.add("Zapisnik ima drugačno število tekem, kot jih ima format srečanja.");
        }
        preveriMesta(razpored, tekme, true, opozorila);
        preveriMesta(razpored, tekme, false, opozorila);
        // po izboljsani poravnavi (OdcitekZapisnika.izboljsaj) so najdene skoraj vse;
        // ce jih ni, je list zvit ali delno zakrit in vsaka celica je pod vprasajem
        if (prebrano.vsehOznak() > 0 && prebrano.najdenihOznak() * 5 < prebrano.vsehOznak() * 4) {
            opozorila.add("Na fotografiji ni bilo mogoče najti vseh oznak vrst (črni kvadratki ob vrsticah). "
                    + "Preveri vse ali list fotografiraj znova.");
        }
        boolean zanesljiv = !negotovo && opozorila.isEmpty();
        return new Predlog(tekme, zanesljiv, opozorila);
    }

    /* Izbrani igralci ene strani tekme in ali je izbira negotova. */
    record Izbira(List<Integer> izbrani, boolean negotovo) {}

    static Izbira izberi(List<PrebranZapisnik.Krizec> kvadratki, int k, int kader) {
        List<PrebranZapisnik.Krizec> krizci = new ArrayList<>();
        List<PrebranZapisnik.Krizec> nejasni = new ArrayList<>();
        for (PrebranZapisnik.Krizec kr : kvadratki) {
            StanjeKvadratka s = StanjeKvadratka.valueOf(kr.stanje());
            if (s == StanjeKvadratka.KRIZEC) {
                krizci.add(kr);
            } else if (s == StanjeKvadratka.NEJASNO) {
                nejasni.add(kr);
            }
        }
        boolean negotovo;
        List<PrebranZapisnik.Krizec> izbrani;
        if (krizci.size() == k && nejasni.isEmpty()) {
            izbrani = krizci;
            negotovo = false;
        } else if (krizci.isEmpty() && nejasni.isEmpty()) {
            return new Izbira(List.of(), false);
        } else {
            // najprej jasni krizci (po crnilu navzdol), nato nejasni (blizje krizcu prej)
            List<PrebranZapisnik.Krizec> vsi = new ArrayList<>(krizci);
            vsi.sort(Comparator.comparingDouble(PrebranZapisnik.Krizec::crnilo).reversed());
            List<PrebranZapisnik.Krizec> ostali = new ArrayList<>(nejasni);
            ostali.sort(Comparator.comparingDouble(PredlogZapisnika::podobnostKrizcu).reversed());
            vsi.addAll(ostali);
            izbrani = vsi.subList(0, Math.min(k, vsi.size()));
            negotovo = true;
        }
        List<Integer> igralci = new ArrayList<>();
        for (PrebranZapisnik.Krizec kr : izbrani) {
            igralci.add(kr.igralec());
            if (kr.igralec() >= kader) {
                negotovo = true;      // nov igralec: izbere ga organizator
            }
        }
        igralci.sort(Integer::compareTo);
        return new Izbira(List.copyOf(igralci), negotovo);
    }

    /* Nejasen kvadratek: kako blizu je obmocju krizca (0-1). */
    private static double podobnostKrizcu(PrebranZapisnik.Krizec kr) {
        double c = kr.crnilo();
        return c < 0.06 ? c / 0.06 : 1 - Math.min(1, (c - 0.55) / 0.15);
    }

    /* Vsako mesto (A, B / X, Y) mora imeti igralca iz prve odigrane posamicne
       tekme s tem mestom, isti igralec pa ne sme zasesti dveh mest. */
    private static void preveriMesta(List<FormatSrecanja.MestoTekme> razpored, List<Tekma> tekme, boolean domaci,
                                     List<String> opozorila) {
        Map<String, Integer> mesta = new HashMap<>();
        Set<String> vsa = new HashSet<>();
        for (int i = 0; i < tekme.size(); i++) {
            FormatSrecanja.MestoTekme m = razpored.get(i);
            if (m.tip() != TipTekmeSrecanja.POSAMICNA) {
                continue;
            }
            String mesto = domaci ? m.domaci() : m.gost();
            vsa.add(mesto);
            Tekma t = tekme.get(i);
            List<Integer> igralci = domaci ? t.domaci() : t.gost();
            if (t.stanje() == StanjeTekme.ODIGRANA && igralci.size() == 1 && !mesta.containsKey(mesto)) {
                mesta.put(mesto, igralci.getFirst());
            }
        }
        for (String m : vsa.stream().sorted().toList()) {
            if (!mesta.containsKey(m)) {
                opozorila.add("Mesta " + m + " ni mogoče določiti: igralec na tem mestu ni odigral nobene tekme.");
            }
        }
        Set<Integer> zasedeni = new HashSet<>();
        for (Integer igralec : mesta.values()) {
            if (!zasedeni.add(igralec)) {
                opozorila.add("Isti igralec je " + (domaci ? "pri domačih" : "pri gostih")
                        + " na dveh mestih začetne postave. Preveri križce.");
                break;
            }
        }
    }
}
