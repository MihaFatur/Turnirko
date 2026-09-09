/* Povezava dveh prijav istega dogodka dvojic v par. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record ParVnos(
        @NotNull(message = "prva prijava je obvezna") Long idPrijave1,
        @NotNull(message = "druga prijava je obvezna") Long idPrijave2
) {}
