/* Ekipa v ligi - izpis. */
package si.turnirko.dto;

import si.turnirko.modeli.Ekipa;

public record EkipaDto(
        Long id,
        Long idKlub,
        String klub,
        int zaporedna,
        String ime,
        String prikazanoIme
) {

    public static EkipaDto iz(Ekipa e) {
        return new EkipaDto(
                e.getId(),
                e.getKlub().getId(),
                e.getKlub().getIme(),
                e.getZaporedna(),
                e.getIme(),
                e.prikazanoIme());
    }
}
