/* Vnos za predogled zapisa med igralci PRED registracijo (korak "Klub in
   zapis" v vmesniku) - isti trije podatki kot pri samodejni povezavi
   (RegistracijaStoritev.poskusiSamodejnoPovezavo), le da gredo ven brez
   racuna in gesla. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PredogledZapisaVnos(
        @NotBlank(message = "ime je obvezno")
        @Size(max = 60, message = "ime je predolgo") String ime,

        @NotBlank(message = "priimek je obvezen")
        @Size(max = 60, message = "priimek je predolg") String priimek,

        @NotNull(message = "datum rojstva je obvezen") LocalDate datumRojstva
) {}
