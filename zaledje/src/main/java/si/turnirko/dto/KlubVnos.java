/* Vnos novega kluba ali sprememba obstojecega. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KlubVnos(
        @NotBlank(message = "ime kluba je obvezno")
        @Size(min = 2, max = 50, message = "ime kluba mora imeti od 2 do 50 znakov") String ime,
        @Size(min = 2, max = 10, message = "kratica mora imeti od 2 do 10 znakov") String kratica
) {}
