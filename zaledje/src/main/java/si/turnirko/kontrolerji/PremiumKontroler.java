/* Javni podatki za oglas Igralec Premium. Zaenkrat ena pot: socialni dokaz.
   Kontroler je tanek adapter, pravila so v PremiumDokazStoritev. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.PremiumDokazDto;
import si.turnirko.storitve.PremiumDokazStoritev;

@RestController
@RequestMapping("/api/v1/premium")
public class PremiumKontroler {

    private final PremiumDokazStoritev dokaz;

    public PremiumKontroler(PremiumDokazStoritev dokaz) {
        this.dokaz = dokaz;
    }

    @GetMapping("/dokaz")
    public PremiumDokazDto dokaz() {
        return dokaz.dokaz();
    }
}
