/* Klic tekme na mizo (M1) ali sprememba klica: miza, druga miza (samo ekipno
   srecanje) in sodnik (id igralca s prijavo na turnirju; prazno = brez sodnika). */
package si.turnirko.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record KlicTekmeVnos(
        @NotNull(message = "Izberi mizo.")
        @Min(value = 1, message = "Miza mora biti med 1 in 99.")
        @Max(value = 99, message = "Miza mora biti med 1 in 99.")
        Integer miza,
        @Min(value = 1, message = "Druga miza mora biti med 1 in 99.")
        @Max(value = 99, message = "Druga miza mora biti med 1 in 99.")
        Integer miza2,
        Long idSodnika
) {}
