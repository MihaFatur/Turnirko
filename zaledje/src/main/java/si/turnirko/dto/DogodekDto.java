/* Dogodek - izpis. */
package si.turnirko.dto;

import java.time.LocalDate;

import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;

public record DogodekDto(
        Long id,
        Long idTurnir,
        String ime,
        Disciplina disciplina,
        SpolKategorija spolKategorija,
        String starostnaKategorija,
        SistemTekmovanja sistemTekmovanja,
        int privzetoSteviloNizov,
        /* Nastavitvi skupinskega dela; prazni pri drugih sistemih. */
        Integer steviloSkupin,
        Integer velikostSkupine,
        Double prijavnina,
        LocalDate rokPrijave,
        StatusTekmovanja status
) {

    public static DogodekDto iz(Dogodek dogodek) {
        return new DogodekDto(
                dogodek.getId(),
                dogodek.getTurnir().getId(),
                dogodek.getIme(),
                dogodek.getDisciplina(),
                dogodek.getSpolKategorija(),
                dogodek.getStarostnaKategorija(),
                dogodek.getSistemTekmovanja(),
                dogodek.getPrivzetoSteviloNizov(),
                dogodek.getSteviloSkupin(),
                dogodek.getVelikostSkupine(),
                dogodek.getPrijavnina(),
                dogodek.getRokPrijave(),
                dogodek.getStatus()
        );
    }
}
