/* Ena vrstica globalne lestvice igralcev: trenutni klubski ELO in
   povzetek vseh odigranih tekem (kadarkoli, na vseh dogodkih). */
package si.turnirko.dto;

public record LestvicaIgralcaDto(
        Long idIgralca,
        String polnoIme,
        String klub,
        Integer rating,
        int odigrane,
        int zmage,
        int porazi
) {}
