/* Iskanje tekme po imenih z listka (UjemanjeTekmeListka).

   Domenska pravila, ki jih ta test varuje:
   - priimka na obeh straneh izbereta tekmo, tudi brez sumnikov in z zacetnico;
   - ena crka razlike pri daljsem priimku je rokopis, pri kratkem ne;
   - zamenjan vrstni red imen je »obrnjeno«;
   - isti par v dveh dogodkih loci kategorija; brez nje izbere organizator;
   - par (dvojice) mora imeti oba igralca;
   - ena sama ujemajoca se stran ni dovolj za samodejno izbiro. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.storitve.UjemanjeTekmeListka.Izid;
import si.turnirko.storitve.UjemanjeTekmeListka.Kandidat;
import si.turnirko.storitve.UjemanjeTekmeListka.Oseba;

class UjemanjeTekmeListkaTest {

    private static final Kandidat NOVAK_KRANJC = new Kandidat(1L,
            List.of(new Oseba("Ana", "Novak")), List.of(new Oseba("Tim", "Kranjc")), "Člani");
    private static final Kandidat KOVACIC_ZUPAN = new Kandidat(2L,
            List.of(new Oseba("Žan", "Kovačič")), List.of(new Oseba("Luka", "Zupan")), "Člani");
    private static final Kandidat KOS_HORVAT = new Kandidat(3L,
            List.of(new Oseba("Jan", "Kos")), List.of(new Oseba("Nik", "Horvat")), "Člani");

    @Test
    void priimkaNaObehStraneh() {
        Izid izid = UjemanjeTekmeListka.najdi("Ana Novak", "Tim Kranjc", null,
                List.of(NOVAK_KRANJC, KOVACIC_ZUPAN, KOS_HORVAT));
        assertNotNull(izid.izbrana());
        assertEquals(1L, izid.izbrana().idTekme());
        assertFalse(izid.izbrana().obrnjeno());
    }

    @Test
    void brezSumnikovInZZacetnico() {
        Izid izid = UjemanjeTekmeListka.najdi("Kovacic Z.", "ZUPAN L", null, List.of(NOVAK_KRANJC, KOVACIC_ZUPAN));
        assertEquals(2L, izid.izbrana().idTekme());
    }

    @Test
    void enaCrkaPriDaljsemPriimku() {
        assertEquals(2L, UjemanjeTekmeListka.najdi("Kovačis", "Zupan", null,
                List.of(KOVACIC_ZUPAN)).izbrana().idTekme());
        // »Kot« namesto »Kos«: kratek priimek mora biti enak
        assertNull(UjemanjeTekmeListka.najdi("Kot", "Horvat", null, List.of(KOS_HORVAT)).izbrana());
    }

    @Test
    void zamenjanVrstniRedJeObrnjeno() {
        Izid izid = UjemanjeTekmeListka.najdi("Kranjc", "Novak", null, List.of(NOVAK_KRANJC, KOS_HORVAT));
        assertEquals(1L, izid.izbrana().idTekme());
        assertTrue(izid.izbrana().obrnjeno());
    }

    @Test
    void istiParVDvehDogodkihLociKategorija() {
        Kandidat u15 = new Kandidat(10L, List.of(new Oseba("Ana", "Novak")),
                List.of(new Oseba("Tim", "Kranjc")), "U15 dečki in deklice");
        Kandidat u18 = new Kandidat(11L, List.of(new Oseba("Ana", "Novak")),
                List.of(new Oseba("Tim", "Kranjc")), "U18");

        Izid brez = UjemanjeTekmeListka.najdi("Novak", "Kranjc", null, List.of(u15, u18));
        assertNull(brez.izbrana(), "brez kategorije izbere organizator");
        assertEquals(2, brez.kandidati().size());

        // bonus kategorije ne zadosca za prednost - organizator izbere, a pravi je prvi
        Izid s = UjemanjeTekmeListka.najdi("Novak", "Kranjc", "U18", List.of(u15, u18));
        assertEquals(11L, s.kandidati().getFirst().idTekme());
    }

    @Test
    void parPotrebujeObaIgralca() {
        Kandidat dvojice = new Kandidat(20L,
                List.of(new Oseba("Ana", "Novak"), new Oseba("Eva", "Kos")),
                List.of(new Oseba("Tim", "Kranjc"), new Oseba("Nik", "Horvat")), "Dvojice");
        assertEquals(20L, UjemanjeTekmeListka.najdi("Novak / Kos", "Kranjc / Horvat", null,
                List.of(dvojice)).izbrana().idTekme());
        assertNull(UjemanjeTekmeListka.najdi("Novak", "Kranjc", null, List.of(dvojice)).izbrana());
    }

    @Test
    void enaStranNiDovolj() {
        Izid izid = UjemanjeTekmeListka.najdi("Novak", "Petek", null, List.of(NOVAK_KRANJC));
        assertNull(izid.izbrana());
    }
}
