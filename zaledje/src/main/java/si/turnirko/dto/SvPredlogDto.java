/* Predlog razporeditve igralcev v skupine nivojev (sistem SV_REGIJA), kot ga
   da zreb BREZ zapisa v bazo. Vmesnik iz njega sestavi urejevalnik skupin:
   organizator predlog popravi (zamenja igralce med skupinami in nivoji) in ga
   pošlje nazaj kot rocni vpis. Pravila zreba tako ostanejo samo na strezniku. */
package si.turnirko.dto;

import java.util.List;

public record SvPredlogDto(List<NivoPredlogDto> nivoji) {

    public record NivoPredlogDto(int nivo, int odMesta, List<SkupinaPredlogDto> skupine) {}

    public record SkupinaPredlogDto(String oznaka, List<ClanDto> clani) {}

    /* Igralec v predlogu: dovolj za izpis in za vrnitev po id-ju prijave. */
    public record ClanDto(Long idPrijave, String polnoIme, String klub, Integer rating,
                          Integer stNosilca) {}
}
