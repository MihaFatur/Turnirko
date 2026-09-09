/* Koncne tocke za turnirje in njihove dogodke. */
package si.turnirko.kontrolerji;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.DogodekDto;
import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.StatistikaTekmovanjaDto;
import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.PovzetkiStoritev;
import si.turnirko.storitve.PovzetkiStoritev.StevciDogodka;
import si.turnirko.storitve.StatistikaTekmovanjaStoritev;
import si.turnirko.storitve.TurnirjiStoritev;

@RestController
@RequestMapping("/api/v1/turnirji")
public class TurnirjiKontroler {

    private final TurnirjiStoritev turnirjiStoritev;
    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;
    private final PovzetkiStoritev povzetkiStoritev;
    private final StatistikaTekmovanjaStoritev statistikaTekmovanjaStoritev;

    public TurnirjiKontroler(TurnirjiStoritev turnirjiStoritev,
                             TurnirRepozitorij turnirRepozitorij,
                             DogodekRepozitorij dogodekRepozitorij,
                             PovzetkiStoritev povzetkiStoritev,
                             StatistikaTekmovanjaStoritev statistikaTekmovanjaStoritev) {
        this.turnirjiStoritev = turnirjiStoritev;
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
        this.povzetkiStoritev = povzetkiStoritev;
        this.statistikaTekmovanjaStoritev = statistikaTekmovanjaStoritev;
    }

    @GetMapping
    public List<TurnirDto> seznam() {
        Map<Long, TurnirDto.Stevci> stevci = povzetkiStoritev.zaVseTurnirje();
        Map<Long, TurnirDto.Potek> poteki = povzetkiStoritev.potekiVsehTurnirjev();
        return turnirRepozitorij.najdiVseSKrajem().stream()
                .map(t -> TurnirDto.iz(t,
                        stevci.getOrDefault(t.getId(), TurnirDto.Stevci.PRAZNI),
                        poteki.getOrDefault(t.getId(), TurnirDto.Potek.PRAZEN)))
                .toList();
    }

    @GetMapping("/{id}")
    public TurnirDto najdi(@PathVariable Long id) {
        Map<Long, TurnirDto.Potek> poteki = povzetkiStoritev.potekiVsehTurnirjev();
        return turnirRepozitorij.najdiSKrajem(id)
                .map(t -> TurnirDto.iz(t, povzetkiStoritev.zaTurnir(id),
                        poteki.getOrDefault(id, TurnirDto.Potek.PRAZEN)))
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + id + " ne obstaja."));
    }

    /* Zavihek "Zanimivosti" turnirja - cez VSE njegove dogodke skupaj.
       Na ravni ene kategorije je tekem pogosto premalo, da bi kaj povedale,
       klub in "V stevilkah" pa tam izgubita pomen. Javno kot ostali GET-i in
       dosegljivo ze med turnirjem: gledalec je uporabnik st. 1. */
    @GetMapping("/{id}/statistika")
    public StatistikaTekmovanjaDto statistika(@PathVariable Long id) {
        return statistikaTekmovanjaStoritev.zaTurnir(id);
    }

    @GetMapping("/{id}/dogodki")
    public List<DogodekDto> dogodki(@PathVariable Long id) {
        Map<Long, StevciDogodka> stevci = povzetkiStoritev.zaDogodkeTurnirja(id);
        return dogodekRepozitorij.findByTurnirIdOrderByIdAsc(id).stream()
                .map(d -> {
                    StevciDogodka s = stevci.getOrDefault(d.getId(), StevciDogodka.PRAZNI);
                    return DogodekDto.iz(d, s.prijav(), s.odigranih(), s.vseh());
                })
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TurnirDto ustvari(@Valid @RequestBody TurnirVnos vnos) {
        return TurnirDto.iz(turnirjiStoritev.ustvari(vnos));
    }

    @PostMapping("/{id}/dogodki")
    @ResponseStatus(HttpStatus.CREATED)
    public DogodekDto dodajDogodek(@PathVariable Long id, @Valid @RequestBody DogodekVnos vnos) {
        return DogodekDto.iz(turnirjiStoritev.dodajDogodek(id, vnos));
    }

    @PostMapping("/{id}/zakljuci")
    public TurnirDto zakljuci(@PathVariable Long id) {
        return TurnirDto.iz(turnirjiStoritev.zakljuci(id));
    }
}
