/* Vnos rezultata tekme.
   Za normalno igrano tekmo zadostujeta dobljena niza (in po zelji tocke
   po nizih); za posebne izide (BREZ_BOJA, PREDAJA, DISKVALIFIKACIJA)
   je treba navesti zmagovalca (stran 1 ali 2).

   Stevilo nizov je neobvezno: organizator ga ob vnosu lahko prepise za to
   tekmo (finale "prvi do 3" na dogodku "prvi do 2"). Pri popravku in pri
   ekipni tekmi ga ni. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;

public record VnosRezultata(
        IzidTekme izidTip,       // null pomeni IGRANO
        Integer dobljeniNizi1,
        Integer dobljeniNizi2,
        Integer zmagovalecStran, // 1 ali 2 - obvezno za posebne izide
        List<NizVnos> nizi,      // tocke po nizih (neobvezne za klubske turnirje)
        Integer steviloNizov     // null: ostane, kar je dolocil dogodek; sicer 3, 5 ali 7
) {

    /* Vnos brez prepisa stevila nizov (kot doslej). */
    public VnosRezultata(IzidTekme izidTip, Integer dobljeniNizi1, Integer dobljeniNizi2,
                         Integer zmagovalecStran, List<NizVnos> nizi) {
        this(izidTip, dobljeniNizi1, dobljeniNizi2, zmagovalecStran, nizi, null);
    }
}
