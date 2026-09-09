/* Ekipa v ligi - izpis (s stevilom igralcev v kadru za seznam ekip).
   Klub je prazen pri prosti ekipi (nastopa samo v tej ligi, v registru
   klubov je ni). */
package si.turnirko.dto;

import si.turnirko.modeli.Ekipa;

public record EkipaDto(
        Long id,
        Long idKlub,
        String klub,
        int zaporedna,
        String ime,
        String prikazanoIme,
        // Mesto na jakostni lestvici lige (1 = najmocnejsa); pove kaj samo pri
        // ligi z enakomerno razvrstitvijo, sicer je vrstni red vpisa.
        Integer stNosilca,
        int steviloKadra
) {

    public static EkipaDto iz(Ekipa e, int steviloKadra) {
        return new EkipaDto(
                e.getId(),
                e.jeProsta() ? null : e.getKlub().getId(),
                e.jeProsta() ? null : e.getKlub().getIme(),
                e.getZaporedna(),
                e.getIme(),
                e.prikazanoIme(),
                e.getStNosilca(),
                steviloKadra);
    }
}
