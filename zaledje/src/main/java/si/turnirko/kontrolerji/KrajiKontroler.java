/* Koncne tocke za sifrant krajev. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.KrajDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Kraj;
import si.turnirko.repozitoriji.KrajRepozitorij;

@RestController
@RequestMapping("/api/v1/kraji")
public class KrajiKontroler {

    private final KrajRepozitorij krajRepozitorij;

    public KrajiKontroler(KrajRepozitorij krajRepozitorij) {
        this.krajRepozitorij = krajRepozitorij;
    }

    @GetMapping
    public List<KrajDto> seznam() {
        return krajRepozitorij.findAll(org.springframework.data.domain.Sort.by("postnaSt"))
                .stream().map(KrajDto::iz).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KrajDto ustvari(@Valid @RequestBody KrajDto vnos) {
        // postna stevilka je primarni kljuc: save() bi obstojeci kraj tiho
        // POSODOBIL, zato podvojen vnos izrecno zavrnemo
        if (krajRepozitorij.existsById(vnos.postnaSt())) {
            throw new DomenskaIzjema(
                    "Kraj s postno stevilko " + vnos.postnaSt() + " ze obstaja.");
        }
        return KrajDto.iz(krajRepozitorij.save(new Kraj(vnos.postnaSt(), vnos.ime().trim())));
    }

    @PutMapping("/{postnaSt}")
    public KrajDto posodobi(@PathVariable Integer postnaSt, @Valid @RequestBody KrajDto vnos) {
        Kraj kraj = najdi(postnaSt);
        kraj.setIme(vnos.ime().trim());
        return KrajDto.iz(krajRepozitorij.save(kraj));
    }

    @DeleteMapping("/{postnaSt}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izbrisi(@PathVariable Integer postnaSt) {
        krajRepozitorij.delete(najdi(postnaSt));
    }

    private Kraj najdi(Integer postnaSt) {
        return krajRepozitorij.findById(postnaSt)
                .orElseThrow(() -> new NiNajdenoIzjema("Kraj s postno stevilko " + postnaSt + " ne obstaja."));
    }
}
