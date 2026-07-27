/* Vedenje, znacilno za NAMIZNO (portable) razlicico - aktivno samo
   pod profilom "namizni", da streznikske postavitve tega nikoli nimajo:
   - ob zagonu samodejno odpre brskalnik,
   - ponudi koncno tocko za zaustavitev aplikacije ob zaprtju zavihka. */
package si.turnirko.nastavitve;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Component
@Profile("namizni")
public class NamizniNacin {

    private final Environment okolje;

    public NamizniNacin(Environment okolje) {
        this.okolje = okolje;
    }

    /* Ko je aplikacija pripravljena, odpre privzeti brskalnik. */
    @EventListener(ApplicationReadyEvent.class)
    public void odpriBrskalnik() {
        String vrata = okolje.getProperty("local.server.port",
                okolje.getProperty("server.port", "8080"));
        String naslov = "http://localhost:" + vrata;
        try {
            // "start" prek cmd deluje zanesljivo na Windows sistemih
            new ProcessBuilder("cmd", "/c", "start", "", naslov).start();
        } catch (Exception napaka) {
            System.out.println("Brskalnika ni bilo mogoce odpreti - odpri rocno: " + naslov);
        }
    }

    /* Koncna tocka za zaustavitev - obstaja SAMO v namiznem nacinu.
       Klice jo vmesnik, ko uporabnik zapre zavihek. */
    @RestController
    @Profile("namizni")
    static class IzklopKontroler {

        private final ConfigurableApplicationContext kontekst;

        IzklopKontroler(ConfigurableApplicationContext kontekst) {
            this.kontekst = kontekst;
        }

        @PostMapping("/api/v1/sistem/izklop")
        void izklopi() {
            // kratek zamik, da se HTTP odgovor se poslje
            new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException prekinjeno) {
                    Thread.currentThread().interrupt();
                }
                kontekst.close();
            }).start();
        }
    }
}
