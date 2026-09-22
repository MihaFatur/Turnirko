/* Racun igralca za administratorjev pregled: kaj je oseba navedla ob
   registraciji, v katerem stanju je racun in s katerim igralcem je povezan.
   Predlogi so igralci iz sifranta, ki se ujemajo z navedenim imenom -
   potrditev je zato en klik. Pogled je samo administratorjev, zato sme
   nositi tudi navedeni datum rojstva (podlaga za pravo povezavo). */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirPovezave;
import si.turnirko.modeli.Vloga;

public record RacunIgralcaDto(
        Long id,
        String email,
        // vloga loci racun igralca od organizatorja (potrjevanje je razlicno)
        Vloga vloga,
        String prijavljenoIme,
        String prijavljeniPriimek,
        LocalDate prijavljeniDatumRojstva,
        String klubZelja,
        // potrjen klub organizatorja (pri igralcu prazen)
        Long idKlub,
        String klub,
        StatusRacuna status,
        boolean aktiven,
        Long idIgralec,
        String imeIgralca,
        // ali je lastnik naslova vpisal kodo; nepotrjene registracije se same izbrisejo
        boolean emailPotrjen,
        // null = skrbnik ni potreben; false = se caka; true = soglasje dano
        Boolean skrbnikPotrjen,
        // kdo je racun povezal z igralcem (samodejne povezave admin lahko razveze)
        VirPovezave virPovezave,
        LocalDateTime povezanOb,
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
                u.getPrijavljeniDatumRojstva(),
                u.getKlubZelja() != null ? u.getKlubZelja().getIme() : null,
                u.getKlub() != null ? u.getKlub().getId() : null,
                u.getKlub() != null ? u.getKlub().getIme() : null,
                u.getStatus(),
                u.isAktiven(),
                u.getIgralec() != null ? u.getIgralec().getId() : null,
                u.getIgralec() != null ? u.getIgralec().polnoIme() : null,
                u.jeEmailPotrjen(),
                u.getEmailSkrbnika() == null ? null : !u.cakaSkrbnika(),
                u.getVirPovezave(),
                u.getPovezanOb(),
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
