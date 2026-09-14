/* Dogodek Stupe na seznamu za uvoz (samo admin): kaj je pri viru in kaj je
   ze v Turnirku. */
package si.turnirko.dto;

import java.time.LocalDate;

public record UvozDogodekDto(
        long id,
        String ime,
        /* TURNIR ali LIGA */
        String vrsta,
        LocalDate zacetek,
        LocalDate konec,
        String sezona,
        /* uvozeno tekmovanje v Turnirku (null, ce se ni uvozeno) */
        Long idTurnir,
        Long idLiga,
        UvozZagonDto zadnjiUvoz
) {}
