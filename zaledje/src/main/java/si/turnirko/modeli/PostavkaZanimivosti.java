/* Razdelki zavihka »Zanimivosti«, ki jih organizator lahko skrije gledalcem
   (V51). Vsaka konstanta ustreza polju v StatistikaTekmovanjaDto (VZPONI je
   »vzponi«, PRVI_NASLOVI »prviNaslovi« ...). Imena gredo v bazo in v API, zato
   se konstante ne preimenujejo - kvečjemu dodajajo.

   Ne vsak razdelek velja za vsako tekmovanje: liga nima prvega naslova, turnir
   nima srečanj na nož, gostov in nosilcev, presenečenje in vzpon pa merita rating,
   ki ga tekmovanje, ki ne šteje v Turnirko rating, nima. Organizatorju ponudimo
   samo razdelke, ki na njegovem tekmovanju sploh lahko nastanejo - sicer bi mu
   nastavitev, ki nič ne pomeni, izgledala kot pomembna. */
package si.turnirko.modeli;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum PostavkaZanimivosti {

    STEVILKE,
    PRESENECENJE,
    OBRATI,
    NAJDALJSI_NIZ,
    NAJDALJSA_TEKMA,
    NA_NOZ,
    DVOJICA,
    PRVI_NASLOVI,
    VZPONI,
    ZID,
    DELAVCI,
    KLUBI,
    GOSTJE,
    NOSILCI;

    /* Razdelki, ki na tekmovanju lahko nastanejo, v vrstnem redu konstant (ta je
       tudi vrstni red v vmesniku). Liga ima srečanja, turnir dogodke z naslovi;
       rating pa je samo tam, kjer tekmovanje šteje. */
    public static List<PostavkaZanimivosti> zaTekmovanje(boolean jeLiga, boolean stejeVElo) {
        return Arrays.stream(values())
                .filter(p -> p.velja(jeLiga, stejeVElo))
                .toList();
    }

    private boolean velja(boolean jeLiga, boolean stejeVElo) {
        return switch (this) {
            case PRESENECENJE, VZPONI -> stejeVElo;
            case NA_NOZ, GOSTJE, NOSILCI -> jeLiga;
            case PRVI_NASLOVI -> !jeLiga;
            default -> true;
        };
    }

    /* Zapis v bazi: imena, ločena z vejico, po vrstnem redu konstant - isti nabor
       da vedno isti niz. Prazen nabor je NULL, kar pomeni »vse vidno« (privzetek
       za vsa tekmovanja, ki so nastala pred V51). */
    public static String vZapis(Collection<PostavkaZanimivosti> skrite) {
        if (skrite == null || skrite.isEmpty()) {
            return null;
        }
        return skrite.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /* Neznano ime se tiho izpusti: razdelek, ki je bil odstranjen, ne sme zaradi
       stare vrednosti v bazi zrušiti branja celega tekmovanja. */
    public static Set<PostavkaZanimivosti> izZapisa(String zapis) {
        Set<PostavkaZanimivosti> skrite = EnumSet.noneOf(PostavkaZanimivosti.class);
        if (zapis == null || zapis.isBlank()) {
            return skrite;
        }
        for (String ime : zapis.split(",")) {
            for (PostavkaZanimivosti p : values()) {
                if (p.name().equals(ime.trim())) {
                    skrite.add(p);
                }
            }
        }
        return skrite;
    }
}
