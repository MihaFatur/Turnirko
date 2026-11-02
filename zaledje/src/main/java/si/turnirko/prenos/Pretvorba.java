/* Pretvorba vrednosti iz SQLite v tipe PostgreSQL (ukaz "prenos", faza 3 v
   docs/PRENOVA-BAZE.md).

   SQLite zapise vsako vrednost po svoje: logicno kot 0/1, datum kot besedilo
   "2026-10-05", cas v stirih oblikah (samo datum, "T" ali presledek, z
   sekundami ali brez, z do devetimi decimalkami). Ciljni tip doloci stolpec v
   PostgreSQL, zato pretvornik NE ugiba: vrednost, ki ne ustreza, se zavrne z
   razlogom in prenos se ustavi. Tihega NULL ali "priblizka" ni.

   Edina izjema je namerna in stejem jo: decimalke pod milisekundo se odrezejo
   (stolpec je timestamp(3), aplikacija pa zapisuje trenutke prek LocalDateTime
   in nanosekunde nimajo pomena). Vsak tak primer gre v porocilo. */
package si.turnirko.prenos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

final class Pretvorba {

    private Pretvorba() {
    }

    /* Tipi stolpcev, ki jih shema (V53) uporablja. Nov tip v prihodnji
       migraciji ustavi prenos z jasno napako, dokler ga ne dodamo sem. */
    enum Tip {
        CELO64, CELO32, LOGICNO, DECIMALNO, BESEDILO, DATUM, CAS, BAJTI;

        static Tip izPostgresa(String tip) {
            return switch (tip) {
                case "bigint" -> CELO64;
                case "integer" -> CELO32;
                case "boolean" -> LOGICNO;
                case "double precision" -> DECIMALNO;
                case "text" -> BESEDILO;
                case "date" -> DATUM;
                case "timestamp without time zone" -> CAS;
                case "bytea" -> BAJTI;
                default -> null;
            };
        }
    }

    /* Razlog zavrnitve ene vrednosti. Brez sledi sklada: jih je lahko tisoce. */
    static final class Zavrnitev extends RuntimeException {
        Zavrnitev(String razlog) {
            super(razlog, null, false, false);
        }
    }

    /* Stevci, ki jih prenos pokaze v porocilu: katere oblike casa so bile v
       katerem stolpcu in koliko vrednosti je bilo odrezanih. */
    static final class Opombe {
        private final Map<String, Long> stevci = new TreeMap<>();

        void dodaj(String kljuc) {
            stevci.merge(kljuc, 1L, Long::sum);
        }

        Map<String, Long> stevci() {
            return stevci;
        }
    }

