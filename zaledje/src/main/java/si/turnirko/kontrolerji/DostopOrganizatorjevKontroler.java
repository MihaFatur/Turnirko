/* Koncne tocke za dostop organizatorjev do turnirja ali lige (V62). Vse, tudi
   branje, so samo za administratorja (pravilo v VarnostneNastavitve stoji pred
   splosnim »GET je javen«): odgovor nosi e-poste organizatorjev. */
package si.turnirko.kontrolerji;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.DostopOrganizatorjaVnos;
import si.turnirko.dto.DostopTekmovanjaDto;
import si.turnirko.storitve.DostopOrganizatorjevStoritev;

@RestController
public class DostopOrganizatorjevKontroler {

    private final DostopOrganizatorjevStoritev storitev;

    public DostopOrganizatorjevKontroler(DostopOrganizatorjevStoritev storitev) {
        this.storitev = storitev;
    }

    @GetMapping("/api/v1/turnirji/{id}/organizatorji")
    public DostopTekmovanjaDto turnir(@PathVariable Long id) {
        return storitev.zaTurnir(id);
    }

    @PutMapping("/api/v1/turnirji/{id}/organizatorji/{idRacun}")
    public DostopTekmovanjaDto nastaviTurnir(@PathVariable Long id, @PathVariable Long idRacun,
                                             @Valid @RequestBody DostopOrganizatorjaVnos vnos) {
        return storitev.nastaviZaTurnir(id, idRacun, vnos.steje());
    }

    @DeleteMapping("/api/v1/turnirji/{id}/organizatorji/{idRacun}")
    public DostopTekmovanjaDto odstraniTurnir(@PathVariable Long id, @PathVariable Long idRacun) {
        return storitev.odstraniZaTurnir(id, idRacun);
    }

    @GetMapping("/api/v1/lige/{id}/organizatorji")
    public DostopTekmovanjaDto liga(@PathVariable Long id) {
        return storitev.zaLigo(id);
    }

    @PutMapping("/api/v1/lige/{id}/organizatorji/{idRacun}")
    public DostopTekmovanjaDto nastaviLigo(@PathVariable Long id, @PathVariable Long idRacun,
                                           @Valid @RequestBody DostopOrganizatorjaVnos vnos) {
        return storitev.nastaviZaLigo(id, idRacun, vnos.steje());
    }

    @DeleteMapping("/api/v1/lige/{id}/organizatorji/{idRacun}")
    public DostopTekmovanjaDto odstraniLigo(@PathVariable Long id, @PathVariable Long idRacun) {
        return storitev.odstraniZaLigo(id, idRacun);
    }
}
