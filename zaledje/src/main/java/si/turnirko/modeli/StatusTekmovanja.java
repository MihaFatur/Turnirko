/* Zivljenjski cikel turnirja in dogodka.
   Prehode vedno doloca streznik, nikoli odjemalec:
   PRIPRAVA (prijave odprte) -> V_TEKU (zreb narejen, tekme tecejo) -> ZAKLJUCEN. */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.ZoneId;

public enum StatusTekmovanja {
    PRIPRAVA,
    V_TEKU,
    ZAKLJUCEN;

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    /* Stanje, kot ga vidi gledalec (DTO). Zreb je za streznika V_TEKU - prijave so
       zaprte, popravek zreba mogoc -, a tekmovanje "tece" sele na svoj dan: zreb,
       narejen pred datumom zacetka, je se vedno "V pripravi". Brez datuma ostane
       kot doslej. Pravilo samo prikazuje; prehodov ne spreminja (ta ostajajo
       na dejanskem statusu). */
    public static StatusTekmovanja zaPrikaz(StatusTekmovanja dejanski, LocalDate zacetek) {
        return zaPrikaz(dejanski, zacetek, LocalDate.now(SLOVENIJA));
    }

    /* Enako kot zgoraj, z danim dnevom (testi in poizvedbe, ki danes ze imajo). */
    public static StatusTekmovanja zaPrikaz(StatusTekmovanja dejanski, LocalDate zacetek, LocalDate danes) {
        if (dejanski == V_TEKU && zacetek != null && zacetek.isAfter(danes)) {
            return PRIPRAVA;
        }
        return dejanski;
    }
}
