/* Vloga uporabnika sistema.
   ADMIN (sodnik/organizator) sme spreminjati podatke.
   IGRALEC vidi svoj profil s statistiko in si lahko zamenja geslo, drugega
   pa ne sme spreminjati.
   Gost (nihce prijavljen) ima samo bralni dostop in ni predstavljen z
   zapisom v tej tabeli. */
package si.turnirko.modeli;

public enum Vloga {
    ADMIN,
    IGRALEC
}
