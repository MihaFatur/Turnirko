/* Ekipa v ligi - izpis (s stevilom igralcev v kadru za seznam ekip). */
package si.turnirko.dto;

import si.turnirko.modeli.Ekipa;

public record EkipaDto(
        Long id,
        Long idKlub,
        String klub,
        int zaporedna,
        String ime,
        String prikazanoIme,
        int steviloKadra
) {

    public static EkipaDto iz(Ekipa e, int steviloKadra) {
        return new EkipaDto(
                e.getId(),
                e.getKlub().getId(),
                e.getKlub().getIme(),
                e.getZaporedna(),
                e.getIme(),
                e.prikazanoIme(),
                steviloKadra);
    }
}
