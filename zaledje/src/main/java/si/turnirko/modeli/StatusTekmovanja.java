/* Zivljenjski cikel turnirja in dogodka.
   Prehode vedno doloca streznik, nikoli odjemalec:
   PRIPRAVA (prijave odprte) -> V_TEKU (zreb narejen, tekme tecejo) -> ZAKLJUCEN. */
package si.turnirko.modeli;

public enum StatusTekmovanja {
    PRIPRAVA,
    V_TEKU,
    ZAKLJUCEN
}
