/* Kaj se zgodi z narocnino, ko obnova ne uspe, in kaj Checkout zahteva od kupca.

   Stripe ob neuspeli obnovi poslje DVA dogodka (invoice.payment_failed in
   customer.subscription.updated s statusom past_due) in vrstni red ni
   zagotovljen. Stanje narocnine zato ne sme biti odvisno od tega, kateri
   pride prvi - tu sta oba vrstna reda. Dogodke podpisemo rocno z isto
   skrivnostjo kot PlacilaStoritevTest; Stripe API se ne klice. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NadgradnjaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZivljenjskiCikelNarocnineTest {

    /* Mora se ujemati z turnirko.stripe.webhook-skrivnost v application-test.properties. */
    private static final String SKRIVNOST = "whsec_testna_skrivnost";
    private static final String STRIPE_ID = "sub_cikel_1";

    @Autowired private PlacilaStoritev placila;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Neuspela obnova: oba vrstna reda dogodkov dasta isto ----------

    @Test
    void neuspelaObnovaUgasneDostop() {
        Uporabnik u = igralec("cikel-1@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(racunNeuspel("subscription_cycle"));

        Narocnina n = naroncina(u);
        assertEquals(StatusNarocnine.ZAPADLA, n.getStatus());
        assertFalse(n.jeVeljavna(LocalDateTime.now()), "brez placila Premium ne velja");
    }

    @Test
    void zapadlaPrvaPotemPastDueDaIsto() {
        Uporabnik u = igralec("cikel-2@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(racunNeuspel("subscription_cycle"));
        dostavi(posodobitev("past_due", false));

        assertEquals(StatusNarocnine.ZAPADLA, naroncina(u).getStatus());
    }

    /* Prej je ta vrstni red naroncino vrnil na AKTIVNA, ker je posodobitev gledala
       samo preklic ob koncu obdobja in ne Stripovega statusa. */
    @Test
    void pastDueNapravljenPrviPotemZapadlaDaIsto() {
        Uporabnik u = igralec("cikel-3@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("past_due", false));
        dostavi(racunNeuspel("subscription_cycle"));

        assertEquals(StatusNarocnine.ZAPADLA, naroncina(u).getStatus());
    }

    @Test
    void pastDueSamPostaviZapadlo() {
        Uporabnik u = igralec("cikel-4@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("past_due", false));

        assertEquals(StatusNarocnine.ZAPADLA, naroncina(u).getStatus());
    }

    @Test
    void unpaidJeZapadla() {
        Uporabnik u = igralec("cikel-5@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("unpaid", false));

        assertEquals(StatusNarocnine.ZAPADLA, naroncina(u).getStatus());
    }

    /* Kartica je bila popravljena, Stripe je ponovil placilo in je spet active. */
    @Test
    void uspesenPonovniPoskusVrneDostop() {
        Uporabnik u = igralec("cikel-6@test");
        premium(u, StatusNarocnine.ZAPADLA);

        dostavi(posodobitev("active", false));

        Narocnina n = naroncina(u);
        assertEquals(StatusNarocnine.AKTIVNA, n.getStatus());
        assertTrue(n.jeVeljavna(LocalDateTime.now()));
    }

    @Test
    void activeBrezPreklicaOstaneAktivna() {
        Uporabnik u = igralec("cikel-7@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("active", false));

        assertEquals(StatusNarocnine.AKTIVNA, naroncina(u).getStatus());
    }

    @Test
    void activeSPreklicemOKoncuJePreklicana() {
        Uporabnik u = igralec("cikel-8@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("active", true));

        assertEquals(StatusNarocnine.PREKLICANA, naroncina(u).getStatus());
    }

    /* Zapadlo placilo ne postane veljavno zato, ker je uporabnik tudi preklical. */
    @Test
    void zapadlaInPreklicanaOstaneZapadla() {
        Uporabnik u = igralec("cikel-9@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(posodobitev("past_due", true));

        assertEquals(StatusNarocnine.ZAPADLA, naroncina(u).getStatus());
    }

    /* Stripe se do konca poskusov odloci: narocnino preklice. Kot izbris
       (customer.subscription.deleted) - dostop se konca takoj. */
    @Test
    void canceledKotIzbrisKoncaDostopTakoj() {
        Uporabnik u = igralec("cikel-10@test");
        Narocnina n0 = premium(u, StatusNarocnine.ZAPADLA);
        n0.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(12));
        narocninaRepozitorij.save(n0);

        dostavi(posodobitev("canceled", false));

        Narocnina n = naroncina(u);
        assertEquals(StatusNarocnine.PREKLICANA, n.getStatus());
        assertFalse(n.jeVeljavna(LocalDateTime.now()), "preklicana narocnina ne sme ostati veljavna do starega konca");
    }

    // ---------- Neuspelo placilo, ki NI obnova, narocnine ne ugasne ----------

    /* Doplacilo ob nadgradnji organizatorja: ce kartica odpove, Stripe nadgradnjo
       zavrne in narocnina tece naprej po starem. Dogodek payment_failed zanjo
       zato ne sme postaviti ZAPADLA. */
    @Test
    void neuspeloDoplaciloOdNadgradnjeNeUgasneNarocnine() {
        Uporabnik u = igralec("cikel-11@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(racunNeuspel("subscription_update"));

        assertEquals(StatusNarocnine.AKTIVNA, naroncina(u).getStatus());
    }

    @Test
    void neuspeloPlaciloTujeNarocnineNicNeSpremeni() {
        Uporabnik u = igralec("cikel-12@test");
        premium(u, StatusNarocnine.AKTIVNA);

        dostavi(racunNeuspelZaNarocnino("sub_tuja", "subscription_cycle"));

        assertEquals(StatusNarocnine.AKTIVNA, naroncina(u).getStatus());
    }

    // ---------- Druga narocnina ob zivi prvi bi pomenila dvojno zaracunavanje ----------

    /* Stran Narocnina za ZAPADLA pokaze stanje brez paketa. Brez te zavore bi
       uporabnik kupil znova, Stripe pa bi staro narocnino se naprej ponavljal
       (in jo morda uspesno zaracunal), medtem ko bi nova v bazi prepisala vrstico in
       stara ostala brez lastnika. */
    @Test
    void nadgradnjaPriZapadliNarocniniJeZavrnjena() {
        Uporabnik u = igralec("zavora-1@test");
        premium(u, StatusNarocnine.ZAPADLA);
        prijava(u);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> placila.zacniNadgradnjo(new NadgradnjaVnos(Paket.PREMIUM, CiklusPlacila.MESECNO)));

        assertTrue(napaka.getMessage().contains("kartico"), "uporabnik mora izvedeti, kaj naj stori: " + napaka.getMessage());
    }

    @Test
    void nadgradnjaPriAktivniNarocniniJeZavrnjena() {
        Uporabnik u = igralec("zavora-2@test");
        premium(u, StatusNarocnine.AKTIVNA);
        prijava(u);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> placila.zacniNadgradnjo(new NadgradnjaVnos(Paket.PREMIUM, CiklusPlacila.LETNO)));

        assertTrue(napaka.getMessage().contains("ze imas"), "zavora, ne Stripe: " + napaka.getMessage());
    }

    @Test
    void nadgradnjaPriPreklicaniAVeljavniNarocniniJeZavrnjena() {
        Uporabnik u = igralec("zavora-3@test");
        Narocnina n = premium(u, StatusNarocnine.PREKLICANA);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(5));
        narocninaRepozitorij.save(n);
        prijava(u);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> placila.zacniNadgradnjo(new NadgradnjaVnos(Paket.PREMIUM, CiklusPlacila.MESECNO)));

        assertTrue(napaka.getMessage().contains("ze imas"), "zavora, ne Stripe: " + napaka.getMessage());
    }

    /* Potekla preklicana narocnina je konec: "nova narocnina prek Checkouta".
       Zavora ne sme zavrniti; do Stripa pride (v testu brez kljuca) in tam pade
       z drugim sporocilom kot zavora. */
    @Test
    void nadgradnjaPoTekluPreklicaneNarocnineZavoreNeSprozi() {
        Uporabnik u = igralec("zavora-4@test");
        Narocnina n = premium(u, StatusNarocnine.PREKLICANA);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().minusDays(1));
        narocninaRepozitorij.save(n);
        prijava(u);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> placila.zacniNadgradnjo(new NadgradnjaVnos(Paket.PREMIUM, CiklusPlacila.MESECNO)));

        assertTrue(napaka.getMessage().contains("zaceti"), "pricakovan je Stripe, ne zavora: " + napaka.getMessage());
    }

    // ---------- Checkout: kar kupec vidi in mora potrditi ----------

    @Test
    @SuppressWarnings("unchecked")
    void checkoutZahtevaSoglasjeInJeSlovenski() {
        Map<String, Object> p = placila.osnovaSeje(Paket.PREMIUM, CiklusPlacila.MESECNO).build().toMap();

        assertEquals("sl", p.get("locale"));
        assertEquals("subscription", p.get("mode"));
        assertEquals("required", ((Map<String, Object>) p.get("consent_collection")).get("terms_of_service"));
        assertEquals(List.of("card"), p.get("payment_method_types"),
                "kartica je po ZDavPR gotovina - drugih nacinov ne ponudimo brez posebne presoje");
        Map<String, Object> besedila = (Map<String, Object>) p.get("custom_text");
        String gumb = (String) ((Map<String, Object>) besedila.get("submit")).get("message");
        assertTrue(gumb.contains("obveznostjo placila") || gumb.contains("obveznostjo plačila"),
                "mora pisati, da je to narocilo z obveznostjo placila: " + gumb);
        assertTrue(gumb.contains("vsak mesec"), gumb);
        String pogoji = (String) ((Map<String, Object>) besedila.get("terms_of_service_acceptance")).get("message");
        assertTrue(pogoji.contains("/pogoji"), "povezava na pogoje: " + pogoji);
    }

    @Test
    @SuppressWarnings("unchecked")
    void letnoBesediloPoveObnoviNaLeto() {
        Map<String, Object> p = placila.osnovaSeje(Paket.PREMIUM, CiklusPlacila.LETNO).build().toMap();
        Map<String, Object> besedila = (Map<String, Object>) p.get("custom_text");
        String gumb = (String) ((Map<String, Object>) besedila.get("submit")).get("message");
        assertTrue(gumb.contains("vsako leto"), gumb);
    }

    /* Organizator je pogosto klub ali podjetje: racun brez naslova in davcne
       stevilke kupca knjigovodstvu kluba ne koristi. Igralec tega ne potrebuje -
       en obrazec manj pri nakupu za nekaj evrov. */
    @Test
    @SuppressWarnings("unchecked")
    void organizatorOdgovoriNaslovInDavcnoStevilko() {
        Map<String, Object> org = placila.osnovaSeje(Paket.ORGANIZATOR_PLUS, CiklusPlacila.LETNO).build().toMap();
        Map<String, Object> igr = placila.osnovaSeje(Paket.PREMIUM, CiklusPlacila.MESECNO).build().toMap();

        assertEquals("required", org.get("billing_address_collection"));
        assertEquals(Boolean.TRUE, ((Map<String, Object>) org.get("tax_id_collection")).get("enabled"));
        assertNull(igr.get("billing_address_collection"));
        assertNull(igr.get("tax_id_collection"));
    }

    // ---------- Pomozno ----------

    private void dostavi(String telo) {
        placila.obdelajDogodek(telo, podpisi(telo));
    }

    private Narocnina naroncina(Uporabnik u) {
        return narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
    }

    private Uporabnik igralec(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        u.setPrijavljeniDatumRojstva(java.time.LocalDate.of(2000, 1, 1));
        return uporabnikRepozitorij.save(u);
    }

    private Narocnina premium(Uporabnik u, StatusNarocnine status) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setCiklus(CiklusPlacila.MESECNO);
        n.setStatus(status);
        n.setStarejsiOd21(true);
        n.setCenaObSklenitvi(4.99);
        n.setStripeNarocninaId(STRIPE_ID);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(18));
        return narocninaRepozitorij.save(n);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }

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

    private static String racunNeuspel(String razlog) {
        return racunNeuspelZaNarocnino(STRIPE_ID, razlog);
    }

    /* invoice.payment_failed v obliki "basil": narocnina je pod parent.subscription_details. */
    private static String racunNeuspelZaNarocnino(String idNarocnine, String razlog) {
        return """
                {
                  "id": "evt_racun",
                  "object": "event",
                  "type": "invoice.payment_failed",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "in_1",
                      "object": "invoice",
                      "billing_reason": "%s",
                      "parent": {
                        "type": "subscription_details",
                        "subscription_details": { "subscription": "%s" }
                      }
                    }
                  }
                }
                """.formatted(razlog, idNarocnine);
    }

    /* customer.subscription.updated, kot ga poslje Stripe: status in preklic ob koncu
       obdobja na narocnini, obdobje na postavki. */
    private static String posodobitev(String status, boolean preklicOKoncu) {
        long od = Instant.now().minusSeconds(20L * 86400).getEpochSecond();
        long do_ = Instant.now().plusSeconds(10L * 86400).getEpochSecond();
        return """
                {
                  "id": "evt_upd",
                  "object": "event",
                  "type": "customer.subscription.updated",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "subscription",
                      "status": "%s",
                      "cancel_at_period_end": %s,
                      "schedule": null,
                      "items": {
                        "object": "list",
                        "data": [
                          {
                            "id": "si_1",
                            "object": "subscription_item",
                            "current_period_start": %d,
                            "current_period_end": %d,
                            "price": {
                              "id": "price_1",
                              "object": "price",
                              "unit_amount": 499,
                              "currency": "eur",
                              "recurring": { "interval": "month", "interval_count": 1 }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(STRIPE_ID, status, preklicOKoncu, od, do_);
    }
}
