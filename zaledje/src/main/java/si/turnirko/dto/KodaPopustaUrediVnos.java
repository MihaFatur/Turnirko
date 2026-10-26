/* Popravek kode popusta (admin): samo to, kar sme ostati spremenljivo, ko je
   koda ze v obtoku - ugodnost se ne spreminja (glej KodaPopusta). */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KodaPopustaUrediVnos(
        @Size(max = 200, message = "opis je predolg") String opis,
        LocalDate veljaDo,
        Integer najvecUporab,
        @NotNull(message = "aktivnost je obvezna") Boolean aktivna
) {}
