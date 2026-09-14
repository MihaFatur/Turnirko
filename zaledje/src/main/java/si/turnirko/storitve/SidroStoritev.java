/* Starostno sidro: kje naj novinec zacne, preden o njem karkoli vemo.

   Sidro je mediana ratinga umerjenih in aktivnih igralcev istega spola in
   starosti. Novinec zacne tu in ne pri enotnih 1000 - sicer se dva novinca v
   U11, ki igrata med sabo, ustalita pri isti stevilki kot dva novinca v U19,
   kar ocitno ni res.

   Starost je po 11. clenu PST (StarostniPas.letaVSezoni), merjena na dan
   TEKME in ne danes: le tako da ponovni preracun pretekle zgodovine iste
   rezultate kot prvotni obracun.

   Tabelo drzimo v pomnilniku, ker je majhna (nekaj deset vrstic) in se
   spreminja samo z zavestnim posegom administratorja; pri obracunu bi bila
   sicer poizvedba na vsakega novinca. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StarostniPas;
import si.turnirko.modeli.StarostnoSidro;
import si.turnirko.repozitoriji.StarostnoSidroRepozitorij;

@Service
public class SidroStoritev {

    private final StarostnoSidroRepozitorij repozitorij;

    /* spol -> (starost -> vrednost); prazen, dokler ga prva uporaba ne naloZi. */
    private volatile Map<Spol, Map<Integer, Integer>> tabela;

    public SidroStoritev(StarostnoSidroRepozitorij repozitorij) {
        this.repozitorij = repozitorij;
    }

    /* Izhodiscni rating igralca za tekmo na dani dan. Ce letnice rojstva ni
       ali starost pade izven tabele, se vrnemo na enotno zacetno vrednost -
       ugibati sidro brez starosti nima smisla. */
    public int zacetniRating(Igralec igralec, LocalDate danTekme) {
        Integer sidro = sidro(igralec.getSpol(),
                StarostniPas.letaVSezoni(igralec.getDatumRojstva(), danTekme));
        return sidro != null ? sidro : TurnirkoRatingStoritev.ZACETNI_RATING;
    }

    /* Sidro za dano starost; null, kadar ga ni mogoce dolociti. Starost izven
       tabele se prilepi na najblizji rob - pri sedemdesetletniku nimamo
       podatkov, a najblizja starost pove vec kot enotnih 1000. */
    public Integer sidro(Spol spol, Integer starost) {
        if (spol == null || starost == null) {
            return null;
        }
        Map<Integer, Integer> poStarosti = tabela().get(spol);
        if (poStarosti == null || poStarosti.isEmpty()) {
            return null;
        }
        Integer tocno = poStarosti.get(starost);
        if (tocno != null) {
            return tocno;
        }
        int najblizja = poStarosti.keySet().stream()
                .min((a, b) -> Integer.compare(Math.abs(a - starost), Math.abs(b - starost)))
                .orElseThrow();
        return poStarosti.get(najblizja);
    }

    /* Znova prebere tabelo iz baze - po zavestni spremembi sidra. */
    public void osvezi() {
        tabela = null;
    }

    private Map<Spol, Map<Integer, Integer>> tabela() {
        Map<Spol, Map<Integer, Integer>> trenutna = tabela;
        if (trenutna == null) {
            trenutna = new HashMap<>();
            for (StarostnoSidro vrstica : repozitorij.findAll()) {
                trenutna.computeIfAbsent(vrstica.getSpol(), k -> new HashMap<>())
                        .put(vrstica.getStarost(), vrstica.getVrednost());
            }
            tabela = trenutna;
        }
        return trenutna;
    }
}
