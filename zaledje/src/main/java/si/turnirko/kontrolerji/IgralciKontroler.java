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
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.ZacetniRatingVnos;
import si.turnirko.dto.ZunanjaUvrstitevVnos;
import si.turnirko.storitve.IgralciStoritev;

@RestController
@RequestMapping("/api/v1/igralci")
public class IgralciKontroler {

    private final IgralciStoritev igralciStoritev;

    public IgralciKontroler(IgralciStoritev igralciStoritev) {
        this.igralciStoritev = igralciStoritev;
    }

    /* Javni seznam brez osebnih podatkov - bere ga tudi gost (izbirniki
       igralcev, "1 na 1", prijave na turnir). */
    @GetMapping
    public List<IgralecJavniDto> seznam() {
        return igralciStoritev.seznam();
    }

    /* Sifrant z osebnimi podatki. Varnostna veriga to pot omeji na ADMIN;
       poti ne zdruzuj z javno, ker bi bila razlika odvisna od ene same
       pogojne veje namesto od pravila v verigi. */
    @GetMapping("/podrobno")
    public List<IgralecDto> seznamPodrobno() {
        return igralciStoritev.seznamPodrobno();
    }

    @GetMapping("/{id}")
    public IgralecJavniDto najdi(@PathVariable Long id) {
        return igralciStoritev.najdi(id);
    }

    @GetMapping("/{id}/podrobno")
    public IgralecDto najdiPodrobno(@PathVariable Long id) {
        return igralciStoritev.najdiPodrobno(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IgralecJavniDto ustvari(@Valid @RequestBody IgralecVnos vnos) {
        return igralciStoritev.ustvari(vnos);
    }

    @PutMapping("/{id}")
    public IgralecJavniDto posodobi(@PathVariable Long id, @Valid @RequestBody IgralecVnos vnos) {
        return igralciStoritev.posodobi(id, vnos);
    }

    /* Postavitveni (zacetni) Turnirko rating za novinca - dovoljen le, dokler
       igralec ni odigral nobene ratinske tekme. */
    @PostMapping("/{id}/zacetni-rating")
    public IgralecJavniDto nastaviZacetniRating(@PathVariable Long id,
                                                @Valid @RequestBody ZacetniRatingVnos vnos) {
        return igralciStoritev.nastaviZacetniRating(id, vnos.vrednost());
    }

    /* Zunanja uvrstitev: rating z zunanje lestvice za redkega gosta. Za
       razliko od zacetnega ratinga je dovoljena tudi igralcu s tekmami,
       zahteva pa vir in pojasnilo - oboje je javno vidno na profilu. */
    @PostMapping("/{id}/zunanja-uvrstitev")
    public IgralecJavniDto zunanjaUvrstitev(@PathVariable Long id,
                                            @Valid @RequestBody ZunanjaUvrstitevVnos vnos) {
        return igralciStoritev.zunanjaUvrstitev(id, vnos);
    }

    /* Brisanje je v resnici arhiviranje - zgodovina tekem ostane. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arhiviraj(@PathVariable Long id) {
        igralciStoritev.arhiviraj(id);
    }
}
