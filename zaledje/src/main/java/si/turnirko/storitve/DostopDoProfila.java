/* Kdo sme videti zasebni del profila: igralec sam in administrator.

   Pravilo zivi tu in ne v posamezni storitvi, ker ga potrebujeta dva pogleda
   (zasebne analize in napoved tekme), pozneje pa jih bo vec. Dve kopiji istega
   pogoja bi se scasoma razsli in en pogled bi pokazal vec od drugega.

   Varnostna veriga (VarnostneNastavitve) zahteva samo PRIJAVO - o tem, cigav
   je zapis, ne ve nicesar, zato je lastnistvo preverjeno sele tu. */
package si.turnirko.storitve;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Service
public class DostopDoProfila {

    private final UporabnikRepozitorij uporabnikRepozitorij;

    public DostopDoProfila(UporabnikRepozitorij uporabnikRepozitorij) {
        this.uporabnikRepozitorij = uporabnikRepozitorij;
    }

    /* Administrator sme vse; igralec samo svoj profil.
       "prijavnoIme" je ime prijavljenega uporabnika iz varnostnega konteksta. */
    @Transactional(readOnly = true)
    public void preveriLastnistvo(Long idIgralec, String prijavnoIme) {
        Uporabnik u = uporabnikRepozitorij.najdiZVsem(prijavnoIme)
                .orElseThrow(() -> new PrepovedanoIzjema("Prijavljeni uporabnik ne obstaja."));
        if (u.getVloga() == Vloga.ADMIN) {
            return;
        }
        if (!u.jePotrjenIgralec()) {
            throw new PrepovedanoIzjema(
                    "Racun se ni potrjen, zato zasebna statistika ni na voljo.");
        }
        if (!u.getIgralec().getId().equals(idIgralec)) {
            throw new PrepovedanoIzjema("Zasebno statistiko lahko vidi samo igralec sam.");
        }
    }
}
