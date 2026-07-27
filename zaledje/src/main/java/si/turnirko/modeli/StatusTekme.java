/* Zivljenjski cikel tekme:
   CAKA (se nista znana oba igralca) -> PRIPRAVLJENA (oba znana) ->
   V_IGRI -> KONCANA. Rezultat je mogoce vnesti le na PRIPRAVLJENA ali V_IGRI. */
package si.turnirko.modeli;

public enum StatusTekme {
    CAKA,
    PRIPRAVLJENA,
    V_IGRI,
    KONCANA
}
