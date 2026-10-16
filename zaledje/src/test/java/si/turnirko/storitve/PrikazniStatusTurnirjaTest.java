/* Zreb pred datumom turnirja: gledalec vidi "V pripravi" (turnir, dogodek, stevci,
   faza), streznik pa ostane pri V_TEKU - prijave so zaprte, zreb je popravljiv.
   Na dan turnirja se pokaze "V teku", brez datuma je vse kot doslej. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DogodekDto;
import si.turnirko.dto.TurnirDto;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;

class PrikazniStatusTurnirjaTest extends IntegracijskiTest {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    @Autowired private PovzetkiStoritev povzetkiStoritev;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    @Test
    void zrebPredDatumomTurnirjaJeVPripravi() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(LocalDate.now(SLOVENIJA).plusDays(7));

        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(StatusTekmovanja.PRIPRAVA, TurnirDto.iz(turnir).status());
        DogodekDto dto = DogodekDto.iz(dogodek);
        assertEquals(StatusTekmovanja.PRIPRAVA, dto.status());
        assertEquals(StatusTekmovanja.V_TEKU, dto.dejanskiStatus(),
                "streznik ostaja pri V_TEKU: prijave so zaprte, zreb je popravljiv");

        TurnirDto.Stevci stevci = povzetkiStoritev.zaTurnir(turnir.getId());
        assertEquals(0, stevci.dogodkovVTeku());
        assertEquals(1, stevci.dogodkovVPripravi());

        assertNull(povzetkiStoritev.potekiVsehTurnirjev().get(turnir.getId()).faza(),
                "pred datumom ni faze - turnir ne tekmuje");
    }

    @Test
    void naDanTurnirjaJeVTeku() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(LocalDate.now(SLOVENIJA));

        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(StatusTekmovanja.V_TEKU, TurnirDto.iz(turnir).status());
        assertEquals(StatusTekmovanja.V_TEKU, DogodekDto.iz(dogodek).status());
        assertEquals(1, povzetkiStoritev.zaTurnir(turnir.getId()).dogodkovVTeku());
    }

    @Test
    void brezDatumaJeVTekuTakoJKotDoslej() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(null);

        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(StatusTekmovanja.V_TEKU, TurnirDto.iz(turnir).status());
        assertEquals(StatusTekmovanja.V_TEKU, DogodekDto.iz(dogodek).status());
    }
}
