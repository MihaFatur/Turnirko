/* Ena vrstica globalne lestvice igralcev: trenutni klubski ELO in
   povzetek vseh odigranih tekem (kadarkoli, na vseh dogodkih).

   Poleg tega nosi stiri stvari, ki jih vmesnik sam ne more izracunati:
   premik mesta in spremembo ratinga (za oboje bi potreboval stanje izpred
   meseca), potek ELO v zadnjem letu (dnevnik ratinga) in pripadnost (klub,
   lige, kategorija) za filtre. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.KategorijaIgralca;

public record LestvicaIgralcaDto(
        Long idIgralca,
        // Ime in priimek locena: v stolpcu se igralec bere kot "Jan Petric",
        // v izbirniku pa kot "Petric Jan" (abecedno) - polnoIme zna samo eno.
        String ime,
        String priimek,
        String polnoIme,
        String klub,
        Long idKluba,
        Integer rating,
        int odigrane,
        int zmage,
        int porazi,
        // Koliko mest je igralec pridobil (+) oz. izgubil (-) v zadnjem mesecu;
        // null, ce ga pred mesecem na lestvici se ni bilo.
        Integer premik,
        // Razlika klubskega ELO proti stanju pred 30 dnevi; null z istim
        // razlogom kot premik (pred mesecem ratinga se ni bilo).
        Integer spremembaRatinga,
        // Starostno-spolna kategorija (izpeljana, glej KategorijaIgralca) za
        // filter nad lestvico; null, kadar je ni mogoce dolociti.
        KategorijaIgralca kategorija,
        // Do sedem tock klubskega ELO iz zadnjih 12 mesecev (najstarejsa prva)
        // za crto ob vrstici; tocke sledijo TEKMAM, ne koledarju (glej
        // StatistikaStoritev). Prazen seznam = crte ni cesa risati.
        List<Integer> eloZgodovina,
        // Lige, v katerih je igralec v kadru katere od ekip.
        List<Long> idjiLig
) {}
