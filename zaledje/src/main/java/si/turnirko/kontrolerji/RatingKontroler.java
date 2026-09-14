/* Koncne tocke za vzdrzevanje Turnirko ratinga.

   Dostop: varnostna veriga vse, kar ni nasteto posebej, zaklene na ADMIN
   (zadnje pravilo "/api/**"), zato ta pot posebnega vnosa ne potrebuje.
   Preracun je vzdrzevalno opravilo in ne dejanje organizatorja. */
package si.turnirko.kontrolerji;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.storitve.NeaktivnostStoritev;
import si.turnirko.storitve.PreracunRatingaStoritev;

@RestController
@RequestMapping("/api/v1/rating")
public class RatingKontroler {

    private final PreracunRatingaStoritev preracunStoritev;
    private final NeaktivnostStoritev neaktivnostStoritev;

    public RatingKontroler(PreracunRatingaStoritev preracunStoritev,
                           NeaktivnostStoritev neaktivnostStoritev) {
        this.preracunStoritev = preracunStoritev;
        this.neaktivnostStoritev = neaktivnostStoritev;
    }

    /* Ponovno preracuna rating od datuma naprej; brez datuma od zacetka.
       Dolgotrajno opravilo (pri uvozeni zgodovini nekaj deset tisoc tekem),
       zato ga ne klice noben reden postopek - sprozi ga administrator. */
    @PostMapping("/preracun")
    public PreracunRatingaStoritev.Porocilo preracunaj(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od) {
        return preracunStoritev.preracunajOd(od);
    }

    /* Uveljavi vse zapadle odbitke za neaktivnost. Isto pocne dnevno opravilo;
       tu je zato, da administratorju ni treba cakati do treh zjutraj. */
    @PostMapping("/neaktivnost")
    public NeaktivnostStoritev.Porocilo neaktivnost() {
        return neaktivnostStoritev.uveljaviVse(LocalDateTime.now());
    }
}
