/* Testi pravila odbitka za neaktivnost - cista funkcija casa, brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class NeaktivnostTest {

    private static final LocalDateTime ZADNJA = LocalDate.of(2025, 1, 10).atStartOfDay();

    private static LocalDateTime cez(int mesecev) {
        return ZADNJA.plusMonths(mesecev);
    }

    /* Pod sestimi meseci ni odbitka - premor cez poletje ni neaktivnost. */
    @Test
    void podSestimiMeseciNiOdbitka() {
        assertTrue(Neaktivnost.zapadli(ZADNJA, cez(5), 0).isEmpty());
    }

    /* Stopnje so kumulativne: -10, nato skupno -25, nato skupno -40. */
    @Test
    void stopnjeSoKumulativne() {
        assertEquals(List.of(10),
                Neaktivnost.zapadli(ZADNJA, cez(6), 0).stream().map(Neaktivnost.Odbitek::tock).toList());
        assertEquals(List.of(10, 15),
                Neaktivnost.zapadli(ZADNJA, cez(12), 0).stream().map(Neaktivnost.Odbitek::tock).toList());
        assertEquals(List.of(10, 15, 15),
                Neaktivnost.zapadli(ZADNJA, cez(24), 0).stream().map(Neaktivnost.Odbitek::tock).toList());
    }

    /* Po dveh letih se odbijanje USTAVI - kdor se ne vrne, ne tone v
       neskoncnost; z lestvice ga umakne pravilo 18 mesecev. */
    @Test
    void poDvehLetihSeUstavi() {
        assertEquals(3, Neaktivnost.zapadli(ZADNJA, cez(60), 0).size());
        assertEquals(Neaktivnost.stopenj(), Neaktivnost.zapadli(ZADNJA, cez(120), 0).size());
    }

    /* Ze uveljavljene stopnje se preskocijo - isti odbitek se ne sme zapisati
       dvakrat, ce dnevno opravilo tece vsak dan. */
    @Test
    void zeUveljavljeneStopnjeSePreskocijo() {
        assertTrue(Neaktivnost.zapadli(ZADNJA, cez(7), 1).isEmpty());
        assertEquals(1, Neaktivnost.zapadli(ZADNJA, cez(13), 1).size());
        assertTrue(Neaktivnost.zapadli(ZADNJA, cez(13), 2).isEmpty());
    }

    /* Odbitek velja na dan, ko ZAPADE, in ne na dan vpisa - le tako ga zna
       ponovni preracun postaviti na isto mesto v casovno vrsto. */
    @Test
    void odbitekVeljaNaDanZapadlosti() {
        List<Neaktivnost.Odbitek> odbitki = Neaktivnost.zapadli(ZADNJA, cez(24), 0);
        assertEquals(cez(6), odbitki.get(0).velja());
        assertEquals(cez(12), odbitki.get(1).velja());
        assertEquals(cez(24), odbitki.get(2).velja());
    }

    /* Skritje z javne lestvice je locen prag (18 mesecev) in ne odbitek. */
    @Test
    void poOsemnajstihMesecihIzgineZJavneLestvice() {
        assertTrue(Neaktivnost.naJavniLestvici(ZADNJA, cez(17)));
        assertTrue(Neaktivnost.naJavniLestvici(ZADNJA, cez(18)));
        assertFalse(Neaktivnost.naJavniLestvici(ZADNJA, cez(19)));
        /* Brez znanega termina igralca NE skrijemo: iz nevednosti ne smemo
           sklepati na neaktivnost. Sicer bi igralec, ki je odigral samo tekme
           brez datuma (uvoz brez termina srecanja), izginil z lestvice. */
        assertTrue(Neaktivnost.naJavniLestvici(null, cez(1)),
                "brez znanega termina zadnje tekme igralec ostane na lestvici");
    }
}
