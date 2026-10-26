package si.turnirko.posta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import si.turnirko.posta.SporociloAdminu.Zahteva;

/* Oblika sporocila adminu: zadeva nosi ime (in placan paket), telo pove kdo,
   kaj je izbral in zakaj racun caka; vse, kar vpise obiskovalec, je ubezeno.
   Igralec in organizator imata isto zgradbo, razlikujejo se vloga, paket in
   poziv. */
class SporociloAdminuTest {

    private static final String DOSTOPI = "https://turnirko.test/racuni";
    private static final String RAZLOG = "V šifrantu ni igralca z istim imenom.";
    private static final String RAZLOG_ORGANIZATOR = "Organizatorja vedno potrdi admin.";
    private static final LocalDate DAN = LocalDate.of(2026, 10, 26);

    private static Zahteva brezplacen() {
        return new Zahteva(false, "Ana Novak", "ana@test.si", "NTK Žalec", null, null, null, false, RAZLOG,
                DAN, DOSTOPI);
    }

    private static Zahteva premium() {
        return new Zahteva(false, "Ana Novak", "ana@test.si", null, "Premium", "letno", 9.99, false, RAZLOG,
                DAN, DOSTOPI);
    }

    private static Zahteva organizator(String paket) {
        return new Zahteva(true, "Bor Kos", "bor@test.si", "NTK Žalec", paket, paket == null ? null : "letno",
                paket == null ? null : 169.99, false, RAZLOG_ORGANIZATOR, DAN, DOSTOPI);
    }

    @Test
    void zadevaNosiImeInPlacanPaketSamoKjerJePlacan() {
        assertEquals("Turnirko: račun čaka na potrditev · Ana Novak", SporociloAdminu.zadeva(brezplacen()));
        assertEquals("Turnirko: račun čaka na potrditev · Ana Novak · Premium", SporociloAdminu.zadeva(premium()));
        assertEquals("Turnirko: organizator čaka na potrditev · Bor Kos · Plus",
                SporociloAdminu.zadeva(organizator("Plus")));
        assertEquals("Turnirko: organizator čaka na potrditev · Bor Kos",
                SporociloAdminu.zadeva(organizator(null)));
    }

    @Test
    void brezplacenRacunPoveKdoKlubInRazlog() {
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(brezplacen());
        for (String vsebina : new String[] {b.telo(), b.html()}) {
            assertTrue(vsebina.contains("Ana Novak"), vsebina);
            assertTrue(vsebina.contains("ana@test.si"), vsebina);
            assertTrue(vsebina.contains("NTK Žalec"), vsebina);
            assertTrue(vsebina.contains("Brezplačno"), vsebina);
            assertTrue(vsebina.contains(RAZLOG), vsebina);
            assertTrue(vsebina.contains(DOSTOPI), vsebina);
            assertTrue(vsebina.contains("Poveži račun z igralcem"), vsebina);
            assertFalse(vsebina.contains("Premium"), vsebina);
        }
        assertTrue(b.telo().contains("Registracija: 26. 10. 2026"), b.telo());
        assertTrue(b.telo().contains("čaka na potrditev"), b.telo());
    }

    @Test
    void placanPremiumJeNaVrhuInPoveCikelInCeno() {
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(premium());
        assertTrue(b.telo().contains("Igralec Premium · letno · 9,99 € · plačano"), b.telo());
        assertTrue(b.telo().contains("ni izbran"), "klub je neobvezen: " + b.telo());
        assertTrue(b.telo().indexOf("Že plačan Premium") < b.telo().indexOf("Igralec:"), b.telo());
        assertTrue(b.html().contains("Že plačan Premium"), b.html());
        assertTrue(b.html().contains("letno · 9,99 €"), b.html());
        assertTrue(b.html().contains("plačano"), b.html());
    }

