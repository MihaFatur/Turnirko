/* Novo geslo s kodo iz e-poste (pozabljeno geslo). */
package si.turnirko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovoGesloVnos(
        @NotBlank(message = "e-posta je obvezna")
        @Email(message = "e-posta ni veljavna") String email,

        @NotBlank(message = "koda je obvezna")
        @Pattern(regexp = "\\s*\\d(\\s*\\d){5}\\s*", message = "koda ima sest stevk") String koda,

        @NotBlank(message = "geslo je obvezno")
        @Size(min = 8, max = 100, message = "geslo mora imeti vsaj 8 znakov") String geslo
) {}
