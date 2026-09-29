/* Ime lige se vedno zacne z veliko crko: obrazec jo popravi sproti, storitev pa
   je zadnja obramba za vnos mimo obrazca. Stevka na zacetku ostane. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;

class LigaImeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;

    @Test
    void imeLigeSeZacneZVelikoCrko() {
        assertEquals("Savinja liga", ligaStoritev.ustvari(vnos("savinja liga")).ime());
    }

    /* Slovenske crke z strešico niso izjema (š → Š, č → Č). */
    @Test
    void velikaJeTudiSlovenskaCrka() {
        assertEquals("Športna liga", ligaStoritev.ustvari(vnos("športna liga")).ime());
        assertEquals("Črna liga", ligaStoritev.ustvari(vnos("črna liga")).ime());
    }

    /* Presledek pred imenom se odreze, veliko postane prva prava crka. */
    @Test
    void presledekPredImenomNiZacetnaCrka() {
        assertEquals("Liga A", ligaStoritev.ustvari(vnos("  liga A ")).ime());
    }

    @Test
    void stevkaNaZacetkuOstane() {
        assertEquals("1. SNTL moški", ligaStoritev.ustvari(vnos("1. SNTL moški")).ime());
    }

    /* Ostale crke imena se ne dotaknemo (SNTL ostane SNTL, ne Sntl). */
    @Test
    void preostaloImeOstaneNespremenjeno() {
        assertEquals("Savinja LIGA b", ligaStoritev.ustvari(vnos("savinja LIGA b")).ime());
    }

    /* Isto pravilo velja pri urejanju pravil (PUT), ne le ob ustvarjanju. */
    @Test
    void imeSePopraviTudiPriUrejanju() {
        LigaDto liga = ligaStoritev.ustvari(vnos("Liga A"));

        assertEquals("Liga b", ligaStoritev.uredi(liga.id(), vnos("liga b")).ime());
    }

    private LigaVnos vnos(String ime) {
        return new LigaVnos(ime, "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null);
    }
}
