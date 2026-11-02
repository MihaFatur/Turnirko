/* Podpora klubu (PodporaKlubaStoritev, V61): vse poti zahtevajo prijavo (pravilo v
   VarnostneNastavitve, pred splosnim »GET je javen«); paket Navijac preveri storitev. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.PodporaKlubaDto;
import si.turnirko.storitve.PodporaKlubaStoritev;

@RestController
@RequestMapping("/api/v1/podpora-kluba")
public class PodporaKlubaKontroler {

    private final PodporaKlubaStoritev podpora;

    public PodporaKlubaKontroler(PodporaKlubaStoritev podpora) {
        this.podpora = podpora;
    }

    @GetMapping
    public PodporaKlubaDto moja() {
        return podpora.moja();
    }

    @PutMapping("/{idKlub}")
    public PodporaKlubaDto izberi(@PathVariable Long idKlub) {
        return podpora.izberi(idKlub);
    }

    @DeleteMapping
    public PodporaKlubaDto umakni() {
        return podpora.umakni();
    }

    /* Popust kluba prijavljenega organizatorja (stran Narocnina organizatorja). */
    @GetMapping("/moj-klub")
    public PodporaKlubaDto mojKlub() {
        return podpora.zaOrganizatorja();
    }
}
