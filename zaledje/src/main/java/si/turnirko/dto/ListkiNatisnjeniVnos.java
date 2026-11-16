/* Oznaka "listek natisnjen" za izbrane tekme dogodka (natisnjeno = false oznako
   odstrani). Tekem je lahko vec sto naenkrat (celoten dogodek), zato mejo
   postavlja kontroler, ne vmesnik. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListkiNatisnjeniVnos(
        @NotNull(message = "manjka seznam tekem")
        @Size(max = 2000, message = "naenkrat je mogoce oznaciti najvec 2000 tekem")
        List<Long> idjiTekem,
        boolean natisnjeno
) {}
