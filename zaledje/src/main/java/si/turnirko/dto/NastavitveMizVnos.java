/* Nastavitve miz turnirja (M1): stevilo miz (prazno = brez miz), zacasno
   izklopljene mize, ali porazenec zadnje tekme na mizi sodi naslednjo, in
   pocitek v minutah, ki ga uposteva predlog tekem (M2). Vse je obvezno razen
   stevila miz: delni vnos ne sme tiho ponastaviti nastavitev. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NastavitveMizVnos(
        @Min(value = 1, message = "Število miz mora biti vsaj 1.")
        @Max(value = 99, message = "Število miz je največ 99 (toliko jih prebere okence listka).")
        Integer steviloMiz,
        @NotNull(message = "Seznam izklopljenih miz je obvezen (lahko je prazen).")
        @Size(max = 99, message = "Izklopljenih miz je lahko največ 99.")
        List<@NotNull Integer> izklopljeneMize,
        @NotNull(message = "Povej, ali poraženec sodi.")
        Boolean porazenecSodi,
        @NotNull(message = "Počitek je obvezen.")
        @Min(value = 0, message = "Počitek ne more biti negativen.")
        @Max(value = 30, message = "Počitek je največ 30 minut.")
        Integer pocitekMin
) {}
