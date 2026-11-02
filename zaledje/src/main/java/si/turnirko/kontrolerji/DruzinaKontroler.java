/* Druzina (DruzinaStoritev, V59).

   Dva sklopa poti z razlicno varnostjo (pravila v VarnostneNastavitve, pred
   splosnim »GET je javen«):
   - /druzina (prijava): racunov pregled, zahteva za povezavo, umik; strezniku
     sporoca samo id igralca, ostalo (paket, pas, meja) preveri storitev;
   - /druzina/admin in odlocitve (samo ADMIN): seznam zahtev s starostjo,
     potrditev, zavrnitev, razveza. Izpis nosi datum rojstva, ker admin po njem
     odloca. */
package si.turnirko.kontrolerji;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.DruzinaDto;
import si.turnirko.storitve.DruzinaStoritev;

@RestController
@RequestMapping("/api/v1/druzina")
public class DruzinaKontroler {

    private final DruzinaStoritev druzina;

    public DruzinaKontroler(DruzinaStoritev druzina) {
        this.druzina = druzina;
    }

    @GetMapping
    public DruzinaDto.Pregled pregled() {
        return druzina.pregled();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DruzinaDto.Povezava zahtevaj(@Valid @RequestBody DruzinaDto.Zahteva vnos) {
        return druzina.zahtevaj(vnos.idIgralec());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void umakni(@PathVariable Long id) {
        druzina.umakni(id);
    }

    @GetMapping("/admin")
    public List<DruzinaDto.Admin> zaAdmina() {
        return druzina.zaAdmina();
    }

    @PostMapping("/{id}/potrdi")
    public DruzinaDto.Admin potrdi(@PathVariable Long id) {
        return druzina.potrdi(id);
    }

    @PostMapping("/{id}/zavrni")
    public DruzinaDto.Admin zavrni(@PathVariable Long id) {
        return druzina.zavrni(id);
    }

    @PostMapping("/{id}/razveze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void razveze(@PathVariable Long id) {
        druzina.razveze(id);
    }
}
