/* Koncne tocke za srecanja: podroben pogled (zapisnik), postava in vnos
   rezultatov posamicnih tekem. Tanek adapter nad SrecanjeStoritev. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.storitve.SrecanjeStoritev;

@RestController
@RequestMapping("/api/v1/srecanja")
public class SrecanjaKontroler {

    private final SrecanjeStoritev srecanjeStoritev;

    public SrecanjaKontroler(SrecanjeStoritev srecanjeStoritev) {
        this.srecanjeStoritev = srecanjeStoritev;
    }

    @GetMapping("/{id}")
    public SrecanjePodrobnoDto podrobno(@PathVariable Long id) {
        return srecanjeStoritev.podrobno(id);
    }

    @PutMapping("/{id}/postava")
    public SrecanjePodrobnoDto nastaviPostavo(@PathVariable Long id, @Valid @RequestBody PostavaVnos vnos) {
        srecanjeStoritev.nastaviPostavo(id, vnos);
        return srecanjeStoritev.podrobno(id);
    }

    @PostMapping("/tekme/{idTekma}/rezultat")
    public TekmaSrecanjaDto vnesiRezultat(@PathVariable Long idTekma,
                                          @Valid @RequestBody VnosRezultataSrecanja vnos) {
        return srecanjeStoritev.vnesiRezultat(idTekma, vnos);
    }
}
