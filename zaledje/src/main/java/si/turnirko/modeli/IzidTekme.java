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

    /* Ali o odigrani tekmi vemo samo, KDO je zmagal - izid po nizih pa ne pove
       tega, kar rating iz njega bere (margina presenecenja).

       Tako je pri PREDAJI (delni izid tekme, ki ni bila odigrana do konca) in
       pri odigrani tekmi z izidom 0 : 0. Slednjo pozna samo uvoz: Stupa pri
       nekaterih srecanjih zapise zmagovalce podtekem brez nizov (1. SNTL zensk
       2025/26 - srecanja 5 : 1 in 4 : 5 so bila zares odigrana). V aplikaciji
       take tekme ni mogoce vnesti (zmagovalec mora dobiti dovolj nizov), zato
       0 : 0 pri odigrani tekmi ne more pomeniti nicesar drugega. Brez tega
       pravila bi rating 0 : 0 bral kot gladko zmago in zmagovalcu pripisal
       najvisji bonus za tekmo, katere izida ne poznamo. */
    public static boolean samoZmagovalec(IzidTekme izid, int nizi1, int nizi2) {
        return izid == PREDAJA || (izid == IGRANO && nizi1 == 0 && nizi2 == 0);
    }
}
