/* Branje listka, kot ga vidi sodnik na svojem telefonu (S2). Samo tisto, kar je
   na listku v njegovih rokah: imeni (javni podatek tekme), dogodek, prebrane tocke
   in miza. Brez fotografije, id-jev igralcev ali cesar koli drugega.

   stanje: V_VRSTI (se bere), PREBRANO (predlog za potrditev), NAPAKA (fotografiraj
   znova), POTRJENO / ZAVRZENO (zakljuceno). predlog je v straneh tekme (1 =
   igralec1), kot ga dobi organizator. poklicanaMiza je miza iz klica (M1), ce je
   tekma ze poklicana. velja: VELJA_TAKOJ (izid gre v tekmovanje ob potrditvi) ali
   PREDPREGLED (pocaka organizatorja). */
package si.turnirko.dto;

import si.turnirko.modeli.SodniskaPotrditev;
import si.turnirko.modeli.StanjeBranja;

public record SodniskoBranjeDto(
        Long id,
        StanjeBranja stanje,
        String napaka,
        String dogodek,
        String del,
        String igralec1,
        String igralec2,
        int steviloNizov,
        PredlogIzidaDto predlog,
        Integer prebranaMiza,
        boolean negotovaMiza,
        Integer poklicanaMiza,
        boolean izidZeVpisan,
        SodniskaPotrditev velja,
        boolean sodnikPotrdil
) {}
