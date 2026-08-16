/* Ena vrstica lestvice posameznikov ZNOTRAJ ene lige.

   Loceno od LestvicaIgralcaDto (globalna lestvica po klubskem ELO): tu ne
   nastopa rating, ker ta tece cez vsa tekmovanja, tukaj pa stejejo samo
   posamicne tekme te lige. Merilo je zato izkupicek: zmage, ob izenacenju
   uspesnost in razlika nizov.

   "ekipa" je ekipa, za katero je igralec v tej ligi najveckrat nastopil -
   pri dovoljeni dvojni registraciji jih je lahko vec, vrstica pa pokaze eno. */
package si.turnirko.dto;

public record LestvicaIgralcaLigeDto(
        int mesto,
        Long idIgralec,
        String polnoIme,
        String ekipa,
        int odigrane,
        int zmage,
        int porazi,
        // delez dobljenih tekem, zaokrozen na celo stevilo (za prikaz)
        int odstotek,
        int dobljeniNizi,
        int prejetiNizi
) {}
