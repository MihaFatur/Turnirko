/* Srecanje dveh ekip - povzetek (za razpored/kola, koncnico in ekipne tekme
   turnirja).

   Srecanje pripada ligi ALI ekipni tekmi turnirja (V28). Vmesnik po idLiga
   oz. idTurnir nalozi tekmovanje, ki nosi lastnistvo, in vodi povezavo nazaj;
   polji tekmovanja sta zato natanko eno prazno. Podatki koncnice (serija,
   krog, tekma v seriji) so prazni pri rednem delu in pri turnirju.

   Klicatelj mora pri srecanju turnirja nalozit tekmo z dogodkom in turnirjem
   (join fetch) - pri ligi zadostujeta id-ja, ki ju posrednik vrne brez
   nalaganja. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;

public record SrecanjeDto(
        Long id,
        // liga, ki ji srecanje pripada - vmesnik po njej preveri lastnistvo
        Long idLiga,
        // ekipna tekma turnirja: turnir, dogodek in tekma v mrezi/skupini
        Long idTurnir,
        Long idDogodek,
        Long idTekma,
        int kolo,
        Long idEkipaDomaci,
        String domaci,
        Long idEkipaGost,
        String gost,
        int dobljeneDomaci,
        int dobljeneGost,
        StatusSrecanja status,
        LocalDateTime predvidenZacetek,
        // ura kola, ob kateri se srecanje igra (0 = prva ura lige, V31);
        // prazno pri kolu kroznega sistema
        Integer uraVKolu,
        // koncnica lige: serija, krog koncnice in zaporedna tekma v seriji
        Long idSerija,
        Integer krogKoncnice,
        Integer tekmaVSeriji,
        // srecanje ni bilo odigrano, izid je registriran (V29) - razpored
        // izid oznaci, da ga gledalec ne bere kot odigranega
        boolean brezBoja
) {

    public static SrecanjeDto iz(Srecanje s) {
        boolean turnirsko = s.jeTurnirsko();
        return new SrecanjeDto(
                s.getId(),
                s.getLiga() != null ? s.getLiga().getId() : null,
                turnirsko ? s.getTekma().getDogodek().getTurnir().getId() : null,
                turnirsko ? s.getTekma().getDogodek().getId() : null,
                turnirsko ? s.getTekma().getId() : null,
                s.getKolo(),
                s.getEkipaDomaci().getId(),
                s.getEkipaDomaci().prikazanoIme(),
                s.getEkipaGost().getId(),
                s.getEkipaGost().prikazanoIme(),
                s.getDobljeneDomaci(),
                s.getDobljeneGost(),
                s.getStatus(),
                s.getPredvidenZacetek(),
                s.getUraVKolu(),
                s.jeKoncnica() ? s.getSerija().getId() : null,
                s.jeKoncnica() ? s.getSerija().getKrog() : null,
                s.getTekmaVSeriji(),
                s.isBrezBoja());
    }
}
