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

        /* true = registracija navijaca (V58); izkljucuje se z organizatorjem. Obvezni
           boolean bi starejsemu odjemalcu, ki polja ne poslje, vrnil 400. */
        Boolean navijac,

        /* Izbran paket (igralec: BREZPLACNO ali PREMIUM; organizator: eden od treh). */
        @NotNull(message = "paket je obvezen") Paket paket,

        /* Neobvezno: naslov, s katerim se oseba registrira. Z njim se lastni
           nepotrjen ostanek prejsnjega poskusa ne steje med zasedena mesta kode. */
        @Size(max = 120, message = "e-posta je predolga") String email
) {

    public PreveriKodoVnos {
        if (navijac == null) {
            navijac = false;
        }
    }

    /* Registracija igralca ali organizatorja (do V58 edina oblika vnosa). */
    public PreveriKodoVnos(String koda, boolean organizator, Paket paket, String email) {
        this(koda, organizator, false, paket, email);
    }
}
