/* Zamenjava lastnega gesla. Staro geslo je obvezno tudi za prijavljenega,
   da ukraden odprt zavihek ne omogoci prevzema racuna. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpremembaGeslaVnos(
        @NotBlank(message = "staro geslo je obvezno") String staro,
        @NotBlank(message = "novo geslo je obvezno")
        @Size(min = 8, max = 100, message = "novo geslo mora imeti vsaj 8 znakov") String novo
) {}
