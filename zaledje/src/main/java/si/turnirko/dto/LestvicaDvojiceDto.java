/* Ena vrstica lestvice dvojic ZNOTRAJ ene lige.

   Dvojica je par igralcev, ne posameznik: izida para ni mogoce pripisati enemu
   (zato dvojice ne stejejo ne v ELO ne v osebno statistiko), skupaj pa je par
   svoja tekmovalna enota in lestvico ima lahko. Kljuc para je NEUREJEN - ista
   dva igralca sta ista dvojica, ne glede na to, kdo je bil doma in v katerem
   zaporedju sta zapisana v postavi.

   Vrstni red imen v paru je abecedni, da je par vedno zapisan enako. */
package si.turnirko.dto;

public record LestvicaDvojiceDto(
        int mesto,
        Long idPrvi,
        String prvi,
        Long idDrugi,
        String drugi,
        String ekipa,
        int odigrane,
        int zmage,
        int porazi,
        // delez dobljenih tekem, zaokrozen na celo stevilo (za prikaz)
        int odstotek,
        int dobljeniNizi,
        int prejetiNizi
) {}
