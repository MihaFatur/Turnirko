/* Rocni termini kol lige - kdaj se igra katero kolo.

   Loceno od LigaVnos iz istega razloga kot PrehodiVnos: pravila lige se po
   generiranju razporeda zaklenejo, termini pa se morajo dati popraviti tudi
   sredi sezone (kolo se prestavi). Loceno tudi zato, ker se pravila vpisejo
   ze ob ustvarjanju lige, ko ekip in s tem stevila kol se ni.

   Termin je last KOLA in ne posameznega srecanja: vsa srecanja kola dobijo
   isti zacetek. Kolo, ki ga seznam ne nasteje, ostane nedotaknjeno; kolo z
   zacetkom null termin izgubi. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TerminiVnos(
        @NotNull(message = "seznam kol je obvezen") @Valid List<TerminKola> kola
) {

    // zacetek = null pomeni "to kolo nima termina" (izpise se spet "razpored")
    public record TerminKola(
            @NotNull(message = "stevilka kola je obvezna") Integer kolo,
            LocalDateTime zacetek
    ) {}
}
