/* Webhook je edini vir resnice o placilu (PlacilaStoritev): racun in
   narocnina nastanejo SELE, ko Stripe potrdi "checkout.session.completed" -
   ne prej. Dogodek tu rocno podpisemo z isto skrivnostjo, ki jo ima test
   profil (turnirko.stripe.webhook-skrivnost), namesto da bi klicali pravi
   Stripe API (testi ne smejo biti odvisni od omrezja/pravih kljucev). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlacilaStoritevTest {

    /* Mora se ujemati z turnirko.stripe.webhook-skrivnost v application-test.properties. */
    private static final String SKRIVNOST = "whsec_testna_skrivnost";

    @Autowired private PlacilaStoritev placila;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @Test
    void neveljavenPodpisZavrne() {
        String telo = dogodekRegistracija("evt_1", "cs_1", "cus_1", "sub_1",
                "Ana", "Novak", "ana@test.si", "hash", "false", "2000-01-01", "",
                "PREMIUM", "MESECNO", "false");
        String glava = "t=" + Instant.now().getEpochSecond() + ",v1=neveljaven";
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.obdelajDogodek(telo, glava));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isEmpty(),
                "brez veljavnega podpisa se racun ne sme ustvariti");
    }

    /* Jedro obljube: uspesno placilo ustvari racun (ne prej, glej primerjavo
       spodaj) IN narocnino, z geslom, ki je bilo zgosceno ze OB ZACETKU
       placila (registrirajPoPlacilu ga ne sme znova zgostiti). */
    @Test
    void uspesnoPlaciloUstvariRacunInNarocnino() {
        String telo = dogodekRegistracija("evt_2", "cs_2", "cus_2", "sub_2",
                "Jan", "Kranjc", "jan@test.si", "$2a$10$zgoscenoGeslo", "false",
                "2010-05-01", "", "PREMIUM", "MESECNO", "true");

        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("jan@test.si").isEmpty(),
                "racuna pred webhookom se ne sme biti");

        placila.obdelajDogodek(telo, podpisi(telo));

        Uporabnik u = uporabnikRepozitorij.findByUporabniskoIme("jan@test.si").orElseThrow();
        assertEquals(StatusRacuna.CAKA, u.getStatus());
        assertEquals("$2a$10$zgoscenoGeslo", u.getGesloHash(), "geslo se ne sme ponovno zgostiti");

        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        assertEquals(Paket.PREMIUM, n.getPaket());
        assertEquals(StatusNarocnine.AKTIVNA, n.getStatus());
        assertEquals("sub_2", n.getStripeNarocninaId());
        assertEquals("cus_2", n.getStripeCustomerId());
        assertEquals(Boolean.TRUE, n.getStarejsiOd21());
    }

    /* Stripe dostavi dogodke "vsaj enkrat" - isti dogodek dvakrat ne sme
       podvojiti ne racuna ne narocnine. */
    @Test
    void istiDogodekDvakratJeIdempotenten() {
        String telo = dogodekRegistracija("evt_3", "cs_3", "cus_3", "sub_3",
                "Eva", "Zupan", "eva@test.si", "hash", "false",
                "1990-01-01", "", "PREMIUM", "LETNO", "true");
        String podpis = podpisi(telo);

        placila.obdelajDogodek(telo, podpis);
        placila.obdelajDogodek(telo, podpis);

        assertEquals(1, uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getUporabniskoIme().equals("eva@test.si")).count());
        assertEquals(1, narocninaRepozitorij.findAll().stream()
                .filter(n -> "sub_3".equals(n.getStripeNarocninaId())).count());
    }

    // ---------- Pomozno: rocno podpisan Stripe dogodek (isti obrazec kot pravi Stripe) ----------

    private static String podpisi(String telo) {
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

    private static String dogodekRegistracija(String idDogodka, String idSeje, String customer,
            String subscription, String ime, String priimek, String email, String gesloHash,
            String organizator, String datumRojstva, String emailSkrbnika, String paket,
            String ciklus, String starejsiOd21) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "customer": "%s",
                      "subscription": "%s",
                      "metadata": {
                        "namen": "registracija",
                        "ime": "%s",
                        "priimek": "%s",
                        "idKlub": "",
                        "email": "%s",
                        "gesloHash": "%s",
                        "organizator": "%s",
                        "datumRojstva": "%s",
                        "emailSkrbnika": "%s",
                        "paket": "%s",
                        "ciklus": "%s",
                        "starejsiOd21": "%s"
                      }
                    }
                  }
                }
                """.formatted(idDogodka, idSeje, customer, subscription, ime, priimek, email,
                gesloHash, organizator, datumRojstva, emailSkrbnika, paket, ciklus, starejsiOd21);
    }
}
