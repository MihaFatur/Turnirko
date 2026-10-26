/* Upravljanje kod popusta (samo ADMIN, pravilo v VarnostneNastavitve): seznam,
   nova koda, popravek (opis, rok, najvec uporab, aktivnost) in izbris neuporabljene
   kode. Predogled kode pri registraciji je javen in je v AvtentikacijaKontroler. */
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

import si.turnirko.dto.KodaPopustaDto;
import si.turnirko.dto.KodaPopustaUrediVnos;
import si.turnirko.dto.KodaPopustaVnos;
import si.turnirko.storitve.KodePopustaStoritev;

@RestController
@RequestMapping("/api/v1/kode-popusta")
public class KodePopustaKontroler {

    private final KodePopustaStoritev kode;

    public KodePopustaKontroler(KodePopustaStoritev kode) {
        this.kode = kode;
    }

    @GetMapping
    public List<KodaPopustaDto> seznam() {
        return kode.seznam();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KodaPopustaDto ustvari(@Valid @RequestBody KodaPopustaVnos vnos) {
        return kode.ustvari(vnos);
    }

    @PutMapping("/{id}")
    public KodaPopustaDto uredi(@PathVariable Long id, @Valid @RequestBody KodaPopustaUrediVnos vnos) {
        return kode.uredi(id, vnos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zbrisi(@PathVariable Long id) {
        kode.zbrisi(id);
    }
}
