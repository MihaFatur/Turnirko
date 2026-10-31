/* Povzetek igralca, ki ga racun spremlja, za sklop "Moj igralec" na domaci
   strani: kdo je, kje je na lestvici, kako mu gre in kdaj igra naslednjic.

   Vse to je ze javno na profilu igralca (ProfilDto), zato je javno tudi tu:
   osebnih podatkov (datum rojstva, e-posta) v povzetku ni. Zasebne analize
   profila (forma, nasprotniki, napoved) v sklop ne gredo - te vidi samo
   igralec sam. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.StatusTekmovanja;

public record DomovIgralecDto(
        Long idIgralec,
        String polnoIme,
        String klub,
        // Turnirko rating; prazen, dokler igralec ni odigral tekme, ki bi stela.
        Integer rating,
        // Mesto na lestvici igralcevega spola in skupine - isti zapis kot v
        // glavi profila, da se mesti ne moreta razhajati.
        ProfilDto.Uvrstitev uvrstitev,
        // Zadnje tri odigrane tekme, najnovejsa prva (isti zapis kot na profilu).
        List<ProfilDto.TekmaProfila> zadnje,
        // Naslednje ligasko srecanje ekipe, v kateri igra; prazno, kadar ga ni.
        NaslednjeSrecanje naslednjeSrecanje,
        // Najbolj zgoden turnir, na katerega je prijavljen in se ni mimo.
        PrihajajociTurnir prihajajociTurnir,
        // Igralec je v druzini racuna (V59): vmesnik ponudi zasebno statistiko na profilu.
        boolean druzina
) {

    /* Srecanje ekipe igralca. Igralec v srecanju ne igra nujno (kader je lahko
       sirsi od postave), zato je podana ekipa in ne on. "zacetek" je lahko
       prazen (razpored brez predvidenega zacetka); srecanje, ki caka na
       termin, ali tisto, ki mu je termin ze mimo, ni "naslednje". */
    public record NaslednjeSrecanje(
            Long idSrecanje,
            Long idLiga,
            String liga,
            int kolo,
            LocalDateTime zacetek,
            String ekipa,
            String nasprotnik,
            boolean doma
    ) {}

    /* Turnir, na katerega je igralec prijavljen (posamicno, v paru ali v kadru
       ekipe). Status je prikazni - zreb pred datumom je "V pripravi". */
    public record PrihajajociTurnir(
            Long idTurnir,
            String ime,
            LocalDate datum,
            StatusTekmovanja status
    ) {}
}
