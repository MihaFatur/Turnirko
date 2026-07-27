/* Prijava na dogodek - izpis. */
package si.turnirko.dto;

import si.turnirko.modeli.Prijava;

public record PrijavaDto(
        Long id,
        Long idIgralca,
        String polnoIme,
        String klub,
        Prijava.StatusPrijave status,
        Integer stNosilca,
        Integer ratingObZrebu,
        Integer koncnoMesto,
        /* Trenutni klubski ELO - podlaga za jakostni vrstni red v pripravi;
           null pomeni, da igralec se nima nobene obracunane tekme. */
        Integer rating,
        Long idSkupina,
        Integer mestoVSkupini
) {

    public static PrijavaDto iz(Prijava prijava) {
        return iz(prijava, null);
    }

    public static PrijavaDto iz(Prijava prijava, Integer rating) {
        return new PrijavaDto(
                prijava.getId(),
                prijava.getIgralec().getId(),
                prijava.getIgralec().polnoIme(),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.getStatus(),
                prijava.getStNosilca(),
                prijava.getRatingObZrebu(),
                prijava.getKoncnoMesto(),
                rating,
                prijava.getIdSkupina(),
                prijava.getMestoVSkupini()
        );
    }
}
