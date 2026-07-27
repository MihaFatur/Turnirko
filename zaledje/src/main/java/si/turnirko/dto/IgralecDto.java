/* Igralec - izpis, vkljucno s trenutnim klubskim ELO ratingom. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.Spol;

public record IgralecDto(
        Long id,
        String ime,
        String priimek,
        Spol spol,
        LocalDate datumRojstva,
        String email,
        String telefonskaSt,
        IgralnaRoka igralnaRoka,
        String ntzsLicenca,
        String drzavljanstvo,
        String naslov,
        KrajDto kraj,
        KlubDto klub,
        Integer rating, // trenutni klubski ELO; null, ce igralec se ni igral
        // stevilo ze odigranih ratinskih tekem; 0 -> se je mogoce postaviti
        // zacetni rating; nizko stevilo -> rating je se provizoricen
        int steviloTekem
) {

    public static IgralecDto iz(Igralec igralec, Integer rating) {
        return iz(igralec, rating, 0);
    }

    public static IgralecDto iz(Igralec igralec, Integer rating, int steviloTekem) {
        return new IgralecDto(
                igralec.getId(),
                igralec.getIme(),
                igralec.getPriimek(),
                igralec.getSpol(),
                igralec.getDatumRojstva(),
                igralec.getEmail(),
                igralec.getTelefonskaSt(),
                igralec.getIgralnaRoka(),
                igralec.getNtzsLicenca(),
                igralec.getDrzavljanstvo(),
                igralec.getNaslov(),
                igralec.getKraj() != null ? KrajDto.iz(igralec.getKraj()) : null,
                KlubDto.iz(igralec.getKlub()),
                rating,
                steviloTekem
        );
    }
}