    private static final DateTimeFormatter CAS_V_BAZI =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");
    private static final Pattern DATUM = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /* Vrednost iz SQLite (Long, Integer, Double, String; nikoli null) v
       tip, ki ga zahteva stolpec: Long, Boolean, Double, String, LocalDate ali
       LocalDateTime. */
    static Object izSqlite(Tip tip, Object vrednost, String stolpec, Opombe opombe) {
        return switch (tip) {
            case CELO64 -> celo(vrednost, Long.MIN_VALUE, Long.MAX_VALUE);
            case CELO32 -> celo(vrednost, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case LOGICNO -> celo(vrednost, 0, 1) == 1;
            case DECIMALNO -> {
                if (vrednost instanceof Double d) {
                    yield d;
                }
                if (vrednost instanceof Long || vrednost instanceof Integer) {
                    opombe.dodaj(stolpec + " : celo stevilo v stolpcu double");
                    yield ((Number) vrednost).doubleValue();
                }
                throw new Zavrnitev("pricakovano decimalno stevilo, v SQLite je " + opis(vrednost));
            }
            case BESEDILO -> {
                if (!(vrednost instanceof String s)) {
                    throw new Zavrnitev("pricakovano besedilo, v SQLite je " + opis(vrednost));
                }
                if (s.indexOf('\0') >= 0) {
                    throw new Zavrnitev("besedilo vsebuje znak NUL, ki ga PostgreSQL ne sprejme");
                }
                yield s;
            }
            case DATUM -> {
                String s = besedilo(vrednost, "datum");
                if (!DATUM.matcher(s).matches()) {
                    throw new Zavrnitev("pricakovan datum LLLL-MM-DD, v SQLite je '" + skrajsaj(s) + "'");
                }
                try {
                    yield LocalDate.parse(s);
                } catch (DateTimeParseException napaka) {
                    throw new Zavrnitev("neveljaven datum '" + skrajsaj(s) + "'");
                }
            }
            case CAS -> cas(besedilo(vrednost, "cas"), stolpec, opombe);
            case BAJTI -> {
                if (vrednost instanceof byte[] bajti) {
                    yield bajti;
                }
                throw new Zavrnitev("pricakovani bajti (BLOB), v SQLite je " + opis(vrednost));
            }
        };
    }

    private static LocalDateTime cas(String zapis, String stolpec, Opombe opombe) {
        opombe.dodaj(stolpec + " : " + oblika(zapis));
        String t = zapis.trim().replace(' ', 'T');
        LocalDateTime cas;
        try {
            // dan brez ure je polnoc (tako je te zapise bral tudi stari pretvornik)
            cas = DATUM.matcher(t).matches() ? LocalDate.parse(t).atStartOfDay() : LocalDateTime.parse(t);
        } catch (DateTimeParseException napaka) {
            throw new Zavrnitev("neveljaven cas '" + skrajsaj(zapis) + "'");
        }
        LocalDateTime zaokrozen = cas.truncatedTo(ChronoUnit.MILLIS);
        if (!zaokrozen.equals(cas)) {
            opombe.dodaj(stolpec + " : odrezano na milisekunde");
        }
        return zaokrozen;
    }

    private static long celo(Object vrednost, long najmanj, long najvec) {
        if (vrednost instanceof Long || vrednost instanceof Integer
                || vrednost instanceof Short || vrednost instanceof Byte) {
            long n = ((Number) vrednost).longValue();
            if (n < najmanj || n > najvec) {
                throw new Zavrnitev("stevilo " + n + " je izven obsega " + najmanj + ".." + najvec);
            }
            return n;
        }
        throw new Zavrnitev("pricakovano celo stevilo, v SQLite je " + opis(vrednost));
    }

    private static String besedilo(Object vrednost, String kaj) {
        if (vrednost instanceof String s) {
            return s;
        }
        throw new Zavrnitev("pricakovano besedilo (" + kaj + "), v SQLite je " + opis(vrednost));
    }

    /* "2026-10-05T18:00:00.123" -> "9999-99-99T99:99:99.999" */
    static String oblika(String zapis) {
        return zapis.replaceAll("\\d", "9");
    }

    private static String opis(Object vrednost) {
        return vrednost.getClass().getSimpleName() + " " + skrajsaj(String.valueOf(vrednost));
    }

    static String skrajsaj(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    /* Ena vrednost v obliki, kot jo bere COPY (besedilni nacin, locilo tab).
       Isto obliko uporabimo za kontrolno vsoto na obeh straneh, zato se
       vsota ujema natanko takrat, ko se ujemajo vrednosti. */
    static void zapisi(StringBuilder sb, Object vrednost) {
        switch (vrednost) {
            case null -> sb.append("\\N");
            case String s -> {
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    switch (c) {
                        case '\\' -> sb.append("\\\\");
                        case '\t' -> sb.append("\\t");
                        case '\n' -> sb.append("\\n");
                        case '\r' -> sb.append("\\r");
                        default -> sb.append(c);
                    }
                }
            }
            // bytea v besedilnem COPY: "\x" + šestnajstiško, poševnica pa se ubeži
            case byte[] bajti -> sb.append("\\\\x").append(HexFormat.of().formatHex(bajti));
            case Boolean b -> sb.append(b ? 't' : 'f');
            case LocalDateTime t -> sb.append(CAS_V_BAZI.format(t));
            default -> sb.append(vrednost);
        }
    }
}
