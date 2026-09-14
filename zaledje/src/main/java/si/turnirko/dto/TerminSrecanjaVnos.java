/* Termin ene tekme koncnice; null pomeni, da termin se ni dolocen. Ura 00:00
   pomeni "ura ni dolocena" - enako kot pri terminih kol. */
package si.turnirko.dto;

import java.time.LocalDateTime;

public record TerminSrecanjaVnos(LocalDateTime zacetek) {}
