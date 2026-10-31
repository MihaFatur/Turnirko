/* Potisna obvestila (PotisnaObvestilaStoritev, V60).

   - GET /push/kljuc je javen (kot vsi GET): javni kljuc VAPID ni skrivnost, brskalnik ga
     potrebuje ob narocnini; prazen pomeni, da obvestila na strezniku niso vklopljena;
   - ostalo zahteva prijavo (pravilo v VarnostneNastavitve, pred splosnim »GET je javen«);
     paket Navijac ali Premium preveri storitev. */
package si.turnirko.kontrolerji;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.storitve.PotisnaObvestilaStoritev;

@RestController
@RequestMapping("/api/v1/push")
public class PotisnaObvestilaKontroler {

    /* Oblika PushSubscription.toJSON() v brskalniku: endpoint, rok (ignoriramo) in kljuca. */
    public record NapravaVnos(
            @NotBlank @Size(max = 600) String endpoint,
            @NotNull @Valid Kljuca keys
    ) {
        public record Kljuca(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}
    }

    public record IzpisVnos(@NotBlank @Size(max = 600) String endpoint) {}

    public record KljucOdgovor(String kljuc) {}

    public record PreizkusOdgovor(int poslanih) {}

    private final PotisnaObvestilaStoritev potisna;

    public PotisnaObvestilaKontroler(PotisnaObvestilaStoritev potisna) {
        this.potisna = potisna;
    }

    @GetMapping("/kljuc")
    public KljucOdgovor kljuc() {
        return new KljucOdgovor(potisna.javniKljucAliPrazno());
    }

    @PostMapping("/naprava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vpisi(@Valid @RequestBody NapravaVnos vnos) {
        potisna.vpisi(vnos.endpoint(), vnos.keys().p256dh(), vnos.keys().auth());
    }

    @PostMapping("/izpis")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void izpisi(@Valid @RequestBody IzpisVnos vnos) {
        potisna.izpisi(vnos.endpoint());
    }

    @PostMapping("/preizkus")
    public PreizkusOdgovor preizkus() {
        return new PreizkusOdgovor(potisna.preizkus());
    }
}
