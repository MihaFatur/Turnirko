/* Prijava, registracija in lastni racun.

   Prijava uporablja HTTP Basic: odjemalec poslje poverilnice v glavi
   Authorization na /auth/me. Ce so veljavne, dobi svoj profil (200); sicer
   varnostni filter vrne 401. Locene "login" koncne tocke ni - preverba
   poverilnic IN branje profila se zgodita v enem klicu.

   Registracija je edina mutacija brez prijave: ustvari racun igralca v
   stanju CAKA, ki sam po sebi ne daje nobene pravice, dokler ga
   administrator ne potrdi in poveze z zapisom igralca. */
package si.turnirko.kontrolerji;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.SpremembaGeslaVnos;
import si.turnirko.dto.UporabnikDto;
import si.turnirko.storitve.RacuniStoritev;

@RestController
@RequestMapping("/api/v1/auth")
public class AvtentikacijaKontroler {

    private final RacuniStoritev racuniStoritev;

    public AvtentikacijaKontroler(RacuniStoritev racuniStoritev) {
        this.racuniStoritev = racuniStoritev;
    }

    /* Profil trenutno prijavljenega uporabnika. Do sem pride samo zahteva z
       veljavno prijavo (varnostni filter zavrne ostale). */
    @GetMapping("/me")
    public UporabnikDto jaz(Principal prijavljeni) {
        return racuniStoritev.profil(prijavljeni.getName());
    }

    @PostMapping("/registracija")
    @ResponseStatus(HttpStatus.CREATED)
    public UporabnikDto registracija(@Valid @RequestBody RegistracijaVnos vnos) {
        return racuniStoritev.registriraj(vnos);
    }

    @PostMapping("/geslo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zamenjajGeslo(Principal prijavljeni, @Valid @RequestBody SpremembaGeslaVnos vnos) {
        racuniStoritev.zamenjajGeslo(prijavljeni.getName(), vnos);
    }
}
