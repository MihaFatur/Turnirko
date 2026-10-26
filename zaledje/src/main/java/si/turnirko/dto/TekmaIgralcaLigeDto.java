/* Posamicna ali dvojiska tekma igralca v ligi, z njegovega vidika: razteg
   vrstice igralca v kadru ekipe pokaze vse njegove tekme v tej ligi. Srecanje
   (kolo, termin, ekipe) vmesnik vzame iz seznama srecanj lige, ki ga ze ima. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.TipTekmeSrecanja;

public record TekmaIgralcaLigeDto(
        Long id,
        Long idSrecanje,
        /* Ekipa, za katero je igralec nastopil v tem srecanju - igralec je lahko
           v ligi v dveh kadrih. */
        Long idEkipa,
        String ekipa,
        TipTekmeSrecanja tip,
        boolean domaci,
        /* Drugi igralec para pri dvojicah, sicer null. */
        String soigralec,
        /* Polna imena nasprotne strani: en igralec ali par. */
        List<String> nasprotniki,
        /* Dobljeni nizi igralca oz. para in nasprotne strani v tej tekmi. */
        int niziIgralca,
        int niziNasprotnika,
        IzidTekme izidTip,
        /* Zmaga igralca oz. para; null, dokler tekma ni odigrana. */
        Boolean zmaga
) {
}
