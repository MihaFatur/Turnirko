/* Testi formata srecanja - preverjajo tocen vrstni red postavitev (brez baze). */
package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FormatSrecanjaTest {

    @Test
    void sntlImaDvojicePrveInTocenVrstniRedPosamicnih() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.SNTL.razpored();
        assertEquals(10, r.size(), "SNTL: dvojice + 9 posamicnih");

        // prva tekma so dvojice
        assertEquals(TipTekmeSrecanja.DVOJICE, r.get(0).tip());
        assertEquals("dvojice", r.get(0).oznaka());

        // tocen vrstni red posamicnih: 1. krog A-X,B-Y,C-Z; 2. krog B-X,A-Z,C-Y;
        // 3. krog B-Z,C-X,A-Y
        List<String> pricakovano = List.of(
                "dvojice", "A-X", "B-Y", "C-Z", "B-X", "A-Z", "C-Y", "B-Z", "C-X", "A-Y");
        List<String> dejansko = r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(pricakovano, dejansko);

        // vseh 9 kombinacij (A,B,C) x (X,Y,Z) se pojavi natanko enkrat
        long stPosamicnih = r.stream().filter(m -> m.tip() == TipTekmeSrecanja.POSAMICNA).count();
        assertEquals(9, stPosamicnih);
        assertEquals(9, r.stream()
                .filter(m -> m.tip() == TipTekmeSrecanja.POSAMICNA)
                .map(FormatSrecanja.MestoTekme::oznaka)
                .distinct().count(), "vsaka postavitev natanko enkrat");
    }

    @Test
    void sntlMesta() {
        assertEquals(List.of("A", "B", "C"), FormatSrecanja.SNTL.pozicijeDomaci());
        assertEquals(List.of("X", "Y", "Z"), FormatSrecanja.SNTL.pozicijeGost());
        assertEquals(3, FormatSrecanja.SNTL.getStIgralcev());
        assertTrue(FormatSrecanja.SNTL.imaDvojice());
        assertTrue(FormatSrecanja.SNTL.izbiraDvojice(), "pri SNTL se par izbere (2 od 3)");
        assertEquals(2, FormatSrecanja.SNTL.stVDvojici());
    }

    @Test
    void corbillonImaDvojiceNaSredini() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.CORBILLON.razpored();
        assertEquals(5, r.size());
        List<String> dejansko = r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(List.of("A-X", "B-Y", "dvojice", "A-Y", "B-X"), dejansko);
        assertEquals(2, FormatSrecanja.CORBILLON.getStIgralcev());
        assertTrue(FormatSrecanja.CORBILLON.imaDvojice());
        assertTrue(!FormatSrecanja.CORBILLON.izbiraDvojice(), "pri Corbillon igrata oba igralca");
    }

    /* Savinja je Corbillon z dvojicami na zacetku - razlika je samo v vrstnem
       redu, zato ju je smiselno primerjati drugo ob drugem. */
    @Test
    void savinjaImaDvojicePrve() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.SAVINJA.razpored();
        assertEquals(5, r.size());
        List<String> dejansko = r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(List.of("dvojice", "A-X", "B-Y", "A-Y", "B-X"), dejansko);
        assertEquals(TipTekmeSrecanja.DVOJICE, r.get(0).tip());

        assertEquals(2, FormatSrecanja.SAVINJA.getStIgralcev());
        assertEquals(List.of("A", "B"), FormatSrecanja.SAVINJA.pozicijeDomaci());
        assertEquals(List.of("X", "Y"), FormatSrecanja.SAVINJA.pozicijeGost());
        assertTrue(FormatSrecanja.SAVINJA.imaDvojice());
        assertTrue(!FormatSrecanja.SAVINJA.izbiraDvojice(), "pri Savinji igrata oba igralca");
        assertEquals(2, FormatSrecanja.SAVINJA.stVDvojici());

        // ista mnozica tekem kot Corbillon, drugacen vrstni red
        assertEquals(
                FormatSrecanja.CORBILLON.razpored().stream()
                        .map(FormatSrecanja.MestoTekme::oznaka).sorted().toList(),
                dejansko.stream().sorted().toList());
    }
}
