/* Vstopna tocka aplikacije Turnirko. */
package si.turnirko;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TurnirkoAplikacija {

    /* Predpona naslova SQLite; za druge baze (PostgreSQL) mape ni treba delati. */
    private static final String PREDPONA = "jdbc:sqlite:";

    /* Isti privzetek kot v application.properties - velja, kadar naslova ne
       poda ne okolje ne ukazna vrstica (obicajen razvojni zagon). */
    private static final String PRIVZETI_NASLOV = PREDPONA + "./podatki/turnirko.db";

    public static void main(String[] args) {
        pripraviMapoBaze(args);
        SpringApplication.run(TurnirkoAplikacija.class, args);
    }

    /* SQLite zna ustvariti datoteko baze, ne pa tudi mape zanjo.

       Mapo izpeljemo iz naslova, ki ga bo uporabil Spring, in NE iz fiksne
       relativne poti: v vsebniku je delovna mapa /app, zato bi se "podatki"
       razresilo v /app/podatki - kamor aplikacija (uporabnik 10001) ne sme
       pisati, prava baza pa lezi na priklopljenem /podatki. Taksen poskus je
       vsebnik ustavil se pred zagonom Springa. */
    private static void pripraviMapoBaze(String[] args) {
        Path mapa = mapaZaBazo(naslovBaze(args));
        if (mapa == null) {
            return;
        }
        try {
            Files.createDirectories(mapa);
        } catch (IOException napaka) {
            /* Zagona ne ustavljamo. Ce baza res ni dosegljiva, to pove Flyway
               z razumljivim sporocilom; sled sklada pred zagonom Springa pa
               samo skrije pravi vzrok. */
            System.err.println("Opozorilo: mape " + mapa + " ni bilo mogoce ustvariti ("
                    + napaka.getMessage() + "); nadaljujem, baza bo javila natancnejso napako.");
        }
    }

    /* Naslov baze po enakem vrstnem redu prednosti, kot ga upostevata Spring
       Boot in Docker: ukazna vrstica, nato spremenljivka okolja, nato privzetek. */
    private static String naslovBaze(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--spring.datasource.url=")) {
                return arg.substring("--spring.datasource.url=".length());
            }
        }
        String izOkolja = System.getenv("SPRING_DATASOURCE_URL");
        return izOkolja == null || izOkolja.isBlank() ? PRIVZETI_NASLOV : izOkolja;
    }

    /* Mapa, v kateri naj lezi datoteka baze; null, kadar je ni treba ustvariti
       (druga vrsta baze ali datoteka brez nadrejene mape). */
    static Path mapaZaBazo(String naslov) {
        if (naslov == null || !naslov.startsWith(PREDPONA)) {
            return null;
        }
        String pot = naslov.substring(PREDPONA.length());
        /* Naslov lahko nosi dodatne nastavitve za gonilnikom (?cache=shared). */
        int vprasaj = pot.indexOf('?');
        if (vprasaj >= 0) {
            pot = pot.substring(0, vprasaj);
        }
        if (pot.isBlank() || pot.startsWith(":")) {
            return null; // pomnilniska baza (:memory:)
        }
        return Path.of(pot).getParent();
    }
}
