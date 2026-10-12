/* Zdruzitev dveh igralcev: obdrzani je v naslovu zahteve, odvecni (ki se
   arhivira) v telesu. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record ZdruzitevIgralcevVnos(
        @NotNull(message = "odvecni igralec je obvezen") Long idOdvec
) {}
