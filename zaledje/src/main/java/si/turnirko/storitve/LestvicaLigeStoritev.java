/* Izracun lestvice lige iz koncanih srecanj.

   Kriteriji izenacenja (potrjen vrstni red): tocke -> medsebojni izid ->
   razlika posamicnih tekem -> razlika nizov -> ime. Medsebojni izid je
   izracunan parno (tocke, ki sta jih izenaceni ekipi osvojili druga proti
   drugi) - tocno za dvojno izenacenje, za vec ekip je hevristika (natancni
   mini-turnir je nadgradnja). Zadnji kriterij "razlika tock (zogic)" v v1 ni
   podprt, ker se za ligo tocke po zogicah ne vnasajo. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.modeli.Ekipa;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StranEkipe;
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
            agg.put(e.getId(), new Vrstica(e.getId(), e.prikazanoIme(), e.getKlub().getIme()));
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

    /* Bilanca posamicnih tekem vsakega igralca v eni ligi (kljuc je id igralca).
       Sluzi prikazu kadra pod vrstico lestvice: vrstni red kadra je organizatorjev,
       bilanca pa pove, koliko je posameznik ligi dejansko prinesel. Igralec brez
       odigrane tekme v zemljevidu ne nastopa - klicatelj ga steje kot 0 : 0. */
    @Transactional(readOnly = true)
    public Map<Long, Bilanca> bilancePosamicnih(Long idLiga) {
        Map<Long, int[]> zbir = new HashMap<>();
        for (Object[] r : tekmaSrecanjaRepozitorij.posamicniIzidiLige(idLiga)) {
            Long domaci = ((Number) r[0]).longValue();
            Long gost = ((Number) r[1]).longValue();
            boolean zmagalDomaci = r[2] == StranEkipe.DOMACI;
            zbir.computeIfAbsent(domaci, k -> new int[2])[zmagalDomaci ? 0 : 1]++;
            zbir.computeIfAbsent(gost, k -> new int[2])[zmagalDomaci ? 1 : 0]++;
        }
        Map<Long, Bilanca> bilance = new HashMap<>();
        zbir.forEach((id, z) -> bilance.put(id, new Bilanca(z[0], z[1])));
        return bilance;
    }

    public record Bilanca(int zmage, int porazi) {
        public static final Bilanca PRAZNA = new Bilanca(0, 0);
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
