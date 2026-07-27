/* Vnos rezultata ene posamicne tekme srecanja.
   Za igrano tekmo zadostujeta dobljena niza; za posebne izide
   (BREZ_BOJA, PREDAJA, DISKVALIFIKACIJA) je treba navesti zmagovalno stran. */
package si.turnirko.dto;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StranEkipe;

public record VnosRezultataSrecanja(
        IzidTekme izidTip,          // null pomeni IGRANO
        Integer dobljeniNiziDomaci,
        Integer dobljeniNiziGost,
        StranEkipe zmagovalecStran  // obvezno za posebne izide
) {}
