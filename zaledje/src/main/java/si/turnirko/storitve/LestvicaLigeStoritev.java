/* Izracun lestvic lige iz koncanih srecanj: ekipne (glavne) ter lestvic
   posameznikov in dvojic, ki tecejo SAMO po tekmah te lige.

   Kriteriji izenacenja ekipne lestvice (potrjen vrstni red): tocke -> medsebojni izid ->
   razlika posamicnih tekem -> razlika nizov -> ime. Medsebojni izid je
   izracunan parno (tocke, ki sta jih izenaceni ekipi osvojili druga proti
   drugi) - tocno za dvojno izenacenje, za vec ekip je hevristika (natancni
   mini-turnir je nadgradnja). Zadnji kriterij "razlika tock (zogic)" v v1 ni
   podprt, ker se za ligo tocke po zogicah ne vnasajo. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.LestvicaDvojiceDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LestvicaIgralcaLigeDto;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.repozitoriji.EkipaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

@Service
public class LestvicaLigeStoritev {

    private final LigaRepozitorij ligaRepozitorij;
    private final EkipaRepozitorij ekipaRepozitorij;
    private final SrecanjeRepozitorij srecanjeRepozitorij;
    private final TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;

    public LestvicaLigeStoritev(LigaRepozitorij ligaRepozitorij,
                                EkipaRepozitorij ekipaRepozitorij,
                                SrecanjeRepozitorij srecanjeRepozitorij,
                                TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij) {
        this.ligaRepozitorij = ligaRepozitorij;
        this.ekipaRepozitorij = ekipaRepozitorij;
        this.srecanjeRepozitorij = srecanjeRepozitorij;
        this.tekmaSrecanjaRepozitorij = tekmaSrecanjaRepozitorij;
    }

    @Transactional(readOnly = true)
    public List<LestvicaEkipeDto> lestvica(Long idLiga) {
        Liga liga = ligaRepozitorij.findById(idLiga)
                .orElseThrow(() -> new IllegalArgumentException("Liga ne obstaja: " + idLiga));

        Map<Long, Vrstica> agg = new LinkedHashMap<>();
        for (Ekipa e : ekipaRepozitorij.najdiZaLigo(idLiga)) {
            // prosta ekipa kluba nima - stolpec ostane prazen
            agg.put(e.getId(), new Vrstica(e.getId(), e.prikazanoIme(),
                    e.jeProsta() ? null : e.getKlub().getIme()));
        }

        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLiga);
        // medsebojni izid: hth[a][b] = tocke, ki jih je ekipa a osvojila proti b
        Map<Long, Map<Long, Integer>> hth = new HashMap<>();

        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.KONCANO) {
                continue;
            }
            Vrstica vd = agg.get(s.getEkipaDomaci().getId());
            Vrstica vg = agg.get(s.getEkipaGost().getId());
            if (vd == null || vg == null) {
                continue;
            }
            int dd = s.getDobljeneDomaci();
            int gg = s.getDobljeneGost();
            vd.odigrane++;
            vg.odigrane++;
            vd.dobljeneTekme += dd;
            vd.prejeteTekme += gg;
            vg.dobljeneTekme += gg;
            vg.prejeteTekme += dd;

            int tockeD;
            int tockeG;
            if (dd > gg) {
                vd.zmage++;
                vg.porazi++;
                tockeD = liga.getTockeZmaga();
                tockeG = liga.getTockePoraz();
            } else if (dd < gg) {
                vg.zmage++;
                vd.porazi++;
                tockeD = liga.getTockePoraz();
                tockeG = liga.getTockeZmaga();
            } else {
                vd.neodlocene++;
                vg.neodlocene++;
                tockeD = liga.getTockeNeodloceno();
                tockeG = liga.getTockeNeodloceno();
            }
            vd.tocke += tockeD;
            vg.tocke += tockeG;
            hth.computeIfAbsent(vd.id, k -> new HashMap<>()).merge(vg.id, tockeD, Integer::sum);
            hth.computeIfAbsent(vg.id, k -> new HashMap<>()).merge(vd.id, tockeG, Integer::sum);
        }

        // razlika nizov iz sestevka po srecanjih
        Map<Long, long[]> niziPoSrecanju = new HashMap<>();
        for (Object[] r : tekmaSrecanjaRepozitorij.niziPoSrecanjih(idLiga)) {
            niziPoSrecanju.put(((Number) r[0]).longValue(),
                    new long[] { ((Number) r[1]).longValue(), ((Number) r[2]).longValue() });
        }
        for (Srecanje s : srecanja) {
            if (s.getStatus() != StatusSrecanja.KONCANO) {
                continue;
            }
            long[] n = niziPoSrecanju.get(s.getId());
            if (n == null) {
                continue;
            }
            Vrstica vd = agg.get(s.getEkipaDomaci().getId());
            Vrstica vg = agg.get(s.getEkipaGost().getId());
            if (vd != null) {
                vd.dobljeniNizi += (int) n[0];
                vd.prejetiNizi += (int) n[1];
            }
            if (vg != null) {
                vg.dobljeniNizi += (int) n[1];
                vg.prejetiNizi += (int) n[0];
            }
        }

        List<Vrstica> vrstice = new ArrayList<>(agg.values());
        vrstice.sort((x, y) -> {
            if (x.tocke != y.tocke) {
                return Integer.compare(y.tocke, x.tocke);
            }
            int xh = hth.getOrDefault(x.id, Map.of()).getOrDefault(y.id, 0);
            int yh = hth.getOrDefault(y.id, Map.of()).getOrDefault(x.id, 0);
            if (xh != yh) {
                return Integer.compare(yh, xh);
            }
            int xrt = x.dobljeneTekme - x.prejeteTekme;
            int yrt = y.dobljeneTekme - y.prejeteTekme;
            if (xrt != yrt) {
                return Integer.compare(yrt, xrt);
            }
            int xrn = x.dobljeniNizi - x.prejetiNizi;
            int yrn = y.dobljeniNizi - y.prejetiNizi;
            if (xrn != yrn) {
                return Integer.compare(yrn, xrn);
            }
            return x.ekipa.compareToIgnoreCase(y.ekipa);
        });

        int stEkip = vrstice.size();
        int mejaIzpada = stEkip - liga.getStIzpade() + 1; // mesto, od katerega naprej se izpade
        List<LestvicaEkipeDto> rezultat = new ArrayList<>();
        for (int i = 0; i < vrstice.size(); i++) {
            Vrstica v = vrstice.get(i);
            int mesto = i + 1;
            String cona = null;
            if (liga.getStNapreduje() > 0 && mesto <= liga.getStNapreduje()) {
                cona = "NAPREDUJE";
            } else if (liga.getStIzpade() > 0 && mesto >= mejaIzpada) {
                cona = "IZPADE";
            }
            rezultat.add(new LestvicaEkipeDto(
                    mesto, v.id, v.ekipa, v.klub,
                    v.odigrane, v.zmage, v.neodlocene, v.porazi,
                    v.dobljeneTekme, v.prejeteTekme, v.dobljeneTekme - v.prejeteTekme,
                    v.dobljeniNizi, v.prejetiNizi, v.dobljeniNizi - v.prejetiNizi,
                    v.tocke, cona));
        }
        return rezultat;
    }

    /* Bilanca posamicnih tekem v eni ligi, po EKIPI in igralcu. Sluzi prikazu
       kadra pod vrstico lestvice, kjer pove, koliko je posameznik prinesel
       prav tej ekipi - zato je kljuc par in ne sam igralec: liga brez prepovedi
       dvojne registracije sme istega igralca voditi v dveh kadrih, njegov
       izkupicek pa tam ni isti. Igralec brez odigrane tekme v zemljevidu ne
       nastopa; BilanceLige ga vrne kot 0 : 0. */
    @Transactional(readOnly = true)
    public BilanceLige bilancePosamicnih(Long idLiga) {
        Map<Long, Map<Long, int[]>> zbir = new HashMap<>();
        for (Object[] r : tekmaSrecanjaRepozitorij.posamicniIzidiLige(idLiga)) {
            Long ekipaDomaci = ((Number) r[0]).longValue();
            Long ekipaGost = ((Number) r[1]).longValue();
            Long domaci = ((Number) r[2]).longValue();
            Long gost = ((Number) r[3]).longValue();
            boolean zmagalDomaci = r[4] == StranEkipe.DOMACI;
            zbir.computeIfAbsent(ekipaDomaci, k -> new HashMap<>())
                    .computeIfAbsent(domaci, k -> new int[2])[zmagalDomaci ? 0 : 1]++;
            zbir.computeIfAbsent(ekipaGost, k -> new HashMap<>())
                    .computeIfAbsent(gost, k -> new int[2])[zmagalDomaci ? 1 : 0]++;
        }
        Map<Long, Map<Long, Bilanca>> bilance = new HashMap<>();
        zbir.forEach((idEkipa, poIgralcih) -> {
            Map<Long, Bilanca> zaEkipo = new HashMap<>();
            poIgralcih.forEach((idIgralec, z) -> zaEkipo.put(idIgralec, new Bilanca(z[0], z[1])));
            bilance.put(idEkipa, zaEkipo);
        });
        return new BilanceLige(bilance);
    }

    /* Bilance ene lige z iskanjem po paru (ekipa, igralec). */
    public record BilanceLige(Map<Long, Map<Long, Bilanca>> poEkipah) {
        public Bilanca za(Long idEkipa, Long idIgralec) {
            return poEkipah.getOrDefault(idEkipa, Map.of()).getOrDefault(idIgralec, Bilanca.PRAZNA);
        }
    }

    public record Bilanca(int zmage, int porazi) {
        public static final Bilanca PRAZNA = new Bilanca(0, 0);
    }

    // ---------- Lestvici posameznikov in dvojic v ligi ----------

    /* Najboljsi posamezniki te lige. Steje SAMO posamicne tekme te lige - ne
       ratinga (ta tece cez vsa tekmovanja) in ne dvojic (izida para ni mogoce
       pripisati enemu igralcu, zato imajo svojo lestvico).

       Igralec brez odigrane tekme na lestvici ne nastopa: kdo je v kadru, pove
       kader pod vrstico ekipe, tu pa vrstica brez tekme nima izkupicka. */
    @Transactional(readOnly = true)
    public List<LestvicaIgralcaLigeDto> lestvicaIgralcev(Long idLiga) {
        Map<Long, Izkupicek> po = new LinkedHashMap<>();
        for (TekmaSrecanja t : tekmaSrecanjaRepozitorij.najdiPosamicneLige(idLiga)) {
            boolean zmagalDomaci = t.getZmagovalecStran() == StranEkipe.DOMACI;
            Srecanje s = t.getSrecanje();
            po.computeIfAbsent(t.getIgralecDomaci().getId(),
                            k -> new Izkupicek(List.of(t.getIgralecDomaci())))
                    .dodaj(s.getEkipaDomaci().prikazanoIme(), zmagalDomaci,
                            t.getDobljeniNiziDomaci(), t.getDobljeniNiziGost());
            po.computeIfAbsent(t.getIgralecGost().getId(),
                            k -> new Izkupicek(List.of(t.getIgralecGost())))
                    .dodaj(s.getEkipaGost().prikazanoIme(), !zmagalDomaci,
                            t.getDobljeniNiziGost(), t.getDobljeniNiziDomaci());
        }

        List<Izkupicek> vrstice = new ArrayList<>(po.values());
        vrstice.sort(PO_IZKUPICKU);
        List<LestvicaIgralcaLigeDto> rezultat = new ArrayList<>(vrstice.size());
        for (int i = 0; i < vrstice.size(); i++) {
            Izkupicek v = vrstice.get(i);
            Igralec igralec = v.igralci.get(0);
            rezultat.add(new LestvicaIgralcaLigeDto(
                    i + 1, igralec.getId(), igralec.polnoIme(), v.ekipa(),
                    v.odigrane, v.zmage, v.porazi, v.odstotek(),
                    v.niziZa, v.niziProti));
        }
        return rezultat;
    }

    /* Najboljse dvojice te lige. Enota je par in ne igralec, zato se izid pripise
       obema skupaj; ista dva igralca sta ista dvojica tudi, ko igrata v gosteh
       (glej Par). Tekma, pri kateri par ni v celoti postavljen, v poizvedbo ne
       pride - dvojice brez obeh imen ni. */
    @Transactional(readOnly = true)
    public List<LestvicaDvojiceDto> lestvicaDvojic(Long idLiga) {
        Map<Par, Izkupicek> po = new LinkedHashMap<>();
        for (TekmaSrecanja t : tekmaSrecanjaRepozitorij.najdiDvojiceLige(idLiga)) {
            boolean zmagalDomaci = t.getZmagovalecStran() == StranEkipe.DOMACI;
            Srecanje s = t.getSrecanje();
            dodajPar(po, t.getIgralecDomaci(), t.getIgralecDomaci2(),
                    s.getEkipaDomaci().prikazanoIme(), zmagalDomaci,
                    t.getDobljeniNiziDomaci(), t.getDobljeniNiziGost());
            dodajPar(po, t.getIgralecGost(), t.getIgralecGost2(),
                    s.getEkipaGost().prikazanoIme(), !zmagalDomaci,
                    t.getDobljeniNiziGost(), t.getDobljeniNiziDomaci());
        }

        List<Izkupicek> vrstice = new ArrayList<>(po.values());
        vrstice.sort(PO_IZKUPICKU);
        List<LestvicaDvojiceDto> rezultat = new ArrayList<>(vrstice.size());
        for (int i = 0; i < vrstice.size(); i++) {
            Izkupicek v = vrstice.get(i);
            Igralec prvi = v.igralci.get(0);
            Igralec drugi = v.igralci.get(1);
            rezultat.add(new LestvicaDvojiceDto(
                    i + 1,
                    prvi.getId(), prvi.polnoIme(),
                    drugi.getId(), drugi.polnoIme(),
                    v.ekipa(), v.odigrane, v.zmage, v.porazi, v.odstotek(),
                    v.niziZa, v.niziProti));
        }
        return rezultat;
    }

    private static void dodajPar(Map<Par, Izkupicek> po, Igralec a, Igralec b,
                                 String ekipa, boolean zmaga, int za, int proti) {
        po.computeIfAbsent(Par.iz(a, b), k -> new Izkupicek(vrstniRedVParu(a, b)))
                .dodaj(ekipa, zmaga, za, proti);
    }

    /* Par se zapise vedno enako - abecedno, ne po strani ali mestu v postavi. */
    private static List<Igralec> vrstniRedVParu(Igralec a, Igralec b) {
        return a.abecedno().compareToIgnoreCase(b.abecedno()) <= 0
                ? List.of(a, b) : List.of(b, a);
    }

    /* Merilo obeh lestvic: najprej zmage, ob izenacenju uspesnost, nato razlika
       nizov in ime. Uspesnost je drugo in ne prvo merilo, ker liga ni turnir -
       kdor je odigral vec kol, mora stati pred tistim, ki ima 100 % iz ene same
       tekme. Deleza primerjamo navzkrizno (zmage x tuje odigrane), da o vrstnem
       redu ne odloca zaokrozeni odstotek iz prikaza. */
    private static final Comparator<Izkupicek> PO_IZKUPICKU = (x, y) -> {
        if (x.zmage != y.zmage) {
            return Integer.compare(y.zmage, x.zmage);
        }
        long xu = (long) x.zmage * y.odigrane;
        long yu = (long) y.zmage * x.odigrane;
        if (xu != yu) {
            return Long.compare(yu, xu);
        }
        int xr = x.niziZa - x.niziProti;
        int yr = y.niziZa - y.niziProti;
        if (xr != yr) {
            return Integer.compare(yr, xr);
        }
        return x.ime.compareToIgnoreCase(y.ime);
    };

    /* Kljuc dvojice: ista dva igralca sta ista dvojica ne glede na to, na kateri
       strani sta igrala in v kaksnem zaporedju sta zapisana v postavi - zato sta
       identifikatorja urejena po velikosti. */
    private record Par(Long manjsi, Long vecji) {
        static Par iz(Igralec a, Igralec b) {
            return a.getId() <= b.getId()
                    ? new Par(a.getId(), b.getId())
                    : new Par(b.getId(), a.getId());
        }
    }

    /* Zbir nastopov ene tekmovalne enote v eni ligi: igralca (en clan) ali
       dvojice (dva). Ekip je lahko vec, kadar liga dovoli dvojno registracijo,
       zato jih stejemo in vrstica pokaze najpogostejso. */
    private static final class Izkupicek {
        final List<Igralec> igralci;
        final String ime;
        final Map<String, Integer> ekipe = new LinkedHashMap<>();
        int odigrane;
        int zmage;
        int porazi;
        int niziZa;
        int niziProti;

        Izkupicek(List<Igralec> igralci) {
            this.igralci = igralci;
            this.ime = igralci.stream().map(Igralec::polnoIme).collect(Collectors.joining(" / "));
        }

        void dodaj(String ekipa, boolean zmaga, int za, int proti) {
            odigrane++;
            if (zmaga) {
                zmage++;
            } else {
                porazi++;
            }
            niziZa += za;
            niziProti += proti;
            ekipe.merge(ekipa, 1, Integer::sum);
        }

        String ekipa() {
            return ekipe.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        int odstotek() {
            return odigrane == 0 ? 0 : Math.round(zmage * 100f / odigrane);
        }
    }

    /* Zbir za eno ekipo med izracunom lestvice. */
    private static final class Vrstica {
        final Long id;
        final String ekipa;
        final String klub;
        int odigrane;
        int zmage;
        int neodlocene;
        int porazi;
        int dobljeneTekme;
        int prejeteTekme;
        int dobljeniNizi;
        int prejetiNizi;
        int tocke;

        Vrstica(Long id, String ekipa, String klub) {
            this.id = id;
            this.ekipa = ekipa;
            this.klub = klub;
        }
    }
}
