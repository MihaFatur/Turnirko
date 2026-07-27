/* Vnos igralca v kader ekipe. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record KaderVnos(
        @NotNull(message = "igralec je obvezen") Long idIgralec,
        // jakostni vrstni red v ekipi (neobvezno)
        Integer vrstniRed
) {}
