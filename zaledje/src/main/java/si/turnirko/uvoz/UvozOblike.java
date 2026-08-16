/* Pretvorbe oblik zapisa med Stupo in Turnirkom.

   Stupa je glede oblik ohlapna: datum rojstva je enkrat "2012-07-26", drugic
   "2012-07-26T02:00:00.000Z"; prazno polje je enkrat null, drugic "".
   Vsa taka mesta so zbrana tu, da jih ni treba loviti po uvozniku. */
package si.turnirko.uvoz;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;

public final class UvozOblike {

    private UvozOblike() {}

    /* Datum iz "2025-09-06" ali "2012-07-26T02:00:00.000Z"; null, ce ga ni. */
    public static LocalDate datum(String v) {
        String s = ocisti(v);
        if (s == null) {
            return null;
        }
        try {
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return null;
    }

    /* Casovni zig iz "2021-10-09" ali "2021-10-09T17:00:00". Ura je pomembna:
       ligaska srecanja jo imajo in po njej se tekme uvrstijo v casovno vrsto
       za obracun ELO (2. SNTL odigra dve koli v istem dnevu). Kjer ure ni,
       vzamemo polnoc. */
    public static LocalDateTime casovniZig(String v) {
        String s = ocisti(v);
        if (s == null) {
            return null;
        }
        if (s.length() >= 16 && s.charAt(10) == 'T') {
            try {
                return LocalDateTime.parse(s.substring(0, Math.min(19, s.length())));
            } catch (DateTimeParseException e) {
                // pade na branje samega datuma spodaj
            }
        }
        LocalDate d = datum(s);
        return d == null ? null : d.atStartOfDay();
    }

    /* Stupa pise spol kot "Male"/"Female", gender_id pa kot 1/2; stara stran
       NTZS pa ze kot Turnirkovi vrednosti. */
    public static Spol spol(String v, int genderId) {
        String s = ocisti(v);
        if (s != null) {
            if (s.equalsIgnoreCase("Male") || s.equalsIgnoreCase("MOSKI")) {
                return Spol.MOSKI;
            }
            if (s.equalsIgnoreCase("Female") || s.equalsIgnoreCase("ZENSKI")) {
                return Spol.ZENSKI;
            }
        }
        return switch (genderId) {
            case 1 -> Spol.MOSKI;
            case 2 -> Spol.ZENSKI;
            default -> null;
        };
    }

    public static SpolKategorija spolKategorija(int genderId) {
        return switch (genderId) {
            case 1 -> SpolKategorija.MOSKI;
            case 2 -> SpolKategorija.ZENSKE;
            default -> SpolKategorija.MESANO;
        };
    }

    /* Drzavljanstvo kot tricrkovna oznaka; Turnirko privzame "SLO". */
    public static String drzavljanstvo(String drzava) {
        String s = ocisti(drzava);
        if (s == null || s.equalsIgnoreCase("Slovenia") || s.equalsIgnoreCase("Slovenija")) {
            return "SLO";
        }
        return s.length() <= 3 ? s.toUpperCase() : s.substring(0, 3).toUpperCase();
    }

    /* Prireze besedilo na najvec N znakov (shema ima CHECK na dolzino).
       Rezanje je zadnja izbira - klicalec naj to zabelezi v porocilo. */
    public static String prirezi(String v, int najvec) {
        String s = ocisti(v);
        if (s == null) {
            return null;
        }
        return s.length() <= najvec ? s : s.substring(0, najvec).trim();
    }

    public static String ocisti(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        return (s.isEmpty() || s.equalsIgnoreCase("null")) ? null : s;
    }
}
