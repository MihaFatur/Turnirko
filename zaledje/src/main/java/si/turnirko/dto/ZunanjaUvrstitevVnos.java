/* Zunanja uvrstitev: rating, prepisan z zunanje lestvice (ITTF, NTZS, druga
   zveza), za redkega gosta, ki pri nas odigra premalo tekem.

   Vir in pojasnilo sta OBVEZNA in javna. To ni formalnost: stevilka, ki jo je
   na javno lestvico vpisal clovek, je brez zapisanega vira videti kot
   naklonjenost, z virom pa je trditev, ki jo lahko vsak preveri. Meji se
   ujemata s TurnirkoRatingStoritev (100..3000). */
package si.turnirko.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZunanjaUvrstitevVnos(
        @Min(value = 100, message = "rating ne sme biti pod 100")
        @Max(value = 3000, message = "rating ne sme presegati 3000") int vrednost,

        @NotBlank(message = "navedi vir (npr. ITTF svetovna lestvica, september 2026)")
        @Size(max = 200, message = "vir naj bo krajsi od 200 znakov") String vir,

        @NotBlank(message = "navedi pojasnilo, zakaj je poseg potreben")
        @Size(max = 500, message = "pojasnilo naj bo krajse od 500 znakov") String pojasnilo
) {}
