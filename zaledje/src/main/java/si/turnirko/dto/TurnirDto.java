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
        String opombe,
        // Lastnistvo: racun, ki je turnir ustvaril, in klub lastnik. Po njiju
        // vmesnik pokaze urejevalna dejanja le lastniku; streznik je zadnja
        // obramba (LastnistvoStoritev). idLastnik ni obcutljiv podatek.
        Long idLastnik,
        Long idKlubLastnik,
        String klubLastnik
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
                turnir.getOpombe(),
                turnir.getUstvaril() != null ? turnir.getUstvaril().getId() : null,
                turnir.getKlubLastnik() != null ? turnir.getKlubLastnik().getId() : null,
                turnir.getKlubLastnik() != null ? turnir.getKlubLastnik().getIme() : null
        );
    }
}
