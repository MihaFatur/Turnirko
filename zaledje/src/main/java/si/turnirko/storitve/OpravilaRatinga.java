/* Opravila ratinga po urniku.

   Svoj razred zato, ker urnik metodo poklice neposredno: transakcijska metoda,
   poklicana iz istega razreda, gre mimo Springovega posrednika in tece brez
   transakcije (in brez zaklepa). Od tu gre klic vedno skozi posrednika. */
package si.turnirko.storitve;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import si.turnirko.izjeme.DomenskaIzjema;

@Component
public class OpravilaRatinga {

    private static final Logger dnevnik = LoggerFactory.getLogger(OpravilaRatinga.class);

    private final NeaktivnostStoritev neaktivnost;
    private final PreracunRatingaStoritev preracun;

    public OpravilaRatinga(NeaktivnostStoritev neaktivnost, PreracunRatingaStoritev preracun) {
        this.neaktivnost = neaktivnost;
        this.preracun = preracun;
    }

    /* Dnevno ob 3.15 zjutraj: takrat nihce ne vnasa rezultatov, odbitki pa so
       vezani na datum in ne na uro, zato tocna ura ni pomembna. Ce ravno tece
       preracun, ga ta uveljavi sam ob koncu. */
    @Scheduled(cron = "0 15 3 * * *")
    public void odbitkiZaNeaktivnost() {
        try {
            neaktivnost.uveljaviVseZZaklepom(LocalDateTime.now());
        } catch (DomenskaIzjema preracunTece) {
            dnevnik.info("Odbitki za neaktivnost preskoceni: tece preracun, ki jih uveljavi sam.");
        }
    }

    /* Varovalka (PreracunRatingaStoritev.varovalka): odlozeni preracuni in
       koncane tekme brez obracuna. V testih je razmik dolg, da varovalka ne
       obracuna tekem, ki jih test namenoma pusti brez obracuna. */
    @Scheduled(fixedDelayString = "${turnirko.rating.varovalka:PT5M}",
               initialDelayString = "${turnirko.rating.varovalka:PT5M}")
    public void varovalka() {
        preracun.varovalka();
    }
}
