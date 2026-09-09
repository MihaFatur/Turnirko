/* Tocke enega niza, npr. 11:7. Isti zapis uporabljata vnos rezultata
   turnirske tekme (strani 1 in 2) in ligaske (domaci in gost) - pravila niza
   so ista, zato je zapis en sam (glej NiziPravila). */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record NizVnos(
        @NotNull Integer tocke1,
        @NotNull Integer tocke2
) {}
