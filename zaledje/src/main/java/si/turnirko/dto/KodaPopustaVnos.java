/* Nova koda popusta (admin). Katera polja so obvezna, doloca vrsta:
   PREMIUM_MESECI potrebuje `meseci`, ODSTOTEK `odstotek` (neobvezno `zaVlogo`),
   ORGANIZATOR_MESECI `meseci` in organizatorski `paket`; ostala polja se
   prezrejo. Brez `koda` streznik ustvari nakljucno. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Paket;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.modeli.Vloga;

public record KodaPopustaVnos(
        @Size(max = 40, message = "koda je predolga") String koda,

        @Size(max = 200, message = "opis je predolg") String opis,

        @NotNull(message = "vrsta je obvezna") VrstaKodePopusta vrsta,

        Integer meseci,
        Integer odstotek,
        Paket paket,
        Vloga zaVlogo,

        /* Zadnji dan, ko se koda sprejme; prazno = brez roka. */
        LocalDate veljaDo,

        /* Prazno = neomejeno. */
        Integer najvecUporab
) {}
