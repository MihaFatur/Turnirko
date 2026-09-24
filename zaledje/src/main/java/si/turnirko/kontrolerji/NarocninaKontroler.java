/* Stran "Narocnina": pregled in upravljanje obstojece narocnine prijavljenega
   uporabnika. Nova narocnina (nadgradnja) je v PlacilaKontroler, ker gre skozi
   Stripe Checkout; tu je samo tisto, kar narocnina ze ima. Kontroler je tanek
   adapter - pravila so v UpravljanjeNarocnineStoritev. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.NarocninaCiklusVnos;
import si.turnirko.dto.NarocninaDto;
import si.turnirko.storitve.UpravljanjeNarocnineStoritev;

@RestController
@RequestMapping("/api/v1/narocnina")
public class NarocninaKontroler {

    private final UpravljanjeNarocnineStoritev narocnina;

    public NarocninaKontroler(UpravljanjeNarocnineStoritev narocnina) {
        this.narocnina = narocnina;
    }

    @GetMapping
    public NarocninaDto pregled() {
        return narocnina.pregled();
    }

    @PostMapping("/preklic")
    public NarocninaDto preklic() {
        return narocnina.prekliciOKoncuObdobja();
    }

    /* Telo je neobvezno: obnova brez izbranega cikla ohrani tekoci cikel. */
    @PostMapping("/obnova")
    public NarocninaDto obnova(@RequestBody(required = false) NarocninaCiklusVnos vnos) {
        return narocnina.obnovi(vnos == null ? null : vnos.ciklus());
    }

    @PutMapping("/preklop")
    public NarocninaDto preklop(@RequestBody NarocninaCiklusVnos vnos) {
        return narocnina.preklopi(vnos.ciklus());
    }

    @DeleteMapping("/preklop")
    public NarocninaDto umikPreklopa() {
        return narocnina.razveljaviPreklop();
    }
}
