/* Obvestila po kolu: nastavitev racuna in odjava iz e-poste.

   Nastavitev je osebna kot izbor lig (prijava, ne vloga). Odjava je brez
   prijave - dokaz je zeton iz sporocila - in sprejme tudi POST odjave z enim
   klikom (RFC 8058), ki ga odjemalec poste poslje na naslov iz glave
   List-Unsubscribe (telo "List-Unsubscribe=One-Click" se prezre). */
package si.turnirko.kontrolerji;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.ObvestilaDto;
import si.turnirko.dto.ObvestilaVnos;
import si.turnirko.storitve.ObvestilaPoKoluStoritev;

@RestController
@RequestMapping("/api/v1/obvestila")
public class ObvestilaKontroler {

    private final ObvestilaPoKoluStoritev obvestila;

    public ObvestilaKontroler(ObvestilaPoKoluStoritev obvestila) {
        this.obvestila = obvestila;
    }

    @GetMapping
    public ObvestilaDto nastavitve() {
        return obvestila.nastavitve();
    }

    @PutMapping
    public ObvestilaDto nastavi(@RequestBody ObvestilaVnos vnos) {
        return obvestila.nastavi(vnos.poKolu());
    }

    @PostMapping("/odjava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odjava(@RequestParam("z") String zeton) {
        obvestila.odjava(zeton);
    }
}
