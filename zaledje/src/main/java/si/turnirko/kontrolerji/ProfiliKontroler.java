/* Profil igralca s statistiko. Javni del sme brati vsak, zasebnega pa samo
   igralec sam ali administrator - lastnistvo preveri DostopDoProfila, ker
   varnostna veriga pozna samo vlogo, ne pa cigav je zapis. */
package si.turnirko.kontrolerji;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.NapovedTekmeDto;
import si.turnirko.dto.ProfilDto;
import si.turnirko.dto.ProfilZasebnoDto;
import si.turnirko.storitve.NapovedTekmeStoritev;
import si.turnirko.storitve.ProfilStoritev;

@RestController
@RequestMapping("/api/v1/igralci")
public class ProfiliKontroler {

    private final ProfilStoritev profilStoritev;
    private final NapovedTekmeStoritev napovedStoritev;

    public ProfiliKontroler(ProfilStoritev profilStoritev,
                            NapovedTekmeStoritev napovedStoritev) {
        this.profilStoritev = profilStoritev;
        this.napovedStoritev = napovedStoritev;
    }

    @GetMapping("/{id}/profil")
    public ProfilDto profil(@PathVariable Long id) {
        return profilStoritev.profil(id);
    }

    /* Do sem pride samo prijavljena zahteva (varnostna veriga). */
    @GetMapping("/{id}/profil/zasebno")
    public ProfilZasebnoDto zasebno(@PathVariable Long id, Principal prijavljeni) {
        return profilStoritev.zasebno(id, prijavljeni.getName());
    }

    /* Kaj bi prinesla tekma proti izbranemu nasprotniku, ce bi bila zdaj.
       Stoji pod /profil, ker je del profila in ne samostojen pogled - in je
       zato tudi zaprta za istim pravilom kot zasebne analize. */
    @GetMapping("/{id}/profil/napoved")
    public NapovedTekmeDto napoved(@PathVariable Long id,
                                   @RequestParam Long nasprotnik,
                                   @RequestParam(required = false) Integer nizov,
                                   Principal prijavljeni) {
        return napovedStoritev.napoved(id, nasprotnik, nizov, prijavljeni.getName());
    }
}
