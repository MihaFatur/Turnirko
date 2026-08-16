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

    /* 1. SNTL moski: dvojice + samo 6 posamicnih. Vsak igralec odigra DVA od
       treh nasprotnikov - to je bistvena razlika proti SNTL in edino, kar
       preprecuje, da bi ligo vodili po napacnem razporedu. */
    @Test
    void sntlPrvaImaSestPosamicnihInVsakIgralecDvaNasprotnika() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.SNTL_PRVA.razpored();
        assertEquals(7, r.size(), "SNTL_PRVA: dvojice + 6 posamicnih");

        assertEquals(TipTekmeSrecanja.DVOJICE, r.get(0).tip());
        List<String> dejansko = r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(List.of("dvojice", "B-X", "A-Z", "C-Y", "B-Z", "C-X", "A-Y"), dejansko);

        // vsako mesto (domace in gostujoce) nastopi natanko dvakrat
        for (String mesto : List.of("A", "B", "C", "X", "Y", "Z")) {
            long nastopov = r.stream()
                    .filter(m -> m.tip() == TipTekmeSrecanja.POSAMICNA)
                    .filter(m -> mesto.equals(m.domaci()) || mesto.equals(m.gost()))
                    .count();
            assertEquals(2, nastopov, "mesto " + mesto + " odigra dve posamicni tekmi");
        }
        assertEquals(3, FormatSrecanja.SNTL_PRVA.getStIgralcev());
        assertTrue(FormatSrecanja.SNTL_PRVA.imaDvojice());
        assertEquals(4, FormatSrecanja.SNTL_PRVA.stTekem() / 2 + 1, "srecanje se konca pri 4 zmagah");
    }

    /* 1. SNTL zenske: isti razpored kot SNTL, samo brez dvojic. */
    @Test
    void sntlBrezDvojicJeSntlBrezPrveTekme() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.SNTL_BREZ_DVOJIC.razpored();
        assertEquals(9, r.size(), "SNTL_BREZ_DVOJIC: samo 9 posamicnih");

        assertTrue(r.stream().noneMatch(m -> m.tip() == TipTekmeSrecanja.DVOJICE),
                "v tem formatu dvojic sploh ni");
        assertTrue(!FormatSrecanja.SNTL_BREZ_DVOJIC.imaDvojice());
        assertEquals(0, FormatSrecanja.SNTL_BREZ_DVOJIC.stVDvojici());

        // enak vrstni red kot SNTL, le brez uvodnih dvojic
        List<String> brezDvojic = FormatSrecanja.SNTL.razpored().stream()
                .filter(m -> m.tip() == TipTekmeSrecanja.POSAMICNA)
                .map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(brezDvojic, r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList());
    }

    /* Olimpijski sistem (ekipni DP mladincev in kadetov): trije igralci, brez
       dvojic, najvec pet tekem. Ni skrajsava SNTL_BREZ_DVOJIC - ze cetrta
       tekma je druga (A-Y namesto B-X) in prav to razlikovanje je edino, po
       cemer uvoz loci format lige iz odigranega razporeda. */
    @Test
    void olimpijskiJePetTekemBrezDvojic() {
        List<FormatSrecanja.MestoTekme> r = FormatSrecanja.OLIMPIJSKI.razpored();
        assertEquals(List.of("A-X", "B-Y", "C-Z", "A-Y", "B-X"),
                r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList());

        assertEquals(3, FormatSrecanja.OLIMPIJSKI.getStIgralcev());
        assertTrue(!FormatSrecanja.OLIMPIJSKI.imaDvojice());
        assertEquals(0, FormatSrecanja.OLIMPIJSKI.stVDvojici());
        assertEquals(3, FormatSrecanja.OLIMPIJSKI.stTekem() / 2 + 1, "srecanje se konca pri 3 zmagah");

        // C in Z odigrata po eno tekmo, ostali po dve - zato tudi ni razpored
        // "vsak z vsakim"
        assertEquals(1, r.stream().filter(m -> "C".equals(m.domaci())).count());
        assertEquals(1, r.stream().filter(m -> "Z".equals(m.gost())).count());

        // razlika proti SNTL_BREZ_DVOJIC je ze pri cetrti tekmi
        List<String> sntl = FormatSrecanja.SNTL_BREZ_DVOJIC.razpored().stream()
                .map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(sntl.subList(0, 3),
                r.stream().map(FormatSrecanja.MestoTekme::oznaka).toList().subList(0, 3));
        assertTrue(!sntl.get(3).equals(r.get(3).oznaka()),
                "cetrta tekma loci olimpijski sistem od SNTL brez dvojic");
    }

    /* Sezona 2022/23: isti posamicni razpored kot SNTL oz. SNTL_PRVA, le da
       dvojice ne odprejo srecanja. Tega ni mogoce zapisati kot skrajsavo
       obstojecih formatov, zato sta svoja. */
    @Test
    void sntlSezone2022ImaDvojiceZnotrajRazporeda() {
        List<String> desetTekem = FormatSrecanja.SNTL_DVOJICE_SEDMA.razpored().stream()
                .map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(List.of("A-X", "B-Y", "C-Z", "B-X", "A-Z", "C-Y", "dvojice", "B-Z", "C-X", "A-Y"),
                desetTekem);
        assertEquals(TipTekmeSrecanja.DVOJICE,
                FormatSrecanja.SNTL_DVOJICE_SEDMA.razpored().get(6).tip());

        List<String> sedemTekem = FormatSrecanja.SNTL_PRVA_DVOJICE_CETRTA.razpored().stream()
                .map(FormatSrecanja.MestoTekme::oznaka).toList();
        assertEquals(List.of("B-X", "A-Z", "C-Y", "dvojice", "B-Z", "C-X", "A-Y"), sedemTekem);

        // posamicne tekme so iste in v istem vrstnem redu kot pri SNTL oz.
        // SNTL_PRVA - premaknejo se samo dvojice
        for (FormatSrecanja[] par : new FormatSrecanja[][] {
                { FormatSrecanja.SNTL, FormatSrecanja.SNTL_DVOJICE_SEDMA },
                { FormatSrecanja.SNTL_PRVA, FormatSrecanja.SNTL_PRVA_DVOJICE_CETRTA } }) {
            assertEquals(brezDvojic(par[0]), brezDvojic(par[1]),
                    par[1] + " ima iste posamicne tekme kot " + par[0]);
            assertEquals(par[0].stTekem(), par[1].stTekem());
            assertTrue(par[1].imaDvojice() && par[1].izbiraDvojice());
        }
    }

    private static List<String> brezDvojic(FormatSrecanja format) {
        return format.razpored().stream()
                .filter(m -> m.tip() == TipTekmeSrecanja.POSAMICNA)
                .map(FormatSrecanja.MestoTekme::oznaka).toList();
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
