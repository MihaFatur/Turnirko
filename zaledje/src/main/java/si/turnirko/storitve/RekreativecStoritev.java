/* Kdo je rekreativec in kdo tekmovalec.

   Rekreativec je igralec, ki se ni odigral treh tekem na tekmovanju ravni
   URADNO ali KLUBSKO (teza >= 0,75). Rekreativci so svoja lestvica: ni
   posteno, da dober rekreativec prehiti nekoliko slabsega igralca, ki hodi na
   clanske turnirje NTZS - teza tekmovanja to razliko sicer ublazi, a je ne
   odpravi, ker se stevilki se vedno merita na isti lestvici.

   Prehod je TRAJEN: kdor je enkrat odigral tri prava tekmovanja, se med
   rekreativce ne vraca, tudi ce potem igra samo rekreativno. Zato se stevilo
   ne meri v kaksnem oknu zadnjih mesecev, ampak cez vso zgodovino.

   Zastavica se IZPELJE iz dnevnika in se ne hrani v stanju: stevilo
   tekmovalnih tekem je stvar zgodovine, ki se ne more zmanjsati, hranjenje pa
   bi zahtevalo se eno polje, ki ga mora ponovni preracun obnoviti.

   Izpeljavo potrebujeta vsak ogled lestvice in profila, zato mora biti poceni:
   poizvedba iz dnevnika bere samo pokrivni indeks (V30, glej
   RatingZgodovinaRepozitorij.tekmovalnihTekem) in prebere ves dnevnik, njen
   izid pa se od nov. 2026 hrani v predpomnilniku, dokler ga zapis v bazo ne
   razveljavi (PredpomnilnikBranja). V transakciji, ki pise, se steje sproti -
   tam naj jo klicatelj sprozi ENKRAT za vse igralce, ki jih potrebuje. */
package si.turnirko.storitve;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

@Service
public class RekreativecStoritev {

    /* Koliko tekem na tekmovanjih ravni URADNO ali KLUBSKO naredi tekmovalca. */
    public static final int PRAG_TEKEM = 3;

    /* Ravni, ki stejejo kot "pravo tekmovanje" (teza >= 0,75). */
    private static final List<RavenTekmovanja> TEKMOVALNE =
            List.of(RavenTekmovanja.URADNO, RavenTekmovanja.KLUBSKO);

    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final PredpomnilnikBranja predpomnilnik;

    public RekreativecStoritev(RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                               PredpomnilnikBranja predpomnilnik) {
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.predpomnilnik = predpomnilnik;
    }

    /* Med danimi igralci vrne tiste, ki so (se) rekreativci. */
    @Transactional(readOnly = true)
    public Set<Long> rekreativci(Collection<Long> idjiIgralcev) {
        /* Stetje cez ves dnevnik je najdrazji del lestvice in profila (na
           kopiji produkcije ~130 ms od ~470 ms profila), odgovor pa je za
           vse zahtevke isti, dokler se dnevnik ne spremeni - zato pride iz
           predpomnilnika. Zastavica ostane IZPELJANA in se ne hrani v bazi:
           vsak zapis v bazo predpomnilnik razveljavi (PredpomnilnikBranja). */
        Map<Long, Integer> tekmovalnih = predpomnilnik.vrni(
                "tekmovalnih-tekem", null, this::prestejTekmovalne);
        Set<Long> rekreativci = new HashSet<>();
        for (Long id : idjiIgralcev) {
            if (tekmovalnih.getOrDefault(id, 0) < PRAG_TEKEM) {
                rekreativci.add(id);
            }
        }
        return rekreativci;
    }

    /* Stevilo tekmovalnih tekem po igralcu; kdor jih nima, v mapi ni. */
    private Map<Long, Integer> prestejTekmovalne() {
        Map<Long, Integer> tekmovalnih = new HashMap<>();
        for (Object[] r : zgodovinaRepozitorij.tekmovalnihTekem(
                RatingStanje.SISTEM_TURNIRKO, TEKMOVALNE)) {
            tekmovalnih.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return Map.copyOf(tekmovalnih);
    }
}
