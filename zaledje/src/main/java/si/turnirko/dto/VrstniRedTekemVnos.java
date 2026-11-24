/* Rocni vrstni red vrste »Na vrsti« na strani Mize (V77; docs/pravila/mize.md):
   id-ji tekem v vrstnem redu, kot ga je organizator uredil. Prazen seznam ali null =
   samodejni vrstni red. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record VrstniRedTekemVnos(@Size(max = 2000) List<Long> idjiTekem) {}
