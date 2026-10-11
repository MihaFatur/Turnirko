/* Racun igralca za administratorjev pregled: kaj je oseba navedla ob
   registraciji, v katerem stanju je racun in s katerim igralcem je povezan.
   Predlogi so igralci iz sifranta, ki se ujemajo z navedenim imenom -
   potrditev je zato en klik. Pogled je samo administratorjev, zato sme
   nositi tudi navedeni datum rojstva (podlaga za pravo povezavo). Enako
   velja za naročnino: administrator mora videti, kdo ima kateri paket, tudi
   pred potrditvijo racuna. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
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
        // placilni paket racuna (vedno izpolnjen; vsebina je odvisna od vloge)
        NarocninaRacuna narocnina,
        List<PredlogIgralcaDto> predlogi
) {

    public static RacunIgralcaDto iz(Uporabnik u, List<PredlogIgralcaDto> predlogi,
                                     NarocninaRacuna narocnina) {
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
                narocnina,
                predlogi);
    }

    /* Naročnina racuna. Igralec brez zapisa je BREZPLACNO (Free); organizator
       brez zapisa nima paketa (paket = null), ker ga zapis dobi sele s placilom.
       Stanje se pokaze ne glede na vlogo, tudi ce racun se caka na potrditev -
       placan paket pred potrditvijo je ravno tisto, kar admin mora videti. */
    public record NarocninaRacuna(
            Paket paket,
            // null = zapisa ni (igralec Free ali organizator brez paketa)
            StatusNarocnine status,
            CiklusPlacila ciklus,
            // konec placanega obdobja kot koledarski dan (ISO), null brez zapisa
            LocalDate obdobjeDo,
            // ali paket zdaj daje pravice (preklicana ostane true do konca obdobja)
            boolean veljavna
    ) {}

    /* Kandidat za povezavo. Igralci, ki racun ze imajo, so oznaceni, da jih
       administrator ne poveze dvakrat. */
    public record PredlogIgralcaDto(
            Long idIgralec,
            String polnoIme,
            String klub,
            boolean zeImaRacun
    ) {}
}
