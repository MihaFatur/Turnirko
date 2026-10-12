/* Odlocitev »ni duplikat«: ta dva igralca sta dve osebi. Vrstni red ni
   pomemben, storitev par shrani urejeno. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record NiDuplikatVnos(
        @NotNull(message = "prvi igralec je obvezen") Long idA,
        @NotNull(message = "drugi igralec je obvezen") Long idB
) {}
