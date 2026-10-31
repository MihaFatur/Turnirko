/* Web Push brez knjiznice (WebPush): sifriranje po RFC 8291 mora z uradnimi
   vhodi iz dodatka A dati natanko uradni izhod - drugace ga potisna storitev
   (FCM, Mozilla, Apple) tiho zavrne ali naprava ne more odsifrirati, tega pa
   brez prave naprave ne bi opazili. */
package si.turnirko.pomozno;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class WebPushTest {

    // Dodatek A RFC 8291: vhodi in rezultat primera iz razdelka 5.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_JAVNI = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_ZASEBNI = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_JAVNI = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SOL = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String GLAVA = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SIFRIRANO = "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    private static byte[] b(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }

    @Test
    void sifriranjeDaUradniRezultatRfc8291() throws Exception {
        byte[] rezultat = WebPush.sifriraj(PLAINTEXT.getBytes(StandardCharsets.UTF_8), b(UA_JAVNI), b(AUTH),
                WebPush.zasebniKljuc(AS_ZASEBNI), b(AS_JAVNI), b(SOL));

        byte[] glava = b(GLAVA);
        byte[] sifrirano = b(SIFRIRANO);
        byte[] pricakovano = new byte[glava.length + sifrirano.length];
        System.arraycopy(glava, 0, pricakovano, 0, glava.length);
        System.arraycopy(sifrirano, 0, pricakovano, glava.length, sifrirano.length);

        assertEquals(86, glava.length, "glava je 86 oktetov");
        assertArrayEquals(pricakovano, rezultat);
    }

    @Test
    void sporociloPreDolgoZaEnZapisSeZavrne() {
        byte[] preDolgo = new byte[4000];
        assertThrows(IllegalArgumentException.class,
                () -> WebPush.sifriraj(preDolgo, UA_JAVNI, AUTH));
    }

    @Test
    void nakljucnoSifriranjeVsakicDaDrugoSporociloSEnakoGlavo() {
        byte[] a = WebPush.sifriraj("zivjo".getBytes(StandardCharsets.UTF_8), UA_JAVNI, AUTH);
        byte[] bb = WebPush.sifriraj("zivjo".getBytes(StandardCharsets.UTF_8), UA_JAVNI, AUTH);

        assertEquals(86 + "zivjo".length() + 1 + 16, a.length);
        assertTrue(!Arrays.equals(a, bb), "sol in efemerni kljuc sta nakljucna");
        // velikost zapisa 4096 (0x00001000) in dolzina id 65 sta v glavi na mestih 16-20
        assertArrayEquals(new byte[] {0, 0, 16, 0, 65}, Arrays.copyOfRange(a, 16, 21));
    }

    @Test
    void podpisVapidSePreveriZJavnimKljucem() throws Exception {
        String[] par = WebPush.novParKljucevVapid();
        assertEquals(65, WebPush.dekodiraj(par[0]).length, "javni kljuc je nestisnjena tocka");
        assertEquals(32, WebPush.dekodiraj(par[1]).length, "zasebni kljuc je 32 oktetov");

        PrivateKey zasebni = WebPush.zasebniKljuc(par[1]);
        String jwt = WebPush.vapidJwt("https://fcm.googleapis.com", "mailto:info@turnirko-nt.si", 1_900_000_000L, zasebni);
        String[] deli = jwt.split("\\.");
        assertEquals(3, deli.length);
        String telo = new String(b(deli[1]), StandardCharsets.UTF_8);
        assertTrue(telo.contains("\"aud\":\"https://fcm.googleapis.com\""), telo);
        assertTrue(telo.contains("\"sub\":\"mailto:info@turnirko-nt.si\""), telo);
        assertTrue(telo.contains("\"exp\":1900000000"), telo);

        PublicKey javni = WebPush.javniKljuc(b(par[0]));
        Signature preveri = Signature.getInstance("SHA256withECDSAinP1363Format");
        preveri.initVerify(javni);
        preveri.update((deli[0] + "." + deli[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(preveri.verify(b(deli[2])), "podpis ES256 (r || s) se ujema z javnim kljucem");
        assertEquals(64, b(deli[2]).length);
    }

    @Test
    void izvorNaslovaJeShemaInGostitelj() {
        assertEquals("https://fcm.googleapis.com", WebPush.izvor("https://fcm.googleapis.com/fcm/send/abc"));
        assertEquals("https://updates.push.services.mozilla.com:443",
                WebPush.izvor("https://updates.push.services.mozilla.com:443/wpush/v2/xyz"));
    }
}
