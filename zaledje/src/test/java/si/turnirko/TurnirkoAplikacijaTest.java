/* Mapa za datoteko baze se izpelje iz nastavljenega naslova.

   Ozadje: fiksna relativna pot "podatki" je v vsebniku (delovna mapa /app)
   pomenila /app/podatki, kamor aplikacija ne sme pisati - zagon se je ustavil,
   prava baza pa lezi na priklopljenem /podatki. */
package si.turnirko;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TurnirkoAplikacijaTest {

    @Test
    @DisplayName("absolutni naslov (vsebnik) da priklopljeno mapo, ne /app/podatki")
    void absolutniNaslov() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:sqlite:/podatki/turnirko.db"))
                .isEqualTo(Path.of("/podatki"));
    }

    @Test
    @DisplayName("privzeti relativni naslov ostane pri mapi podatki")
    void relativniNaslov() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:sqlite:./podatki/turnirko.db"))
                .isEqualTo(Path.of("./podatki"));
    }

    @Test
    @DisplayName("nastavitve za naslovom se ne stejejo v pot")
    void naslovZNastavitvami() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:sqlite:/podatki/turnirko.db?cache=shared"))
                .isEqualTo(Path.of("/podatki"));
    }

    @Test
    @DisplayName("druga vrsta baze ne potrebuje mape")
    void drugaBaza() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:postgresql://localhost:5432/turnirko")).isNull();
    }

    @Test
    @DisplayName("pomnilniska baza ne potrebuje mape")
    void pomnilniskaBaza() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:sqlite::memory:")).isNull();
    }

    @Test
    @DisplayName("datoteka brez nadrejene mape ne potrebuje nicesar")
    void brezNadrejeneMape() {
        assertThat(TurnirkoAplikacija.mapaZaBazo("jdbc:sqlite:turnirko.db")).isNull();
    }

    @Test
    @DisplayName("manjkajoc naslov ne sesuje zagona")
    void manjkajocNaslov() {
        assertThat(TurnirkoAplikacija.mapaZaBazo(null)).isNull();
    }
}
