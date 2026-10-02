/* Obstojec igralec, ki je podoben vpisu - za administratorja.

   Poln zapis (IgralecDto) z datumom rojstva in primerjava vpisanega datuma
   z njim. Vrne ga samo /igralci/podobni/podrobno, ki ga veriga omeji na ADMIN
   (isto pravilo kot /igralci/podrobno). */
package si.turnirko.dto;

import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.UjemanjeImena;

public record PodobenIgralecDto(
        IgralecDto igralec,
        UjemanjeImena ujemanje,
        PrimerjavaDatuma datum,
        boolean arhiviran
) {}
