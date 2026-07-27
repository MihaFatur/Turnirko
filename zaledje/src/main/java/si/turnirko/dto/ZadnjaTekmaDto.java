/* Zadnje odigrane tekme cez vse dogodke - za "Zadnji rezultati" na domaci
   strani. S spremembo klubskega ELO obeh igralcev. */
package si.turnirko.dto;

import si.turnirko.modeli.IzidTekme;

public record ZadnjaTekmaDto(
        Long idTekme,
        String turnir,
        String dogodek,
        String igralec1,
        String klub1,
        String igralec2,
        String klub2,
        int nizi1,
        int nizi2,
        boolean zmagalPrvi,
        IzidTekme izidTip,
        Integer spremembaElo1,
        Integer spremembaElo2
) {}
