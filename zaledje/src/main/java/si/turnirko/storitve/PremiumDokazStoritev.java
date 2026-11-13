/* Socialni dokaz v oglasu Igralec Premium: koliko igralcev ze placuje Premium.

   Oglas (vmesnik: komponente/PremiumOglas) trdi "N igralcev iz tvojega kluba ze
   ima Premium". Ta stevilka je resnica in ne okras, zato pride od tu, in kadar
   je ni ali je premajhna, oglas vrstice ne pokaze - izmisljena stevilka bi bila
   laz o izdelku, ki ga clovek sam kupuje.

   Meje, ki jih ne razbij:
   - ime igralca s Premium NE gre navzven, samo stevilo: placnik je osebna
     odlocitev, javni DTO-ji o nikomer ne povedo paketa;
   - pod mejo (NAJMANJ) se stevilo ne pokaze. Pri enem ali dveh placnikih v
     klubu bi ga brali kot "tisti dva" (v malem klubu se vsi poznajo), za
     gledalca pa bi bilo brez teze - "2 igralca" ni dokaz, ampak priznanje;
   - najprej se poskusi klub prijavljenega igralca, sele nato vsi igralci. Klub
     je moc dokaz ("moji soigralci"), zato ima prednost, a ko je pod mejo, je
     posten skupni podatek boljsi od praznine;
   - pot je javna (kot vsak GET), racun se bere iz glave Authorization, kot pri
     GET /domov/lige. */
package si.turnirko.storitve;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PremiumDokazDto;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.NarocninaRepozitorij;

@Service
public class PremiumDokazStoritev {

    /* Najmanjse stevilo placnikov, pri katerem se vrstica sploh pokaze. */
    public static final int NAJMANJ = 3;

    private final NarocninaRepozitorij narocninaRepozitorij;
    private final LastnistvoStoritev lastnistvo;

    public PremiumDokazStoritev(NarocninaRepozitorij narocninaRepozitorij,
                                LastnistvoStoritev lastnistvo) {
        this.narocninaRepozitorij = narocninaRepozitorij;
        this.lastnistvo = lastnistvo;
    }

    @Transactional(readOnly = true)
    public PremiumDokazDto dokaz() {
        /* Steje baza (COUNT) in ne aplikacija: oglas vprasa ob vsakem prikazu,
           nalaganje vseh narocnin z racuni, igralci in klubi pa raste s
           stevilom placnikov. */
        LocalDateTime zdaj = LocalDateTime.now();
        Long idKluba = klubPrijavljenega();
        if (idKluba != null) {
            long izKluba = narocninaRepozitorij.prestejVeljavnePremiumIgralceKluba(idKluba, zdaj);
            if (izKluba >= NAJMANJ) {
                return new PremiumDokazDto((int) izKluba, true);
            }
        }
        long vseh = narocninaRepozitorij.prestejVeljavnePremiumIgralce(zdaj);
        if (vseh >= NAJMANJ) {
            return new PremiumDokazDto((int) vseh, false);
        }
        return PremiumDokazDto.PRAZEN;
    }

    /* Klub igralca, s katerim je prijavljeni racun povezan; null za gosta, za
       racun brez povezave in za igralca brez kluba. */
    private Long klubPrijavljenega() {
        return idKlubaRacuna(lastnistvo.trenutni());
    }

    private static Long idKlubaRacuna(Uporabnik u) {
        if (u == null) {
            return null;
        }
        Igralec igralec = u.getIgralec();
        Klub klub = igralec == null ? null : igralec.getKlub();
        return klub == null ? null : klub.getId();
    }
}
