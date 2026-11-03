/* Koda popusta, ki jo ze prijavljen racun vpise na strani Narocnina
   (POST /api/v1/narocnina/koda). Racun in vloga sta iz prijave, zato je v
   vnosu samo koda. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KodaZaRacunVnos(
        @NotBlank(message = "koda je obvezna")
        @Size(max = 40, message = "koda je predolga") String koda
) {}
