/* Rocno urejen jakostni vrstni red prijavljenih na dogodku (sistem SKUPINE).
   Vsebovati mora VSE prijavljene natanko enkrat - delni seznam bi tiho pustil
   koga brez mesta na lestvici. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record VrstniRedVnos(
        @NotEmpty(message = "vrstni red ne sme biti prazen") List<Long> idjiPrijav
) {}
