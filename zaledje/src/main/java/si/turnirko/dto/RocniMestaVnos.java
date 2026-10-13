/* Rocna razporeditev izlocilne mreze: id prijav po mestih od vrha navzdol, null za
   prosto mesto (bye). Sosednja mesta (1.-2., 3.-4. ...) igrata med seboj v prvem kolu,
   zato dva prosta mesta drug ob drugem ne smeta biti. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record RocniMestaVnos(
        @NotNull(message = "manjka razporeditev mest")
        List<Long> idjiPoMestih
) {}
