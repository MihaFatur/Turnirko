/* Stanje posamicne tekme znotraj srecanja:
   CAKA       - rezultat se ni vnesen,
   KONCANA    - rezultat vnesen,
   NEODIGRANA - srecanje je bilo odloceno prej (dosezen prag zmag), zato se
                ta tekma po pravilih ne igra vec. */
package si.turnirko.modeli;

public enum StatusTekmeSrecanja {
    CAKA,
    KONCANA,
    NEODIGRANA
}
