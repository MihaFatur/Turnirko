/* Kako se srecanje izpise v vrstici tekme (profil, dvoboj): ime tekmovanja,
   mesto srecanja v njem in dan.

   Srecanje pripada ligi ALI ekipni tekmi turnirja (V28), zato vrstica ne sme
   brati lige kar tako - ekipna tekma turnirja je nima. Pravilo je na enem
   mestu, ker ga berejo profil in pregled "1 na 1"; dve kopiji bi eno od
   vrstic pustili z "null. kolo".

   Klicatelj mora imeti nalozeno ligo oz. tekmo z dogodkom in turnirjem
   (poizvedbe v TekmaSrecanjaRepozitorij to naredijo). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.LocalDateTime;

import si.turnirko.modeli.Srecanje;

public final class OpisSrecanja {

    private OpisSrecanja() {}

    /* Ime tekmovanja: liga ali turnir. */
    public static String tekmovanje(Srecanje s) {
        return s.jeTurnirsko() ? s.getTekma().getDogodek().getTurnir().getIme() : s.getLiga().getIme();
    }

    /* Mesto srecanja v tekmovanju z ekipama: "3. kolo · A – B",
       "koncnica · polfinale · 2. tekma · A – B" oz. "U15 ekipno · A – B". */
    public static String del(Srecanje s) {
        String ekipi = s.getEkipaDomaci().prikazanoIme() + " – " + s.getEkipaGost().prikazanoIme();
        if (s.jeTurnirsko()) {
            return s.getTekma().getDogodek().getIme() + " · " + ekipi;
        }
        if (s.jeKoncnica()) {
            Integer ekip = s.getLiga().getKoncnicaEkip();
            int krogov = ekip != null ? KoncnicaStoritev.steviloKrogov(ekip) : s.getSerija().getKrog();
            return KoncnicaStoritev.opisTekme(s.getSerija().getKrog(), krogov, s.getTekmaVSeriji())
                    + " · " + ekipi;
        }
        return s.getKolo() + ". kolo · " + ekipi;
    }

    /* Kdaj je bilo srecanje: pri ekipni tekmi turnirja dan turnirja (kot vse
       turnirske tekme), pri ligi zakljucek srecanja, sicer termin. "odigranOb"
       postavi sele zakljucek v aplikaciji - uvozena zgodovina ga nima, termin
       pa ima vsako uvozeno srecanje. */
    public static LocalDateTime cas(Srecanje s) {
        if (s.jeTurnirsko()) {
            LocalDate datum = s.datumTurnirja();
            return datum == null ? null : datum.atStartOfDay();
        }
        return s.getOdigranOb() != null ? s.getOdigranOb() : s.getPredvidenZacetek();
    }
}
