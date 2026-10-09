/* Koncne tocke za srecanja: podroben pogled (zapisnik), postava, vnos
   rezultatov posamicnih tekem in razveljavitev zapisnika; za tekme koncnice se
   termin in domace pravice.
   Tanek adapter nad SrecanjeStoritev in KoncnicaStoritev. Srecanje je lahko
   ligasko ali ekipna tekma turnirja - koncne tocke so iste. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.MenjavaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.TerminSrecanjaVnos;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.storitve.KoncnicaStoritev;
import si.turnirko.storitve.SrecanjeStoritev;

@RestController
@RequestMapping("/api/v1/srecanja")
public class SrecanjaKontroler {

    private final SrecanjeStoritev srecanjeStoritev;
    private final KoncnicaStoritev koncnicaStoritev;

    public SrecanjaKontroler(SrecanjeStoritev srecanjeStoritev, KoncnicaStoritev koncnicaStoritev) {
        this.srecanjeStoritev = srecanjeStoritev;
        this.koncnicaStoritev = koncnicaStoritev;
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

    /* Menjava: kdo bo igral eno se neodigrano tekmo srecanja. */
    @PutMapping("/tekme/{idTekma}/igralci")
    public SrecanjePodrobnoDto zamenjajIgralce(@PathVariable Long idTekma, @RequestBody MenjavaVnos vnos) {
        return srecanjeStoritev.zamenjajIgralce(idTekma, vnos);
    }

    @PostMapping("/tekme/{idTekma}/rezultat")
    public TekmaSrecanjaDto vnesiRezultat(@PathVariable Long idTekma,
                                          @Valid @RequestBody VnosRezultataSrecanja vnos) {
        return srecanjeStoritev.vnesiRezultat(idTekma, vnos);
    }

    /* Popravek ze vpisanega rezultata (PUT, ker rezultat zamenja - glej
       TekmeKontroler). Zmagovalca sme spremeniti le, ce ostane potek
       tekmovanja isti (SrecanjeStoritev.popraviRezultat); rating preracuna sam. */
    @PutMapping("/tekme/{idTekma}/rezultat")
    public TekmaSrecanjaDto popraviRezultat(@PathVariable Long idTekma,
                                            @Valid @RequestBody VnosRezultataSrecanja vnos) {
        return srecanjeStoritev.popraviRezultat(idTekma, vnos);
    }

    /* Razveljavitev zapisnika: postava, izidi in obracun gredo, srecanje spet
       caka na vnos (SrecanjeStoritev.razveljaviZapisnik); rating preracuna sam. */
    @DeleteMapping("/{id}/zapisnik")
    public SrecanjePodrobnoDto razveljaviZapisnik(@PathVariable Long id) {
        return srecanjeStoritev.razveljaviZapisnik(id);
    }

    /* Termin tekme koncnice (redni del ima termine po kolih - /lige/{id}/termini). */
    @PutMapping("/{id}/termin")
    public SrecanjeDto nastaviTermin(@PathVariable Long id, @RequestBody TerminSrecanjaVnos vnos) {
        return koncnicaStoritev.nastaviTermin(id, vnos.zacetek());
    }

    /* Zamenja domacina in gosta tekme koncnice, ki se se ni zacela. */
    @PostMapping("/{id}/zamenjaj-domacina")
    public SrecanjeDto zamenjajDomacina(@PathVariable Long id) {
        return koncnicaStoritev.zamenjajDomacina(id);
    }
}
