/* Koncne tocke domace strani: povzetki lig in osebni izbor spremljanih lig.

   Povzetki so javni (gost vidi vse), izbor pa je last racuna in zahteva
   prijavo - to je edina pot v vmesniku, kjer sme tudi navaden igralec kaj
   zapisati, zato jo varnostna veriga nasteje posebej. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.storitve.DomovStoritev;

@RestController
@RequestMapping("/api/v1/domov")
public class DomovKontroler {

    private final DomovStoritev domovStoritev;

    public DomovKontroler(DomovStoritev domovStoritev) {
        this.domovStoritev = domovStoritev;
    }

    /* Povzetki lig; brez parametra vrne lige, ki so v teku. */
    @GetMapping("/lige")
    public List<DomovLigaDto> lige(@RequestParam(required = false) List<Long> idji) {
        return domovStoritev.povzetkiLig(idji);
    }

    @GetMapping("/moje-lige")
    public List<Long> mojeLige() {
        return domovStoritev.mojeLige();
    }

    @PutMapping("/moje-lige/{idLiga}")
    public List<Long> spremljaj(@PathVariable Long idLiga) {
        return domovStoritev.spremljaj(idLiga);
    }

    @DeleteMapping("/moje-lige/{idLiga}")
    public List<Long> nehajSpremljati(@PathVariable Long idLiga) {
        return domovStoritev.nehajSpremljati(idLiga);
    }
}
