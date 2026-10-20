/* Webhook je edini vir resnice o placilu (PlacilaStoritev): racun in
   narocnina nastanejo SELE, ko Stripe potrdi "checkout.session.completed" -
   ne prej. Dogodek tu rocno podpisemo z isto skrivnostjo, ki jo ima test
   profil (turnirko.stripe.webhook-skrivnost), namesto da bi klicali pravi
   Stripe API (testi ne smejo biti odvisni od omrezja/pravih kljucev).

   Podatki obrazca do placila cakajo v nasi bazi (V48), v Stripe gre samo
   zeton: Stripove dogodke bere tudi Moj Racun. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.CakajocaRegistracija;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlacilaStoritevTest {

    /* Mora se ujemati z turnirko.stripe.webhook-skrivnost v application-test.properties. */
    private static final String SKRIVNOST = "whsec_testna_skrivnost";

    @Autowired private PlacilaStoritev placila;
    @Autowired private CiscenjeRacunovStoritev ciscenje;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private CakajocaRegistracijaRepozitorij cakajoce;
    @Autowired private PasswordEncoder kodirnik;

    @Test
    void neveljavenPodpisZavrne() {
        cakajoca("zeton-1", "Ana", "Novak", "ana@test.si", "hash", LocalDateTime.now());
        String telo = dogodekRegistracija("evt_1", "cs_1", "cus_1", "sub_1", "zeton-1",
                "PREMIUM", "MESECNO", "false");
        String glava = "t=" + Instant.now().getEpochSecond() + ",v1=neveljaven";
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.obdelajDogodek(telo, glava));
        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("ana@test.si").isEmpty(),
                "brez veljavnega podpisa se racun ne sme ustvariti");
        assertTrue(cakajoce.findByZeton("zeton-1").isPresent(), "registracija caka naprej");
    }

    /* Jedro obljube: uspesno placilo ustvari racun (ne prej, glej primerjavo
       spodaj) IN narocnino, z geslom, ki je bilo zgosceno ze OB ZACETKU
       placila (registrirajPoPlacilu ga ne sme znova zgostiti). Cakajoca vrstica
       gre - podatki so zdaj v racunu. */
    @Test
    void uspesnoPlaciloUstvariRacunInNarocnino() {
        cakajoca("zeton-2", "Jan", "Kranjc", "jan@test.si", "$2a$10$zgoscenoGeslo", LocalDateTime.now());
        String telo = dogodekRegistracija("evt_2", "cs_2", "cus_2", "sub_2", "zeton-2",
                "PREMIUM", "MESECNO", "true");

        assertTrue(uporabnikRepozitorij.findByUporabniskoIme("jan@test.si").isEmpty(),
                "racuna pred webhookom se ne sme biti");

        placila.obdelajDogodek(telo, podpisi(telo));

        Uporabnik u = uporabnikRepozitorij.findByUporabniskoIme("jan@test.si").orElseThrow();
        assertEquals(StatusRacuna.CAKA, u.getStatus());
        assertEquals("$2a$10$zgoscenoGeslo", u.getGesloHash(), "geslo se ne sme ponovno zgostiti");
        assertEquals("Jan", u.getPrijavljenoIme());
        assertEquals(LocalDate.of(1990, 1, 1), u.getPrijavljeniDatumRojstva());

        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        assertEquals(Paket.PREMIUM, n.getPaket());
        assertEquals(StatusNarocnine.AKTIVNA, n.getStatus());
        assertEquals("sub_2", n.getStripeNarocninaId());
        assertEquals("cus_2", n.getStripeCustomerId());
        assertEquals(Boolean.TRUE, n.getStarejsiOd21());

        assertTrue(cakajoce.findByZeton("zeton-2").isEmpty(), "kopija podatkov ne sme ostati");
    }

    /* Stripe dostavi dogodke "vsaj enkrat" - isti dogodek dvakrat ne sme
       podvojiti ne racuna ne narocnine (in ga drugic ne sme zmesti, da
       cakajoce vrstice ni vec). */
    @Test
    void istiDogodekDvakratJeIdempotenten() {
        cakajoca("zeton-3", "Eva", "Zupan", "eva@test.si", "hash", LocalDateTime.now());
        String telo = dogodekRegistracija("evt_3", "cs_3", "cus_3", "sub_3", "zeton-3",
                "PREMIUM", "LETNO", "true");
        String podpis = podpisi(telo);

        placila.obdelajDogodek(telo, podpis);
        placila.obdelajDogodek(telo, podpis);

        assertEquals(1, uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getUporabniskoIme().equals("eva@test.si")).count());
        assertEquals(1, narocninaRepozitorij.findAll().stream()
                .filter(n -> "sub_3".equals(n.getStripeNarocninaId())).count());
    }

    /* V Stripe gre samo zeton (in paket, ki ga kupec vidi tako ali tako):
       gesla, datuma rojstva in imena v metapodatkih ni. Zgosceno geslo in
       datum cakata pri nas. */
    @Test
    void vStripeGreSamoZeton() {
        var vnos = new PlacanaRegistracijaVnos(
                new RegistracijaVnos("Maja", "Kos", null, "maja@test.si", "geslo1234", false,
                        LocalDate.of(1995, 3, 4), null),
                Paket.PREMIUM, CiklusPlacila.MESECNO);

        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(vnos);

        Map<String, Object> seja = p.seja().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) seja.get("metadata");
        assertEquals(Set.of(PlacilaStoritev.M_NAMEN, PlacilaStoritev.M_ZETON, "paket", "ciklus", "starejsiOd21"),
                meta.keySet());
        assertEquals(p.cakajoca().getZeton(), meta.get(PlacilaStoritev.M_ZETON));
        String vse = meta.values().toString();
        assertFalse(vse.contains("1995"), "datum rojstva ne sme v Stripe");
        assertFalse(vse.contains("Maja") || vse.contains("Kos"), "ime ne sme v metapodatke");
        assertFalse(vse.contains(p.cakajoca().getGesloHash()), "geslo ne sme v Stripe");
        assertEquals("maja@test.si", seja.get("customer_email"), "e-posta je za racun potrebna");

        CakajocaRegistracija shranjena = cakajoce.findByZeton(p.cakajoca().getZeton()).orElseThrow();
        assertTrue(kodirnik.matches("geslo1234", shranjena.getGesloHash()));
        assertEquals(LocalDate.of(1995, 3, 4), shranjena.getDatumRojstva());
    }

    /* Placilo brez cakajoce registracije (potekla ali seja izpred V48) ne
       ustvari nicesar in Stripu ne vrne napake - ponovni poskusi ne bi
       pomagali, potrebna je rocna obravnava (opozorilo v dnevniku). */
    @Test
    void placiloBrezCakajoceRegistracijeNeUstvariRacuna() {
        String telo = dogodekRegistracija("evt_4", "cs_4", "cus_4", "sub_4", "ni-ga",
                "PREMIUM", "MESECNO", "false");

        placila.obdelajDogodek(telo, podpisi(telo));

        assertTrue(narocninaRepozitorij.findByStripeNarocninaId("sub_4").isEmpty());
    }

    /* Opuscen nakup ne pusti gesla in datuma rojstva v bazi: po 4 dneh (seja
       24 ur + ponovni poskusi webhooka do 3 dni) vrstico pobere nocno ciscenje. */
    @Test
    void ciscenjePobriseNeplacanoRegistracijo() {
        LocalDateTime zdaj = LocalDateTime.now();
        cakajoca("zeton-5", "Tim", "Mlakar", "tim@test.si", "hash", zdaj);

        ciscenje.pocisti(zdaj.plusDays(3));
        assertTrue(cakajoce.findByZeton("zeton-5").isPresent(), "placilo lahko se pride");

        ciscenje.pocisti(zdaj.plusDays(5));
        assertTrue(cakajoce.findByZeton("zeton-5").isEmpty());
    }

    // ---------- Pomozno ----------

    private void cakajoca(String zeton, String ime, String priimek, String email, String gesloHash,
                          LocalDateTime ustvarjenaOb) {
        cakajoce.save(new CakajocaRegistracija(zeton, ime, priimek, null, email, gesloHash, false,
                LocalDate.of(1990, 1, 1), null, ustvarjenaOb));
    }

    /* Rocno podpisan Stripe dogodek (isti obrazec kot pravi Stripe). */
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
            String subscription, String zeton, String paket, String ciklus, String starejsiOd21) {
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
                        "registracija": "%s",
                        "paket": "%s",
                        "ciklus": "%s",
                        "starejsiOd21": "%s"
                      }
                    }
                  }
                }
                """.formatted(idDogodka, idSeje, customer, subscription, zeton, paket, ciklus, starejsiOd21);
    }
}
