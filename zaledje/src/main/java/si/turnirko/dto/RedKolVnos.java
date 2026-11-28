/* Vrstni red kol skupin kategorije na strani Mize (V80; docs/pravila/mize.md): stevilke
   kol v vrstnem redu igranja, npr. [3, 1, 2]. Prazen seznam vrne naravni red 1, 2, 3 ... */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RedKolVnos(
        @NotNull(message = "Kategorija je obvezna.")
        Long idDogodka,
        @NotNull(message = "Seznam kol je obvezen (lahko je prazen).")
        @Size(max = 100)
        List<@NotNull Integer> kola
) {}
