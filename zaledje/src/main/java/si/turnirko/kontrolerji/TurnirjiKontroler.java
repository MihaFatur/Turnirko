/* Koncne tocke za turnirje in njihove dogodke. */
package si.turnirko.kontrolerji;

import java.util.List;

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
import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.storitve.TurnirjiStoritev;

@RestController
@RequestMapping("/api/v1/turnirji")
public class TurnirjiKontroler {

    private final TurnirjiStoritev turnirjiStoritev;
    private final TurnirRepozitorij turnirRepozitorij;
    private final DogodekRepozitorij dogodekRepozitorij;

    public TurnirjiKontroler(TurnirjiStoritev turnirjiStoritev,
                             TurnirRepozitorij turnirRepozitorij,
                             DogodekRepozitorij dogodekRepozitorij) {
        this.turnirjiStoritev = turnirjiStoritev;
        this.turnirRepozitorij = turnirRepozitorij;
        this.dogodekRepozitorij = dogodekRepozitorij;
    }

    @GetMapping
    public List<TurnirDto> seznam() {
        return turnirRepozitorij.najdiVseSKrajem()
                .stream().map(TurnirDto::iz).toList();
    }

    @GetMapping("/{id}")
    public TurnirDto najdi(@PathVariable Long id) {
        return turnirRepozitorij.najdiSKrajem(id).map(TurnirDto::iz)
                .orElseThrow(() -> new NiNajdenoIzjema("Turnir z id " + id + " ne obstaja."));
    }

    @GetMapping("/{id}/dogodki")
    public List<DogodekDto> dogodki(@PathVariable Long id) {
        return dogodekRepozitorij.findByTurnirIdOrderByImeAsc(id)
                .stream().map(DogodekDto::iz).toList();
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
