/* Rocno urejen jakostni vrstni red ekip v ligi (enakomerna razvrstitev).
   Vsebovati mora VSE ekipe lige natanko enkrat - delni seznam bi tiho pustil
   koga brez mesta, po njem pa tece razdelitev na pare in celoten zreb.

   Locen od LigaVnos in EkipaVnos iz istega razloga kot VrstniRedVnos pri
   dogodkih: vrstni red se ne ureja po eni ekipi, ampak kot celota. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record VrstniRedEkipVnos(
        @NotEmpty(message = "vrstni red ne sme biti prazen") List<Long> idjiEkip
) {}
