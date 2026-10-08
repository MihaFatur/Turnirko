/* Koncne tocke Turnirko ratinga: vzdrzevanje (preracun, odbitki) in javna
   razlaga (pravila in preizkusa na strani /o-ratingu).

   Dostop: varnostna veriga vse, kar ni nasteto posebej, zaklene na ADMIN
   (zadnje pravilo "/api/**"), zato vzdrzevalni POST-i posebnega vnosa ne
   potrebujejo - preracun je vzdrzevalno opravilo in ne dejanje organizatorja.
   GET-i razlage so javni po splosnem pravilu "GET je javen": nic ne zapisejo
   in ne nosijo osebnih podatkov. */
package si.turnirko.kontrolerji;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.IzracunTekmeDto;
import si.turnirko.dto.PravilaRatingaDto;
import si.turnirko.dto.PrviDanNovincaDto;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.storitve.NeaktivnostStoritev;
import si.turnirko.storitve.PreracunRatingaStoritev;
import si.turnirko.storitve.RazlagaRatingaStoritev;

@RestController
@RequestMapping("/api/v1/rating")
public class RatingKontroler {

    private final PreracunRatingaStoritev preracunStoritev;
    private final NeaktivnostStoritev neaktivnostStoritev;
    private final RazlagaRatingaStoritev razlagaStoritev;

    public RatingKontroler(PreracunRatingaStoritev preracunStoritev,
                           NeaktivnostStoritev neaktivnostStoritev,
                           RazlagaRatingaStoritev razlagaStoritev) {
        this.preracunStoritev = preracunStoritev;
        this.neaktivnostStoritev = neaktivnostStoritev;
        this.razlagaStoritev = razlagaStoritev;
    }

    /* Stevilke pravil za javno razlago (K, teze, odbitki, sidra). */
    @GetMapping("/pravila")
    public PravilaRatingaDto pravila() {
        return razlagaStoritev.pravila();
    }

    /* Preizkus ene tekme dveh izmisljenih igralcev na vseh ravneh. */
    @GetMapping("/izracun")
    public IzracunTekmeDto izracun(@RequestParam int rating, @RequestParam int nasprotnik,
                                   @RequestParam(defaultValue = "50") int tekem,
                                   @RequestParam(defaultValue = "50") int tekemNasprotnika,
                                   @RequestParam(defaultValue = "false") boolean vrnitev,
                                   @RequestParam(defaultValue = "false") boolean vrnitevNasprotnika) {
        return razlagaStoritev.izracun(rating, nasprotnik, tekem, tekemNasprotnika,
                vrnitev, vrnitevNasprotnika);
    }

    /* Preizkus prvega dne novinca: tekme po vrsti, za vsako rating nasprotnika
       in izid (nasprotnik=1200&zmaga=true&nasprotnik=950&zmaga=false ...). */
    @GetMapping("/prvi-dan")
    public PrviDanNovincaDto prviDan(@RequestParam int izhodisce,
                                     @RequestParam(defaultValue = "URADNO") RavenTekmovanja raven,
                                     @RequestParam List<Integer> nasprotnik,
                                     @RequestParam List<Boolean> zmaga) {
        return razlagaStoritev.prviDan(izhodisce, raven, nasprotnik, zmaga);
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
