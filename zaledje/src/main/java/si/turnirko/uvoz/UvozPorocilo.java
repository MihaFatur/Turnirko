/* Porocilo o uvozu: stevci, opozorila in seznami za rocni pregled.

   Uvoz tujih podatkov brez porocila je slepa vera. Vsak podatek, ki ga
   uvoznik ni znal preslikati (nerazdeljeno ime, manjkajoc datum rojstva,
   nepodprta disciplina), mora priti ven poimensko - sicer se izgubi tiho. */
package si.turnirko.uvoz;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UvozPorocilo {

    private final Map<String, Integer> stevci = new LinkedHashMap<>();
    private final Map<String, List<String>> opozorila = new LinkedHashMap<>();

    public void prestej(String kaj) {
        prestej(kaj, 1);
    }

    public void prestej(String kaj, int koliko) {
        stevci.merge(kaj, koliko, Integer::sum);
    }

    /* Opozorilo se vedno nosi svoj primer (ime, id), ne le vrsto tezave -
       drugace ga ni mogoce preveriti. */
    public void opozori(String vrsta, String primer) {
        opozorila.computeIfAbsent(vrsta, k -> new ArrayList<>()).add(primer);
    }

    public int stevec(String kaj) {
        return stevci.getOrDefault(kaj, 0);
    }

    public String povzetek() {
        StringBuilder s = new StringBuilder();
        s.append("\n=== UVOZ ZGODOVINE NTZS: POVZETEK ===\n");
        stevci.forEach((k, v) -> s.append(String.format("  %-46s %6d%n", k, v)));
        if (!opozorila.isEmpty()) {
            s.append("\n=== OPOZORILA ===\n");
            opozorila.forEach((vrsta, primeri) -> {
                s.append(String.format("  %-46s %6d%n", vrsta, primeri.size()));
                primeri.stream().limit(5).forEach(p -> s.append("        - ").append(p).append('\n'));
                if (primeri.size() > 5) {
                    s.append("        ... in se ").append(primeri.size() - 5).append('\n');
                }
            });
        }
        return s.toString();
    }

    /* Zapise vsa opozorila v datoteko, da jih je mogoce pregledati v celoti
       (v izpisu so namenoma le prvi primeri vsake vrste). */
    public void zapisi(Path datoteka) {
        StringBuilder s = new StringBuilder();
        s.append("vrsta;primer\n");
        opozorila.forEach((vrsta, primeri) ->
                primeri.forEach(p -> s.append(vrsta).append(';').append(p.replace(';', ',')).append('\n')));
        try {
            Files.createDirectories(datoteka.getParent());
            Files.writeString(datoteka, s.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
