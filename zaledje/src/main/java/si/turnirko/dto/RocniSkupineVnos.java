/* Rocni vpis skupin pri sistemih z jakostnim zrebom skupin (SKUPINE_IZLOCILNI,
   SKUPINE, SKUPINE_ZA_MESTA): organizator pove, kdo je v kateri skupini in kot kateri
   nosilec (jakostni vrstni red, 1 = najmocnejsi). Skupine so po vrsti A, B ...;
   prazna skupina ne pride v zahtevo. Vrstni red igralcev v skupini je po nosilcu
   navzgor (najmocnejsi prvi), kot ga da zreb. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record RocniSkupineVnos(
        @NotEmpty(message = "vpisane morajo biti skupine")
        List<List<Clan>> skupine
) {

    public record Clan(
            @NotNull(message = "manjka prijava")
            Long idPrijave,
            @NotNull(message = "manjka nosilec")
            @Min(value = 1, message = "nosilec mora biti vsaj 1")
            Integer nosilec
    ) {}
}
