/* Tisk zapisnikov za bralnik: srecanja (eno ali vse v kolu) in oblika; null =
   samodejno (vrstice tekem, kadar gre kader nanje, sicer tabela kadra). */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import si.turnirko.branje.GeometrijaZapisnika;

public record NatisZapisnikovVnos(@NotEmpty @Size(max = 60) List<Long> srecanja, GeometrijaZapisnika.Oblika oblika) {}
