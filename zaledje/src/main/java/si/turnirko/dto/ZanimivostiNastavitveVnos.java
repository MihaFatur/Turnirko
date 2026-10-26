/* Nov nabor skritih razdelkov - celoten seznam, ne razlika. Kar ni na seznamu,
   je vidno (isti vzorec kot pri vrstnem redu ekip). Prazen ali manjkajoč seznam
   pomeni, da gledalcu ni skrit nobeden. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.PostavkaZanimivosti;

public record ZanimivostiNastavitveVnos(List<PostavkaZanimivosti> skrite) {}
