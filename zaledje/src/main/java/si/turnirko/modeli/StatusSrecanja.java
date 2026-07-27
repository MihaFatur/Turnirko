/* Zivljenjski cikel srecanja dveh ekip:
   RAZPORED - dolocen je le termin (kolo), postav se ni,
   POTEKA   - postave dolocene in tekme generirane, vnasajo se rezultati,
   KONCANO  - srecanje odloceno (dosezen prag zmag ali odigrane vse tekme). */
package si.turnirko.modeli;

public enum StatusSrecanja {
    RAZPORED,
    POTEKA,
    KONCANO
}
