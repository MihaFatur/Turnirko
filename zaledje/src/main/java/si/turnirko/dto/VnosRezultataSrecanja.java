/* Vnos rezultata ene posamicne tekme srecanja.
   Za igrano tekmo zadostujeta dobljena niza (in po zelji tocke po nizih);
   za posebne izide (BREZ_BOJA, PREDAJA, DISKVALIFIKACIJA) je treba navesti
   zmagovalno stran. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.StranEkipe;

public record VnosRezultataSrecanja(
        IzidTekme izidTip,          // null pomeni IGRANO
        Integer dobljeniNiziDomaci,
        Integer dobljeniNiziGost,
        StranEkipe zmagovalecStran, // obvezno za posebne izide
        // tocke po nizih (neobvezne - enako kot pri turnirjih); tocke1 so
        // domacih, tocke2 gostov
        List<NizVnos> nizi
) {}
