/* Profil igralca s statistiko. Javni del sme brati vsak, zasebnega pa samo
   igralec sam ali administrator - lastnistvo preveri ProfilStoritev, ker
   varnostna veriga pozna samo vlogo, ne pa cigav je zapis. */
package si.turnirko.kontrolerji;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.ProfilDto;
import si.turnirko.dto.ProfilZasebnoDto;
import si.turnirko.storitve.ProfilStoritev;

@RestController
@RequestMapping("/api/v1/igralci")
public class ProfiliKontroler {

    private final ProfilStoritev profilStoritev;

    public ProfiliKontroler(ProfilStoritev profilStoritev) {
        this.profilStoritev = profilStoritev;
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
}
