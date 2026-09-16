/* Ena vrstica globalne lestvice igralcev: trenutni Turnirko rating in
   povzetek vseh odigranih tekem (kadarkoli, na vseh dogodkih).

   Poleg tega nosi stiri stvari, ki jih vmesnik sam ne more izracunati:
   premik mesta in spremembo ratinga (za oboje bi potreboval stanje izpred
   meseca), potek ratinga v zadnjem letu (dnevnik ratinga) in pripadnost (klub,
   lige, starostni pas) za filtre. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StarostniPas;

public record LestvicaIgralcaDto(
        Long idIgralca,
        // Igralec se povsod bere kot "Jan Petric" (polnoIme); locena ime in
        // priimek sta urejevalni kljuc, po katerem se razvrstijo izenaceni
        // (glej StatistikaStoritev.primerjavaLestvice).
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
        // Spol izbere lestvico (moska/zenska), starostni pas pa kategorijo v
        // filtru nad njo (U11 ... U21, clani). Oba sta lahko null, kadar ju ni
        // mogoce dolociti. Pas je isti kot pri prijavah na dogodek (sezonsko
        // pravilo PST), zato ima igralec na lestvici in v prijavi isto
        // kategorijo; javen je tako ali tako (glej IgralecJavniDto).
        Spol spol,
        StarostniPas starostniPas,
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
