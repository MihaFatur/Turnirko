/* Prikazni status lige: razpored je narejen (dejanski V_TEKU), a liga je se "V pripravi"
   do dneva prvega kola. Brez zacetka ostane kot doslej. Brez baze - LigaDto.iz le bere
   polja entitete. */
package si.turnirko.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;

class LigaPrikazniStatusTest {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    @Test
    void razporedPredZacetkomJeVPripravi() {
        Liga liga = liga(LocalDateTime.of(LocalDate.now(SLOVENIJA).plusDays(7), LocalTime.of(18, 30)));
        liga.setStatus(StatusTekmovanja.V_TEKU);

        LigaDto dto = LigaDto.iz(liga, 4);

        assertEquals(StatusTekmovanja.PRIPRAVA, dto.status());
        assertEquals(StatusTekmovanja.V_TEKU, dto.dejanskiStatus(),
                "streznik ostaja pri V_TEKU: razpored je narejen, kontrole so se v rokah organizatorja");
    }

    @Test
    void naDanZacetkaJeVTeku() {
        Liga liga = liga(LocalDateTime.of(LocalDate.now(SLOVENIJA), LocalTime.of(18, 30)));
        liga.setStatus(StatusTekmovanja.V_TEKU);

        LigaDto dto = LigaDto.iz(liga, 4);

        assertEquals(StatusTekmovanja.V_TEKU, dto.status());
        assertEquals(StatusTekmovanja.V_TEKU, dto.dejanskiStatus());
    }

    @Test
    void brezZacetkaJeVTekuKotDoslej() {
        Liga liga = liga(null);
        liga.setStatus(StatusTekmovanja.V_TEKU);

        LigaDto dto = LigaDto.iz(liga, 4);

        assertEquals(StatusTekmovanja.V_TEKU, dto.status());
        assertEquals(StatusTekmovanja.V_TEKU, dto.dejanskiStatus());
    }

    private static Liga liga(LocalDateTime zacetekPrvegaKola) {
        Liga liga = new Liga();
        liga.setIme("Testna liga");
        liga.setSpolKategorija(SpolKategorija.MESANO);
        liga.setZacetekPrvegaKola(zacetekPrvegaKola);
        return liga;
    }
}
