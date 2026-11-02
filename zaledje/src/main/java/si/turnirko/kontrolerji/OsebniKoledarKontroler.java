/* Osebni koledar .ics (OsebniKoledarStoritev).

   Dve poti z razlicno varnostjo:
   - /moj-koledar (prijava, paket Navijac ali Premium): povezava racuna in
     njena zamenjava; pravilo je v VarnostneNastavitve pred "GET je javen";
   - /koledar-ics/{zeton}.ics BREZ prijave: koledar v telefonu ne zna poslati
     gesla, zato ga varuje nakljucen zeton (to je javni GET kot vsi ostali). */
package si.turnirko.kontrolerji;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.KoledarPovezavaDto;
import si.turnirko.storitve.OsebniKoledarStoritev;

@RestController
@RequestMapping("/api/v1")
public class OsebniKoledarKontroler {

    private static final MediaType KOLEDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final OsebniKoledarStoritev koledar;

    public OsebniKoledarKontroler(OsebniKoledarStoritev koledar) {
        this.koledar = koledar;
    }

    @GetMapping("/moj-koledar")
    public KoledarPovezavaDto povezava() {
        return new KoledarPovezavaDto(koledar.zeton());
    }

    @PostMapping("/moj-koledar/zamenjaj")
    public KoledarPovezavaDto zamenjaj() {
        return new KoledarPovezavaDto(koledar.zamenjajZeton());
    }

    /* Koncnica .ics je del naslova (koledarji jo pricakujejo), zato je spremenljivka
       cela "datoteka" in se koncnica odreze tu. */
    @GetMapping("/koledar-ics/{datoteka}")
    public ResponseEntity<String> koledarIcs(@PathVariable String datoteka) {
        String zeton = datoteka.endsWith(".ics") ? datoteka.substring(0, datoteka.length() - 4) : datoteka;
        return ResponseEntity.ok()
                .contentType(KOLEDAR)
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline; filename=\"turnirko.ics\"")
                .body(koledar.koledar(zeton));
    }
}
