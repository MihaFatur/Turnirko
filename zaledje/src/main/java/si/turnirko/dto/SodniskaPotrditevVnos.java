/* Sodnikova potrditev izida (S2): zeton branja, izid v straneh tekme (kot rocni
   vnos) in miza, kot jo je potrdil sodnik (1-99 ali null). */
package si.turnirko.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SodniskaPotrditevVnos(
        @NotBlank @Size(max = 100) String zeton,
        @NotNull @Valid VnosRezultata izid,
        @Min(1) @Max(99) Integer miza
) {}
