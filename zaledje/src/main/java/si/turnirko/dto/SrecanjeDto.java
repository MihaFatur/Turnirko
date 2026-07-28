/* Srecanje dveh ekip - povzetek (za razpored/kola). */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;

public record SrecanjeDto(
        Long id,
        // liga, ki ji srecanje pripada - vmesnik po njej preveri lastnistvo
        Long idLiga,
        int kolo,
        Long idEkipaDomaci,
        String domaci,
        Long idEkipaGost,
        String gost,
        int dobljeneDomaci,
        int dobljeneGost,
        StatusSrecanja status,
        LocalDateTime predvidenZacetek
) {

    public static SrecanjeDto iz(Srecanje s) {
        return new SrecanjeDto(
                s.getId(),
                s.getLiga().getId(),
                s.getKolo(),
                s.getEkipaDomaci().getId(),
                s.getEkipaDomaci().prikazanoIme(),
                s.getEkipaGost().getId(),
                s.getEkipaGost().prikazanoIme(),
                s.getDobljeneDomaci(),
                s.getDobljeneGost(),
                s.getStatus(),
                s.getPredvidenZacetek());
    }
}
