/* Sinhronizacija s Stupo (samo admin - glej VarnostneNastavitve): seznam
   dogodkov sezone, predogled, uvoz in dnevnik. Tanek adapter nad
   UvozStupeStoritev. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.IzidUvozaDto;
import si.turnirko.dto.PredogledUvozaDto;
import si.turnirko.dto.SezonaUvozaDto;
import si.turnirko.dto.UvozDogodekDto;
import si.turnirko.dto.UvozZagonDto;
import si.turnirko.dto.UvozZahtevaDto;
import si.turnirko.uvoz.stupa.UvozStupeStoritev;

@RestController
@RequestMapping("/api/v1/uvoz/stupa")
public class UvozKontroler {

    private final UvozStupeStoritev uvoz;

    public UvozKontroler(UvozStupeStoritev uvoz) {
        this.uvoz = uvoz;
    }

    @GetMapping("/sezone")
    public List<SezonaUvozaDto> sezone() {
        return uvoz.sezone();
    }

    /* Objavljeni dogodki sezone (privzeto tekoce) s stanjem uvoza. */
    @GetMapping("/dogodki")
    public List<UvozDogodekDto> dogodki(@RequestParam(required = false) Long sezona) {
        return uvoz.dogodki(sezona);
    }

    /* Posnetek dogodka in poskusni zapis brez potrditve. */
    @PostMapping("/dogodki/{id}/predogled")
    public PredogledUvozaDto predogled(@PathVariable long id, @RequestBody(required = false) UvozZahtevaDto zahteva) {
        return uvoz.predogled(id, zahteva);
    }

    /* Uvoz nad posnetkom iz predogleda. */
    @PostMapping("/dogodki/{id}/uvoz")
    public IzidUvozaDto uvozi(@PathVariable long id, @RequestBody UvozZahtevaDto zahteva) {
        return uvoz.uvozi(id, zahteva);
    }

    @GetMapping("/zagoni")
    public List<UvozZagonDto> zagoni() {
        return uvoz.zagoni();
    }
}
