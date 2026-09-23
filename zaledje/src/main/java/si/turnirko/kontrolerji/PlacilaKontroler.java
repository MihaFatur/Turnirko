/* Placila (Stripe Checkout) za placljive pakete: zacetek nove placane
   registracije, nadgradnja obstojecega racuna, Stripe Billing Portal in
   webhook, ki edini dejansko ustvari/posodobi racun in narocnino - glej
   PlacilaStoritev. */
package si.turnirko.kontrolerji;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.NadgradnjaVnos;
import si.turnirko.dto.PlacanaRegistracijaVnos;
import si.turnirko.dto.PlacilnaSejaDto;
import si.turnirko.storitve.PlacilaStoritev;

@RestController
@RequestMapping("/api/v1/placila")
public class PlacilaKontroler {

    private final PlacilaStoritev placila;

    public PlacilaKontroler(PlacilaStoritev placila) {
        this.placila = placila;
    }

    @PostMapping("/registracija")
    public PlacilnaSejaDto registracija(@Valid @RequestBody PlacanaRegistracijaVnos vnos) {
        return placila.zacniRegistracijskoPlacilo(vnos);
    }

    @PostMapping("/nadgradnja")
    public PlacilnaSejaDto nadgradnja(@Valid @RequestBody NadgradnjaVnos vnos) {
        return placila.zacniNadgradnjo(vnos);
    }

    @PostMapping("/portal")
    public PlacilnaSejaDto portal() {
        return placila.zacniPortal();
    }

    /* Stripe poslje surovo telo (podpis se preverja NAD razclenjenim JSON-om),
       zato je vnos navaden String in ne DTO. */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody String telo, @RequestHeader("Stripe-Signature") String podpis) {
        placila.obdelajDogodek(telo, podpis);
    }
}
