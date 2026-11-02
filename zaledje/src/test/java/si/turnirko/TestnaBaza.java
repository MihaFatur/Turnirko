/* Pravi PostgreSQL za teste, zagnan enkrat na testni zagon (JVM).

   Lokalno ni ne Dockerja ne namescenega PostgreSQL, zato binarne datoteke
   prinese Maven (zonky embedded-postgres, ista glavna razlicica kot v
   produkciji, glej pom.xml) in jih zazene v zacasni mapi. Baza je ob vsakem
   zagonu sveza in jo zgradi Flyway iz migracij - tako kot je prej nastala
   nova datoteka SQLite v target/.

   Zakaj poslusalec seje JUnit in ne @DynamicPropertySource: testnih razredov
   je vec kot sto in vsak bi moral dedovati isto osnovo. Poslusalca nalozi
   JUnit sam (META-INF/services), naslov baze pa gre v sistemske lastnosti, ki
   imajo prednost pred application-test.properties. Isto velja za zagon enega
   testa iz IDE. */
package si.turnirko;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

public class TestnaBaza implements LauncherSessionListener {

    /* Isti stavek kot na strezniku (docs/PRENOVA-BAZE.md, razdelek 3.2):
       razvrscanje po kodnih tockah, kot ga je imel SQLite. */
    static final String USTVARI_BAZO = """
            CREATE DATABASE turnirko
              ENCODING 'UTF8' LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8'
              TEMPLATE template0""";

    private static EmbeddedPostgres baza;

    @Override
    public void launcherSessionOpened(LauncherSession seja) {
        if (baza != null) {
            return;
        }
        try {
            baza = EmbeddedPostgres.builder()
                    // initdb bi na Windows vzel sistemske nastavitve
                    // (Slovenian_Slovenia.1250 in kodiranje WIN1250)
                    .setLocaleConfig("locale", "C")
                    // baza zivi le med testi: brez sinhronih zapisov na disk
                    .setServerConfig("fsync", "off")
                    .setServerConfig("synchronous_commit", "off")
                    .setServerConfig("full_page_writes", "off")
                    // vsak Springov kontekst v predpomnilniku drzi svoj bazen
                    .setServerConfig("max_connections", "500")
                    .start();
            try (Connection povezava = baza.getPostgresDatabase().getConnection();
                 Statement stavek = povezava.createStatement()) {
                stavek.execute(USTVARI_BAZO);
            }
        } catch (IOException napaka) {
            throw new UncheckedIOException("Testnega PostgreSQL ni bilo mogoce zagnati", napaka);
        } catch (SQLException napaka) {
            throw new IllegalStateException("Testne baze ni bilo mogoce ustvariti", napaka);
        }
        System.setProperty("spring.datasource.url", baza.getJdbcUrl("postgres", "turnirko"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
    }

    @Override
    public void launcherSessionClosed(LauncherSession seja) {
        if (baza == null) {
            return;
        }
        try {
            baza.close();
        } catch (IOException napaka) {
            // proces ugasne z JVM; zacasna mapa ostane, a ni nevarna
        }
        baza = null;
    }
}
