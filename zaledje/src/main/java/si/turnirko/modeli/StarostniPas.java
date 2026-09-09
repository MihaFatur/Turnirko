/* Tekmovalni starostni pas igralca po 11. clenu PST. NI stolpec v bazi -
   izpelje se iz letnice rojstva, zato se s casom premakne sam.

   Pas je NAJOZJI, ki mu igralec ustreza: kdor sodi v U11, sodi tudi v U13 in
   U15, a ga tu nastejemo enkrat. Vmesnik iz teh pasov sestavi filter (za
   turnir U15 organizator izbere U11, U13 in U15) - vec vrednosti hkrati zna,
   prekrivajocih se pasov pa ne.

   Zakaj poleg KategorijaIgralca se en pojem o starosti:
   - KategorijaIgralca je starostno-SPOLNA kategorija lestvice (Clani/Clanice/
     U19/Veterani) in tece po koledarskem letu;
   - StarostniPas je tekmovalna kategorija, po kateri se prijavlja na turnir:
     brez spola (ta je svoje merilo) in po SEZONI, ne po koledarskem letu.
   Zdruzitev bi enemu od obeh pogledov spremenila pomen, zato stojita loceno.

   Osebnega podatka ne razkriva - datum rojstva ostane v podrobnem pogledu
   (samo ADMIN), navzven gre groba skupina, ki je ob nastopu tako ali tako
   javna (isti premislek kot pri KategorijaIgralca). */
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

    /* Meje mladinskih pasov iz PST; veteran je od 40 naprej (isto kot na
       lestvici, glej KategorijaIgralca). */
    private static final int[] MLADINSKE_MEJE = { 11, 13, 15, 17, 19, 21 };
    private static final StarostniPas[] MLADINSKI = { U11, U13, U15, U17, U19, U21 };
    private static final int LET_VETERAN = 40;

    /* Prvi mesec nove sezone. Sezona tece od septembra do julija (PST), rez
       postavimo na 1. julij - isto kot sezonaIzDatuma v vmesniku, zato imata
       tekmovanje in igralec isto sezono. */
    private static final int PRVI_MESEC_SEZONE = 7;

    /* Starost se za sezono doloca na 31. december v letu, v katerem se sezona
       zacne (11. clen PST): igralec mora biti na ta dan MLAJSI od stevilke
       kategorije. Zato od januarja do junija se vedno velja letnica prejsnjega
       leta - kdor v tem letu dopolni 15, je do konca sezone se U15.

       Vrne null, kadar letnice ni: pasu ni mogoce ugibati in filter takega
       igralca preprosto ne zajame. */
    public static StarostniPas izpelji(LocalDate datumRojstva, LocalDate danes) {
        if (datumRojstva == null || danes == null) {
            return null;
        }
        int starost = letoSezone(danes) - datumRojstva.getYear();
        for (int i = 0; i < MLADINSKE_MEJE.length; i++) {
            if (starost < MLADINSKE_MEJE[i]) {
                return MLADINSKI[i];
            }
        }
        return starost >= LET_VETERAN ? VETERANI : CLANI;
    }

    /* Leto, v katerem se je zacela sezona, ki tece na dani dan. */
    private static int letoSezone(LocalDate danes) {
        return danes.getMonthValue() >= PRVI_MESEC_SEZONE ? danes.getYear() : danes.getYear() - 1;
    }
}
