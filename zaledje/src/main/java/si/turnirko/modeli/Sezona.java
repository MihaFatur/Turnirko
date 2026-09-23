/* Sezonski rez, skupen vsemu, kar sezono racuna od 1. julija (PST).

   Izlusceno iz StarostniPas, ker isto pravilo zdaj potrebuje se
   NarocninaStoritev (stetje turnirjev/lig organizatorja na sezono) - dve
   kopiji istega reza bi se sceasoma razsli. */
package si.turnirko.modeli;

import java.time.LocalDate;

public final class Sezona {

    /* Prvi mesec nove sezone; sezona tece od julija do junija. */
    private static final int PRVI_MESEC_SEZONE = 7;

    private Sezona() {}

    /* Leto, v katerem se je zacela sezona, ki tece na dani dan. */
    public static int zacetnoLeto(LocalDate danes) {
        return danes.getMonthValue() >= PRVI_MESEC_SEZONE ? danes.getYear() : danes.getYear() - 1;
    }

    /* Datum, ko se je zacela tekoca sezona (1. julij zacetnega leta) - spodnja
       meja za stetje, kaj je nastalo "v tej sezoni". */
    public static LocalDate zacetek(LocalDate danes) {
        return LocalDate.of(zacetnoLeto(danes), PRVI_MESEC_SEZONE, 1);
    }
}
