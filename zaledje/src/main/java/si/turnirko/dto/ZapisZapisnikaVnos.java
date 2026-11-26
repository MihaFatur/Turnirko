/* Zapis prebranega (in po potrebi popravljenega) ligaskega zapisnika v srecanje.
   Tekme po vrsti razporeda formata: kdo je igral (id igralcev iz kadra; pri
   dvojicah dva) in izid. Tekma brez izida (nizi prazni in izidTip null) ostane
   brez vpisa - pri pragu zmag je po odlocitvi neodigrana.

   prepisi: srecanje ze ima vpisan zapisnik - najprej se razveljavi (ista pot kot
   gumb »Razveljavi zapisnik«), nato se zapise ta. casPregledaMs: koliko je
   organizator gledal predlog (merilo, neobvezno). */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StranEkipe;

public record ZapisZapisnikaVnos(@NotNull @Valid List<Tekma> tekme, boolean prepisi, Long casPregledaMs) {

    public record Tekma(List<Long> domaci, List<Long> gost, @Valid List<NizVnos> nizi, IzidTekme izidTip,
                        StranEkipe zmagovalecStran) {

        public boolean imaIzid() {
            return izidTip != null || (nizi != null && !nizi.isEmpty());
        }
    }
}
