/* Stanje racuna igralca.
   CAKA      - registriran, a administrator ga se ni pregledal; prijaviti se
               sme, vendar ne vidi nicesar zasebnega
   POTRJEN   - administrator ga je odobril in povezal z zapisom igralca
   ZAVRNJEN  - administrator je zahtevo zavrnil (npr. lazna registracija) */
package si.turnirko.modeli;

public enum StatusRacuna {
    CAKA,
    POTRJEN,
    ZAVRNJEN
}
