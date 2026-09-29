/* Organizatorski pregled (stran »Moj profil« organizatorja). Kontroler je tanek
   adapter - pravila so v OrganizatorPregledStoritev. Pot je omejena na vlogo
   ORGANIZATOR v VarnostneNastavitve (GET je sicer javen: pregled bere lastnino
   in porabo paketa, gostu ali igralcu ne sme priti). */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.OrganizatorPregledDto;
import si.turnirko.storitve.OrganizatorPregledStoritev;

@RestController
@RequestMapping("/api/v1/organizator")
public class OrganizatorKontroler {

    private final OrganizatorPregledStoritev pregled;

    public OrganizatorKontroler(OrganizatorPregledStoritev pregled) {
        this.pregled = pregled;
    }

    @GetMapping("/pregled")
    public OrganizatorPregledDto pregled() {
        return pregled.pregled();
    }
}
