/* Izbor lig iz parametra javne poti GET /domov/lige.

   Vsaka liga v izboru pomeni poln izracun lestvice. Do novembra 2026 dolzina
   seznama ni bila omejena, zato je en zahtevek gosta z nekaj sto id-ji sprozil
   nekaj sto izracunov. Pravilo je cista funkcija, zato test ne potrebuje baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class DomovIzborLigTest {

    @Test
    void obicajenIzborOstaneNespremenjen() {
        assertEquals(List.of(7L, 3L, 12L), DomovStoritev.omejiIzbor(List.of(7L, 3L, 12L)),
                "vrstni red sledi izboru racuna");
    }

    @Test
    void predolgSeznamSeOdrezeNaMejo() {
        List<Long> petsto = LongStream.rangeClosed(1, 500).boxed().toList();

        List<Long> omejen = DomovStoritev.omejiIzbor(petsto);

        assertEquals(DomovStoritev.NAJVEC_LIG_V_ZAHTEVKU, omejen.size());
        assertEquals(petsto.subList(0, DomovStoritev.NAJVEC_LIG_V_ZAHTEVKU), omejen,
                "obveljajo prve lige izbora, presezek tiho odpade");
    }

    /* Ista liga, ponovljena petstokrat, je bila prej petsto izracunov iste lestvice. */
    @Test
    void ponovitveInPrazneVrednostiOdpadejo() {
        List<Long> zPonovitvami = new ArrayList<>(Arrays.asList(5L, null, 5L, 9L, 5L, null, 9L));

        assertEquals(List.of(5L, 9L), DomovStoritev.omejiIzbor(zPonovitvami));
    }
}
