/* Kar je bralnik prepisal z enega sodniskega listka - natanko po
   stolpcih papirja (levo/desno), brez kakrsnegakoli sklepanja o tekmi.

   Sestavi ga LokalniBralnikListka (RazlagaListka) in se kot JSON shrani v
   branje_listka.prebrano. Kaj iz tega sledi za tekmo (kdo je
   prijava 1, kateri niz je neveljaven, ali se izid ujema s celico Rezultat),
   odloci PredlogIzidaListka - bralnik samo prepisuje. */
package si.turnirko.storitve;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrebranListek(
        /* Na sliki je Turnirkov sodniski listek (ali prazen listek). */
        boolean jeListek,
        /* Natisnjena stevilka tekme ob QR kodi (»T-48213«), kot je zapisana. */
        String koda,
        /* Imeni (par: oba igralca) v glavi levega in desnega stolpca. */
        String imeLevo,
        String imeDesno,
        /* Imeni sta napisani z roko (prazen listek), ne natisnjeni. */
        boolean imenaRocno,
        String kategorija,
        String faza,
        /* Vse vrstice nizov po vrsti, tudi prazne. */
        List<Niz> nizi,
        /* Celica »Rezultat« (dobljeni nizi), ce je izpolnjena. */
        Integer rezultatLevo,
        Integer rezultatDesno,
        boolean negotovRezultat,
        /* NI, BREZ_BOJA, PREDAJA ali DISKVALIFIKACIJA - samo ce je na listku zapisano. */
        String posebnost,
        /* LEVO ali DESNO: kdo je zmagal pri posebnem izidu, ce je razvidno. */
        String posebnostZmagovalec,
        /* Kratka opomba bralnika o necem nenavadnem (prečrtano, pripis). */
        String opomba
) {

    /* Ena vrstica niza: tocke levega in desnega stolpca; null = prazna ali
       neberljiva celica. Negotovo = popravljeno, precrtano ali tezko berljivo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Niz(Integer levo, Integer desno, boolean negotovoLevo, boolean negotovoDesno) {}

    public List<Niz> niziAliPrazno() {
        return nizi == null ? List.of() : nizi;
    }
}
