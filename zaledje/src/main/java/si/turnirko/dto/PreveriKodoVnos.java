/* Predogled kode popusta v registraciji: katero kodo je oseba vpisala in
   kaj je izbrala (vloga, paket), da streznik pove, ali koda tam velja. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Paket;

public record PreveriKodoVnos(
        @NotBlank(message = "koda je obvezna")
        @Size(max = 40, message = "koda je predolga") String koda,

        /* true = registracija organizatorja. */
        boolean organizator,

        /* Izbran paket (igralec: BREZPLACNO ali PREMIUM; organizator: eden od treh). */
        @NotNull(message = "paket je obvezen") Paket paket,

        /* Neobvezno: naslov, s katerim se oseba registrira. Z njim se lastni
           nepotrjen ostanek prejsnjega poskusa ne steje med zasedena mesta kode. */
        @Size(max = 120, message = "e-posta je predolga") String email
) {}
