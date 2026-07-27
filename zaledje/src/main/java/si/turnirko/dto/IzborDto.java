/* Predogled izbora in razreza v skupine pri sistemu SKUPINE (format TOP).

   Vmesnik s tem izrise crto reza in predogled skupin, ne da bi moral sam
   ponoviti domenska pravila (koliko jih igra, kako se razrezejo skupine,
   kdaj razrez ni izvedljiv). */
package si.turnirko.dto;

import java.util.List;

public record IzborDto(
        /* Koliko najboljsih igra = stevilo skupin x velikost skupine. */
        int meja,
        int prijavljenih,
        /* Koliko jih dejansko igra (manj od meje, ce je prijav premalo). */
        int igra,
        List<SkupinaPredogledDto> skupine,
        /* Zakaj zreb (se) ni mogoc; null pomeni, da je vse pripravljeno. */
        String zadrzek
) {

    /* Ena skupina v predogledu: katera mesta jakostne lestvice zajame. */
    public record SkupinaPredogledDto(String oznaka, int velikost, int odMesta, int doMesta) {}
}
