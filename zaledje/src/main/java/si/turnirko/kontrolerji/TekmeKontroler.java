/* Koncne tocke za tekme. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PopravekPredogledDto;
import si.turnirko.dto.TekmaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.NacinPopravka;
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
        return TekmaDto.iz(tekmaStoritev.vnesiRezultat(id, vnos), nizi(id));
    }

    /* Popravi rezultat ze koncane tekme (napacen vpis, napaka v zapisniku).
       PUT in ne POST: rezultat na tej tekmi ze obstaja in se zamenja - vnos je
       ustvarjanje, popravek pa zamenjava. Sme spremeniti tudi zmagovalca; ce je po
       tekmi ze kaj odigrano, `nacin` pove, kaj naj se s tem zgodi (predogled spodaj).
       Rating preracuna sam (glej TekmaStoritev.popraviRezultat). */
    @PutMapping("/{id}/rezultat")
    public TekmaDto popraviRezultat(@PathVariable Long id, @Valid @RequestBody VnosRezultata vnos,
                                    @RequestParam(required = false) NacinPopravka nacin) {
        return TekmaDto.iz(tekmaStoritev.popraviRezultat(id, vnos, nacin), nizi(id));
    }

    /* Kaj bi popravek naredil - brez zapisa. POST, ker nosi celoten vnos. */
    @PostMapping("/{id}/rezultat/predogled")
    public PopravekPredogledDto predogledPopravka(@PathVariable Long id,
                                                  @Valid @RequestBody VnosRezultata vnos) {
        return tekmaStoritev.predogledPopravka(id, vnos);
    }

    private List<NizVnos> nizi(Long idTekme) {
        return nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(idTekme).stream()
                .map(n -> new NizVnos(n.getTocke1(), n.getTocke2()))
                .toList();
    }
}
