/* Vnos novega igralca ali sprememba obstojecega. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.Spol;

public record IgralecVnos(
        @NotBlank(message = "ime je obvezno")
        @Size(min = 2, max = 30, message = "ime mora imeti od 2 do 30 znakov") String ime,
        @NotBlank(message = "priimek je obvezen")
        @Size(min = 2, max = 40, message = "priimek mora imeti od 2 do 40 znakov") String priimek,
        @NotNull(message = "spol je obvezen") Spol spol,
        @NotNull(message = "datum rojstva je obvezen") @Past(message = "datum rojstva mora biti v preteklosti") LocalDate datumRojstva,
        @Email(message = "e-posta ni veljavna") String email,
        String telefonskaSt,
        IgralnaRoka igralnaRoka,
        String ntzsLicenca,
        String drzavljanstvo,
        String naslov,
        Integer postnaSt,
        Long idKlub
) {}
