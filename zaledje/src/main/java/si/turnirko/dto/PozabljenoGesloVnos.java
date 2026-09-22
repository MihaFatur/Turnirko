/* Zahteva za kodo za novo geslo. Odgovor ne pove, ali racun obstaja. */
package si.turnirko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PozabljenoGesloVnos(
        @NotBlank(message = "e-posta je obvezna")
        @Email(message = "e-posta ni veljavna") String email
) {}
