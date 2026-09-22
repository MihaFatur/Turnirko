/* Zahteva za novo kodo (naslov ali skrbnik). Za geslo je locena pot. */
package si.turnirko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.NamenKode;

public record PonovnoPosiljanjeVnos(
        @NotBlank(message = "e-posta je obvezna")
        @Email(message = "e-posta ni veljavna") String email,

        @NotNull(message = "namen je obvezen") NamenKode namen
) {}
