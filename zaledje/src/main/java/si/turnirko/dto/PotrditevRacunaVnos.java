/* Potrditev racuna: administrator izbere igralca iz sifranta, s katerim se
   racun poveze. Brez povezave potrditev ni mogoca - racun sicer ne bi vedel,
   cigavo statistiko sme videti. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record PotrditevRacunaVnos(
        @NotNull(message = "izbrati je treba igralca") Long idIgralec
) {}
