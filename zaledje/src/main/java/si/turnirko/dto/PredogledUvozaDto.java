/* Predogled uvoza dogodka iz Stupe: vse, kar bi uvoz zapisal in preveril,
   brez zapisa (transakcija se razveljavi). */
package si.turnirko.dto;

public record PredogledUvozaDto(
        long idDogodka,
        String ime,
        /* oznaka posnetka, nad katerim tece uvoz */
        String posnetek,
        String zgostitev,
        /* posnetek je enak kot pri zadnjem uspesnem uvozu */
        boolean enakKotZadnjic,
        /* uvoz je mogoc: brez napak, odlocitev in neujemanj obveznih preverb */
        boolean dovoljuje,
        Long idTurnir,
        Long idLiga,
        PorociloUvozaDto porocilo
) {}
