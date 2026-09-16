/* Koncne tocke za tekme. */
package si.turnirko.kontrolerji;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.storitve.TekmaStoritev;

@RestController
@RequestMapping("/api/v1/tekme")
public class TekmeKontroler {

    private final TekmaStoritev tekmaStoritev;
    private final NizRepozitorij nizRepozitorij;

    public TekmeKontroler(TekmaStoritev tekmaStoritev, NizRepozitorij nizRepozitorij) {
        this.tekmaStoritev = tekmaStoritev;
        this.nizRepozitorij = nizRepozitorij;
    }

    /* Vnese koncni rezultat tekme in sprozi napredovanje po mrezi. */
    @PostMapping("/{id}/rezultat")
    public TekmaDto vnesiRezultat(@PathVariable Long id, @Valid @RequestBody VnosRezultata vnos) {
        return TekmaDto.iz(tekmaStoritev.vnesiRezultat(id, vnos),
                nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(id).stream()
                        .map(n -> new NizVnos(n.getTocke1(), n.getTocke2()))
                        .toList());
    }
}
