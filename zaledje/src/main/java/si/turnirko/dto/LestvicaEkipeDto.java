/* Ena vrstica lestvice lige. Cona oznaci mesta napredovanja/izpada
   ("NAPREDUJE" / "KVALIFIKACIJE_GOR" / "KVALIFIKACIJE_DOL" / "IZPADE" / null;
   kvalifikacije V41). Klub je prazen pri prosti ekipi. */
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
