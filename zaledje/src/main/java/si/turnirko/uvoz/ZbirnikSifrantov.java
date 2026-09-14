/* Zbiranje surovih zapisov o klubih in igralcih stare strani NTZS.

   Zakaj cez cel arhiv in ne po tekmovanjih sproti: isti igralec nastopa v
   desetih tekmovanjih in v vsakem je zapisan znova. Sifrant mora nastati
   enkrat, preden se uvozi prvi turnir, sicer bi ista oseba dobila deset
   zapisov.

   Osebe iz Stupe se v zbirnik ne zbirajo vec - uvaza jih sinhronizacija
   (si.turnirko.uvoz.stupa), ki jih z igralci stare strani poveze po licenci,
   datumu rojstva in spolu. Kljuc zapisa je oznacen z virom ("stara:3300"). */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class ZbirnikSifrantov {

    public static final String VIR_STARA = "stara";

    public static String kljuc(String vir, Object id) {
        return vir + ":" + id;
    }

    /* Klub. Kljuc je oznaka kluba v naslovu stare strani (npr. "ntk_arrigoni"). */
    public record SurovKlub(String id, String ime, String kratica) {}

    /* Igralec.

       "ime"/"priimek" hrani stara stran LOCENO (atribut data-name je "Priimek
       Ime", besedilo povezave "Ime Priimek").

       "samoLetnik" pove, da datum rojstva ni pravi datum, ampak 1. januar
       letnika - stara stran dneva in meseca ne objavi.

       "zadnjiNastop" sluzi za izbiro kluba: igralec med sezonami prestopa,
       zato v sifrant zapisemo klub iz NAJNOVEJSEGA nastopa. */
    public record SurovIgralec(String id, String polnoIme, String ime, String priimek,
                               LocalDate rojstvo, boolean samoLetnik, String spol,
                               String licenca, String drzava, String idKluba,
                               LocalDate zadnjiNastop) {}

    private final Map<String, SurovKlub> klubi = new LinkedHashMap<>();
    private final Map<String, SurovIgralec> igralci = new LinkedHashMap<>();

    public Map<String, SurovKlub> klubi() {
        return klubi;
    }

    public Map<String, SurovIgralec> igralci() {
        return igralci;
    }

    public void dodajKlub(String id, String ime, String kratica) {
        if (id == null || ime == null || ime.isBlank()) {
            return;
        }
        klubi.put(id, new SurovKlub(id, ime.trim(), prazenVNull(kratica)));
    }

    public void dodajIgralca(SurovIgralec nov) {
        if (nov.id() == null || nov.polnoIme() == null || nov.polnoIme().isBlank()) {
            return;
        }
        SurovIgralec obstojec = igralci.get(nov.id());
        igralci.put(nov.id(), obstojec == null ? nov : zdruzi(obstojec, nov));
    }

    /* Dva zapisa iste osebe: od vsakega polja najboljsi podatek - loceno ime,
       pravi datum pred letnikom, klub iz najnovejsega nastopa. */
    public static SurovIgralec zdruzi(SurovIgralec obstojec, SurovIgralec nov) {
        boolean novejsi = nov.zadnjiNastop() != null
                && (obstojec.zadnjiNastop() == null || nov.zadnjiNastop().isAfter(obstojec.zadnjiNastop()));
        String polno = obstojec.polnoIme();
        if (steviloBesed(polno) < 2 && steviloBesed(nov.polnoIme()) >= 2) {
            polno = nov.polnoIme();
        }
        boolean vzemiRojstvoNovega = obstojec.rojstvo() == null
                || (obstojec.samoLetnik() && nov.rojstvo() != null && !nov.samoLetnik());
        return new SurovIgralec(
                obstojec.id(),
                polno,
                obstojec.ime() != null ? obstojec.ime() : nov.ime(),
                obstojec.priimek() != null ? obstojec.priimek() : nov.priimek(),
                vzemiRojstvoNovega ? nov.rojstvo() : obstojec.rojstvo(),
                vzemiRojstvoNovega ? nov.samoLetnik() : obstojec.samoLetnik(),
                obstojec.spol() != null ? obstojec.spol() : nov.spol(),
                obstojec.licenca() != null ? obstojec.licenca() : nov.licenca(),
                obstojec.drzava() != null ? obstojec.drzava() : nov.drzava(),
                (novejsi && nov.idKluba() != null) ? nov.idKluba() : obstojec.idKluba(),
                novejsi ? nov.zadnjiNastop() : obstojec.zadnjiNastop());
    }

    private static int steviloBesed(String v) {
        return (v == null || v.isBlank()) ? 0 : v.trim().split("\\s+").length;
    }

    private static String prazenVNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
