/* Opomba lige se shrani ob nastanku in popravi v pripravi, prazna pa pomeni brez opombe.
   Opomba ni pravilo tekmovanja, zato jo obrazec ureja kot ostala pravila lige. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;

class LigaOpombeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;

    @Test
    void opombaSeShraniInPopravi() {
        LigaDto liga = ligaStoritev.ustvari(vnos("Igra se v ŠD Golovec"));
        assertEquals("Igra se v ŠD Golovec", liga.opombe());

        LigaDto popravljena = ligaStoritev.uredi(liga.id(), vnos("  Dvorana se premakne  "));
        assertEquals("Dvorana se premakne", popravljena.opombe());
    }

    @Test
    void praznaOpombaPomeniBrezOpombe() {
        LigaDto liga = ligaStoritev.ustvari(vnos("Igra se v ŠD Golovec"));

        assertNull(ligaStoritev.uredi(liga.id(), vnos("   ")).opombe());
    }

    private static LigaVnos vnos(String opombe) {
        return new LigaVnos("Liga A", "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false,
                null, null, null, null, null, null, null, opombe);
    }
}
