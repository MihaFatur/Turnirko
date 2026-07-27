/* Administrator nastavi poljubno geslo racunu igralca. Ker shranjenega gesla
   ne more prebrati (v bazi je le BCrypt zgostitev), je to edini nacin, da ga
   pozna in izroci igralcu - npr. ko ta geslo pozabi. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NastavitevGeslaVnos(
        @NotBlank(message = "geslo je obvezno")
        @Size(min = 8, max = 100, message = "geslo mora imeti vsaj 8 znakov") String geslo
) {}
