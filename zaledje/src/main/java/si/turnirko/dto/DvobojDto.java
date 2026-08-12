/* Pregled "1 na 1": zgodovina vseh medsebojnih tekem dveh igralcev.
   Vsi izidi (zmage, nizi) so gledani z vidika "prvega" igralca. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

import si.turnirko.modeli.IzidTekme;

public record DvobojDto(
        Igralec prvi,
        Igralec drugi,
        int odigrane,
        int zmagePrvega,
        int zmageDrugega,
        int niziPrvega,
        int niziDrugega,
        List<Tekma> tekme
) {

    /* Osnovni podatki igralca v dvoboju. Ime in priimek sta locena, ker se
       velik naslov strani semaforja bere "Nejc Vrhovnik", izbirnik pod njim
       pa abecedno "Vrhovnik Nejc". */
    public record Igralec(
            Long id,
            String ime,
            String priimek,
            String polnoIme,
            String klub,
            Integer rating
    ) {}

    /* Ena medsebojna tekma; nizi so z vidika prvega igralca.
       Sprememba ELO je z vidika vsakega igralca (null, ce ni bila obracunana).
       Tekma je lahko turnirska ali ligaska ("ligaska"); id-ji prihajajo iz
       razlicnih tabel, zato sta za enolicno oznako potrebna oba podatka.
       Pri ligaski tekmi je "tekmovanje" ime lige, "del" pa kolo in srecanje. */
    public record Tekma(
            Long idTekme,
            boolean ligaska,
            String tekmovanje,
            String del,
            // Kdaj: zacetek turnirja oz. dan, ko je bilo srecanje odigrano.
            // Prazen, kadar turnir datuma nima - vrstica takrat pokaze le
            // tekmovanje.
            LocalDate datum,
            int niziPrvega,
            int niziDrugega,
            boolean zmagalPrvi,
            IzidTekme izidTip,
            Integer spremembaPrvega,
            Integer spremembaDrugega
    ) {}
}
