/* Casovno zaporedje uvozenih tekem za obracun ELO.

   To je edino mesto v uvozu, kjer vrstni red spremeni REZULTAT in ne le
   videza: ELO je zaporedna kolicina, zato ista mnozica tekem v napacnem
   zaporedju da druge ratinge. Uvoz gre po tekmovanjih (cel turnir, cela liga),
   kar ni casovno zaporedje - zato tekme na koncu zlozimo v eno vrsto in prav
   to zlaganje se preverja tukaj.

   Test ne potrebuje baze: uredi() je cista funkcija nad seznamom kazalcev,
   zato je EloUvoz sestavljen s praznimi repozitoriji. */
package si.turnirko.uvoz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class EloUvozVrstniRedTest {

    private final EloUvoz eloUvoz = new EloUvoz(null, null, null);

    private List<Long> idjiPoVrsti(List<EloUvoz.VrstaTekme> vrsta) {
        return eloUvoz.uredi(vrsta).stream().map(EloUvoz.VrstaTekme::id).toList();
    }

    /* Turnir, kolo lige, turnir - kot so si sledili v koledarju. Uvoz jih
       obdela po tekmovanjih, vrsta pa jih mora prepletati po datumu. */
    @Test
    void tekmeSePrepletajoPoDatumuInNePoTekmovanju() {
        LocalDate oktober = LocalDate.of(2021, 10, 2);
        LocalDate november = LocalDate.of(2021, 11, 7);

        List<EloUvoz.VrstaTekme> vrsta = List.of(
                // cela liga naenkrat: prvo kolo oktobra, drugo novembra
                EloUvoz.VrstaTekme.ligaska(10, LocalDateTime.of(2021, 10, 9, 17, 0), 1, 1),
                EloUvoz.VrstaTekme.ligaska(11, LocalDateTime.of(2021, 11, 13, 17, 0), 2, 1),
                // in sele nato oba turnirja
                EloUvoz.VrstaTekme.turnirska(20, oktober, false, 1, 1),
                EloUvoz.VrstaTekme.turnirska(21, november, false, 1, 1));

        assertEquals(List.of(20L, 10L, 21L, 11L), idjiPoVrsti(vrsta),
                "turnir 2. 10., kolo 9. 10., turnir 7. 11., kolo 13. 11.");
    }

    /* Znotraj turnirja gre skupinski del pred izlocilnim. Obe stopnji pri viru
       stejeta kroge od 1 naprej, zato bi brez faze prvo kolo finalnega dela
       padlo pred drugo kolo skupin. */
    @Test
    void skupinskiDelJePredIzlocilnim() {
        LocalDate dan = LocalDate.of(2021, 10, 2);

        List<EloUvoz.VrstaTekme> vrsta = List.of(
                EloUvoz.VrstaTekme.turnirska(1, dan, true, 1, 1),    // finalni del, 1. krog
                EloUvoz.VrstaTekme.turnirska(2, dan, false, 3, 1),   // skupine, 3. krog
                EloUvoz.VrstaTekme.turnirska(3, dan, false, 1, 1));  // skupine, 1. krog

        assertEquals(List.of(3L, 2L, 1L), idjiPoVrsti(vrsta));
    }

    /* Ligaska srecanja imajo uro, turnirske tekme je nimajo. Isti dan gre
       turnir pred ligo (turnirji se zacnejo zjutraj), dve srecanji istega dne
       pa se med sabo uredita po uri - 2. SNTL odigra dve koli v enem dnevu. */
    @Test
    void uraLocuje() {
        LocalDate dan = LocalDate.of(2021, 10, 9);

        List<EloUvoz.VrstaTekme> vrsta = List.of(
                EloUvoz.VrstaTekme.ligaska(1, LocalDateTime.of(2021, 10, 9, 14, 0), 1, 1),
                EloUvoz.VrstaTekme.ligaska(2, LocalDateTime.of(2021, 10, 9, 10, 0), 1, 1),
                EloUvoz.VrstaTekme.turnirska(3, dan, false, 1, 1));

        assertEquals(List.of(3L, 2L, 1L), idjiPoVrsti(vrsta));
    }

    /* Tekme brez datuma gredo na zacetek - tam najmanj skodijo: igralec je
       takrat se brez zgodovine in K faktor je tako ali tako najvisji. */
    @Test
    void tekmeBrezDatumaGredoNaZacetek() {
        List<EloUvoz.VrstaTekme> vrsta = List.of(
                EloUvoz.VrstaTekme.turnirska(1, LocalDate.of(2013, 5, 1), false, 1, 1),
                EloUvoz.VrstaTekme.turnirska(2, null, false, 1, 1));

        assertEquals(List.of(2L, 1L), idjiPoVrsti(vrsta));
    }

    /* Ob popolnoma enakem casu in zaporedju odloci identifikator, da je uvoz
       ponovljiv - dvakratni uvoz istih podatkov mora dati iste ratinge. */
    @Test
    void enakCasInZaporedjeSeUreditaPoIdentifikatorju() {
        LocalDate dan = LocalDate.of(2021, 10, 2);

        List<EloUvoz.VrstaTekme> vrsta = List.of(
                EloUvoz.VrstaTekme.turnirska(7, dan, false, 1, 1),
                EloUvoz.VrstaTekme.turnirska(3, dan, false, 1, 1));

        assertEquals(List.of(3L, 7L), idjiPoVrsti(vrsta));
    }
}
