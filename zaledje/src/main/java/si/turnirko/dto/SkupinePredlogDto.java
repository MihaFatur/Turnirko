/* Predlog skupin za rocni vpis: razporeditev, kot bi jo naredil zreb, brez zapisa
   v bazo. Organizator jo popravi in vrne kot rocni vpis (RocniSkupineVnos). Pozicija
   v skupini je mesto znotraj skupine (1 = najmocnejsi v njej). Rezerve so samo pri
   formatu TOP - tam, kdor ni v izboru najboljsih N, ne igra; nimajo pozicije (null). */
package si.turnirko.dto;

import java.util.List;

public record SkupinePredlogDto(
        List<List<Clan>> skupine,
        List<Clan> rezerve
) {

    public record Clan(Long idPrijave, Integer pozicija) {}
}
