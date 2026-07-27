/* Koncne tocke za igralce. */
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

import si.turnirko.dto.IgralecDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.ZacetniRatingVnos;
import si.turnirko.storitve.IgralciStoritev;

@RestController
@RequestMapping("/api/v1/igralci")
public class IgralciKontroler {

    private final IgralciStoritev igralciStoritev;

    public IgralciKontroler(IgralciStoritev igralciStoritev) {
        this.igralciStoritev = igralciStoritev;
    }

    @GetMapping
    public List<IgralecDto> seznam() {
        return igralciStoritev.seznam();
    }

    @GetMapping("/{id}")
    public IgralecDto najdi(@PathVariable Long id) {
        return igralciStoritev.najdi(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IgralecDto ustvari(@Valid @RequestBody IgralecVnos vnos) {
        return igralciStoritev.ustvari(vnos);
    }

    @PutMapping("/{id}")
    public IgralecDto posodobi(@PathVariable Long id, @Valid @RequestBody IgralecVnos vnos) {
        return igralciStoritev.posodobi(id, vnos);
    }

    /* Postavitveni (zacetni) klubski ELO za novinca - dovoljen le, dokler
       igralec ni odigral nobene ratinske tekme. */
    @PostMapping("/{id}/zacetni-rating")
    public IgralecDto nastaviZacetniRating(@PathVariable Long id,
                                           @Valid @RequestBody ZacetniRatingVnos vnos) {
        return igralciStoritev.nastaviZacetniRating(id, vnos.vrednost());
    }

    /* Brisanje je v resnici arhiviranje - zgodovina tekem ostane. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arhiviraj(@PathVariable Long id) {
        igralciStoritev.arhiviraj(id);
    }
}
