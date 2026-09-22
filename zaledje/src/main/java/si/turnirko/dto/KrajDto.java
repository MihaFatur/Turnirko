/* Kraj - uporablja se za vnos in izpis. */
package si.turnirko.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Kraj;

public record KrajDto(
        /* Slovenska postna stevilka je stirimestna. Razpon je bil doslej samo
           v obrazcu (min/max na polju type="number"); pravilo sodi na streznik. */
        @NotNull(message = "postna stevilka je obvezna")
        @Min(value = 1000, message = "postna stevilka je stirimestna (1000-9999)")
        @Max(value = 9999, message = "postna stevilka je stirimestna (1000-9999)")
        Integer postnaSt,
        @NotBlank(message = "ime kraja je obvezno")
        @Size(min = 2, max = 40, message = "ime kraja mora imeti od 2 do 40 znakov") String ime
) {

    public static KrajDto iz(Kraj kraj) {
        return new KrajDto(kraj.getPostnaSt(), kraj.getIme());
    }
}
