/* Stanje profilne slike (V56). Javna je samo OBJAVLJENA - in še ta samo,
   dokler ima račun veljaven Premium (ProfilneSlikeStoritev.vidne). */
package si.turnirko.modeli;

public enum StanjeProfilneSlike {
    /* Čaka na admina: mlajši od 15 let ali račun, ki ga ni povezal admin. */
    CAKA,
    OBJAVLJENA
}
