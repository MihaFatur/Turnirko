/* Testni nacin (turnirko.posta.nacin=pomnilnik): sporocila se hranijo v
   pomnilniku, da test prebere kodo iz besedila - v bazi je le zgostitev in
   drugace do nje ni poti.

   Za teste dostave mnozicnih obvestil zna odpovedati kot pravi streznik:
   zavrniti naslov (zavrni) ali biti nedosegljiv (kanalNeDela). Oboje velja
   samo za sinhrono dostavo (dostavi) in ga pocisti() ponastavi. */
package si.turnirko.posta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnirko.posta.nacin", havingValue = "pomnilnik")
public class PomnilniskiPosiljatelj implements PosiljateljPoste {

    public record Sporocilo(String prejemnik, String zadeva, String besedilo, String html,
                            Map<String, String> glave) {}

    private final List<Sporocilo> poslana = new ArrayList<>();
    private final Set<String> zavrnjeni = new HashSet<>();
    private boolean kanalNeDela;

    @Override
    public synchronized void poslji(String prejemnik, String zadeva, String besedilo) {
        poslana.add(new Sporocilo(prejemnik, zadeva, besedilo, null, Map.of()));
    }

    @Override
    public synchronized void poslji(String prejemnik, SporocilaPoste.Besedilo b) {
        poslana.add(new Sporocilo(prejemnik, b.zadeva(), b.telo(), b.html(), b.glave()));
    }

    @Override
    public synchronized IzidDostave dostavi(String prejemnik, SporocilaPoste.Besedilo b) {
        if (kanalNeDela) {
            return IzidDostave.KANAL_NE_DELA;
        }
        if (zavrnjeni.contains(prejemnik.toLowerCase(Locale.ROOT))) {
            return IzidDostave.ZAVRNJENO;
        }
        poslji(prejemnik, b);
        return IzidDostave.POSLANO;
    }

    /* Testna transakcija se ob koncu razveljavi, zato "po potrditvi" nikoli
       ne pride - sporocilo se shrani takoj. */
    @Override
    public boolean odloziDoPotrditve() {
        return false;
    }

    /* Od zdaj streznik sporocila za ta naslov zavrne (do pocisti()). */
    public synchronized void zavrni(String prejemnik) {
        zavrnjeni.add(prejemnik.toLowerCase(Locale.ROOT));
    }

    /* Streznik poste je nedosegljiv (do pocisti() ali kanalNeDela(false)). */
    public synchronized void kanalNeDela(boolean neDela) {
        kanalNeDela = neDela;
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

    /* Pobrise poslana sporocila in ponastavi odpovedi. */
    public synchronized void pocisti() {
        poslana.clear();
        zavrnjeni.clear();
        kanalNeDela = false;
    }
}
