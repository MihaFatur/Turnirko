/* Od kod je tekmovanje prislo v Turnirko (turnir.vir, liga.vir - V27).

   Tekmovanje z virom je UVOZENO in samo za branje: vir resnice je zveza.
   Popravek, vnesen v Turnirku, bi naslednja sinhronizacija povozila, zato ga
   vnese NTZS in uvoz ga prenese. Pravilo varuje LastnistvoStoritev.
   Tekmovanje brez vira je nastalo v Turnirku. */
package si.turnirko.modeli;

public enum VirTekmovanja {

    /* Stupa Events, sistem NTZS od sezone 2024/25 (ntzseventsott.stupaevents.com). */
    STUPA("NTZS · Stupa Events"),

    /* Stara stran NTZS, sezone 2012/13-2023/24 (stara.ntzs.si). */
    STARA_NTZS("NTZS · stara stran");

    private final String oznaka;

    VirTekmovanja(String oznaka) {
        this.oznaka = oznaka;
    }

    public String getOznaka() { return oznaka; }
}
