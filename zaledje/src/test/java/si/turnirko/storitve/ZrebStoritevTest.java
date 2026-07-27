/* Testi zreba izlocilne mreze: struktura mreze, prosta mesta,
   eksplicitne povezave napredovanja in domenske varovalke. */
package si.turnirko.storitve;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;

class ZrebStoritevTest extends IntegracijskiTest {

    @BeforeEach
    void deterministicniZreb() {
        // s fiksnim semenom je zreb v testih vedno enak
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    @Test
    void mrezaZaOsemIgralcev() {
        Dogodek dogodek = pripraviDogodek(8);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(7, tekme.size(), "8 igralcev -> 7 tekem");

        List<Tekma> prvoKolo = tekme.stream().filter(t -> t.getKolo() == 1).toList();
        assertEquals(4, prvoKolo.size());
        assertTrue(prvoKolo.stream().allMatch(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA),
                "brez prostih mest so vse tekme 1. kola pripravljene");

        // vsak igralec nastopi natanko enkrat v 1. kolu
        long razlicnih = prvoKolo.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getPrijava1(), t.getPrijava2()))
                .map(p -> p.getId())
                .distinct().count();
        assertEquals(8, razlicnih);

        // visja kola imajo eksplicitne povezave na izvorni tekmi
        tekme.stream().filter(t -> t.getKolo() > 1).forEach(t -> {
            assertNotNull(t.getIdIzvorTekma1(), "tekma " + t.getKolo() + "/" + t.getPozicija());
            assertNotNull(t.getIdIzvorTekma2());
            assertEquals(StatusTekme.CAKA, t.getStatus());
        });
    }

    @Test
    void petIgralcevDobiTriProstaMesta() {
        Dogodek dogodek = pripraviDogodek(5);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(7, tekme.size(), "mreza se razsiri na 8 -> 7 tekem");

        List<Tekma> prosta = tekme.stream()
                .filter(t -> t.getIzidTip() == IzidTekme.PROSTO).toList();
        assertEquals(3, prosta.size(), "8 - 5 = 3 prosta mesta");

        for (Tekma tekma : prosta) {
            assertEquals(1, tekma.getKolo(), "prosta mesta so samo v 1. kolu");
            assertEquals(StatusTekme.KONCANA, tekma.getStatus());
            assertNotNull(tekma.getZmagovalec(), "igralec ob prostem mestu napreduje");
        }

        // igralci s prostim prehodom so ze vpisani v 2. kolo
        for (Tekma tekma : prosta) {
            boolean vpisan = tekme.stream()
                    .filter(t -> t.getKolo() == 2)
                    .anyMatch(t -> jeUdelezenec(t, tekma.getZmagovalec().getId()));
            assertTrue(vpisan, "zmagovalec prostega mesta mora biti v 2. kolu");
        }

        // dve prosti mesti se nikoli ne srecata
        assertTrue(prosta.stream().allMatch(t -> t.getPrijava1() != null || t.getPrijava2() != null));
    }

    @Test
    void zrebSpremeniStatusaDogodkaInTurnirja() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals(StatusTekmovanja.V_TEKU, dogodek.getStatus());
        assertEquals(StatusTekmovanja.V_TEKU, dogodek.getTurnir().getStatus());
    }

    @Test
    void ponovniZrebNiMogoc() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediZreb(dogodek.getId()));
    }

    @Test
    void premaloIgralcevZaZreb() {
        Dogodek dogodek = pripraviDogodek(1);
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediZreb(dogodek.getId()));
    }

    private boolean jeUdelezenec(Tekma tekma, Long idPrijave) {
        return (tekma.getPrijava1() != null && tekma.getPrijava1().getId().equals(idPrijave))
                || (tekma.getPrijava2() != null && tekma.getPrijava2().getId().equals(idPrijave));
    }
}
