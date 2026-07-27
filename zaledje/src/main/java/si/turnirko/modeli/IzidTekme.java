/* Nacin, kako se je tekma koncala:
   IGRANO           - normalno odigrana tekma
   PROSTO           - prost prehod (bye): igralec v tem kolu ni imel nasprotnika
   BREZ_BOJA        - w.o.: nasprotnik ni nastopil (rating se NE obracuna)
   PREDAJA          - nasprotnik je tekmo predal med igro (rating SE obracuna)
   DISKVALIFIKACIJA - nasprotnik je bil diskvalificiran (rating se NE obracuna) */
package si.turnirko.modeli;

public enum IzidTekme {
    IGRANO,
    PROSTO,
    BREZ_BOJA,
    PREDAJA,
    DISKVALIFIKACIJA;

    /* Ali sta igralca za mizo dejansko odigrala tekmo.

       Merilo je isto kot pri ratingu: kdor ni nastopil, ni igral. Zato take
       tekme ne smejo steti med "odigrane" v statistiki igralca - sicer bi
       igralec, ki je odstopil pred skupinskimi tekmami, na lestvici dobil
       sedem porazov za tekme, ki jih ni bilo, in pokvarjen odstotek zmag.
       V lestvici SKUPINE pa taka tekma normalno steje kot zmaga nasprotnika,
       ker o uvrstitvi v skupini odloca tekmovalni rezultat (RazvrstitevStoritev). */
    public boolean jeOdigrana() {
        return this == IGRANO || this == PREDAJA;
    }
}
