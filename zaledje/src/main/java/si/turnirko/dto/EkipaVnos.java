/* Vnos ekipe v ligo. Zaporedna loci vec ekip istega kluba (Savinja 1, 2). */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record EkipaVnos(
        @NotNull(message = "klub je obvezen") Long idKlub,
        // ce null, se zaporedna doloci samodejno (naslednja prosta za klub)
        Integer zaporedna,
        // neobvezno lastno ime; sicer se sestavi iz kluba in zaporedne
        String ime
) {}
