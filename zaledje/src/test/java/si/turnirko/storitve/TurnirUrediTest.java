/* Popravek turnirja v pripravi (ime, dvorana, datumi, opombe, raven).

   Domenska pravila, ki jih test varuje:
   - dokler je turnir v pripravi, se njegovi podatki lahko popravijo,
   - po zrebu se zaklenejo, kot pravila dogodka - zreb je iz tekmovanja naredil tekme,
   - datum konca ne sme biti pred zacetkom, in sicer tako pri popravku kot pri vnosu. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.TurnirVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;

class TurnirUrediTest extends IntegracijskiTest {

    @Test
    void popravekVPripraviVelja() {
        Long id = pripraviTurnir().getId();

        turnirjiStoritev.urediTurnir(id, new TurnirVnos("Novo ime", null, "Dvorana B",
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), "Parkirisce zadaj",
                RavenTekmovanja.REKREATIVNO));

        Turnir popravljen = turnirRepozitorij.findById(id).orElseThrow();
        assertEquals("Novo ime", popravljen.getIme());
        assertEquals("Dvorana B", popravljen.getDvorana());
        assertEquals(LocalDate.of(2026, 11, 7), popravljen.getDatumZacetka());
        assertEquals(LocalDate.of(2026, 11, 8), popravljen.getDatumKonca());
        assertEquals("Parkirisce zadaj", popravljen.getOpombe());
        assertEquals(RavenTekmovanja.REKREATIVNO, popravljen.getRaven());
    }

    /* Zreb iz pravil naredi tekme in turnir preide v V_TEKU. Po tem popravek ni dovoljen,
       tudi ce bi bil sicer veljaven, ker bi tiho spremenil tekmovanje, ki tece. */
    @Test
    void poZrebuSeTurnirNeUredi() {
        Dogodek dogodek = pripraviDogodek(4);
        Long id = dogodek.getTurnir().getId();
        zrebStoritev.izvediZreb(dogodek.getId());
        assertEquals(StatusTekmovanja.V_TEKU, turnirRepozitorij.findById(id).orElseThrow().getStatus());

        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.urediTurnir(id,
                new TurnirVnos("Po zrebu", null, null, null, null, null, RavenTekmovanja.URADNO)));
        assertEquals("Testni turnir", turnirRepozitorij.findById(id).orElseThrow().getIme());
    }

    @Test
    void datumKoncaNePredZacetkom() {
        Long id = pripraviTurnir().getId();

        assertThrows(NeveljavenVnosIzjema.class, () -> turnirjiStoritev.urediTurnir(id,
                vnos(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7))));
        assertThrows(NeveljavenVnosIzjema.class, () -> turnirjiStoritev.ustvari(
                vnos(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7))));
        assertEquals(LocalDate.of(2026, 11, 7), turnirRepozitorij.findById(id).orElseThrow().getDatumZacetka());
    }

    private Turnir pripraviTurnir() {
        Turnir turnir = new Turnir();
        turnir.setIme("Testni turnir");
        turnir.setDatumZacetka(LocalDate.of(2026, 11, 7));
        turnir.setRaven(RavenTekmovanja.URADNO);
        return turnirRepozitorij.save(turnir);
    }

    private static TurnirVnos vnos(LocalDate zacetek, LocalDate konec) {
        return new TurnirVnos("Testni turnir", null, null, zacetek, konec, null,
                RavenTekmovanja.URADNO);
    }
}
