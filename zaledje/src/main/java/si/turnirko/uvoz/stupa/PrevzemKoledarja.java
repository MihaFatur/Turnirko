/* Prevzem praznega turnirja iz koledarja ob PRVEM uvozu dogodka iz Stupe
   (okt 2026). Koledar sezone (ntzs.si, klubski koledarji) vpise uradne turnirje
   vnaprej, se preden jih zveza objavi pri Stupi - kot prazne turnirje v
   pripravi. Uvoz bi ob prvem zapisu ustvaril svoj turnir in v seznamu bi stala
   dva. Prevzem prazni turnir poveze z dogodkom vira: id ostane (povezave, ki jih
   je kdo ze delil, delujejo naprej), uvoz pa mu zapise ime, datume, prizorisce
   in tekmovanja iz vira, kot vsakemu uvozenemu turnirju.

   Kandidat je turnir BREZ VIRA, V PRIPRAVI in BREZ DOGODKOV, ki se zacne najvec
   DNI_ODMIKA dni pred prvim ali po zadnjem dnevu dogodka pri viru (Stupa dneve
   vcasih premakne: 22.-23. 11. namesto 23. 11.). Na isti dan je pogosto vec
   kandidatov - OT za U-15 in OT za U-21 sta pri Stupi dva dogodka - zato
   predlog izbere po imenu: kategorija (U-15) se mora ujemati, nato stejejo vrsta
   tekmovanja (OT, TOP, DP, ekipno, kvalifikacije, clani, pokal - skupna za,
   le v enem imenu proti) in zaporedna stevilka. Kadar dveh kandidatov ni mogoce lociti, predloga ni in admin izbere
   sam. Odlocitev je vedno adminova; predogled samo predlaga. */
package si.turnirko.uvoz.stupa;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import si.turnirko.modeli.Turnir;

final class PrevzemKoledarja {

    static final int DNI_ODMIKA = 3;

    /* ujemanje < 0: imeni si nasprotujeta (druga kategorija ali zaporedna
       stevilka) - tak kandidat se ne predlaga nikoli. */
    record Kandidat(Turnir turnir, int ujemanje, long odmik) {}

    private static final Pattern KATEGORIJA = Pattern.compile("\\bU\\s*-?\\s*(\\d{1,2})\\b");
    private static final Pattern ZAPOREDNA = Pattern.compile("^\\s*(\\d{1,2})\\s*\\.");
    private static final Map<String, Pattern> VRSTE = Map.of(
            "OT", Pattern.compile("\\bOT\\b|ODPRTI TURNIR"),
            "TOP", Pattern.compile("\\bTOP\\b"),
            "DP", Pattern.compile("\\bDP\\b|DRŽAVNO PRVENSTVO"),
            "EKIPNO", Pattern.compile("\\bEKIPN|\\bEDP\\b"),
            "KVAL", Pattern.compile("\\bKVAL"),
            "CLAN", Pattern.compile("ČLAN"),
            "POKAL", Pattern.compile("POKAL"));

    private PrevzemKoledarja() {}

    /* Kandidati od najboljsega naprej: ujemanje imena, nato blizina datuma. */
    static List<Kandidat> razvrsti(String imeVira, LocalDate zacetekVira, List<Turnir> prazni) {
        return prazni.stream()
                .map(t -> new Kandidat(t, ujemanje(imeVira, t.getIme()),
                        Math.abs(ChronoUnit.DAYS.between(zacetekVira, t.getDatumZacetka()))))
                .sorted(Comparator.comparingInt((Kandidat k) -> -k.ujemanje())
                        .thenComparingLong(Kandidat::odmik)
                        .thenComparing(k -> k.turnir().getId()))
                .toList();
    }

    /* Predlagan kandidat ali null, kadar ga ni mogoce izbrati brez dvoma. */
    static Turnir predlog(List<Kandidat> razvrsceni) {
        if (razvrsceni.isEmpty() || razvrsceni.get(0).ujemanje() <= 0) {
            return null;
        }
        Kandidat prvi = razvrsceni.get(0);
        if (razvrsceni.size() > 1) {
            Kandidat drugi = razvrsceni.get(1);
            if (drugi.ujemanje() == prvi.ujemanje() && drugi.odmik() == prvi.odmik()) {
                return null;
            }
        }
        return prvi.turnir();
    }

    static int ujemanje(String vir, String koledar) {
        String a = vir == null ? "" : vir.toUpperCase(Locale.ROOT);
        String b = koledar == null ? "" : koledar.toUpperCase(Locale.ROOT);
        Set<String> katA = kategorije(a);
        Set<String> katB = kategorije(b);
        int tocke = 0;
        if (!katA.isEmpty() && !katB.isEmpty()) {
            Set<String> skupne = new HashSet<>(katA);
            skupne.retainAll(katB);
            if (skupne.isEmpty()) {
                return -1;
            }
            tocke += 10 * skupne.size();
        }
        Integer zapA = zaporedna(a);
        Integer zapB = zaporedna(b);
        if (zapA != null && zapB != null) {
            if (!zapA.equals(zapB)) {
                return -1;
            }
            tocke += 1;
        }
        // vrsta, ki jo ima samo eno ime, steje proti: "DP ZA U-19" pri viru je
        // DP in ne kvalifikacije za ekipno DP istega dne
        for (Pattern vrsta : VRSTE.values()) {
            boolean vA = vrsta.matcher(a).find();
            boolean vB = vrsta.matcher(b).find();
            if (vA && vB) {
                tocke += 2;
            } else if (vA != vB) {
                tocke -= 1;
            }
        }
        return tocke;
    }

    private static Set<String> kategorije(String ime) {
        Set<String> r = new HashSet<>();
        Matcher m = KATEGORIJA.matcher(ime);
        while (m.find()) {
            r.add(m.group(1));
        }
        return r;
    }

    private static Integer zaporedna(String ime) {
        Matcher m = ZAPOREDNA.matcher(ime);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
