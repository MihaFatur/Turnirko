/* Vpis kode s poste (potrditev naslova ali skrbnikovo soglasje). Naslov
   pove, kateremu racunu koda pripada - prijava za to ni potrebna, dokaz je
   koda sama. */
package si.turnirko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PotrditevKodeVnos(
        @NotBlank(message = "e-posta je obvezna")
        @Email(message = "e-posta ni veljavna") String email,

        @NotBlank(message = "koda je obvezna")
        @Pattern(regexp = "\\s*\\d(\\s*\\d){5}\\s*", message = "koda ima sest stevk") String koda
) {}
