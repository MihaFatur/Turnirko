/* Predogled uvoza dogodka iz Stupe: vse, kar bi uvoz zapisal in preveril,
   brez zapisa (transakcija se razveljavi). */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

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
        PorociloUvozaDto porocilo,
        /* prazni turnirji iz koledarja, ki jih sme prvi uvoz turnirja prevzeti
           namesto novega (PrevzemKoledarja); prazno, ce jih ni ali je dogodek
           ze uvozen */
        List<KandidatPrevzema> kandidatiPrevzema,
        /* predlagan kandidat; null, kadar ga ni mogoce izbrati brez dvoma */
        Long predlaganPrevzem
) {
    public record KandidatPrevzema(long id, String ime, LocalDate zacetek, LocalDate konec, String kraj) {}
}