    @Test
    void organizatorJeOznacenKotTakInDobiSvojPoziv() {
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(organizator("Plus"));
        for (String vsebina : new String[] {b.telo(), b.html()}) {
            assertTrue(vsebina.contains("Organizator čaka na potrditev"), vsebina);
            assertTrue(vsebina.contains("Bor Kos"), vsebina);
            assertTrue(vsebina.contains("NTK Žalec"), vsebina);
            assertTrue(vsebina.contains("Organizator Plus"), vsebina);
            assertTrue(vsebina.contains("Že plačan paket Plus"), vsebina);
            assertTrue(vsebina.contains("smiselno potrditi čim prej"), vsebina);
            assertTrue(vsebina.contains("Zakaj čaka"), vsebina);
            assertTrue(vsebina.contains(RAZLOG_ORGANIZATOR), vsebina);
            assertTrue(vsebina.contains("Potrdi organizatorja"), vsebina);
            assertFalse(vsebina.contains("Poveži račun z igralcem"), vsebina);
            assertFalse(vsebina.contains("Zakaj ni samodejne povezave"), vsebina);
            assertFalse(vsebina.contains("Igralec Premium"), vsebina);
        }
        assertTrue(b.telo().contains("Organizator Plus · letno · 169,99 € · plačano"), b.telo());
        assertTrue(b.telo().contains("Organizator:  Bor Kos"), b.telo());
    }

    @Test
    void organizatorBrezPaketaJeBrezplacenInBrezPlacanegaPoudarka() {
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(organizator(null));
        assertTrue(b.telo().contains("Paket:        Brezplačno"), b.telo());
        assertFalse(b.telo().contains("Že plačan"), b.telo());
        assertFalse(b.html().contains("Že plačan"), b.html());
    }

    @Test
    void skrbnikJeVrsticaSamoKadarJeSoglasjeDano() {
        Zahteva otrok = new Zahteva(false, "Tim Mali", "tim@test.si", null, null, null, null, true, RAZLOG,
                DAN, DOSTOPI);
        assertTrue(SporociloAdminu.sestavi(otrok).telo().contains("Skrbnik:      soglasje potrjeno"));
        assertTrue(SporociloAdminu.sestavi(otrok).html().contains("mlajši od 15 let"));
        assertFalse(SporociloAdminu.sestavi(brezplacen()).telo().contains("Skrbnik"));
        assertFalse(SporociloAdminu.sestavi(brezplacen()).html().contains("Skrbnik"));
    }

    @Test
    void vnosObiskovalcaJeUbezenInBrezNoveVrsticeVZadevi() {
        Zahteva zlonamerna = new Zahteva(false, "<b>Ana</b>\r\nBcc: tuj@test.si", "a\"@test.si", "<i>Klub</i>",
                null, null, null, false, RAZLOG, DAN, DOSTOPI);
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(zlonamerna);

        assertFalse(b.zadeva().contains("\n") || b.zadeva().contains("\r"), b.zadeva());
        assertFalse(b.html().contains("<b>Ana</b>"), b.html());
        assertFalse(b.html().contains("<i>Klub</i>"), b.html());
        assertTrue(b.html().contains("&lt;b&gt;Ana&lt;/b&gt;"), b.html());
        assertFalse(b.telo().contains("\r"), "nova vrstica v imenu ne sme postati vrstica sporocila");
    }

    @Test
    void sporociloImaHtmlZZnamkoTurnirkoInBrezOdjave() {
        SporocilaPoste.Besedilo b = SporociloAdminu.sestavi(brezplacen());
        assertTrue(b.html().startsWith("<!doctype html>"));
        assertTrue(b.html().contains(">Turnirko</td>"), "ime znamke v glavi");
        assertTrue(b.html().contains("obvestilo za admina"));
        assertTrue(b.html().contains("background:#0071ab"), "modri gumb za Dostope");
        assertTrue(b.glave().isEmpty(), "interno sporocilo nima odjave");
    }
}
