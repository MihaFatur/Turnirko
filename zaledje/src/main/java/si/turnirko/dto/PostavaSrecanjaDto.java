/* Ena postavitev (mesto A/B/C ali X/Y/Z) v srecanju - izpis. */
package si.turnirko.dto;

import si.turnirko.modeli.PostavaSrecanja;
import si.turnirko.modeli.StranEkipe;

public record PostavaSrecanjaDto(
        StranEkipe stran,
        String pozicija,
        Long idIgralec,
        String polnoIme,
        boolean vDvojici
) {

    public static PostavaSrecanjaDto iz(PostavaSrecanja p) {
        return new PostavaSrecanjaDto(
                p.getStran(),
                p.getPozicija(),
                p.getIgralec().getId(),
                p.getIgralec().polnoIme(),
                p.isVDvojici());
    }
}
