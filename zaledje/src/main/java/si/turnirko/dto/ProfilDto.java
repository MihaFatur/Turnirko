/* Javni del profila igralca: kdo je, skupni izkupicek, uvrstitev, graf
   napredka Turnirko ratinga in seznam vseh odigranih tekem.

   Vse to izhaja iz rezultatov, ki so na strani ze javni (lestvica, mreze,
   zapisniki), zato je javno tudi tukaj. Osebni podatki (e-posta, telefon,
   naslov, datum rojstva) v profil NE gredo. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.IgralnaRoka;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.RazlogSpremembe;

public record ProfilDto(
        Glava glava,
        Pregled pregled,
        Uvrstitev uvrstitev,
        List<TockaGrafa> graf,
        List<TekmaProfila> tekme,
        /* Tekme dvojic so LOCEN seznam in ne stejejo v "pregled": izida para
           ni mogoce pripisati posamezniku (isto pravilo kot pri ratingu). */
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
       nima ratinga (ni odigral nobene tekme, ki bi stela v rating). */
    public record Uvrstitev(
            Integer mesto,
            int skupajIgralcev,
            Integer percentil,
            Integer klubskoPovprecje
    ) {}

    /* Ena tocka grafa ratinga: stanje po tekmi in kaj ga je povzrocilo.
       "tekmovanje" in "del" sta ista zapisa kot v seznamu tekem (ime turnirja
       oz. lige in dogodek oz. kolo s parom ekip) - graf in seznam morata ob
       skoku s tocke na vrstico povedati isto. Kadar sta prazna, tocka nima
       para v seznamu tekem (postavitveni rating) in skok ni mogoc.

       Casa sta dva in nista isto: "kdaj" je trenutek OBRACUNA ratinga (po njem
       so tocke urejene), "datum" pa dan, na katerega sprememba VELJA - pri
       tekmi dan tekme (isti kot v vrstici seznama), pri odbitku za neaktivnost
       in pri postavitvi pa dan, ko je zapis zacel veljati. Brez datuma tocka
       ne more biti na casovni osi in uporabnik ne ve, kdaj je padec nastal.

       "razlog" je prazen pri tekmi in poln pri spremembi, ki tekme nima
       (POSTAVITEV, NEAKTIVNOST, ZUNANJA_UVRSTITEV) - po njem vmesnik izpise,
       zakaj je stevilka padla, ne da bi ugibal iz odsotnosti nasprotnika.

       "vir" in "pojasnilo" sta polna samo pri zunanji uvrstitvi in sta JAVNA
       namenoma: rocno vpisana stevilka na lestvici brez zapisanega vira je
       videti kot naklonjenost, z virom pa je trditev, ki jo lahko vsak
       preveri. */
    public record TockaGrafa(
            LocalDateTime kdaj,
            LocalDate datum,
            int vrednost,
            int sprememba,
            Long idTekme,
            boolean ligaska,
            String nasprotnik,
            String tekmovanje,
            String del,
            RazlogSpremembe razlog,
            /* Kako je sprememba nastala; iz tega vmesnik izpise razlago. */
            NacinSpremembe nacin,
            /* Sestavine obrazca; prazne pri vseh nacinih razen KORAK. */
            Razclenitev razclenitev,
            /* Od kod je stevilka in zakaj; samo pri zunanji uvrstitvi. */
            String vir,
            String pojasnilo
    ) {}

    /* Na pet nacinov se rating lahko premakne. Vmesnik jih ne sme ugibati iz
       odsotnosti polj, zato jih strežnik poimenuje. */
    public enum NacinSpremembe {
        /* Navaden korak: K x margina x teza x (izid - pricakovano). */
        KORAK,
        /* Uvrstitev novinca: rating se izracuna znova iz vseh izidov prvega
           dne, zato obrazca koraka ni. */
        UVRSTITEV,
        /* Postavitev od administratorja. */
        POSTAVITEV,
        /* Odbitek za neaktivnost. */
        NEAKTIVNOST,
        /* Stevilka, prepisana z zunanje lestvice (ITTF, NTZS) za redkega
           gosta; razlaga navede vir in pojasnilo. */
        ZUNANJA_UVRSTITEV
    }

    /* Sestavine ene spremembe po obrazcu koraka - tocno tiste stevilke, ki so
       jo naredile, in ne poznejsi izracun (parametri se lahko spremenijo).
       "tocke" je izid tekme (1 zmaga, 0 poraz). */
    public record Razclenitev(
            int k,
            double margina,
            double teza,
            double pricakovano,
            int tocke
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
            Integer spremembaRatinga
    ) {}

    /* Ena odigrana tekma dvojic z vidika lastnika profila: s kom je igral in
       proti komu. Spremembe ratinga ni - dvojice v rating ne stejejo. */
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
