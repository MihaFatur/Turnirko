/* Prijava, registracija s potrditvijo e-poste, pozabljeno geslo in lastni
   racun.

   Prijava uporablja HTTP Basic: odjemalec poslje poverilnice v glavi
   Authorization na /auth/me. Ce so veljavne, dobi svoj profil (200); sicer
   varnostni filter vrne 401. Locene "login" koncne tocke ni - preverba
   poverilnic IN branje profila se zgodita v enem klicu.

   Poti brez prijave (registracija, vpis kode, ponovno posiljanje, pozabljeno
   geslo) vracajo enak odgovor ne glede na to, ali naslov obstaja; edini
   dokaz je koda s poste. Naslov IP klicatelja gre storitvi zaradi omejitve
   stevila zahtev - za Caddyjem ga pove glava X-Forwarded-For
   (forward-headers-strategy v profilu splet). */
package si.turnirko.kontrolerji;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import si.turnirko.dto.NovoGesloVnos;
import si.turnirko.dto.PonovnoPosiljanjeVnos;
import si.turnirko.dto.PotrditevKodeVnos;
import si.turnirko.dto.PotrditevOdgovorDto;
import si.turnirko.dto.PozabljenoGesloVnos;
import si.turnirko.dto.RegistracijaOdgovorDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.SpremembaGeslaVnos;
import si.turnirko.dto.UporabnikDto;
import si.turnirko.storitve.RacuniStoritev;
import si.turnirko.storitve.RegistracijaStoritev;

@RestController
@RequestMapping("/api/v1/auth")
public class AvtentikacijaKontroler {

    private final RacuniStoritev racuniStoritev;
    private final RegistracijaStoritev registracijaStoritev;

    public AvtentikacijaKontroler(RacuniStoritev racuniStoritev,
                                  RegistracijaStoritev registracijaStoritev) {
        this.racuniStoritev = racuniStoritev;
        this.registracijaStoritev = registracijaStoritev;
    }

    /* Profil trenutno prijavljenega uporabnika. Do sem pride samo zahteva z
       veljavno prijavo (varnostni filter zavrne ostale). */
    @GetMapping("/me")
    public UporabnikDto jaz(Principal prijavljeni) {
        return racuniStoritev.profil(prijavljeni.getName());
    }

    @PostMapping("/registracija")
    public RegistracijaOdgovorDto registracija(@Valid @RequestBody RegistracijaVnos vnos,
                                               HttpServletRequest zahteva) {
        return registracijaStoritev.registriraj(vnos, zahteva.getRemoteAddr());
    }

    @PostMapping("/potrdi-eposto")
    public PotrditevOdgovorDto potrdiEposto(@Valid @RequestBody PotrditevKodeVnos vnos) {
        return registracijaStoritev.potrdiEposto(vnos);
    }

    @PostMapping("/potrdi-skrbnika")
    public PotrditevOdgovorDto potrdiSkrbnika(@Valid @RequestBody PotrditevKodeVnos vnos) {
        return registracijaStoritev.potrdiSkrbnika(vnos);
    }

    @PostMapping("/ponovno-poslji")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ponovnoPoslji(@Valid @RequestBody PonovnoPosiljanjeVnos vnos,
                              HttpServletRequest zahteva) {
        registracijaStoritev.ponovnoPoslji(vnos, zahteva.getRemoteAddr());
    }

    @PostMapping("/pozabljeno-geslo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pozabljenoGeslo(@Valid @RequestBody PozabljenoGesloVnos vnos,
                                HttpServletRequest zahteva) {
        registracijaStoritev.pozabljenoGeslo(vnos.email(), zahteva.getRemoteAddr());
    }

    @PostMapping("/novo-geslo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void novoGeslo(@Valid @RequestBody NovoGesloVnos vnos) {
        registracijaStoritev.novoGeslo(vnos);
    }

    @PostMapping("/geslo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zamenjajGeslo(Principal prijavljeni, @Valid @RequestBody SpremembaGeslaVnos vnos) {
        racuniStoritev.zamenjajGeslo(prijavljeni.getName(), vnos);
    }
}
