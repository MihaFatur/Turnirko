/* Razvojni nacin brez SMTP (turnirko.posta.nacin=dnevnik, privzeto): celotno
   sporocilo - tudi koda - gre v dnevnik aplikacije. Na strezniku je to
   uporabno samo zacasno (dokler poste se ni), zato ob zagonu glasno opozori:
   registracija brez prave poste za nikogar zunaj dnevnika ne deluje.

   HTML telo (obvestilo po kolu) se v dnevnik ne izpise; ce je nastavljena
   turnirko.posta.mapa-predogleda, se zapise tja kot datoteka, da se ga da
   odpreti v brskalniku. */
package si.turnirko.posta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnirko.posta.nacin", havingValue = "dnevnik", matchIfMissing = true)
public class DnevniskiPosiljatelj implements PosiljateljPoste {

    private static final Logger dnevnik = LoggerFactory.getLogger(DnevniskiPosiljatelj.class);

    private final String mapaPredogleda;
    private final AtomicInteger stevec = new AtomicInteger();

    public DnevniskiPosiljatelj(@Value("${turnirko.posta.mapa-predogleda:}") String mapaPredogleda) {
        this.mapaPredogleda = mapaPredogleda == null ? "" : mapaPredogleda.trim();
        dnevnik.warn("Posta ni nastavljena (turnirko.posta.nacin=dnevnik): potrditvene kode "
                + "se izpisujejo v dnevnik in ne posiljajo. Za splet nastavi nacin smtp.");
    }

    @Override
    public void poslji(String prejemnik, String zadeva, String besedilo) {
        dnevnik.info("E-posta (ni poslana) za {} / Zadeva: {}\n{}", prejemnik, zadeva, besedilo);
    }

    @Override
    public void poslji(String prejemnik, SporocilaPoste.Besedilo b) {
        poslji(prejemnik, b.zadeva(), b.telo());
        if (b.html() == null || mapaPredogleda.isEmpty()) {
            return;
        }
        Path datoteka = Path.of(mapaPredogleda, String.format("posta-%03d.html", stevec.incrementAndGet()));
        try {
            Files.createDirectories(datoteka.getParent());
            Files.writeString(datoteka, b.html(), StandardCharsets.UTF_8);
            dnevnik.info("HTML telo zapisano v {} (glave: {})", datoteka.toAbsolutePath(), b.glave());
        } catch (IOException napaka) {
            dnevnik.warn("HTML telesa ni bilo mogoce zapisati v {}: {}", datoteka, napaka.getMessage());
        }
    }
}
