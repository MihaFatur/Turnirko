/* Postavitveni (zacetni) klubski ELO, ki ga admin doloci novincu, preden ta
   odigra prvo tekmo. Meji se ujemata z EloStoritev (100..3000). */
package si.turnirko.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ZacetniRatingVnos(
        @Min(value = 100, message = "zacetni rating ne sme biti pod 100")
        @Max(value = 3000, message = "zacetni rating ne sme presegati 3000") int vrednost
) {}
