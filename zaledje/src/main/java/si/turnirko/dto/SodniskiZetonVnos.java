/* Zeton sodniskega branja (S2) v telesu zahteve, ne v URL-ju: naslovi gredo v
   dnevnike streznika in posrednika, telesa ne. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SodniskiZetonVnos(@NotBlank @Size(max = 100) String zeton) {}
