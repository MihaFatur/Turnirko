/* Igralec v kadru ekipe - izpis (z ratingom za lazjo izbiro postave). */
package si.turnirko.dto;

import si.turnirko.modeli.KaderEkipe;

public record KaderIgralecDto(
        Long id,          // id vnosa v kadru
        Long idIgralec,
        String polnoIme,
        String klub,
        Integer vrstniRed,
        Integer rating
) {

    public static KaderIgralecDto iz(KaderEkipe k, Integer rating) {
        return new KaderIgralecDto(
                k.getId(),
                k.getIgralec().getId(),
                k.getIgralec().polnoIme(),
                k.getIgralec().getKlub() != null ? k.getIgralec().getKlub().getIme() : null,
                k.getVrstniRed(),
                rating);
    }
}
