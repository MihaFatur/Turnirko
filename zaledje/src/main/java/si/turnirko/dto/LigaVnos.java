/* Vnos (ustvarjanje/urejanje) lige - vsa prilagodljiva konfiguracija. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.SpolKategorija;

public record LigaVnos(
        @NotBlank(message = "ime lige je obvezno")
        @Size(min = 3, max = 80, message = "ime lige mora imeti od 3 do 80 znakov") String ime,
        String sezona,
        @NotNull(message = "spolna kategorija je obvezna") SpolKategorija spolKategorija,
        @NotNull(message = "format srecanja je obvezen") FormatSrecanja formatSrecanja,
        @NotNull(message = "stevilo nizov je obvezno") Integer steviloNizov,
        // null = odigrajo se vse tekme srecanja; sicer prvi do N zmag
        Integer zmagZaSrecanje,
        Boolean dvokrozno,
        Integer tockeZmaga,
        Integer tockeNeodloceno,
        Integer tockePoraz,
        Boolean dovoljenoNeodloceno,
        Boolean prepovedDvojneRegistracije,
        Boolean stejeVElo,
        Long idVisjaLiga,
        Integer stNapreduje,
        Integer stIzpade
) {}
