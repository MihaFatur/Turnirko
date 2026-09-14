/* Ena vrstica globalne lestvice igralcev: trenutni Turnirko rating in
   povzetek vseh odigranih tekem (kadarkoli, na vseh dogodkih).

   Poleg tega nosi stiri stvari, ki jih vmesnik sam ne more izracunati:
   premik mesta in spremembo ratinga (za oboje bi potreboval stanje izpred
   meseca), potek ratinga v zadnjem letu (dnevnik ratinga) in pripadnost (klub,
   lige, kategorija) za filtre. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.KategorijaIgralca;
import si.turnirko.modeli.Spol;

public record LestvicaIgralcaDto(
        Long idIgralca,
        // Igralec se povsod bere kot "Jan Petric" (polnoIme); locena ime in
        // priimek sta tu zato, ker lestvica zna teci abecedno po priimku.
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
        // Razlika Turnirko ratinga proti stanju pred 30 dnevi; null z istim
        // razlogom kot premik (pred mesecem ratinga se ni bilo).
        Integer spremembaRatinga,
        // Spol in starostno-spolna kategorija (izpeljana, glej
        // KategorijaIgralca) za filtre nad lestvico; oba sta lahko null, kadar
        // ju ni mogoce dolociti.
        //
        // Zakaj oboje, ceprav kategorija spol ze nosi: nosi ga SAMO pri
        // clanih (CLANI/CLANICE), pri U19 in VETERANIH pa se izgubi. Filtra
        // "vse zenske" torej iz kategorije ni mogoce sestaviti, zato gre spol
        // zraven - javen je tako ali tako (glej IgralecJavniDto).
        Spol spol,
        KategorijaIgralca kategorija,
        // Do sedem tock Turnirko ratinga iz zadnjih 12 mesecev (najstarejsa prva)
        // za crto ob vrstici; tocke sledijo TEKMAM, ne koledarju (glej
        // StatistikaStoritev). Prazen seznam = crte ni cesa risati.
        List<Integer> potekRatinga,
        // Lige, v katerih je igralec v kadru katere od ekip.
        List<Long> idjiLig,
        // Ali igralec se ni odigral dovolj tekem na tekmovanjih ravni URADNO
        // ali KLUBSKO (prag: RekreativecStoritev.PRAG_TEKEM). Rekreativci so
        // svoja lestvica: ni posteno, da dober rekreativec prehiti nekoliko
        // slabsega igralca, ki hodi na clanske turnirje NTZS. Prehod je
        // TRAJEN - kdor je enkrat odigral tri prava tekmovanja, se ne vraca.
        boolean rekreativec
) {}
