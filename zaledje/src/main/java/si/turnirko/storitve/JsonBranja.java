/* JSON zapisov branja listkov (branje_listka.prebrano/potrjeno, zlati nabor).
   En pretvornik za vse: kar zapise ena storitev, mora druga prebrati enako.
   Neznana polja se preskocijo (starejsi in novejsi zapisi). */
package si.turnirko.storitve;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonBranja {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonBranja() {}

    static String zapisi(Object vrednost) {
        try {
            return JSON.writeValueAsString(vrednost);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /* Vrze IllegalStateException, ce zapis ni berljiv. */
    static <T> T preberi(String json, Class<T> tip) {
        try {
            return JSON.readValue(json, tip);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Zapis branja ni berljiv.", e);
        }
    }
}
