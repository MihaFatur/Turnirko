/* Koncne tocke za sifrant klubov. */
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

import si.turnirko.dto.KlubDto;
import si.turnirko.dto.KlubVnos;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.repozitoriji.KlubRepozitorij;

@RestController
@RequestMapping("/api/v1/klubi")
public class KlubiKontroler {

    private final KlubRepozitorij klubRepozitorij;

    public KlubiKontroler(KlubRepozitorij klubRepozitorij) {
        this.klubRepozitorij = klubRepozitorij;
    }

    @GetMapping
    public List<KlubDto> seznam() {
        return klubRepozitorij.findAll(org.springframework.data.domain.Sort.by("ime"))
                .stream().map(KlubDto::iz).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KlubDto ustvari(@Valid @RequestBody KlubVnos vnos) {
        return KlubDto.iz(klubRepozitorij.save(new Klub(vnos.ime().trim(), vnos.kratica())));
    }

    @PutMapping("/{id}")
    public KlubDto posodobi(@PathVariable Long id, @Valid @RequestBody KlubVnos vnos) {
        Klub klub = najdi(id);
        klub.setIme(vnos.ime().trim());
        klub.setKratica(vnos.kratica());
        return KlubDto.iz(klubRepozitorij.save(klub));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izbrisi(@PathVariable Long id) {
        klubRepozitorij.delete(najdi(id));
    }

    private Klub najdi(Long id) {
        return klubRepozitorij.findById(id)
                .orElseThrow(() -> new NiNajdenoIzjema("Klub z id " + id + " ne obstaja."));
    }
}
