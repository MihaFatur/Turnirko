/* Povzetek ene lige za sklop "Moje lige" na domaci strani.

   Vrstica lige tam ni povezava s statusom, ampak mali zapisnik: kje je sezona
   (kolo od kol), kdo je na vrhu in kdaj je naslednje kolo. Vse troje je vsota
   cez srecanja lige, zato ga sesteje streznik - vmesnik bi zanj potreboval
   lestvico in razpored vsake lige posebej. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.util.List;

import si.turnirko.modeli.StatusTekmovanja;

public record DomovLigaDto(
        Long id,
        String ime,
        String sezona,
        StatusTekmovanja status,
        // Kje je sezona: "7. od 10 kol". Odigrano je najvisje kolo, ki ima vsaj
        // eno koncano srecanje - liga se v tistem kolu igra.
        int odigranihKol,
        int vsehKol,
        List<Vrh> vrh,
        Naslednje naslednje
) {

    /* Ena vrstica mini razpredelnice (prve tri ekipe). */
    public record Vrh(int mesto, String ekipa, int odigrane, int tocke) {}

    /* Prvo se neodigrano srecanje lige; datum je lahko prazen (razpored brez
       predvidenega zacetka). */
    public record Naslednje(int kolo, LocalDate datum, String domaci, String gost) {}
}
