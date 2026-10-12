/* Par igralcev, ki sta si podobna po imenu - za pogled »Cistka duplikatov«.

   Poln zapis obeh (IgralecDto z datumom rojstva), zato ga vrne samo
   GET /igralci/duplikati, ki ga veriga omeji na ADMIN (isto pravilo kot
   GET /igralci/podrobno). */
package si.turnirko.dto;

import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.UjemanjeImena;

public record DvojnikIgralcaDto(
        /* Stabilen kljuc para (»12-345«, manjsi id prvi), ne glede na to, kdo
           je levi in kdo desni. */
        String id,
        /* Levi je tisti z vec tekmami (pri enakem manjsi id): admin ga ob
           zdruzitvi najpogosteje obdrzi. */
        IgralecDto levi,
        IgralecDto desni,
        UjemanjeImena ujemanje,
        PrimerjavaDatuma datum
) {}
