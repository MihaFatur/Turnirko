/* Pomoc testom nacrtov poizvedb: prestreze SQL, ki ga Hibernate zares poslje,
   in vrne vrstico nacrta sqlite za dano tabelo.

   Zakaj pravi SQL in ne prepis: nacrt je odvisen od tega, kako Hibernate
   poizvedbo zapise (vzdevki, vrstni red pogojev, izrazi), in prepis v testu bi
   se od tega tiho razsel. Nacrt na prazni testni bazi je isti kot na polni:
   sqlite brez statistik (ANALYZE) ne izbira po velikosti tabele. */
package si.turnirko.storitve;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

final class NacrtPoizvedbe {

    private NacrtPoizvedbe() {
    }

    /* Prvi SQL, ki ga Hibernate med dejanjem poslje in omenja dano tabelo. */
    static String poslaniSql(Runnable dejanje, String tabela) {
        Logger zapisnik = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        Level prej = zapisnik.getLevel();
        ListAppender<ILoggingEvent> ujeto = new ListAppender<>();
        ujeto.start();
        zapisnik.addAppender(ujeto);
        zapisnik.setLevel(Level.DEBUG);
        try {
            dejanje.run();
        } finally {
            zapisnik.detachAppender(ujeto);
            zapisnik.setLevel(prej);
        }
        return ujeto.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(s -> s.contains(tabela))
                .findFirst().orElseThrow(() -> new AssertionError("ni poizvedbe po " + tabela));
    }

    /* Vrstica nacrta (SCAN/SEARCH), po kateri sqlite bere dano tabelo. Vsi
       parametri dobijo isto vrednost - nacrt od vrednosti ni odvisen. Brisanje
       nima vzdevka, zato ga nacrt imenuje kar po tabeli. */
    static String vrsticaTabele(DataSource podatkovniVir, String sql, String tabela, Object vrednost) {
        Matcher vzdevek = Pattern.compile("from " + tabela + " (?!where\\b)(\\w+)").matcher(sql);
        String ime = vzdevek.find() ? vzdevek.group(1) : tabela;

        int parametrov = (int) sql.chars().filter(c -> c == '?').count();
        List<String> nacrt = new JdbcTemplate(podatkovniVir).query("EXPLAIN QUERY PLAN " + sql,
                (vrstica, i) -> vrstica.getString("detail"),
                Collections.nCopies(parametrov, vrednost).toArray());
        return nacrt.stream()
                .filter(v -> v.matches("(SCAN|SEARCH) " + ime + " .*"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(tabela + " ni v nacrtu: " + nacrt + "\nSQL: " + sql));
    }
}
