/* Web Push brez knjiznice: sifriranje sporocila (RFC 8291, aes128gcm iz RFC 8188)
   in podpis VAPID (RFC 8292, ES256). Samo toliko, kolikor potrebuje
   PotisnaObvestilaStoritev; vse s kriptografijo, ki je ze v JDK.

   Zakaj brez knjiznice: odvisnost (web-push + Bouncy Castle) bi vlekla celo
   kriptografsko knjiznico zaradi stotih vrstic, ki jih pokrije JDK, sifriranje pa
   je preverjeno z uradnim testnim vektorjem iz dodatka A RFC 8291 (WebPushTest) -
   isti vhodi morajo dati natanko isti izhod, bajt za bajt.

   Pasti formata (prvi poskus jih naredi vse):
   - javni kljuc je 65 oktetov (0x04 || X || Y), zasebni 32 oktetov, koordinate so
     brez vodilnih ničel zapisane kot 32 oktetov;
   - IKM se izpelje s HKDF iz auth skrivnosti (ne iz soli), CEK in NONCE sta nato
     izpeljana iz soli - dvakrat Extract/Expand z razlicnimi vhodi;
   - zadnji zapis nosi razmejitveni oktet 0x02; sporocilo je en zapis (velikost
     4096, torej telo do ~3993 oktetov - za obvestilo je vec kot dovolj);
   - podpis JWT je IEEE P1363 (r || s, 64 oktetov), ne DER, ki ga JDK izda
     privzeto. */
