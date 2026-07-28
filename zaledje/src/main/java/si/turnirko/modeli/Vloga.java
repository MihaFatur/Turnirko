/* Vloga uporabnika sistema.
   ADMIN sme spreminjati vse (sifranti, racuni, vsi turnirji in lige).
   ORGANIZATOR (klub oz. oseba, ki vodi tekmovanja) sme ustvarjati turnirje in
   lige ter upravljati SAMO tiste, ki jih je ustvaril on ali kdo iz njegovega
   kluba; sme tudi dodati novega igralca v skupni sifrant. Klubov in krajev ne
   ureja, tujih igralcev ne spreminja.
   IGRALEC vidi svoj profil s statistiko in si lahko zamenja geslo, drugega
   pa ne sme spreminjati.
   Gost (nihce prijavljen) ima samo bralni dostop in ni predstavljen z
   zapisom v tej tabeli. */
package si.turnirko.modeli;

public enum Vloga {
    ADMIN,
    ORGANIZATOR,
    IGRALEC
}
