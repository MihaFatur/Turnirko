/* Javni del profila igralca: kdo je, skupni izkupicek, uvrstitev, graf
   napredka klubskega ELO in seznam vseh odigranih tekem.

   Vse to izhaja iz rezultatov, ki so na strani ze javni (lestvica, mreze,
   zapisniki), zato je javno tudi tukaj. Osebni podatki (e-posta, telefon,
   naslov, datum rojstva) v profil NE gredo. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.IzidTekme;

public record ProfilDto(
        Glava glava,
        Pregled pregled,
        Uvrstitev uvrstitev,
        List<TockaGrafa> graf,
        List<TekmaProfila> tekme,
        /* Tekme dvojic so LOCEN seznam in ne stejejo v "pregled": izida para
           ni mogoce pripisati posamezniku (isto pravilo kot pri ELO). */
        List<TekmaDvojic> dvojice
) {

    public record Glava(
            Long idIgralec,
            String polnoIme,
            String klub,
            IgralnaRoka igralnaRoka,
            Integer rating
    ) {}

    /* Skupni izkupicek prek vseh tekmovanj (samo posamicne tekme). */
    public record Pregled(
            int odigrane,
            int zmage,
            int porazi,
            int odstotekZmag,
            int dobljeniNizi,
            int prejetiNizi,
            int turnirskih,
            int ligaskih
    ) {}

    /* Umestitev med druge igralce. Vrednosti so lahko prazne, ce igralec se
       nima ratinga (ni odigral nobene tekme, ki bi stela v ELO). */
    public record Uvrstitev(
            Integer mesto,
            int skupajIgralcev,
            Integer percentil,
            Integer klubskoPovprecje
    ) {}

    /* Ena tocka grafa ELO: stanje po tekmi in kaj ga je povzrocilo.
       "tekmovanje" in "del" sta ista zapisa kot v seznamu tekem (ime turnirja
       oz. lige in dogodek oz. kolo s parom ekip) - graf in seznam morata ob
       skoku s tocke na vrstico povedati isto. Kadar sta prazna, tocka nima
       para v seznamu tekem (postavitveni rating) in skok ni mogoc.

       Casa sta dva in nista isto: "kdaj" je trenutek OBRACUNA ratinga (po njem
       so tocke urejene), "datum" pa dan TEKME - isti kot v vrstici seznama.
       Pri uvozeni zgodovini so vsi obracuni nastali ob uvozu, zato sme cas
       izpisati in po njem rezati obdobje samo "datum". Prazen je le pri
       postavitvenem ratingu (tekme ni). */
    public record TockaGrafa(
            LocalDateTime kdaj,
            LocalDate datum,
            int vrednost,
            int sprememba,
            Long idTekme,
            boolean ligaska,
            String nasprotnik,
            String tekmovanje,
            String del
    ) {}

    /* Ena odigrana tekma z vidika lastnika profila (nizi "za : proti"). */
    public record TekmaProfila(
            Long idTekme,
            boolean ligaska,
            LocalDate datum,
            String tekmovanje,
            String del,
            Long idNasprotnika,
            String nasprotnik,
            String klubNasprotnika,
            int niziZa,
            int niziProti,
            boolean zmaga,
            IzidTekme izidTip,
            Integer spremembaElo
    ) {}

    /* Ena odigrana tekma dvojic z vidika lastnika profila: s kom je igral in
       proti komu. Spremembe ELO ni - dvojice v rating ne stejejo. */
    public record TekmaDvojic(
            Long idTekme,
            LocalDate datum,
            String tekmovanje,
            String del,
            Long idSoigralca,
            String soigralec,
            String nasprotnika,
            int niziZa,
            int niziProti,
            boolean zmaga,
            IzidTekme izidTip
    ) {}
}
