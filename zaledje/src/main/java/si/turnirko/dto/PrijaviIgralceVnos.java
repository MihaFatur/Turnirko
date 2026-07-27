/* Prijava enega ali vec igralcev na dogodek. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record PrijaviIgralceVnos(
        @NotEmpty(message = "seznam igralcev ne sme biti prazen") List<Long> idjiIgralcev
) {}
