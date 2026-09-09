/* Vnos rezultata tekme.
   Za normalno igrano tekmo zadostujeta dobljena niza (in po zelji tocke
   po nizih); za posebne izide (BREZ_BOJA, PREDAJA, DISKVALIFIKACIJA)
   je treba navesti zmagovalca (stran 1 ali 2). */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;

public record VnosRezultata(
        IzidTekme izidTip,       // null pomeni IGRANO
        Integer dobljeniNizi1,
        Integer dobljeniNizi2,
        Integer zmagovalecStran, // 1 ali 2 - obvezno za posebne izide
        List<NizVnos> nizi       // tocke po nizih (neobvezne za klubske turnirje)
) {}
