/* Kdaj je bilo ligasko srecanje odigrano (Srecanje.casOdigranja): termin,
   razen ce je bil prvi izid vpisan pred dnem termina. Pravilo bere rating ob
   obracunu in ob preracunu, zato mora dati isti odgovor iz shranjenih
   vrednosti - tudi iz starih zapisov brez ure. */
package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SrecanjeCasOdigranjaTest {

    private static final LocalDateTime TERMIN = LocalDateTime.of(2026, 11, 26, 19, 45);

    @Test
    void vpisanoNaDanTerminaAliPoznejeVeljaTermin() {
        assertEquals(TERMIN, Srecanje.casOdigranja(TERMIN, LocalDateTime.of(2026, 11, 26, 22, 10)));
        assertEquals(TERMIN, Srecanje.casOdigranja(TERMIN, LocalDateTime.of(2026, 11, 27, 9, 0)));
    }

    /* Star zapis brez ure je polnoc istega dne - ura termina tistega vecera
       mora ostati, sicer bi se vse tekme vecera zlozile na polnoc. */
    @Test
    void starZapisBrezUreNaDanTerminaNePovoziUre() {
        assertEquals(TERMIN, Srecanje.casOdigranja(TERMIN, LocalDate.of(2026, 11, 26).atStartOfDay()));
    }

    /* Ekipi sta se zamenjali za termin: srecanje 4. kola je bilo odigrano in
       vpisano ze septembra. Tekma ne sme stati v prihodnosti. */
    @Test
    void vpisanoPredDnemTerminaVeljaVpis() {
        LocalDateTime vpis = LocalDateTime.of(2026, 9, 25, 11, 30);
        assertEquals(vpis, Srecanje.casOdigranja(TERMIN, vpis));
    }

    /* Star zapis brez ure (polnoc = ura ni dolocena) na dan pred terminom:
       dan vpisa, ura termina - in ne polnoc, ki bi tekmo postavila pred vse
       tekme tistega vecera. */
    @Test
    void starZapisBrezUrePredTerminomDobiUroTermina() {
        assertEquals(LocalDateTime.of(2026, 9, 25, 19, 45),
                Srecanje.casOdigranja(TERMIN, LocalDate.of(2026, 9, 25).atStartOfDay()));
    }

    @Test
    void manjkajocaVrednostVrneDrugo() {
        LocalDateTime vpis = LocalDateTime.of(2026, 9, 25, 11, 30);
        assertEquals(vpis, Srecanje.casOdigranja(null, vpis));
        assertEquals(TERMIN, Srecanje.casOdigranja(TERMIN, null));
        assertNull(Srecanje.casOdigranja(null, null));
    }
}
