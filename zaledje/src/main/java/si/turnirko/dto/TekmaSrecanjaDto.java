/* Ena posamicna tekma znotraj srecanja - izpis (zapisnik). Pri dvojicah sta
   na strani dva igralca (domaci2/gost2). Sprememba ratinga velja samo za posamicne
   tekme (dvojice ne stejejo). */
package si.turnirko.dto;

import java.util.List;

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
        /* Kratka oblika za ozka mesta (glava stolpca pri vnosu tock po nizih).
           Ne urejevalni kljuc in ne obrnjen vrstni red - polna imena ostanejo
           "Ana Novak"; priimek pride iz baze, ker rezanje polnega imena pri
           "Ana Marija Novak" odpove. */
        String priimekDomaci,
        String priimekDomaci2,
        String priimekGost,
        String priimekGost2,
        /* Id-ji istih igralcev - okno za menjavo z njimi izbere trenutno
           zasedbo tekme. */
        Long idDomaci,
        Long idDomaci2,
        Long idGost,
        Long idGost2,
        int steviloNizov,
        int dobljeniNiziDomaci,
        int dobljeniNiziGost,
        StranEkipe zmagovalecStran,
        IzidTekme izidTip,
        StatusTekmeSrecanja status,
        Integer spremembaRatingaDomaci,
        Integer spremembaRatingaGost,
        /* Tocke po nizih po vrsti (11:7, 9:11 ...); prazen seznam, kadar jih
           organizator ni vpisal - vnos je neobvezen. */
        List<NizVnos> nizi,
        /* Ali na tej strani igra kdo drug kot na mestu v zacetni postavi
           (menjava). Izpelje ga streznik (SrecanjeStoritev.menjava), da oznaka
           "A-Y" ob imenu, ki ni A, ne zavaja. */
        boolean menjavaDomaci,
        boolean menjavaGost
) {

    public static TekmaSrecanjaDto iz(TekmaSrecanja t, Integer spremembaRatingaDomaci,
                                      Integer spremembaRatingaGost, List<NizVnos> nizi,
                                      boolean menjavaDomaci, boolean menjavaGost) {
        return new TekmaSrecanjaDto(
                t.getId(),
                t.getZaporedje(),
                t.getTip(),
                t.getOznaka(),
                t.getIgralecDomaci() != null ? t.getIgralecDomaci().polnoIme() : null,
                t.getIgralecDomaci2() != null ? t.getIgralecDomaci2().polnoIme() : null,
                t.getIgralecGost() != null ? t.getIgralecGost().polnoIme() : null,
                t.getIgralecGost2() != null ? t.getIgralecGost2().polnoIme() : null,
                t.getIgralecDomaci() != null ? t.getIgralecDomaci().getPriimek() : null,
                t.getIgralecDomaci2() != null ? t.getIgralecDomaci2().getPriimek() : null,
                t.getIgralecGost() != null ? t.getIgralecGost().getPriimek() : null,
                t.getIgralecGost2() != null ? t.getIgralecGost2().getPriimek() : null,
                t.getIgralecDomaci() != null ? t.getIgralecDomaci().getId() : null,
                t.getIgralecDomaci2() != null ? t.getIgralecDomaci2().getId() : null,
                t.getIgralecGost() != null ? t.getIgralecGost().getId() : null,
                t.getIgralecGost2() != null ? t.getIgralecGost2().getId() : null,
                t.getSteviloNizov(),
                t.getDobljeniNiziDomaci(),
                t.getDobljeniNiziGost(),
                t.getZmagovalecStran(),
                t.getIzidTip(),
                t.getStatus(),
                spremembaRatingaDomaci,
                spremembaRatingaGost,
                nizi,
                menjavaDomaci,
                menjavaGost);
    }
}
