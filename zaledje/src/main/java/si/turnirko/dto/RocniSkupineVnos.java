/* Rocni vpis skupin pri sistemih z jakostnim zrebom skupin (SKUPINE_IZLOCILNI,
   SKUPINE, SKUPINE_ZA_MESTA): organizator pove, kdo je v kateri skupini in na kateri
   poziciji v njej (1 = najmocnejsi v skupini, 2 = drugi ...). Pozicija NI nosilec
   turnirja: vsaka skupina ima svoje 1., 2., ... mesto. Skupine so po vrsti A, B ...;
   prazna skupina ne pride v zahtevo. Vrstni red igralcev v skupini je po poziciji
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
            @NotNull(message = "manjka pozicija v skupini")
            @Min(value = 1, message = "pozicija v skupini mora biti vsaj 1")
            Integer pozicija
    ) {}
}
