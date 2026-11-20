/* Potrditev branja listka: izid, kot ga je organizator potrdil (morda popravil),
   in tekma, ce jo je izbral sam (sicer velja prepoznana). */
package si.turnirko.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PotrditevBranjaVnos(
        Long idTekme,
        @NotNull @Valid VnosRezultata izid
) {}
