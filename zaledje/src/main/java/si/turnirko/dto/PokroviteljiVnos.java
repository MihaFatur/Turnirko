/* Celoten seznam pokroviteljev lige ali turnirja (V56), v vrstnem redu traku.
   Shrani se v celoti - prazen seznam pokrovitelje odstrani. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PokroviteljiVnos(@NotNull List<@Valid Pokrovitelj> pokrovitelji) {

    /* Logotip je slika iz knjiznice (POST /slike). */
    public record Pokrovitelj(@NotNull Long idSlika, String ime, String povezava, boolean glavni) {}
}
