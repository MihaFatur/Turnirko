/* Turnir - izpis. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;

public record TurnirDto(
        Long id,
        String ime,
        KrajDto kraj,
        String dvorana,
        LocalDate datumZacetka,
        LocalDate datumKonca,
        StatusTekmovanja status,
        String opombe
) {

    public static TurnirDto iz(Turnir turnir) {
        return new TurnirDto(
                turnir.getId(),
                turnir.getIme(),
                turnir.getKraj() != null ? KrajDto.iz(turnir.getKraj()) : null,
                turnir.getDvorana(),
                turnir.getDatumZacetka(),
                turnir.getDatumKonca(),
                turnir.getStatus(),
                turnir.getOpombe()
        );
    }
}
