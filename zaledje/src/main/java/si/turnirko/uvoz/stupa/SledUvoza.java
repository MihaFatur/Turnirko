/* Sled enega uvoza: kateri zapis vira je postal kateri zapis Turnirka. Iz nje
   uskladitev po zapisu preveri vsako tekmo vira posebej, ne le stevila. */
package si.turnirko.uvoz.stupa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SledUvoza {

    Long idTurnir;
    Long idLiga;

    /* kategorija Stupe -> dogodek */
    final Map<Long, Long> dogodki = new HashMap<>();
    /* udelezenec Stupe -> prijava (turnir) */
    final Map<Long, Long> prijave = new HashMap<>();
    /* udelezenec Stupe -> ekipa (liga in ekipni dogodek) */
    final Map<Long, Long> ekipe = new HashMap<>();
    /* skupina Stupe -> skupina */
    final Map<Long, Long> skupine = new HashMap<>();
    /* tekma Stupe -> tekma turnirja */
    final Map<Long, Long> tekme = new HashMap<>();
    /* tekma Stupe -> srecanje (ligasko ali ekipna tekma turnirja) */
    final Map<Long, Long> srecanja = new HashMap<>();
    /* tekme vira, ki namenoma niso uvozene (razlog je v porocilu) */
    final Set<Long> izpuscene = new HashSet<>();
    /* tekme vira, ki so kopija dvoboja iz prejsnje stopnje (preneseni izid) */
    final Set<Long> prenesene = new HashSet<>();
    /* ali uradne tocke lige sledijo nekemu tockovanju (PreslikavaLigeStupe.tockovanje) -
       samo takrat je ujemanje tock obvezna preverba */
    boolean tockovanjeSkladno = true;
}
