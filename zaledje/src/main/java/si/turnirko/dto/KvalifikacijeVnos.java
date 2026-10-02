/* Vnos kvalifikacij med visjo ligo (iz poti) in nizjo: nacin igranja in ime
   lige kvalifikacij (prazno = predlog streznika, npr. "Kvalifikacije Savinja
   liga A/B"). */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.NacinKvalifikacij;

public record KvalifikacijeVnos(
        @NotNull(message = "nizja liga je obvezna") Long idNizja,
        @NotNull(message = "nacin kvalifikacij je obvezen") NacinKvalifikacij nacin,
        @Size(max = 80, message = "ime lige ima lahko najvec 80 znakov") String ime
) {}
