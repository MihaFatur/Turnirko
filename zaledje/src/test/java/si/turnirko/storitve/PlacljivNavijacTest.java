/* Placljiv paket Navijac (V58, faza 1): cena 19,99 EUR samo letno, nakup ob
   registraciji in kasneje, webhook, kode popusta za navijaca in ugodnosti, ki
   jih paket odpre (neomejeno spremljanih igralcev).

   Stripe je tu lazen (StripeNarocnine) in dogodek rocno podpisan, kot v
   PlacilaStoritevTest: test ne sme klicati omrezja. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PreveriKodoVnos;
import si.turnirko.dto.PreverjenaKodaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CakajocaRegistracija;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class PlacljivNavijacTest extends IntegracijskiTest {

    private static final String IP = "10.0.0.9";
    private static final LocalDate ODRASEL = LocalDate.of(1980, 2, 3);
    private static final Pattern KODA = Pattern.compile("\\b(\\d{6})\\b");
    /* Mora se ujemati z turnirko.stripe.webhook-skrivnost v application-test.properties. */
    private static final String SKRIVNOST = "whsec_testna_skrivnost";

    @Autowired private CenikStoritev cenik;
    @Autowired private PlacilaStoritev placila;
    @Autowired private RegistracijaStoritev registracija;
    @Autowired private KodePopustaStoritev kode;
    @Autowired private NarocninaStoritev narocninaStoritev;
    @Autowired private SpremljaniIgralciStoritev spremljani;
    @Autowired private NapovedTekmeStoritev napoved;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private CakajocaRegistracijaRepozitorij cakajoce;
    @Autowired private PomnilniskiPosiljatelj posta;
    @Autowired private OmejevalnikPoskusov omejevalnik;
    @MockitoBean private StripeNarocnine stripe;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- cena ----------

    @Test
    void navijacStane1999SamoLetno() {
        assertEquals(19.99, cenik.cena(Paket.NAVIJAC, CiklusPlacila.LETNO, null), 0.0001);
        assertThrows(NeveljavenVnosIzjema.class, () -> cenik.cena(Paket.NAVIJAC, CiklusPlacila.MESECNO, null));
        assertThrows(NeveljavenVnosIzjema.class, () -> cenik.cena(Paket.NAVIJAC, null, null));
    }

    // ---------- nakup ob registraciji ----------

    @Test
    void placljivaRegistracijaNavijacaGreVStripeSamoZZetonom() {
        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                navijac("oce1@test.si", Paket.NAVIJAC, CiklusPlacila.LETNO, null));

        Map<String, Object> seja = p.seja().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) seja.get("metadata");
        assertEquals("NAVIJAC", meta.get("paket"));
        assertEquals("LETNO", meta.get("ciklus"));
        assertFalse(meta.values().toString().contains("1980"), "datum rojstva ne sme v Stripe");
        assertTrue(cakajoce.findByZeton(p.cakajoca().getZeton()).orElseThrow().isNavijac(),
                "webhook mora vedeti, da gre za navijaca");
        assertEquals("oce1@test.si", seja.get("customer_email"));
    }

    @Test
    void navijacKupiSamoPaketNavijacInNihceDrugNjegovega() {
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                navijac("oce2@test.si", Paket.PREMIUM, CiklusPlacila.LETNO, null)));
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                navijac("oce3@test.si", Paket.ORGANIZATOR_BASIC, CiklusPlacila.LETNO, null)));
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                new PlacanaRegistracijaVnos(new RegistracijaVnos("Igra", "Lec", null, "igralec1@test.si",
                        "geslo1234", false, ODRASEL, null), Paket.NAVIJAC, CiklusPlacila.LETNO)));
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                navijac("oce4@test.si", Paket.NAVIJAC, CiklusPlacila.MESECNO, null)));
    }

    @Test
    void webhookUstvariRacunNavijacaInNjegovoNarocnino() {
        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                navijac("oce5@test.si", Paket.NAVIJAC, CiklusPlacila.LETNO, null));
        String telo = dogodekRegistracija("evt_n1", "cs_n1", "cus_n1", "sub_n1", p.cakajoca().getZeton());

        placila.obdelajDogodek(telo, podpisi(telo));

        Uporabnik u = uporabnikRepozitorij.najdiZVsem("oce5@test.si").orElseThrow();
        assertEquals(Vloga.NAVIJAC, u.getVloga());
        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        assertEquals(Paket.NAVIJAC, n.getPaket());
        assertEquals(StatusNarocnine.AKTIVNA, n.getStatus());
        assertEquals(19.99, n.getCenaObSklenitvi(), 0.0001);
        assertEquals(CiklusPlacila.LETNO, n.getCiklus());
        assertTrue(narocninaStoritev.imaNavijaca(u));
        assertTrue(narocninaStoritev.imaSirokoSpremljanje(u));

        // po potrditvi e-poste je racun navijaca potrjen brez administratorja
        registracija.potrdiEposto(new PotrditevKodeVnos("oce5@test.si", koda("oce5@test.si")));
        assertEquals(StatusRacuna.POTRJEN, uporabnikRepozitorij.najdiZVsem("oce5@test.si").orElseThrow().getStatus());
    }

    // ---------- ugodnosti paketa ----------

    @Test
    void paketNavijacOdstraniMejoSpremljanihIgralcev() {
        Igralec ana = noviIgralec("Ana", "Novak");
        Igralec bor = noviIgralec("Bor", "Kos");
        Igralec cene = noviIgralec("Cene", "Zupan");
        Uporabnik u = navijacRacun("brezmeje@test");
        narocnina(u, Paket.NAVIJAC, StatusNarocnine.AKTIVNA);
        prijava(u);

        spremljani.spremljaj(ana.getId());
        spremljani.spremljaj(bor.getId());
        assertEquals(List.of(ana.getId(), bor.getId(), cene.getId()), spremljani.spremljaj(cene.getId()));
    }

    @Test
    void zapadlaNarocninaVrneMejoEnegaIgralca() {
        Igralec ana = noviIgralec("Ana", "Novak");
        Igralec bor = noviIgralec("Bor", "Kos");
        Uporabnik u = navijacRacun("zapadla@test");
        narocnina(u, Paket.NAVIJAC, StatusNarocnine.ZAPADLA);
        prijava(u);

        spremljani.spremljaj(ana.getId());
        DomenskaIzjema izjema = assertThrows(DomenskaIzjema.class, () -> spremljani.spremljaj(bor.getId()));
        assertTrue(izjema.getMessage().contains("19,99"), "sporocilo ponudi paket: " + izjema.getMessage());
    }

    @Test
    void paketPremiumPrinesePrejemniceNavijaca() {
        Uporabnik premium = new Uporabnik("premium@test", "{bcrypt}x", Vloga.IGRALEC);
        premium.setStatus(StatusRacuna.CAKA);
        uporabnikRepozitorij.save(premium);
        narocnina(premium, Paket.PREMIUM, StatusNarocnine.AKTIVNA);
        assertTrue(narocninaStoritev.imaSirokoSpremljanje(premium));
        assertFalse(narocninaStoritev.imaNavijaca(premium), "Premium ni paket Navijac");

        Uporabnik brez = navijacRacun("brez@test");
        assertFalse(narocninaStoritev.imaSirokoSpremljanje(brez));
    }

    /* Napoved dvoboja za KATERI KOLI par je ugodnost paketa Navijac in Premium; brez
       njiju napoved ostane samo za lasten profil (kot zasebne analize). */
    @Test
    void napovedZaKateriKoliParJeUgodnostPaketa() {
        Igralec ana = noviIgralec("Ana", "Novak");
        Igralec bor = noviIgralec("Bor", "Kos");
        Uporabnik placnik = navijacRacun("napoved1@test");
        narocnina(placnik, Paket.NAVIJAC, StatusNarocnine.AKTIVNA);
        navijacRacun("napoved2@test");

        assertEquals(ana.getId(), napoved.napoved(ana.getId(), bor.getId(), "napoved1@test").jaz().idIgralec());
        assertThrows(PrepovedanoIzjema.class, () -> napoved.napoved(ana.getId(), bor.getId(), "napoved2@test"));
    }

    // ---------- kode popusta ----------

    @Test
    void kodaZaMesecePaketaNavijacZacneTecOdPotrditveRacuna() {
        kode.ustvari(vnos("NAV3", VrstaKodePopusta.NAVIJAC_MESECI, 3, null));

        registracija.registriraj(navijacBrezPlacila("oce6@test.si", "NAV3"), IP);
        Uporabnik cakajoc = uporabnikRepozitorij.najdiZVsem("oce6@test.si").orElseThrow();
        assertTrue(narocninaRepozitorij.findByUporabnikId(cakajoc.getId()).isEmpty(),
                "mesec ne sme teci med cakanjem na kodo");

        registracija.potrdiEposto(new PotrditevKodeVnos("oce6@test.si", koda("oce6@test.si")));

        Uporabnik u = uporabnikRepozitorij.najdiZVsem("oce6@test.si").orElseThrow();
        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        assertEquals(Paket.NAVIJAC, n.getPaket());
        assertEquals(StatusNarocnine.PREIZKUS, n.getStatus());
        assertTrue(n.getTrenutnoObdobjeDo().isAfter(LocalDateTime.now().plusMonths(2)));
        assertTrue(narocninaStoritev.imaNavijaca(u));
    }

    @Test
    void kodaZaMesecePaketaNavijacVelja_samoZaNavijaca() {
        kode.ustvari(vnos("SAMONAV", VrstaKodePopusta.NAVIJAC_MESECI, 1, null));

        PreverjenaKodaDto igralec = kode.predogled(new PreveriKodoVnos("SAMONAV", false, Paket.BREZPLACNO, null), IP);
        assertFalse(igralec.uporabna(), "koda za navijaca igralcu ne velja");
        PreverjenaKodaDto navijac = kode.predogled(
                new PreveriKodoVnos("SAMONAV", false, true, Paket.BREZPLACNO, null), IP);
        assertTrue(navijac.uporabna(), navijac.sporocilo());
        PreverjenaKodaDto placljivo = kode.predogled(
                new PreveriKodoVnos("SAMONAV", false, true, Paket.NAVIJAC, null), IP);
        assertFalse(placljivo.uporabna(), "koda z meseci se ne zdruzuje s placilom");
    }

    @Test
    void odstotekZaNavijacaZnizaPrvoPlaciloPaketaNavijac() {
        kode.ustvari(new KodaPopustaVnos("NAV20", null, VrstaKodePopusta.ODSTOTEK, null, 20, null,
                si.turnirko.modeli.Vloga.NAVIJAC, null, null));
        when(stripe.ustvariKupon("NAV20", 20)).thenReturn("co_nav20");

        PlacilaStoritev.PripravljenaRegistracija p = placila.pripraviRegistracijo(
                navijac("oce7@test.si", Paket.NAVIJAC, CiklusPlacila.LETNO, "nav20"), IP);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> popusti = (List<Map<String, Object>>) p.seja().toMap().get("discounts");
        assertEquals("co_nav20", popusti.get(0).get("coupon"));

        // koda za igralce navijacu ne velja
        kode.ustvari(new KodaPopustaVnos("SAMOIGR", null, VrstaKodePopusta.ODSTOTEK, null, 10, null,
                si.turnirko.modeli.Vloga.IGRALEC, null, null));
        assertThrows(NeveljavenVnosIzjema.class, () -> placila.pripraviRegistracijo(
                navijac("oce8@test.si", Paket.NAVIJAC, CiklusPlacila.LETNO, "samoigr"), IP));
    }

    // ---------- priprava ----------

    private static PlacanaRegistracijaVnos navijac(String email, Paket paket, CiklusPlacila ciklus, String koda) {
        return new PlacanaRegistracijaVnos(new RegistracijaVnos("Oče", "Kovač", null, email, "geslo1234", false,
                ODRASEL, null, koda, true), paket, ciklus);
    }

    private static RegistracijaVnos navijacBrezPlacila(String email, String koda) {
        return new RegistracijaVnos("Oče", "Kovač", null, email, "geslo1234", false, ODRASEL, null, koda, true);
    }

    private static KodaPopustaVnos vnos(String koda, VrstaKodePopusta vrsta, Integer meseci, Integer odstotek) {
        return new KodaPopustaVnos(koda, null, vrsta, meseci, odstotek, null, null, null, null);
    }

    private Uporabnik navijacRacun(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.NAVIJAC);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void narocnina(Uporabnik u, Paket paket, StatusNarocnine status) {
        Narocnina n = new Narocnina(u, paket);
        n.setStatus(status);
        n.setCiklus(CiklusPlacila.LETNO);
        n.setStripeNarocninaId("sub_" + u.getUporabniskoIme());
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusMonths(6));
        narocninaRepozitorij.save(n);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_NAVIJAC"))));
    }

    private String koda(String email) {
        String besedilo = posta.zadnje(email).orElseThrow(
                () -> new AssertionError("ni sporocila za " + email)).besedilo();
        Matcher m = KODA.matcher(besedilo);
        assertTrue(m.find(), "v sporocilu ni kode: " + besedilo);
        return m.group(1);
    }

    /* Rocno podpisan Stripe dogodek (isti obrazec kot pravi Stripe). */
    private static String podpisi(String telo) {
        long trenutek = Instant.now().getEpochSecond();
        String podpisano = trenutek + "." + telo;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SKRIVNOST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "t=" + trenutek + ",v1="
                    + HexFormat.of().formatHex(mac.doFinal(podpisano.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dogodekRegistracija(String idDogodka, String idSeje, String customer,
                                              String subscription, String zeton) {
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
                        "paket": "NAVIJAC",
                        "ciklus": "LETNO",
                        "starejsiOd21": "false"
                      }
                    }
                  }
                }
                """.formatted(idDogodka, idSeje, customer, subscription, zeton);
    }
}
