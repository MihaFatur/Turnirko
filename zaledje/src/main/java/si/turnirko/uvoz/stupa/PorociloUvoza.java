/* Zbiralnik vsega, kar uvoz enega dogodka ugotovi: stevci, napake, opozorila,
   odlocitve o istovetnosti, novi igralci in preverbe uskladitve.

   Napaka in nerazresena odlocitev uvoz ustavita; opozorilo ne. Meja je
   namerna: kar bi v bazi pustilo napacen izid (tekma brez zmagovalca, oseba,
   ki bi jo zlepili z drugo), je napaka, kar pa je pri viru nepopolno in se
   da zapisati brez ugibanja (neveljavne tocke niza), je opozorilo. */
package si.turnirko.uvoz.stupa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import si.turnirko.dto.PorociloUvozaDto;

public class PorociloUvoza {

    private static final int NAJVEC_PRIMEROV = 12;

    private final Map<String, Integer> stevci = new LinkedHashMap<>();
    private final Map<String, List<String>> napake = new LinkedHashMap<>();
    private final Map<String, Integer> stNapak = new LinkedHashMap<>();
    private final Map<String, List<String>> opozorila = new LinkedHashMap<>();
    private final Map<String, Integer> stOpozoril = new LinkedHashMap<>();
    private final Map<Long, PorociloUvozaDto.Odlocitev> odlocitve = new LinkedHashMap<>();
    private final List<PorociloUvozaDto.NovIgralec> noviIgralci = new ArrayList<>();
    private final List<PorociloUvozaDto.Preverba> preverbe = new ArrayList<>();

    public void prestej(String kaj) {
        prestej(kaj, 1);
    }

    public void prestej(String kaj, int koliko) {
        if (koliko != 0) {
            stevci.merge(kaj, koliko, Integer::sum);
        }
    }

    public int stevec(String kaj) {
        return stevci.getOrDefault(kaj, 0);
    }

    public void napaka(String vrsta, String primer) {
        zabelezi(napake, stNapak, vrsta, primer);
    }

    public void opozori(String vrsta, String primer) {
        zabelezi(opozorila, stOpozoril, vrsta, primer);
    }

    public void odlocitev(PorociloUvozaDto.Odlocitev odlocitev) {
        odlocitve.putIfAbsent(odlocitev.idOsebe(), odlocitev);
    }

    public void novIgralec(PorociloUvozaDto.NovIgralec nov) {
        noviIgralci.add(nov);
    }

    /* Obvezna preverba: neujemanje ustavi uvoz. */
    public void preverba(String podrocje, String opis, boolean ujemanje, String podrobnosti) {
        preverbe.add(new PorociloUvozaDto.Preverba(podrocje, opis, ujemanje, true, podrobnosti));
    }

    /* Preverba razvrstitve: neujemanje admin vidi, uvoza pa ne ustavi. */
    public void preverbaRazvrstitve(String podrocje, String opis, boolean ujemanje, String podrobnosti) {
        preverbe.add(new PorociloUvozaDto.Preverba(podrocje, opis, ujemanje, false, podrobnosti));
    }

    /* Ali uvoz sme naprej: brez napak, brez nerazresenih odlocitev in brez
       obvezne preverbe, ki se z virom ne ujema. */
    public boolean dovoljuje() {
        return napake.isEmpty() && odlocitve.isEmpty()
                && preverbe.stream().allMatch(p -> p.ujemanje() || !p.obvezna());
    }

    public boolean imaNapake() {
        return !napake.isEmpty();
    }

    public PorociloUvozaDto vDto() {
        return new PorociloUvozaDto(new LinkedHashMap<>(stevci),
                ugotovitve(napake, stNapak), ugotovitve(opozorila, stOpozoril),
                List.copyOf(odlocitve.values()), List.copyOf(noviIgralci), List.copyOf(preverbe));
    }

    private static void zabelezi(Map<String, List<String>> primeri, Map<String, Integer> stevila,
                                 String vrsta, String primer) {
        stevila.merge(vrsta, 1, Integer::sum);
        List<String> seznam = primeri.computeIfAbsent(vrsta, k -> new ArrayList<>());
        if (primer != null && seznam.size() < NAJVEC_PRIMEROV) {
            seznam.add(primer);
        }
    }

    private static List<PorociloUvozaDto.Ugotovitev> ugotovitve(Map<String, List<String>> primeri,
                                                                Map<String, Integer> stevila) {
        return primeri.entrySet().stream()
                .map(e -> new PorociloUvozaDto.Ugotovitev(e.getKey(), stevila.get(e.getKey()), List.copyOf(e.getValue())))
                .toList();
    }
}
