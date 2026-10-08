/* Pravilo poteka lige (PotekLige) - cista funkcija, brez baze.

   Primer, zaradi katerega je pravilo eno: v 1. kolu Savinja lige B (24. 9.)
   sta dve srecanji ostali brez vpisanega izida. Stran lige je zato do 7. 10.
   trdila "0. od 6 kol · naslednje 24. 9.", domaca stran pa za isto ligo
   "1. od 6 kol · naslednje 15. 10." - test drzi, da odgovor zdaj sledi
   datumu. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.modeli.StatusSrecanja;

class PotekLigeTest {

    private static final LocalDate DANES = LocalDate.of(2026, 10, 7);

    private static PotekLige.Termin srecanje(int kolo, StatusSrecanja status, LocalDate dan) {
        return new PotekLige.Termin(kolo, status, dan == null ? null : dan.atTime(18, 30), false);
    }

    /* Savinja B: 1. kolo 24. 9. (eno srecanje brez izida), 2. kolo 15. 10. */
    @Test
    void nevpisanIzidNeZadrziKolaInNiNaslednji() {
        List<PotekLige.Termin> s = List.of(
                srecanje(1, StatusSrecanja.KONCANO, LocalDate.of(2026, 9, 24)),
                srecanje(1, StatusSrecanja.RAZPORED, LocalDate.of(2026, 9, 24)),
                srecanje(2, StatusSrecanja.RAZPORED, LocalDate.of(2026, 10, 15)),
                srecanje(3, StatusSrecanja.RAZPORED, LocalDate.of(2026, 10, 28)));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(List.of(1), p.odigranaKola());
        assertEquals(3, p.vseh());
        assertEquals(new PotekLige.Naslednje(2, LocalDate.of(2026, 10, 15)), p.naslednje(),
                "srecanje s starim datumom in brez izida ni naslednje");
    }

    /* Danes je kolo se naslednje - tudi ce je del srecanj ze odigran. */
    @Test
    void danasnjeKoloJeNaslednjeInNeOdigrano() {
        List<PotekLige.Termin> s = List.of(
                srecanje(1, StatusSrecanja.KONCANO, DANES.minusDays(7)),
                srecanje(2, StatusSrecanja.KONCANO, DANES),
                srecanje(2, StatusSrecanja.POTEKA, DANES));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(List.of(1), p.odigranaKola());
        assertEquals(new PotekLige.Naslednje(2, DANES), p.naslednje());
    }

    /* Neuradna menjava termina: srecanje poznega kola je ze odigrano, a kolo
       zato ni odigrano - datum je pred nami. */
    @Test
    void zgodajOdigranoSrecanjeKolaNeOdigra() {
        List<PotekLige.Termin> s = List.of(
                srecanje(1, StatusSrecanja.KONCANO, DANES.minusDays(7)),
                srecanje(3, StatusSrecanja.KONCANO, DANES.plusDays(14)),
                srecanje(3, StatusSrecanja.RAZPORED, DANES.plusDays(14)),
                srecanje(2, StatusSrecanja.RAZPORED, DANES.plusDays(7)));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(List.of(1), p.odigranaKola());
        assertEquals(2, p.naslednje().kolo());
    }

    /* Prestavljeno srecanje starejsega kola, ki se igra PRED naslednjim
       kolom, je naslednje. */
    @Test
    void prestavljenoSrecanjeStarejsegaKolaJeNaslednjeCeJePrej() {
        List<PotekLige.Termin> s = List.of(
                srecanje(2, StatusSrecanja.RAZPORED, DANES.plusDays(7)),
                srecanje(3, StatusSrecanja.RAZPORED, DANES.plusDays(14)),
                srecanje(3, StatusSrecanja.RAZPORED, DANES.plusDays(2)));

        assertEquals(new PotekLige.Naslednje(3, DANES.plusDays(2)),
                PotekLige.izracunaj(s, DANES).naslednje());
    }

    /* Brez datuma je edino merilo koncanost; kolo brez termina je kandidat
       za naslednje (ne vemo, da je mimo). */
    @Test
    void koloBrezDatumaSteKoncanost() {
        List<PotekLige.Termin> s = List.of(
                srecanje(1, StatusSrecanja.KONCANO, null),
                srecanje(1, StatusSrecanja.KONCANO, null),
                srecanje(2, StatusSrecanja.KONCANO, null),
                srecanje(2, StatusSrecanja.RAZPORED, null));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(List.of(1), p.odigranaKola());
        assertEquals(new PotekLige.Naslednje(2, null), p.naslednje());
    }

    /* Koncnica v stevcu kol ne steje, v "naslednje" pa (domaca stran po
       rednem delu obljublja naslednjo tekmo serije). */
    @Test
    void koncnicaNeStejeVKoleInJeLahkoNaslednja() {
        List<PotekLige.Termin> s = new ArrayList<>(List.of(
                srecanje(1, StatusSrecanja.KONCANO, DANES.minusDays(14)),
                srecanje(2, StatusSrecanja.KONCANO, DANES.minusDays(7))));
        s.add(new PotekLige.Termin(1, StatusSrecanja.RAZPORED, DANES.plusDays(3).atTime(18, 0), true));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(2, p.vseh());
        assertEquals(2, p.odigranih());
        assertEquals(new PotekLige.Naslednje(1, DANES.plusDays(3)), p.naslednje());
    }

    @Test
    void vseOdigranoNimaNaslednjega() {
        List<PotekLige.Termin> s = List.of(
                srecanje(1, StatusSrecanja.KONCANO, DANES.minusDays(14)),
                srecanje(2, StatusSrecanja.RAZPORED, DANES.minusDays(7)));

        PotekLige.Potek p = PotekLige.izracunaj(s, DANES);

        assertEquals(2, p.odigranih());
        assertNull(p.naslednje(), "nevpisan izid preteklega kola ni naslednje kolo");
    }

    @Test
    void ligaBrezSrecanj() {
        PotekLige.Potek p = PotekLige.izracunaj(List.of(), DANES);

        assertEquals(0, p.vseh());
        assertEquals(0, p.odigranih());
        assertNull(p.naslednje());
    }

    /* Ura ne premakne dneva: srecanje ob 00.00 je se vedno tisti dan. */
    @Test
    void uraNeSpremeniDneva() {
        List<PotekLige.Termin> s = List.of(
                new PotekLige.Termin(1, StatusSrecanja.RAZPORED, LocalDateTime.of(DANES, LocalTime.MIDNIGHT), false));

        assertEquals(new PotekLige.Naslednje(1, DANES), PotekLige.izracunaj(s, DANES).naslednje());
    }
}
