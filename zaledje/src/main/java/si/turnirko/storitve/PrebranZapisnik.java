/* Prepis ligaskega zapisnika, kot ga je prebral bralnik (LokalniBralnikZapisnika),
   brez odlocitev: za vsako tekmo stanje vseh kvadratkov obeh kadrov in tocke nizov
   (domaci = levo, gost = desno) po pravilih namiznega tenisa. Kdo je igral, ali je
   zapis zanesljiv in kaj se zapise, izracuna PredlogZapisnika.

   Shrani se kot JSON v branje_zapisnika.prebrano (merjenje, ponovni predlog brez
   ponovnega branja fotografije). */
package si.turnirko.storitve;

import java.util.List;

public record PrebranZapisnik(long idNatisa, int najdenihOznak, int vsehOznak, List<Tekma> tekme) {

    /* Kvadratek ene strani tekme: vrstica kadra natisa (zadnja = nov igralec),
       delez crnila in stanje (OdcitekZapisnika.StanjeKvadratka). */
    public record Krizec(int igralec, double crnilo, String stanje) {}

    /* opomba: opomba pravil (npr. »slabo se ujemajo s pravili«) ali popravljene celice. */
    public record Tekma(List<Krizec> domaci, List<Krizec> gost, List<PrebranListek.Niz> nizi, String opomba) {}
}
