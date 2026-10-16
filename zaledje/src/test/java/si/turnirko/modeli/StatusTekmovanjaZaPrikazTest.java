/* Prikazni status: zreb pred datumom zacetka je gledalcu "V pripravi", na dan
   zacetka in po njem "V teku", brez datuma ostane kot doslej. Pravilo se dotika
   samo V_TEKU - PRIPRAVA in ZAKLJUCEN se ne spreminjata (brez baze). */
package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class StatusTekmovanjaZaPrikazTest {

    private static final LocalDate DANES = LocalDate.of(2026, 10, 16);

    @Test
    void zrebPredDatumomJeVPripravi() {
        assertEquals(StatusTekmovanja.PRIPRAVA,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.V_TEKU, DANES.plusDays(9), DANES));
    }

    @Test
    void naDanZacetkaJeVTeku() {
        assertEquals(StatusTekmovanja.V_TEKU,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.V_TEKU, DANES, DANES));
    }

    @Test
    void poDatumuZacetkaJeVTeku() {
        assertEquals(StatusTekmovanja.V_TEKU,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.V_TEKU, DANES.minusDays(1), DANES));
    }

    @Test
    void brezDatumaJeVTekuKotDoslej() {
        assertEquals(StatusTekmovanja.V_TEKU,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.V_TEKU, null, DANES));
    }

    @Test
    void praviloSeDotikaSamoVTeku() {
        assertEquals(StatusTekmovanja.PRIPRAVA,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.PRIPRAVA, DANES.plusDays(9), DANES));
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                StatusTekmovanja.zaPrikaz(StatusTekmovanja.ZAKLJUCEN, DANES.plusDays(9), DANES));
    }
}
