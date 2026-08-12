/* Starostno-spolna kategorija igralca na lestvici. NI stolpec v bazi in ne
   pove, v cem igralec dejansko nastopa - izpelje se iz spola in letnice
   rojstva, tako kot se v namiznem tenisu doloca starostna skupina (po
   koledarskem letu, ne po rojstnem dnevu).

   Ker je izpeljana, se s starostjo sama premakne in je ni treba vzdrzevati.
   Osebnega podatka ne razkriva: datum rojstva ostane v podrobnem pogledu,
   navzven gre le groba skupina, ki je pri nastopu tako ali tako javna. */
package si.turnirko.modeli;

import java.time.LocalDate;

public enum KategorijaIgralca {
    CLANI,
    CLANICE,
    U19,
    VETERANI;

    /* Meji sta obicajni za slovenska tekmovanja: mladinci do 19, veterani od 40. */
    private static final int LET_MLADINEC = 19;
    private static final int LET_VETERAN = 40;

    public static KategorijaIgralca izpelji(Spol spol, LocalDate datumRojstva, LocalDate danes) {
        if (spol == null || datumRojstva == null) {
            return null;
        }
        int starost = danes.getYear() - datumRojstva.getYear();
        if (starost < LET_MLADINEC) {
            return U19;
        }
        if (starost >= LET_VETERAN) {
            return VETERANI;
        }
        return spol == Spol.ZENSKI ? CLANICE : CLANI;
    }
}
