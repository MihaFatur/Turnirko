/* Ena posamicna tekma znotraj srecanja - izpis (zapisnik). Pri dvojicah sta
   na strani dva igralca (domaci2/gost2). Sprememba ELO velja samo za posamicne
   tekme (dvojice ne stejejo). */
package si.turnirko.dto;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;

public record TekmaSrecanjaDto(
        Long id,
        int zaporedje,
        TipTekmeSrecanja tip,
        String oznaka,
        String domaci,
        String domaci2,
        String gost,
        String gost2,
        int steviloNizov,
        int dobljeniNiziDomaci,
        int dobljeniNiziGost,
        StranEkipe zmagovalecStran,
        IzidTekme izidTip,
        StatusTekmeSrecanja status,
        Integer spremembaEloDomaci,
        Integer spremembaEloGost
) {

    public static TekmaSrecanjaDto iz(TekmaSrecanja t, Integer spremembaEloDomaci, Integer spremembaEloGost) {
        return new TekmaSrecanjaDto(
                t.getId(),
                t.getZaporedje(),
                t.getTip(),
                t.getOznaka(),
                t.getIgralecDomaci() != null ? t.getIgralecDomaci().polnoIme() : null,
                t.getIgralecDomaci2() != null ? t.getIgralecDomaci2().polnoIme() : null,
                t.getIgralecGost() != null ? t.getIgralecGost().polnoIme() : null,
                t.getIgralecGost2() != null ? t.getIgralecGost2().polnoIme() : null,
                t.getSteviloNizov(),
                t.getDobljeniNiziDomaci(),
                t.getDobljeniNiziGost(),
                t.getZmagovalecStran(),
                t.getIzidTip(),
                t.getStatus(),
                spremembaEloDomaci,
                spremembaEloGost);
    }
}
