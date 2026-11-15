/* Rocno podpisan Stripov dogodek za teste placil (isti obrazec kot pravi
   Stripe; skrivnost je turnirko.stripe.webhook-skrivnost iz
   application-test.properties). Testi ne klicejo Stripa. */
package si.turnirko.storitve;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class TestniStripeDogodek {

    private static final String SKRIVNOST = "whsec_testna_skrivnost";

    private TestniStripeDogodek() {}

    static String podpisi(String telo) {
        long trenutek = Instant.now().getEpochSecond();
        String podpisano = trenutek + "." + telo;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SKRIVNOST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String podpis = HexFormat.of().formatHex(mac.doFinal(podpisano.getBytes(StandardCharsets.UTF_8)));
            return "t=" + trenutek + ",v1=" + podpis;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /* checkout.session.completed z danimi metapodatki; id narocnine in stranke
       sta izpeljana iz oznake ("x" -> cs_x, sub_x, cus_x). */
    static String zakljucenaSeja(String oznaka, Map<String, String> metapodatki) {
        String meta = metapodatki.entrySet().stream()
                .map(v -> "\"" + v.getKey() + "\": \"" + v.getValue() + "\"")
                .collect(Collectors.joining(", "));
        return """
                {
                  "id": "evt_%1$s",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "cs_%1$s",
                      "object": "checkout.session",
                      "customer": "cus_%1$s",
                      "subscription": "sub_%1$s",
                      "metadata": { %2$s }
                    }
                  }
                }
                """.formatted(oznaka, meta);
    }

    static Map<String, String> registracija(String zeton, String paket) {
        return Map.of("namen", "registracija", "registracija", zeton, "paket", paket,
                "ciklus", "MESECNO", "starejsiOd21", "true");
    }
}
