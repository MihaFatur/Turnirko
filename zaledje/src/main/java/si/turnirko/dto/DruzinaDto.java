/* Druzina (V59) v odgovorih: pogled racuna (navijaca ali igralca) in pogled
   administratorja.

   Pogled racuna ne nosi osebnih podatkov: ime in klub igralca sta javna, datuma
   rojstva ni. Pogled administratorja ga nosi (odloca o povezavi z otrokom, zato
   mora videti, ali starost ustreza), kot ostali administratorski izpisi. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

import si.turnirko.modeli.DruzinaPovezava;
import si.turnirko.modeli.StarostniPas;

public final class DruzinaDto {

    private DruzinaDto() {}

    /* Kaj racun ve o druzini: ali sme zahtevati povezavo (placljiv paket), koliko
       jih sme imeti, njegove povezave in - za igralca - ali ima Premium po druzini. */
    public record Pregled(
            boolean smemZahtevati,
            int najvec,
            List<Povezava> povezave,
            boolean premiumPrekDruzine
    ) {}

    /* "veljavna" = povezava zdaj dejansko odpira zasebno statistiko (potrjena, paket
       navijaca velja, igralec se ni prerasel obdobja); "status" je odlocitev admina. */
    public record Povezava(
            Long id,
            Long idIgralec,
            String igralec,
            String klub,
            DruzinaPovezava.Status status,
            boolean veljavna,
            LocalDate veljaDo
    ) {}

    public record Zahteva(@jakarta.validation.constraints.NotNull(message = "igralec je obvezen") Long idIgralec) {}

    public record Admin(
            Long id,
            Long idRacun,
            String racun,
            String email,
            Long idIgralec,
            String igralec,
            String klub,
            LocalDate datumRojstva,
            StarostniPas pas,
            DruzinaPovezava.Status status,
            LocalDate zahtevanaOb,
            LocalDate veljaDo,
            // ali ima placnik zdaj veljaven paket Navijac
            boolean placnikImaPaket
    ) {}
}
