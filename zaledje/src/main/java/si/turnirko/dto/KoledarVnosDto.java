/* En vnos koledarja: turnir ali eno kolo lige.

   Zakaj en sam tip za oboje: koledar je pogled po DNEVIH in ne po tekmovanjih -
   gledalec vprasa "kaj je v soboto", ne "kaj ima ta liga". Locena seznama bi
   moral vmesnik zliti sam in bi se dvakrat urejala po datumu.

   Zrnatost ligaskega vnosa je KOLO in ne srecanje: termin je last kola (vsa
   njegova srecanja imajo isti cas, glej LigaStoritev.nastaviTermine), zato bi
   vnos na srecanje isti dan izpisal isto ligo petkrat. Pari kola gredo v
   "srecanja" - dan v celotnem koledarju iz njih izpise, kdo igra. */
package si.turnirko.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.StatusTekmovanja;

public record KoledarVnosDto(
        Vrsta vrsta,
        // id turnirja oz. lige - vmesnik iz njega sestavi pot do tekmovanja
        Long id,
        String ime,
        // Prvi in zadnji dan vnosa. Turnir traja lahko vec dni (oznaci vse),
        // kolo lige je vedno en dan - tam sta datuma enaka.
        LocalDate datum,
        LocalDate datumKonca,
        // Termin kola z uro (samo liga; 00:00 pomeni, da ura ni dolocena).
        LocalDateTime zacetek,
        Integer kolo,
        // Prizorisce turnirja: kraj iz sifranta in/ali ime dvorane. Liga kraja
        // nima - igra se pri domacih ekipah, torej vsako kolo drugje.
        String kraj,
        String dvorana,
        String sezona,
        // Klub lastnik tekmovanja; skupina filtra na strani koledarja.
        String klub,
        StatusTekmovanja status,
        List<Par> srecanja
) {

    public enum Vrsta { TURNIR, LIGA }

    /* Par kola: kdo igra s kom. Id je pot do zapisnika srecanja. */
    public record Par(Long idSrecanje, String domaci, String gost) {}
}
