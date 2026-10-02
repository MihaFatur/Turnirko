/* Kateri dogodki zveze v zgodovino ne sodijo. Seznam ni okras: dogodek, ki ga
   filter spusti skozi, gre ob uvozu v rating vseh igralcev, ki so v njem
   zapisani - tudi kadar so tekme izmisljene (vaja za zapisnikarje). */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestniDogodkiTest {

    @Test
    void testniInVadbeniDogodkiNeSodijoVZgodovino() {
        assertTrue(UvozStupeStoritev.jeTestni("Samo - TEST turnir"));
        assertTrue(UvozStupeStoritev.jeTestni("S15 Test"));
        // Stupa 243: vaja za zapisnikarje s 185 neodigranimi tekmami
        assertTrue(UvozStupeStoritev.jeTestni("Scoring Practise Event"));
        assertTrue(UvozStupeStoritev.jeTestni("Practice event"));
    }

    @Test
    void pravaTekmovanjaOstanejo() {
        assertFalse(UvozStupeStoritev.jeTestni("1. OT ZA KADETE U-15, KIDRIČEVO, 12. 9. 2026"));
        assertFalse(UvozStupeStoritev.jeTestni("35. DP ZA ČLANE IN ČLANICE POSAMEZNO IN DVOJICE"));
        assertFalse(UvozStupeStoritev.jeTestni("2. SNTL - Moški 2024-2025"));
        assertFalse(UvozStupeStoritev.jeTestni(null));
    }
}
