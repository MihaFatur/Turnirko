/* Vsak zapis v bazo razveljavi predpomnilnik javnih agregatov.

   Hibernate vsak stavek SQL pred izvedbo pokaze nadzorniku (StatementInspector)
   - stavke entitet, JPQL in izvorni SQL enako. Nadzornik stavka ne spreminja;
   kadar ni branje, o njem obvesti PredpomnilnikBranja, ta pa razveljavi ob
   potrditvi transakcije. Zato novi storitvi, ki pise, ni treba vedeti, da
   predpomnilnik obstaja.

   Cesa NE vidi: zapisa mimo Hibernata (JdbcTemplate, popravek v bazi na roko,
   uvoz v drugem procesu). Za to je veljavnost vnosa (glej PredpomnilnikBranja). */
package si.turnirko.nastavitve;

import java.util.regex.Pattern;

import org.hibernate.cfg.JdbcSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import si.turnirko.storitve.PredpomnilnikBranja;

@Configuration
public class PredpomnilnikNastavitve {

    /* Stavek z uvodnim WITH je lahko branje ali zapis (Hibernate tako zapise
       nekatere mnozicne vstavke); odloci, ali v njem stoji stavek, ki pise. */
    private static final Pattern PISE = Pattern.compile(
            "\\b(insert\\s+into|update\\s+\\S+\\s+set|delete\\s+from|merge\\s+into)\\b",
            Pattern.CASE_INSENSITIVE);

    @Bean
    HibernatePropertiesCustomizer zapisRazveljaviPredpomnilnik(PredpomnilnikBranja predpomnilnik) {
        StatementInspector nadzornik = sql -> {
            if (jeZapis(sql)) {
                predpomnilnik.obZapisu();
            }
            return sql;
        };
        return lastnosti -> lastnosti.put(JdbcSettings.STATEMENT_INSPECTOR, nadzornik);
    }

    /* Branje je SELECT (tudi zaklep vrstice, naslednja vrednost zaporedja in
       svetovalni zaklep) in WITH brez stavka, ki pise. Vse drugo steje za
       zapis: odvec razveljavitev stane en izracun, zgresena pa zastarel
       odgovor. */
    static boolean jeZapis(String sql) {
        int i = preskociPresledke(sql, 0);
        if (sql.startsWith("/*", i)) {
            int konec = sql.indexOf("*/", i);
            if (konec >= 0) {
                i = preskociPresledke(sql, konec + 2);
            }
        }
        if (sql.regionMatches(true, i, "select", 0, 6)) {
            return false;
        }
        if (sql.regionMatches(true, i, "with", 0, 4)) {
            return PISE.matcher(sql).find();
        }
        return true;
    }

    private static int preskociPresledke(String sql, int od) {
        int i = od;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }
}
