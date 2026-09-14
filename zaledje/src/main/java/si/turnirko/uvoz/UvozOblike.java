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
       za obracun ratinga (2. SNTL odigra dve koli v istem dnevu). Kjer ure ni,
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

    /* Drzave, ki se v podatkih NTZS pojavijo, v oznakah ITTF. Prirez imena
       (prejsnja razlicica) je iz "North Macedonia" naredil "NOR". */
    private static final java.util.Map<String, String> DRZAVE = java.util.Map.ofEntries(
            java.util.Map.entry("slovenia", "SLO"), java.util.Map.entry("slovenija", "SLO"),
            java.util.Map.entry("croatia", "CRO"), java.util.Map.entry("hrvaska", "CRO"),
            java.util.Map.entry("bosnia and herzegovina", "BIH"), java.util.Map.entry("serbia", "SRB"),
            java.util.Map.entry("north macedonia", "MKD"), java.util.Map.entry("montenegro", "MNE"),
            java.util.Map.entry("kosovo", "KOS"), java.util.Map.entry("albania", "ALB"),
            java.util.Map.entry("austria", "AUT"), java.util.Map.entry("italy", "ITA"),
            java.util.Map.entry("hungary", "HUN"), java.util.Map.entry("germany", "GER"),
            java.util.Map.entry("russia", "RUS"), java.util.Map.entry("ukraine", "UKR"),
            java.util.Map.entry("armenia", "ARM"), java.util.Map.entry("czech republic", "CZE"),
            java.util.Map.entry("slovakia", "SVK"), java.util.Map.entry("poland", "POL"),
            java.util.Map.entry("romania", "ROU"), java.util.Map.entry("bulgaria", "BUL"),
            java.util.Map.entry("china", "CHN"), java.util.Map.entry("japan", "JPN"),
            java.util.Map.entry("india", "IND"), java.util.Map.entry("france", "FRA"),
            java.util.Map.entry("spain", "ESP"), java.util.Map.entry("belarus", "BLR"));

    /* Drzavljanstvo kot tricrkovna oznaka; Turnirko privzame "SLO". Neznano
       ime drzave ostane SLO in ne prirez imena - napacna oznaka bi bila
       trditev, prazna pa ni. */
    public static String drzavljanstvo(String drzava) {
        String s = ocisti(drzava);
        if (s == null) {
            return "SLO";
        }
        String oznaka = DRZAVE.get(s.toLowerCase());
        if (oznaka != null) {
            return oznaka;
        }
        return s.length() == 3 ? s.toUpperCase() : "SLO";
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
