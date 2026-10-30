/* Koncne tocke za pokrovitelje lige in turnirja (V56). Branje je javno (trak na
   strani), pisanje samo lastniku in administratorju - preverba je v
   PokroviteljiStoritev, veriga pusti organizatorja na lige in turnirje. */
package si.turnirko.kontrolerji;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.PokroviteljDto;
import si.turnirko.dto.PokroviteljiVnos;
import si.turnirko.storitve.PokroviteljiStoritev;

@RestController
public class PokroviteljiKontroler {

    private final PokroviteljiStoritev storitev;

    public PokroviteljiKontroler(PokroviteljiStoritev storitev) {
        this.storitev = storitev;
    }

    @GetMapping("/api/v1/lige/{id}/pokrovitelji")
    public List<PokroviteljDto> ligi(@PathVariable Long id) {
        return storitev.zaLigo(id);
    }

    @PutMapping("/api/v1/lige/{id}/pokrovitelji")
    public List<PokroviteljDto> shraniLigi(@PathVariable Long id, @Valid @RequestBody PokroviteljiVnos vnos) {
        return storitev.shraniZaLigo(id, vnos.pokrovitelji());
    }

    @GetMapping("/api/v1/turnirji/{id}/pokrovitelji")
    public List<PokroviteljDto> turnirju(@PathVariable Long id) {
        return storitev.zaTurnir(id);
    }

    @PutMapping("/api/v1/turnirji/{id}/pokrovitelji")
    public List<PokroviteljDto> shraniTurnirju(@PathVariable Long id, @Valid @RequestBody PokroviteljiVnos vnos) {
        return storitev.shraniZaTurnir(id, vnos.pokrovitelji());
    }
}
