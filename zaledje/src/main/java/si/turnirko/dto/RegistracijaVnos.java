/* Registracija igralca. Ime, priimek in klub sluzijo administratorju, da
   racun poveze s pravim zapisom v sifrantu igralcev; e-posta je hkrati
   prijavno ime. */
package si.turnirko.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistracijaVnos(
        @NotBlank(message = "ime je obvezno")
        @Size(max = 60, message = "ime je predolgo") String ime,

        @NotBlank(message = "priimek je obvezen")
        @Size(max = 60, message = "priimek je predolg") String priimek,

        /* Neobvezen - igralec morda ni v klubu. */
        Long idKlub,

        @NotBlank(message = "e-posta je obvezna")
        @Email(message = "e-posta ni veljavna")
        @Size(max = 120, message = "e-posta je predolga") String email,

        @NotBlank(message = "geslo je obvezno")
        @Size(min = 8, max = 100, message = "geslo mora imeti vsaj 8 znakov") String geslo
) {}
