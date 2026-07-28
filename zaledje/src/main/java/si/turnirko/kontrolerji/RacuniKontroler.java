/* Administracija racunov igralcev: pregled zahtev, potrditev (s povezavo na
   igralca), zavrnitev, vklop/izklop in ponastavitev gesla.
   Vse te poti so mutacije oz. niso GET pod /api/**, zato jih varnostna
   veriga ze omejuje na administratorja. */
package si.turnirko.kontrolerji;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.NastavitevGeslaVnos;
import si.turnirko.dto.PotrditevOrganizatorjaVnos;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RacunIgralcaDto;
import si.turnirko.storitve.RacuniStoritev;

@RestController
@RequestMapping("/api/v1/racuni")
public class RacuniKontroler {

    private final RacuniStoritev racuniStoritev;

    public RacuniKontroler(RacuniStoritev racuniStoritev) {
        this.racuniStoritev = racuniStoritev;
    }

    /* Seznam racunov igralcev. Je GET, zato bi ga veriga pustila skozi vsem -
       a vsebuje e-poste, zato ga izrecno zapremo na administratorja. */
    @GetMapping
    public List<RacunIgralcaDto> seznam() {
        return racuniStoritev.racuni();
    }

    @PostMapping("/{id}/potrdi")
    public RacunIgralcaDto potrdi(@PathVariable Long id,
                                  @Valid @RequestBody PotrditevRacunaVnos vnos) {
        return racuniStoritev.potrdi(id, vnos);
    }

    /* Potrditev organizatorja: mu dodeli vlogo in (neobvezni) klub. */
    @PostMapping("/{id}/potrdi-organizatorja")
    public RacunIgralcaDto potrdiOrganizatorja(@PathVariable Long id,
                                               @RequestBody PotrditevOrganizatorjaVnos vnos) {
        return racuniStoritev.potrdiOrganizatorja(id, vnos.idKlub());
    }

    @PostMapping("/{id}/zavrni")
    public RacunIgralcaDto zavrni(@PathVariable Long id) {
        return racuniStoritev.zavrni(id);
    }

    @PostMapping("/{id}/aktiven")
    public RacunIgralcaDto nastaviAktiven(@PathVariable Long id, @RequestParam boolean vrednost) {
        return racuniStoritev.nastaviAktiven(id, vrednost);
    }

    /* Izbrise racun (npr. zavrnjeno ali testno registracijo) in s tem
       sprosti e-posto za novo registracijo. Igralca in njegovih tekem ne
       briše - te ostanejo nedotaknjene. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zbrisi(@PathVariable Long id) {
        racuniStoritev.zbrisi(id);
    }

    /* Vrne novo nakljucno geslo, ki ga administrator izroci igralcu -
       shranjena je le zgostitev, zato ga kasneje ni mogoce vec prebrati. */
    @PostMapping("/{id}/ponastavi-geslo")
    public Map<String, String> ponastaviGeslo(@PathVariable Long id) {
        return Map.of("geslo", racuniStoritev.ponastaviGeslo(id));
    }

    /* Administrator nastavi geslo po svoji izbiri in ga izroci igralcu.
       Shranjena je le zgostitev, zato ga kasneje ni mogoce vec prebrati. */
    @PostMapping("/{id}/geslo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void nastaviGeslo(@PathVariable Long id,
                             @Valid @RequestBody NastavitevGeslaVnos vnos) {
        racuniStoritev.nastaviGeslo(id, vnos.geslo());
    }
}
