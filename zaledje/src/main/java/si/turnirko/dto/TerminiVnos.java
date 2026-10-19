/* Rocni termini kol lige - kdaj se igra katero kolo.

   Loceno od LigaVnos iz istega razloga kot PrehodiVnos: pravila lige se po
   generiranju razporeda zaklenejo, termini pa se morajo dati popraviti tudi
   sredi sezone (kolo se prestavi). Loceno tudi zato, ker se pravila vpisejo
   ze ob ustvarjanju lige, ko ekip in s tem stevila kol se ni.

   Termin kola dobijo vsa srecanja kola. Kolo, ki ga seznam ne nasteje, ostane
   nedotaknjeno; kolo z zacetkom null termin izgubi. Liga z urami srecanj (V31)
   ima v kolu vec ur, zato sme vnos nasteti se posamezna srecanja - njihov
   zacetek obvelja ZA terminom kola. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TerminiVnos(
        @NotNull(message = "seznam kol je obvezen") @Valid List<TerminKola> kola,
        // neobvezno: popravki posameznih srecanj rednega dela
        @Valid List<TerminSrecanja> srecanja
) {

    public TerminiVnos(List<TerminKola> kola) {
        this(kola, null);
    }

    // zacetek = null pomeni "to kolo nima termina" (izpise se spet "razpored")
    public record TerminKola(
            @NotNull(message = "stevilka kola je obvezna") Integer kolo,
            LocalDateTime zacetek
    ) {}

    /* Stanje enega srecanja rednega dela.

       zacetek = null pomeni "to srecanje nima termina".
       kolo: kolo, v katerem se srecanje igra; prazno = ostane v svojem.
       Vrednost, ki se razlikuje od kola iz zreba, srecanje PRESTAVI (V47); vrne
       ga vrednost, ki je enaka kolu iz zreba. Zacetek strezniku poda klicatelj,
       ta ga ne ugiba iz termina kola - tako ostane prestavitev na drug dan
       (kolo je sicer en dan) izrecna.
       cakaNaTermin: prestavljeno brez novega termina; kolo in zacetek se
       prezreta (srecanje se vrne v kolo iz zreba in zacetka nima).
       razlog: neobvezen razlog prestavitve (najvec 200 znakov), viden vsem. */
    public record TerminSrecanja(
            @NotNull(message = "id srecanja je obvezen") Long id,
            LocalDateTime zacetek,
            Integer kolo,
            Boolean cakaNaTermin,
            @Size(max = 200, message = "razlog je lahko dolg najvec 200 znakov") String razlog
    ) {
        /* Popravek termina brez prestavitve (ura srecanja v kolu). */
        public TerminSrecanja(Long id, LocalDateTime zacetek) {
            this(id, zacetek, null, null, null);
        }
    }
}
