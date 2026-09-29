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

import jakarta.validation.Valid;

import si.turnirko.dto.NarocninaCiklusVnos;
import si.turnirko.dto.NarocninaDto;
import si.turnirko.dto.NarocninaObnovaVnos;
import si.turnirko.dto.NarocninaPaketVnos;
import si.turnirko.dto.SpremembaPaketaDto;
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

    /* Telo je neobvezno: obnova brez izbranega cikla in paketa ohrani tekoca. */
    @PostMapping("/obnova")
    public NarocninaDto obnova(@RequestBody(required = false) NarocninaObnovaVnos vnos) {
        return vnos == null ? narocnina.obnovi(null, null) : narocnina.obnovi(vnos.ciklus(), vnos.paket());
    }

    @PutMapping("/preklop")
    public NarocninaDto preklop(@RequestBody NarocninaCiklusVnos vnos) {
        return narocnina.preklopi(vnos.ciklus());
    }

    @DeleteMapping("/preklop")
    public NarocninaDto umikPreklopa() {
        return narocnina.razveljaviPreklop();
    }

    /* Zamenjava organizatorskega paketa: visji velja takoj (Stripe zaracuna
       sorazmerno doplacilo), nizji ob obnovi. */
    @PutMapping("/paket")
    public SpremembaPaketaDto zamenjavaPaketa(@Valid @RequestBody NarocninaPaketVnos vnos) {
        return narocnina.zamenjajPaket(vnos.paket());
    }

    /* Umik zabelezenega znizanja paketa. */
    @DeleteMapping("/paket")
    public NarocninaDto umikPrehoda() {
        return narocnina.razveljaviPrehod();
    }
}
