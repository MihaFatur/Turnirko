/* Prijava na dogodek - izpis.

   Pri dvojicah je prijava PAR: polja z dvojko nosijo drugega igralca in so
   pri posamicnem dogodku prazna. Imeni ostajata loceni (in se ne zlepita v
   en niz), da ju vmesnik lahko izpise v dveh vrsticah kartice mreze. */
package si.turnirko.dto;

import si.turnirko.modeli.Prijava;

public record PrijavaDto(
        Long id,
        Long idIgralca,
        String polnoIme,
        String klub,
        /* Drugi igralec para; null pri posamicni prijavi in pri prijavljenem
           igralcu dvojic, ki soigralca se nima. */
        Long idIgralca2,
        String polnoIme2,
        String klub2,
        Prijava.StatusPrijave status,
        Integer stNosilca,
        Integer ratingObZrebu,
        Integer ratingObZrebu2,
        Integer koncnoMesto,
        /* Trenutni klubski ELO - podlaga za jakostni vrstni red v pripravi;
           null pomeni, da igralec se nima nobene obracunane tekme. */
        Integer rating,
        Integer rating2,
        Long idSkupina,
        Integer mestoVSkupini
) {

    public static PrijavaDto iz(Prijava prijava) {
        return iz(prijava, null, null);
    }

    public static PrijavaDto iz(Prijava prijava, Integer rating, Integer rating2) {
        return new PrijavaDto(
                prijava.getId(),
                prijava.getIgralec().getId(),
                prijava.getIgralec().polnoIme(),
                prijava.getKlubObPrijavi() != null ? prijava.getKlubObPrijavi().getIme() : null,
                prijava.jePar() ? prijava.getIgralec2().getId() : null,
                prijava.jePar() ? prijava.getIgralec2().polnoIme() : null,
                prijava.getKlubObPrijavi2() != null ? prijava.getKlubObPrijavi2().getIme() : null,
                prijava.getStatus(),
                prijava.getStNosilca(),
                prijava.getRatingObZrebu(),
                prijava.getRatingObZrebu2(),
                prijava.getKoncnoMesto(),
                rating,
                rating2,
                prijava.getIdSkupina(),
                prijava.getMestoVSkupini()
        );
    }
}
