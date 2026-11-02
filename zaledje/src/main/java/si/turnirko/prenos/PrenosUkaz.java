/* Ukaz »prenos«: podatke iz datoteke SQLite prenese v PostgreSQL
   (faza 3 prenove baze, docs/PRENOVA-BAZE.md, razdelek 6).

   Poteka KOT SAMOSTOJEN ZAGON, ne kot del delujoce aplikacije: profil
   »prenos« izklopi urnik opravil in zacetnega admina (ta bi v prazni bazi
   ustvaril racun in prenos bi zavrnil, ker tabela ni prazna). Flyway ob
   zagonu zgradi shemo, ukaz pa jo napolni. Uporabnik baze mora biti lastnik
   sheme (turnirko_lastnik): vloga aplikacije ne sme kopirati v tabele ne
   uskladiti zaporedij.

     cd zaledje
     mvnw spring-boot:run -Dspring-boot.run.profiles=prenos
       -Dspring-boot.run.arguments="--turnirko.prenos.sqlite=<kopija.db>"

   Nastavitve (vse z --turnirko.prenos.…):
     sqlite            pot do datoteke SQLite (obvezno; odpre se samo za branje)
     nacin             prenesi (privzeto) | preglej | preveri
     porocilo          kam se zapise porocilo (Markdown), privzeto prenos-porocilo.md
     ustavi-ob-koncu   true (privzeto): proces se konca z izhodno kodo 0 ob
                       zelenem in 1 ob rdecem porocilu

   Rdece porocilo pomeni, da v PostgreSQL ni spremenjeno nic (ena transakcija). */
package si.turnirko.prenos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(PrenosUkaz.PROFIL)
public class PrenosUkaz implements ApplicationRunner {

    public static final String PROFIL = "prenos";

    private static final Logger dnevnik = LoggerFactory.getLogger(PrenosUkaz.class);

    private final DataSource baza;
    private final ConfigurableApplicationContext kontekst;
    private final String potSqlite;
    private final String nacin;
    private final String potPorocila;
    private final boolean ustaviObKoncu;

    public PrenosUkaz(DataSource baza,
                      ConfigurableApplicationContext kontekst,
                      @Value("${turnirko.prenos.sqlite:}") String potSqlite,
                      @Value("${turnirko.prenos.nacin:prenesi}") String nacin,
                      @Value("${turnirko.prenos.porocilo:prenos-porocilo.md}") String potPorocila,
                      @Value("${turnirko.prenos.ustavi-ob-koncu:true}") boolean ustaviObKoncu) {
        this.baza = baza;
        this.kontekst = kontekst;
        this.potSqlite = potSqlite;
        this.nacin = nacin;
        this.potPorocila = potPorocila;
        this.ustaviObKoncu = ustaviObKoncu;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (potSqlite.isBlank()) {
            throw new IllegalStateException(
                    "Prenos potrebuje pot do datoteke SQLite: --turnirko.prenos.sqlite=<datoteka.db>");
        }
        PrenosPorocilo porocilo = new Prenos(baza, Path.of(potSqlite)).izvedi(Prenos.Nacin.iz(nacin));
        Path datoteka = Path.of(potPorocila);
        Files.writeString(datoteka, porocilo.vMarkdown(), StandardCharsets.UTF_8);
        dnevnik.info(porocilo.povzetek());
        dnevnik.info("Podrobno porocilo: {}", datoteka.toAbsolutePath());
        if (ustaviObKoncu) {
            boolean zeleno = porocilo.zeleno();
            System.exit(SpringApplication.exit(kontekst, () -> zeleno ? 0 : 1));
        }
    }
}
