/* Zivljenjski cikel branja listka s fotografije (V68):
   V_VRSTI (fotografija caka na bralnik) -> PREBRANO (predlog caka na
   organizatorja) ali NAPAKA (branje ni uspelo; poskusi se znova ali vpise
   rocno) -> POTRJENO (izid je v tekmi) ali ZAVRZENO. */
package si.turnirko.modeli;

public enum StanjeBranja {
    V_VRSTI,
    PREBRANO,
    NAPAKA,
    POTRJENO,
    ZAVRZENO;

    /* Branje, ki se caka na organizatorja ali na bralnik. */
    public boolean jeOdprto() {
        return this == V_VRSTI || this == PREBRANO || this == NAPAKA;
    }
}
