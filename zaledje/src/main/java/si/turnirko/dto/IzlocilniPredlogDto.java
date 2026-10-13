/* Predlog izlocilne mreze za rocni vpis: udelezenci (tisti, ki jih mreza mora zajeti)
   in razporeditev po mestih od vrha navzdol, null za prosto mesto. Predlog ne zapise
   nicesar; organizator ga popravi in vrne kot RocniMestaVnos. */
package si.turnirko.dto;

import java.util.List;

public record IzlocilniPredlogDto(
        List<Long> udelezenci,
        List<Long> idjiPoMestih
) {}
