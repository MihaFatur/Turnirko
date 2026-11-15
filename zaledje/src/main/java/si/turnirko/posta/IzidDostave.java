/* Izid sinhrone dostave enega sporocila (PosiljateljPoste.dostavi), po katerem
   mnozicna obvestila vedo, ali prejemnika zapisati kot urejenega.

   Locimo zavrnjeno sporocilo od izpada kanala, ker sta posledici razlicni:
   zavrnitev zadeva enega prejemnika (steje se kot poskus, po nekaj poskusih
   obupamo), izpad pa vse (ne steje se nikomur, dostava se prekine in poskusi
   naslednjic). */
package si.turnirko.posta;

public enum IzidDostave {
    /* Streznik poste je sporocilo sprejel. */
    POSLANO,
    /* Streznik je odgovoril in to sporocilo zavrnil (naslov, vsebina, omejitev). */
    ZAVRNJENO,
    /* Streznika ni mogoce doseci ali se nanj prijaviti: nobeno sporocilo ne bi slo. */
    KANAL_NE_DELA
}
