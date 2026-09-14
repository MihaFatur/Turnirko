/* Vnos novega turnirja. Status vedno doloci streznik. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.RavenTekmovanja;

public record TurnirVnos(
        @NotBlank(message = "ime turnirja je obvezno")
        @Size(min = 3, max = 80, message = "ime turnirja mora imeti od 3 do 80 znakov") String ime,
        Integer postnaSt,
        String dvorana,
        LocalDate datumZacetka,
        LocalDate datumKonca,
        String opombe,
        // raven tekmovanja (teza v Turnirko ratingu); null = privzeto KLUBSKO
        RavenTekmovanja raven
) {}
