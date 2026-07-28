/* Racun igralca za administratorjev pregled: kaj je oseba navedla ob
   registraciji, v katerem stanju je racun in s katerim igralcem je povezan.
   Predlogi so igralci iz sifranta, ki se ujemajo z navedenim imenom -
   potrditev je zato en klik. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;

public record RacunIgralcaDto(
        Long id,
        String email,
        // vloga loci racun igralca od organizatorja (potrjevanje je razlicno)
        Vloga vloga,
        String prijavljenoIme,
        String prijavljeniPriimek,
        String klubZelja,
        // potrjen klub organizatorja (pri igralcu prazen)
        Long idKlub,
        String klub,
        StatusRacuna status,
        boolean aktiven,
        Long idIgralec,
        String imeIgralca,
        LocalDateTime ustvarjenOb,
        List<PredlogIgralcaDto> predlogi
) {

    public static RacunIgralcaDto iz(Uporabnik u, List<PredlogIgralcaDto> predlogi) {
        return new RacunIgralcaDto(
                u.getId(),
                u.getUporabniskoIme(),
                u.getVloga(),
                u.getPrijavljenoIme(),
                u.getPrijavljeniPriimek(),
                u.getKlubZelja() != null ? u.getKlubZelja().getIme() : null,
                u.getKlub() != null ? u.getKlub().getId() : null,
                u.getKlub() != null ? u.getKlub().getIme() : null,
                u.getStatus(),
                u.isAktiven(),
                u.getIgralec() != null ? u.getIgralec().getId() : null,
                u.getIgralec() != null ? u.getIgralec().polnoIme() : null,
                u.getUstvarjenOb(),
                predlogi);
    }

    /* Kandidat za povezavo. Igralci, ki racun ze imajo, so oznaceni, da jih
       administrator ne poveze dvakrat. */
    public record PredlogIgralcaDto(
            Long idIgralec,
            String polnoIme,
            String klub,
            boolean zeImaRacun
    ) {}
}