package si.turnirko.pomozno;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class WebPush {

    private static final SecureRandom NAKLJUCJE = new SecureRandom();
    private static final int VELIKOST_ZAPISA = 4096;
    private static final int NAJVEC_TELO = VELIKOST_ZAPISA - 16 - 1 - 86;

    private static final ECParameterSpec KRIVULJA = krivulja();

    private static final HttpClient ODJEMALEC = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private WebPush() {}

    // ---------- Sifriranje (RFC 8291) ----------

    /* Sifrirano sporocilo za napravo z danim javnim kljucem in auth skrivnostjo (oboje base64url,
       kot ju vrne PushSubscription). Nov par kljucev in sol sta nakljucna pri vsakem klicu. */
    public static byte[] sifriraj(byte[] telo, String p256dh, String auth) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), NAKLJUCJE);
            KeyPair par = generator.generateKeyPair();
            byte[] sol = new byte[16];
            NAKLJUCJE.nextBytes(sol);
            return sifriraj(telo, dekodiraj(p256dh), dekodiraj(auth), par.getPrivate(),
                    nestisni((ECPublicKey) par.getPublic()), sol);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Sifriranja potisnega sporocila ni bilo mogoce", e);
        }
    }

    /* Isti postopek z danimi vhodi: tako ga je mogoce primerjati z uradnim testnim vektorjem. */
    static byte[] sifriraj(byte[] telo, byte[] uaJavni, byte[] authSkrivnost, PrivateKey asZasebni,
                           byte[] asJavni, byte[] sol) throws GeneralSecurityException {
        if (telo.length > NAJVEC_TELO) {
            throw new IllegalArgumentException("Sporocilo je predolgo za en zapis (" + telo.length + " oktetov).");
        }
        KeyAgreement dogovor = KeyAgreement.getInstance("ECDH");
        dogovor.init(asZasebni);
        dogovor.doPhase(javniKljuc(uaJavni), true);
        byte[] ecdh = dogovor.generateSecret();

        byte[] prkKljuc = hmac(authSkrivnost, ecdh);
        byte[] keyInfo = zdruzi("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaJavni, asJavni);
        byte[] ikm = hmac(prkKljuc, zdruzi(keyInfo, new byte[] {1}));

        byte[] prk = hmac(sol, ikm);
        byte[] cek = Arrays.copyOf(
                hmac(prk, zdruzi("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(
                hmac(prk, zdruzi("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);

        Cipher sifra = Cipher.getInstance("AES/GCM/NoPadding");
        sifra.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] sifrirano = sifra.doFinal(zdruzi(telo, new byte[] {2}));

        // glava RFC 8188: sol (16) || velikost zapisa (4) || dolzina id (1) || id = javni kljuc (65)
        ByteBuffer glava = ByteBuffer.allocate(16 + 4 + 1 + asJavni.length);
        glava.put(sol).putInt(VELIKOST_ZAPISA).put((byte) asJavni.length).put(asJavni);
        return zdruzi(glava.array(), sifrirano);
    }

    // ---------- Podpis VAPID (RFC 8292) ----------

    /* JWT, s katerim se streznik predstavi potisni storitvi: `aud` je izvor naslova
       naprave, `sub` stik za primer tezav. */
    public static String vapidJwt(String izvor, String subjekt, long potekSekunde, PrivateKey zasebni) {
        try {
            String glava = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String telo = b64(("{\"aud\":\"" + izvor + "\",\"exp\":" + potekSekunde + ",\"sub\":\"" + subjekt + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            Signature podpis = Signature.getInstance("SHA256withECDSAinP1363Format");
            podpis.initSign(zasebni);
            podpis.update((glava + "." + telo).getBytes(StandardCharsets.US_ASCII));
            return glava + "." + telo + "." + b64(podpis.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Podpisa VAPID ni bilo mogoce narediti", e);
        }
    }

    /* Izvor naslova (shema + gostitelj + vrata) za `aud`. */
    public static String izvor(String naslov) {
        URI uri = URI.create(naslov);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    // ---------- Posiljanje ----------

    /* Odgovor potisne storitve: 201 = sprejeto, 404 in 410 = naprava ne obstaja vec (naročnino
       je treba pobrisati), drugo = napaka. */
    public record Odgovor(int koda, String telo) {
        public boolean jeSprejeto() {
            return koda >= 200 && koda < 300;
        }

        public boolean jePotekla() {
            return koda == 404 || koda == 410;
        }
    }

    public static Odgovor poslji(String endpoint, byte[] sifrirano, String vapidJwt, String vapidJavni, int ttlSekund) {
        try {
            HttpRequest zahteva = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(ttlSekund))
                    .header("Urgency", "normal")
                    .header("Authorization", "vapid t=" + vapidJwt + ", k=" + vapidJavni)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(sifrirano))
                    .build();
            HttpResponse<String> odgovor = ODJEMALEC.send(zahteva, HttpResponse.BodyHandlers.ofString());
            return new Odgovor(odgovor.statusCode(), odgovor.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Odgovor(0, "prekinjeno");
        } catch (java.io.IOException e) {
            return new Odgovor(0, e.getMessage());
        }
    }

    // ---------- Kljuci ----------

    public static PrivateKey zasebniKljuc(String base64url) {
        try {
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, dekodiraj(base64url)), KRIVULJA));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Neveljaven zasebni kljuc VAPID", e);
        }
    }

    /* Javni kljuc iz zapisa 0x04 || X || Y. */
    static PublicKey javniKljuc(byte[] nestisnjen) throws GeneralSecurityException {
        if (nestisnjen.length != 65 || nestisnjen[0] != 4) {
            throw new IllegalArgumentException("Javni kljuc mora biti nestisnjena tocka P-256 (65 oktetov).");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(nestisnjen, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(nestisnjen, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), KRIVULJA));
    }

    /* Nov par kljucev VAPID (javni, zasebni; base64url) za nastavitev streznika. */
    public static String[] novParKljucevVapid() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), NAKLJUCJE);
            KeyPair par = generator.generateKeyPair();
            byte[] d = ((java.security.interfaces.ECPrivateKey) par.getPrivate()).getS().toByteArray();
            return new String[] {
                    b64(nestisni((ECPublicKey) par.getPublic())),
                    b64(poravnaj32(d))
            };
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Pomozno ----------

    static byte[] nestisni(ECPublicKey kljuc) {
        ECPoint t = kljuc.getW();
        return zdruzi(new byte[] {4}, poravnaj32(t.getAffineX().toByteArray()), poravnaj32(t.getAffineY().toByteArray()));
    }

    /* BigInteger.toByteArray() doda vodilni 0 ali izpusti ničle: zapis mora biti natanko 32 oktetov. */
    private static byte[] poravnaj32(byte[] vrednost) {
        if (vrednost.length == 32) {
            return vrednost;
        }
        byte[] izhod = new byte[32];
        if (vrednost.length > 32) {
            System.arraycopy(vrednost, vrednost.length - 32, izhod, 0, 32);
        } else {
            System.arraycopy(vrednost, 0, izhod, 32 - vrednost.length, vrednost.length);
        }
        return izhod;
    }

    private static byte[] hmac(byte[] kljuc, byte[] podatki) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(kljuc, "HmacSHA256"));
        return mac.doFinal(podatki);
    }

    private static byte[] zdruzi(byte[]... deli) {
        int dolzina = 0;
        for (byte[] d : deli) {
            dolzina += d.length;
        }
        byte[] izhod = new byte[dolzina];
        int mesto = 0;
        for (byte[] d : deli) {
            System.arraycopy(d, 0, izhod, mesto, d.length);
            mesto += d.length;
        }
        return izhod;
    }

    static String b64(byte[] podatki) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(podatki);
    }

    static byte[] dekodiraj(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }

    private static ECParameterSpec krivulja() {
        try {
            AlgorithmParameters parametri = AlgorithmParameters.getInstance("EC");
            parametri.init(new ECGenParameterSpec("secp256r1"));
            return parametri.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
