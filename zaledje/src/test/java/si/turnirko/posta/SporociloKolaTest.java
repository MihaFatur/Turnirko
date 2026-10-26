package si.turnirko.posta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.posta.SporociloKola.Izid;
import si.turnirko.posta.SporociloKola.Kolo;
import si.turnirko.posta.SporociloKola.Osebno;
import si.turnirko.posta.SporociloKola.Prejemnik;
import si.turnirko.posta.SporociloKola.Razlog;
import si.turnirko.posta.SporociloKola.Tekma;
import si.turnirko.posta.SporociloKola.Vrstica;

/* Oblika sporocila po kolu: zadeva je vedno ime lige in kolo, vrstni red
   vsebine je za vse prejemnike isti, poudarek je le na prejemnikovi ekipi in
   imenu, Premium je samo pri igralcu brez paketa. */
class SporociloKolaTest {

    private static final String PREMIUM = "S Premium na profilu vidiš še svojo formo, nasprotnike, "
            + "napoved naslednje tekme ter nize in točke";
    private static final String KOREN = "https://turnirko.test";

    /* moja = prejemnikova ekipa je Cestnik (igralec); spremljevalec nima ekipe. */
    private static Kolo kolo(boolean moja) {
        return new Kolo(7L, "Liga Savinja A", "Sezona 8", 1, LocalDate.of(2026, 9, 17), 1, 6,
                List.of(new Izid("Team predelava lesa Cestnik", "Mušketirji", 5, 0, false, moja),
                        new Izid("Amaterji", "Tempo 1", 4, 1, false, false)),
                List.of(new Vrstica(1, "Team predelava lesa Cestnik", 1, 2, moja),
                        new Vrstica(2, "Amaterji", 1, 2, false)),
                12, 2, LocalDate.of(2026, 10, 8));
    }

    private static Osebno osebno() {
        return new Osebno(2092L, "Team predelava lesa Cestnik",
                List.of(new Izid("Team predelava lesa Cestnik", "Mušketirji", 5, 0, false, true)),
                List.of(new Tekma("Ana Novak / Bor Kos", "Aleksander Poropat / Gorazd Štor", 3, 1, true, 6),
                        new Tekma("Ana Novak", "Oliver Štor", 3, 0, true, 1)),
                1378, 7);
    }

    private static Prejemnik igralec(String premium) {
        return new Prejemnik(Razlog.IGRA, osebno(), premium, KOREN + "/odjava-obvestil?z=abc",
                KOREN + "/api/v1/obvestila/odjava?z=abc", KOREN);
    }

    private static Prejemnik spremljevalec() {
        return new Prejemnik(Razlog.SPREMLJA, null, null, KOREN + "/odjava-obvestil?z=def",
                KOREN + "/api/v1/obvestila/odjava?z=def", KOREN);
    }

    @Test
    void zadevaJeImeLigeInKoloZaVseEnako() {
        assertEquals("Liga Savinja A: 1. kolo", SporociloKola.zadeva(kolo(true)));
        assertEquals("Liga Savinja A: 1. kolo", SporociloKola.sestavi(kolo(true), igralec(PREMIUM)).zadeva());
        assertEquals("Liga Savinja A: 1. kolo", SporociloKola.sestavi(kolo(false), spremljevalec()).zadeva());
    }

    @Test
    void vrstniRedJeLestvicaIzidiTekmeNaslednjeKoloPremiumOdjava() {
        for (SporocilaPoste.Besedilo b : List.of(
                SporociloKola.sestavi(kolo(true), igralec(PREMIUM)),
                SporociloKola.sestavi(kolo(true), igralec(null)))) {
            assertOrder(b.telo(), "Lestvica po tem kolu", "Izidi 1. kola", "Tvoje tekme", "Naslednje kolo: 2. kolo",
                    "Odjava od obvestil po kolu");
            assertOrder(b.html(), "Lestvica po tem kolu", "Izidi 1. kola", "Tvoje tekme", "Naslednje kolo: 2. kolo",
                    "Odjava od obvestil po kolu");
        }
        assertOrder(SporociloKola.sestavi(kolo(true), igralec(PREMIUM)).telo(),
                "Naslednje kolo: 2. kolo", PREMIUM, "Odjava od obvestil po kolu");
        assertOrder(SporociloKola.sestavi(kolo(false), spremljevalec()).telo(),
                "Lestvica po tem kolu", "Izidi 1. kola", "Naslednje kolo: 2. kolo", "Odjava od obvestil po kolu");
    }

    @Test
    void premiumSamoKjerJeVrstica() {
        assertTrue(SporociloKola.sestavi(kolo(true), igralec(PREMIUM)).telo().contains(PREMIUM));
        assertFalse(SporociloKola.sestavi(kolo(true), igralec(null)).telo().contains("S Premium"));
        assertFalse(SporociloKola.sestavi(kolo(false), spremljevalec()).telo().contains("S Premium"));
        assertFalse(SporociloKola.sestavi(kolo(false), spremljevalec()).html().contains("S Premium"));
    }

    @Test
    void igralecVidiSvojoEkipoInImeVPoudarku() {
        String html = SporociloKola.sestavi(kolo(true), igralec(null)).html();
        assertTrue(html.contains("<strong style=\"color:#0071ab;\">Team predelava lesa Cestnik</strong>"), html);
        assertTrue(html.contains("<strong style=\"color:#0071ab;\">Ana Novak</strong>"), html);
        assertTrue(html.contains("<strong style=\"color:#0071ab;\">Ana Novak / Bor Kos</strong>"), html);
    }

    @Test
    void spremljevalecNimaPoudarka() {
        String html = SporociloKola.sestavi(kolo(false), spremljevalec()).html();
        assertFalse(html.contains("color:#0071ab;\">Team predelava"), html);
        assertFalse(html.contains("color:#0071ab;\">Ana Novak"), html);
    }

    @Test
    void besedilnaRazlicicaOznaciMojoEkipoInDvojico() {
        String telo = SporociloKola.sestavi(kolo(true), igralec(null)).telo();
        assertTrue(telo.contains("» 1. Team predelava lesa Cestnik"), telo);
        assertTrue(telo.contains("» Team predelava lesa Cestnik – Mušketirji"), telo);
        assertTrue(telo.contains("zmaga 3 : 1 · Ana Novak / Bor Kos proti Aleksander Poropat / Gorazd Štor (+6)"), telo);
        assertFalse(telo.contains("dvojice"), telo);
    }

    @Test
    void naslednjeSrecanjeEkipeNiVSporocilu() {
        for (SporocilaPoste.Besedilo b : List.of(
                SporociloKola.sestavi(kolo(true), igralec(null)),
                SporociloKola.sestavi(kolo(false), spremljevalec()))) {
            assertFalse(b.telo().contains("Tvoja ekipa"), b.telo());
            assertFalse(b.html().contains("Tvoja ekipa"), b.html());
        }
        assertTrue(SporociloKola.sestavi(kolo(true), igralec(null)).telo().contains("Naslednje kolo: 2. kolo"));
    }

    private static void assertOrder(String besedilo, String... zaporedje) {
        int prej = -1;
        for (String del : zaporedje) {
            int i = besedilo.indexOf(del);
            assertTrue(i >= 0, "manjka: " + del);
            assertTrue(i > prej, "narobe vrstni red pri: " + del);
            prej = i;
        }
    }
}
