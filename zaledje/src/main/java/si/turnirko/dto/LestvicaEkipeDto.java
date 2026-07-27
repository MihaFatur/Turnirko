/* Ena vrstica lestvice lige. Cona oznaci mesta napredovanja/izpada
   ("NAPREDUJE" / "IZPADE" / null). */
package si.turnirko.dto;

public record LestvicaEkipeDto(
        int mesto,
        Long idEkipa,
        String ekipa,
        String klub,
        int odigrane,
        int zmage,
        int neodlocene,
        int porazi,
        int dobljeneTekme,
        int prejeteTekme,
        int razlikaTekme,
        int dobljeniNizi,
        int prejetiNizi,
        int razlikaNizi,
        int tocke,
        String cona
) {}
