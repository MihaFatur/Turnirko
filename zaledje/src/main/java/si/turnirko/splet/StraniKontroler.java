/* Strani vmesnika, ki dobijo svoj naslov in predogled za deljenje (glej
   PredogledStrani). Vse ostale poti (domaca stran, admin, neznane) streze
   SpletniVmesnik z nespremenjenim index.html.

   Kontroler ima prednost pred SpletniVmesnik (preslikave kontrolerjev se
   preverijo pred viri), zato tu nastejemo samo poti vmesnika - /api in
   datoteke s pripono sem nikoli ne pridejo.

   Ce vmesnik ni vgrajen (razvoj brez profila "splet", vmesnik tece na Vite),
   index.html ne obstaja in odgovor je 404 - isto kot prej pri SpletniVmesnik. */
package si.turnirko.splet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import si.turnirko.splet.PredogledStrani.Predogled;

@Controller
public class StraniKontroler {

    private static final ClassPathResource ZACETNA = new ClassPathResource("static/index.html");

    private final PredogledStoritev predogledi;
    private final String javniNaslov;
    /* index.html se med tekom ne spremeni (je v .jar), zato se prebere enkrat. */
    private volatile String predloga;

    public StraniKontroler(PredogledStoritev predogledi,
                           @Value("${turnirko.javni-naslov:https://turnirko-nt.si}") String javniNaslov) {
        this.predogledi = predogledi;
        this.javniNaslov = javniNaslov;
    }

    @GetMapping("/lige/{id}")
    public ResponseEntity<String> liga(@PathVariable String id,
                                       @RequestParam(name = "ekipa", required = false) String ekipa) {
        Long idLiga = stevilka(id);
        return stran(idLiga == null ? Optional.empty() : predogledi.liga(idLiga, stevilka(ekipa)));
    }

    @GetMapping("/turnirji/{id}")
    public ResponseEntity<String> turnir(@PathVariable String id) {
        Long idTurnir = stevilka(id);
        return stran(idTurnir == null ? Optional.empty() : predogledi.turnir(idTurnir));
    }

    @GetMapping("/dogodki/{id}")
    public ResponseEntity<String> dogodek(@PathVariable String id) {
        Long idDogodek = stevilka(id);
        return stran(idDogodek == null ? Optional.empty() : predogledi.dogodek(idDogodek));
    }

    @GetMapping("/srecanja/{id}")
    public ResponseEntity<String> srecanje(@PathVariable String id) {
        Long idSrecanje = stevilka(id);
        return stran(idSrecanje == null ? Optional.empty() : predogledi.srecanje(idSrecanje));
    }

    @GetMapping("/igralci/{id}/profil")
    public ResponseEntity<String> profil(@PathVariable String id) {
        Long idIgralec = stevilka(id);
        return stran(idIgralec == null ? Optional.empty() : predogledi.igralec(idIgralec));
    }

    @GetMapping({"/lige", "/turnirji", "/lestvica", "/koledar", "/o-ratingu", "/pogoji", "/dvoboj"})
    public ResponseEntity<String> stalna(HttpServletRequest zahteva) {
        return stran(PredogledStoritev.stalna(zahteva.getRequestURI()));
    }

    /* index.html s predogledom strani oz. nespremenjen, kadar zapisa ni.
       no-cache: ob novi objavi mora brskalnik dobiti nov index.html (z novimi
       imeni datotek vmesnika), predogled pa novo ime lige ali izid. */
    private ResponseEntity<String> stran(Optional<Predogled> predogled) {
        String html = predloga();
        if (html == null) {
            return ResponseEntity.notFound().build();
        }
        String telo = predogled
                .map(p -> PredogledStrani.vstavi(html, p, javniNaslov))
                .orElse(html);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .body(telo);
    }

    private String predloga() {
        String p = predloga;
        if (p == null && ZACETNA.exists()) {
            try (InputStream vhod = ZACETNA.getInputStream()) {
                p = new String(vhod.readAllBytes(), StandardCharsets.UTF_8);
                predloga = p;
            } catch (IOException e) {
                return null;
            }
        }
        return p;
    }

    /* Id iz poti ali parametra; neveljaven (npr. /lige/abc) pomeni splosen
       predogled in ne napake 400 - stran nato sama pove, da zapisa ni. */
    private static Long stevilka(String besedilo) {
        if (besedilo == null) {
            return null;
        }
        try {
            long v = Long.parseLong(besedilo.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
