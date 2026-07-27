/* Vstopna tocka aplikacije Turnirko. */
package si.turnirko;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TurnirkoAplikacija {

    public static void main(String[] args) throws IOException {
        // SQLite zna ustvariti datoteko baze, ne pa tudi mape zanjo
        Files.createDirectories(Path.of("podatki"));
        SpringApplication.run(TurnirkoAplikacija.class, args);
    }
}
