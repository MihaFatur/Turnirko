/* Vnos dostopa organizatorja do tekmovanja (PUT): ali tekmovanje steje v mejo
   njegovega paketa. Enak zapis doda dostop in ga posodobi. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record DostopOrganizatorjaVnos(@NotNull Boolean steje) {
}
