/* Opravila branja listkov, ki tecejo brez organizatorja:
   - vsako noc izbrise fotografije, starejse od 30 dni po turnirju, kljuce
     posiljanja, starejse od 24 ur (F5.2), ter stevke in razlicice mreze cez mejo
     hrambe (UcenjeMreze.pocisti);
   - ob zagonu oznaci branja, ki jih je prekinil ponovni zagon;
   - ob zagonu in vsako noc prebere pogostost izidov nizov (PogostostNizovStoritev).

   Loceno od BranjeListkovStoritev, ker @Scheduled in @EventListener metode
   klicejo transakcijsko metodo storitve PREK posrednika - klic znotraj istega
   razreda bi transakcijo obsel (ista past kot pri obvestilih po kolu). */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OpravilaBranjaListkov {

    private static final Logger log = LoggerFactory.getLogger(OpravilaBranjaListkov.class);
    private static final ZoneId LJUBLJANA = ZoneId.of("Europe/Ljubljana");

    private final BranjeListkovStoritev storitev;
    private final UcenjeMreze ucenje;
    private final PogostostNizovStoritev pogostost;

    public OpravilaBranjaListkov(BranjeListkovStoritev storitev, UcenjeMreze ucenje,
                                 PogostostNizovStoritev pogostost) {
        this.storitev = storitev;
        this.ucenje = ucenje;
        this.pogostost = pogostost;
    }

    @Scheduled(cron = "${turnirko.branje-listkov.ciscenje:0 35 4 * * *}", zone = "Europe/Ljubljana")
    public void izbrisiStareSlike() {
        // dve neodvisni opravili: napaka enega ne ustavi drugega in se ne izgubi
        try {
            int izbrisanih = storitev.izbrisiStareSlike(LocalDate.now(LJUBLJANA));
            if (izbrisanih > 0) {
                log.info("Branje listkov: izbrisanih {} fotografij, starejsih od 30 dni po turnirju", izbrisanih);
            }
        } catch (RuntimeException e) {
            log.error("Branje listkov: brisanje starih fotografij ni uspelo", e);
        }
        try {
            storitev.pobrisiStareKljucePosiljanja();
        } catch (RuntimeException e) {
            log.error("Branje listkov: brisanje kljucev posiljanja ni uspelo", e);
        }
        try {
            int[] hramba = ucenje.pocisti();
            if (hramba[0] > 0 || hramba[1] > 0) {
                log.info("Branje listkov: hramba izbrisala {} stevk in {} razlicic mreze", hramba[0], hramba[1]);
            }
        } catch (RuntimeException e) {
            log.error("Branje listkov: hramba ucenja ni uspela", e);
        }
        osveziPogostost();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void obZagonu() {
        int prekinjenih = storitev.prekiniOstalaVVrsti();
        if (prekinjenih > 0) {
            log.warn("Branje listkov: {} branj je prekinil ponovni zagon", prekinjenih);
        }
        osveziPogostost();
    }

    /* Napaka ne ustavi drugih opravil; branje do naslednjega poskusa uporablja
       zadnjo (ali vgrajeno) pogostost. */
    private void osveziPogostost() {
        try {
            pogostost.osvezi();
        } catch (RuntimeException e) {
            log.error("Branje listkov: pogostosti izidov nizov ni bilo mogoce prebrati", e);
        }
    }
}
