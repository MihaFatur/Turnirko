/* Skupne mize (V78; docs/pravila/mize.md): turnir, s katerim naj ta turnir deli mize.
   Ta turnir (in turnirji, s katerimi ze deli mize) se pridruzi skupini izbranega. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record PovezavaMizVnos(@NotNull(message = "Izberi turnir.") Long idTurnirja) {}
