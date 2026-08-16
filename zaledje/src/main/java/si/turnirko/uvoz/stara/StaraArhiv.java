/* Branje pretvorjenega posnetka stare strani NTZS (stara.ntzs.si).

   Vir je WordPressov vtičnik in oddaja samo HTML. Razbiranje strani opravi
   skripta uvoz-stara-ntzs/pretvori.mjs in zapiše normaliziran JSON; uvoznik
   bere samo tega. Ločnica je namerna: razbiranje HTML je krhko delo, ki se ob
   vsaki spremembi vira spremeni, uvoz v bazo pa mora ostati stabilen in
   preverljiv - poleg tega tako v zaledju ni razčlenjevalnika HTML.

   Kot pri StupaArhiv delamo z drevesom JsonNode in ne z desetinami DTO
   razredov: enkratni uvoznik ne sme s sabo prinesti tolikega bremena. */
package si.turnirko.uvoz.stara;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

public class StaraArhiv {

    private final Path koren;
    private final ObjectMapper mapper = new ObjectMapper();

    public StaraArhiv(Path koren) {
        this.koren = koren;
        if (!Files.isDirectory(koren)) {
            throw new IllegalArgumentException("Mapa s pretvorjenim posnetkom ne obstaja: " + koren);
        }
    }

    public List<JsonNode> klubi() {
        return seznam(beri(koren.resolve("klubi.json")));
    }

    public List<JsonNode> igralci() {
        return seznam(beri(koren.resolve("igralci.json")));
    }

    /* Sezone, ki so v posnetku - v narascajocem vrstnem redu ("2012", "2013" ...). */
    public List<String> sezone() {
        try (Stream<Path> datoteke = Files.list(koren)) {
            return datoteke.map(p -> p.getFileName().toString())
                    .filter(v -> v.startsWith("turnirji-") && v.endsWith(".json"))
                    .map(v -> v.substring("turnirji-".length(), v.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Ne morem prebrati " + koren, e);
        }
    }

    public List<JsonNode> turnirji(String sezona) {
        return seznam(beri(koren.resolve("turnirji-" + sezona + ".json")));
    }

    public List<JsonNode> lige(String sezona) {
        return seznam(beri(koren.resolve("lige-" + sezona + ".json")));
    }

    /* Prvi datum lige - dan njenega prvega kola. Po njem se liga uvrsti med
       turnirje v skupnem casovnem zaporedju tekmovanj. */
    public static String zacetekLige(JsonNode liga) {
        return kola(liga).stream()
                .map(k -> k.path("datum").asText(null))
                .filter(d -> d != null && !d.isBlank())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public static List<JsonNode> kola(JsonNode liga) {
        return seznam(liga.path("kola"));
    }

    private JsonNode beri(Path pot) {
        if (!Files.isRegularFile(pot)) {
            return MissingNode.getInstance();
        }
        try {
            return mapper.readTree(Files.readString(pot));
        } catch (IOException e) {
            throw new UncheckedIOException("Ne morem prebrati " + pot, e);
        }
    }

    private static List<JsonNode> seznam(JsonNode vozlisce) {
        if (vozlisce == null || !vozlisce.isArray()) {
            return List.of();
        }
        List<JsonNode> r = new ArrayList<>(vozlisce.size());
        vozlisce.forEach(r::add);
        return r;
    }
}
