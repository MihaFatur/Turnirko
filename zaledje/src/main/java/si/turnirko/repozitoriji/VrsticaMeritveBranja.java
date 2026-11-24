/* Ena vrstica branja za meritev (MeritevBranjaStoritev): samo stolpci, ki jih
   meritev rabi, brez entitet - meritev obdobja bere do nekaj deset tisoc vrstic. */
package si.turnirko.repozitoriji;

import java.time.LocalDate;

import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.StanjeBranja;

public record VrsticaMeritveBranja(
        Long idTurnir,
        String imeTurnirja,
        LocalDate datumTurnirja,
        StanjeBranja stanje,
        PrepoznavaTekme prepoznava,
        String prebrano,
        String potrjeno,
        Boolean popravljeno,
        Boolean pripravljen,
        boolean kontrola,
        Integer casPregledaMs,
        NacinPotrditve nacinPotrditve,
        String model,
        /* fotografijo je poslal sodnik s svojim telefonom (S2), ne organizator */
        boolean poslalSodnik
) {}
