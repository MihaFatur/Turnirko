/* Zahteva za predogled ali uvoz dogodka iz Stupe.

   posnetek   - oznaka posnetka iz predogleda; uvoz tece nad ISTIM posnetkom,
                ki ga je admin pregledal (pri predogledu se ne poda)
   odlocitve  - istovetnost oseb, ki je strojno ni mogoce varno dolociti:
                idIgralec = igralec v registru, null = nov igralec. Novemu
                igralcu admin sme vpisati ime in priimek, datum rojstva in
                spol - kadar jih vir nima ali jih ima narobe (razdelitev
                imena); vpisano povozi vir. Spol je MOSKI ali ZENSKI.
   vsili      - uvozi tudi, ce je posnetek enak kot pri zadnjem uspesnem uvozu
   prevzemiTurnir - prazen turnir iz koledarja, ki ga prvi uvoz turnirja
                prevzame (eden od kandidatov predogleda); null = nov turnir */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

public record UvozZahtevaDto(
        String posnetek,
        List<Odlocitev> odlocitve,
        boolean vsili,
        Long prevzemiTurnir
) {
    public UvozZahtevaDto(String posnetek, List<Odlocitev> odlocitve, boolean vsili) {
        this(posnetek, odlocitve, vsili, null);
    }

    public record Odlocitev(long idOsebe, Long idIgralec, String ime, String priimek,
                            LocalDate datumRojstva, String spol) {}
}
