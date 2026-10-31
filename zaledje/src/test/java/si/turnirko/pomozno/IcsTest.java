/* Zapis koledarja iCalendar (Ics): pasti formata, ki jih koledar v telefonu
   kaznuje tiho (dogodek preprosto ne pride v koledar). */
package si.turnirko.pomozno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class IcsTest {

    private static final LocalDateTime ZDAJ = LocalDateTime.of(2026, 10, 30, 12, 0);

    /* Slovenski cas gre v UTC: pozimi (CET) je razlika ena ura, poleti (CEST) dve. */
    @Test
    void casGreVUtcPoSlovenskiPoletniInZimskiUri() {
        assertEquals("20261107T160000Z", Ics.utc(LocalDateTime.of(2026, 11, 7, 17, 0)));
        assertEquals("20260701T150000Z", Ics.utc(LocalDateTime.of(2026, 7, 1, 17, 0)));
    }

    @Test
    void besediloUbezeLocilaInNoviRed() {
        assertEquals("a\\;b\\,c\\\\d\\ne", Ics.ubezi("a;b,c\\d\ne"));
        assertEquals("vrstica1\\nvrstica2", Ics.ubezi("vrstica1\r\nvrstica2"));
    }

    @Test
    void vrsticeSoDolgeNajvec75OktetovInKoncajoSCrlf() {
        // sumniki so v UTF-8 dva okteta: vrstica brez pazljivega loma bi presegla mejo
        String dolgo = "Železničarski Namiznoteniški klub Maribor – Športno društvo Škofja Loka, "
                + "1. SNTL MOŠKI 2026-2027, 3. kolo".repeat(2);
        String ics = Ics.koledar("Turnirko", 6)
                .dogodek("srecanje-1@turnirko-nt.si", ZDAJ, LocalDateTime.of(2026, 11, 7, 17, 0),
                        LocalDateTime.of(2026, 11, 7, 20, 0), dolgo, "opis", null, null)
                .zakljuci();

        for (String vrstica : ics.split("\r\n", -1)) {
            assertTrue(vrstica.getBytes(StandardCharsets.UTF_8).length <= 75,
                    "predolga vrstica: " + vrstica.getBytes(StandardCharsets.UTF_8).length);
        }
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertFalse(ics.contains("\n") && ics.replace("\r\n", "").contains("\n"), "samo CRLF");
        // prelomljeno besedilo se po odstranitvi preloma bere kot prej
        String zdruzeno = ics.replace("\r\n ", "");
        assertTrue(zdruzeno.contains("SUMMARY:Železničarski Namiznoteniški klub Maribor – Športno"), zdruzeno);
    }

    @Test
    void celDanImaIzkljucenKonec() {
        String ics = Ics.koledar("Turnirko", 6)
                .celDan("turnir-5@turnirko-nt.si", ZDAJ, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15),
                        "Turnir", null, "Dvorana, Žalec", "https://turnirko-nt.si/turnirji/5")
                .zakljuci();

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261114"), ics);
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261116"), "konec je izkljucen, zato dan po zadnjem: " + ics);
        assertTrue(ics.contains("LOCATION:Dvorana\\, Žalec"), ics);
        assertTrue(ics.contains("UID:turnir-5@turnirko-nt.si"));
        assertTrue(ics.contains("BEGIN:VCALENDAR") && ics.contains("VERSION:2.0"));
        assertFalse(ics.contains("DESCRIPTION"), "prazen opis se ne izpise");
    }
}
