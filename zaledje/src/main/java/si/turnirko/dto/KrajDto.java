/* Kraj - uporablja se za vnos in izpis. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Kraj;

public record KrajDto(
        @NotNull(message = "postna stevilka je obvezna") Integer postnaSt,
        @NotBlank(message = "ime kraja je obvezno")
        @Size(min = 2, max = 40, message = "ime kraja mora imeti od 2 do 40 znakov") String ime
) {

    public static KrajDto iz(Kraj kraj) {
        return new KrajDto(kraj.getPostnaSt(), kraj.getIme());
    }
}
