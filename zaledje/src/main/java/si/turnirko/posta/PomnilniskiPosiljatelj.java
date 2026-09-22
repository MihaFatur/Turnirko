/* Testni nacin (turnirko.posta.nacin=pomnilnik): sporocila se hranijo v
   pomnilniku, da test prebere kodo iz besedila - v bazi je le zgostitev in
   drugace do nje ni poti. */
package si.turnirko.posta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnirko.posta.nacin", havingValue = "pomnilnik")
public class PomnilniskiPosiljatelj implements PosiljateljPoste {

    public record Sporocilo(String prejemnik, String zadeva, String besedilo) {}

    private final List<Sporocilo> poslana = new ArrayList<>();

    @Override
    public synchronized void poslji(String prejemnik, String zadeva, String besedilo) {
        poslana.add(new Sporocilo(prejemnik, zadeva, besedilo));
    }

    /* Testna transakcija se ob koncu razveljavi, zato "po potrditvi" nikoli
       ne pride - sporocilo se shrani takoj. */
    @Override
    public boolean odloziDoPotrditve() {
        return false;
    }

    /* Zadnje sporocilo za naslov (ne glede na velikost crk). */
    public synchronized Optional<Sporocilo> zadnje(String prejemnik) {
        for (int i = poslana.size() - 1; i >= 0; i--) {
            if (poslana.get(i).prejemnik().equalsIgnoreCase(prejemnik)) {
                return Optional.of(poslana.get(i));
            }
        }
        return Optional.empty();
    }

    public synchronized List<Sporocilo> vsa() {
        return List.copyOf(poslana);
    }

    public synchronized void pocisti() {
        poslana.clear();
    }
}
