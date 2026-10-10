/* Tekmovalni starostni pas igralca po 11. clenu PST. NI stolpec v bazi -
   izpelje se iz letnice rojstva, zato se s casom premakne sam.

   Pas je NAJOZJI, ki mu igralec ustreza: kdor sodi v U11, sodi tudi v U13 in
   U15, a ga tu nastejemo enkrat. Vmesnik iz teh pasov sestavi filter (za
   turnir U15 organizator izbere U11, U13 in U15) - vec vrednosti hkrati zna,
   prekrivajocih se pasov pa ne.

   Pas je edini pojem o starosti: po njem se prijavlja na turnir IN filtrira
   lestvica, zato ima igralec na obeh mestih isto kategorijo. Je brez spola
   (ta je svoje merilo - na lestvici izbere moski ali zenski seznam) in tece
   po SEZONI, ne po koledarskem letu.

   Osebnega podatka ne razkriva - datum rojstva ostane v podrobnem pogledu
   (samo ADMIN), navzven gre groba skupina, ki je ob nastopu tako ali tako
   javna. */
package si.turnirko.modeli;

import java.time.LocalDate;

public enum StarostniPas {
    U11,
    U13,
    U15,
    U17,
    U19,
    U21,
    CLANI,
    VETERANI;

    /* Meje mladinskih pasov iz PST; veteran je od 40 naprej. */
    private static final int[] MLADINSKE_MEJE = { 11, 13, 15, 17, 19, 21 };
    private static final StarostniPas[] MLADINSKI = { U11, U13, U15, U17, U19, U21 };
    private static final int LET_VETERAN = 40;

    /* Mladinski pas je za igralce MLAJSE od te starosti (U11 = mlajsi od 11);
       CLANI in VETERANI mejo nimata - clan je vsak, ki mladinskega pasu
       prerase, veteran od veteraniOd() naprej. Meje berejo tudi javna razlaga
       ratinga, zato ostanejo na enem mestu. */
    public Integer mlajsiOd() {
        for (int i = 0; i < MLADINSKI.length; i++) {
            if (MLADINSKI[i] == this) {
                return MLADINSKE_MEJE[i];
            }
        }
        return null;
    }

    public static int veteraniOd() {
        return LET_VETERAN;
    }

    /* Starost se za sezono doloca na 31. december v letu, v katerem se sezona
       zacne (11. clen PST): igralec mora biti na ta dan MLAJSI od stevilke
       kategorije. Zato od januarja do junija se vedno velja letnica prejsnjega
       leta - kdor v tem letu dopolni 15, je do konca sezone se U15.

       Vrne null, kadar letnice ni: pasu ni mogoce ugibati in filter takega
       igralca preprosto ne zajame. */
    public static StarostniPas izpelji(LocalDate datumRojstva, LocalDate danes) {
        Integer starost = letaVSezoni(datumRojstva, danes);
        if (starost == null) {
            return null;
        }
        for (int i = 0; i < MLADINSKE_MEJE.length; i++) {
            if (starost < MLADINSKE_MEJE[i]) {
                return MLADINSKI[i];
            }
        }
        return starost >= LET_VETERAN ? VETERANI : CLANI;
    }

    /* Starost igralca po pravilu PST: leto sezone minus letnica rojstva.
       Ista stevilka, po kateri se doloca pas - zato je tu in ne prepisana
       drugje. Uporablja jo tudi starostno sidro Turnirko ratinga, ki mora biti
       merjeno enako kot tekmovalna kategorija.

       Vrne null, kadar letnice ni: starosti ni mogoce ugibati. */
    public static Integer letaVSezoni(LocalDate datumRojstva, LocalDate danes) {
        if (datumRojstva == null || danes == null) {
            return null;
        }
        return Sezona.zacetnoLeto(danes) - datumRojstva.getYear();
    }
}
