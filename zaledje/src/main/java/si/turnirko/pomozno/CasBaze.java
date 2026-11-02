/* Datumi in casi iz izvornih poizvedb (List<Object[]>, getSingleResult).

   Entitete dobijo LocalDate/LocalDateTime od Hibernata same; izvorna
   poizvedba pa vrne, kar da gonilnik - glede na nastavitev Hibernata java.time
   ali java.sql tip. SQLite je tu vracal besedilo in klicatelji so ga rezali;
   zdaj gre vsaka vrednost skozi ta razred, neznan tip pa je napaka in ne
   ugibanje iz toString(). */
package si.turnirko.pomozno;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public final class CasBaze {

    private CasBaze() {
    }

    public static LocalDateTime casovniZig(Object vrednost) {
        if (vrednost == null) {
            return null;
        }
        if (vrednost instanceof LocalDateTime cas) {
            return cas;
        }
        if (vrednost instanceof Timestamp cas) {
            return cas.toLocalDateTime();
        }
        if (vrednost instanceof OffsetDateTime cas) {
            return cas.toLocalDateTime();
        }
        if (vrednost instanceof LocalDate dan) {
            return dan.atStartOfDay();
        }
        if (vrednost instanceof java.sql.Date dan) {
            return dan.toLocalDate().atStartOfDay();
        }
        throw new IllegalArgumentException("Ni cas: " + vrednost.getClass().getName());
    }

    public static LocalDate datum(Object vrednost) {
        if (vrednost instanceof LocalDate dan) {
            return dan;
        }
        if (vrednost instanceof java.sql.Date dan) {
            return dan.toLocalDate();
        }
        LocalDateTime cas = casovniZig(vrednost);
        return cas == null ? null : cas.toLocalDate();
    }
}
