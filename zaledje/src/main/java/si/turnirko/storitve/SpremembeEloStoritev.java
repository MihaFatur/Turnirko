/* Podatki o klubskem ELO za prikaz ob tekmah: sprememba ("+16 / -16") in
   rating igralca PRED tekmo. Za odigrane tekme oboje prebere iz dnevnika,
   za igralce brez zapisa (npr. tekma se ni odigrana) pa ponudi trenutni
   rating. Uporabljajo jo prikaz mreze in pregled "1 na 1". */
package si.turnirko.storitve;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.modeli.RatingStanje;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

@Service
public class SpremembeEloStoritev {

    private final RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    private final RatingStanjeRepozitorij stanjeRepozitorij;

    public SpremembeEloStoritev(RatingZgodovinaRepozitorij zgodovinaRepozitorij,
                                RatingStanjeRepozitorij stanjeRepozitorij) {
        this.zgodovinaRepozitorij = zgodovinaRepozitorij;
        this.stanjeRepozitorij = stanjeRepozitorij;
    }

    /* Sprememba ratinga ob tekmi in rating pred tekmo (za enega igralca). */
    public record ObTekmi(int sprememba, int ratingPred) {}

    /* Preslikava idTekme -> (idIgralca -> ObTekmi). Tekme brez obracuna
       (npr. prosti prehodi) v preslikavi ni; klic tam vrne null. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, ObTekmi>> zaTekme(Collection<Long> idjiTekem) {
        if (idjiTekem.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, ObTekmi>> poTekmah = new HashMap<>();
        for (Object[] vrstica : zgodovinaRepozitorij
                .spremembeZaTekme(idjiTekem, RatingStanje.SISTEM_KLUBSKI_ELO)) {
            Long idTekme = (Long) vrstica[0];
            Long idIgralca = (Long) vrstica[1];
            int sprememba = ((Number) vrstica[2]).intValue();
            int novaVrednost = ((Number) vrstica[3]).intValue();
            poTekmah.computeIfAbsent(idTekme, k -> new HashMap<>())
                    .put(idIgralca, new ObTekmi(sprememba, novaVrednost - sprememba));
        }
        return poTekmah;
    }

    /* Isto za posamicne tekme ligaskih srecanj: idTekmeSrecanja -> (idIgralca
       -> sprememba). Dnevnik hrani ligaske tekme v locenem stolpcu, zato tudi
       locena poizvedba; vrednosti pred tekmo tu ne potrebujemo. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, Integer>> zaTekmeSrecanja(Collection<Long> idjiTekem) {
        if (idjiTekem.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, Integer>> poTekmah = new HashMap<>();
        for (Object[] vrstica : zgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(idjiTekem, RatingStanje.SISTEM_KLUBSKI_ELO)) {
            Long idTekme = ((Number) vrstica[0]).longValue();
            Long idIgralca = ((Number) vrstica[1]).longValue();
            int sprememba = ((Number) vrstica[2]).intValue();
            poTekmah.computeIfAbsent(idTekme, k -> new HashMap<>()).put(idIgralca, sprememba);
        }
        return poTekmah;
    }

    /* Trenutni klubski ELO danih igralcev (idIgralca -> rating). Za tekme, ki
       se niso odigrane, je to rating, s katerim igralec vstopa v tekmo. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> trenutniRatingi(Collection<Long> idjiIgralcev) {
        if (idjiIgralcev.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> ratingi = new HashMap<>();
        for (RatingStanje stanje : stanjeRepozitorij
                .findByIgralecIdInAndSistem(List.copyOf(idjiIgralcev), RatingStanje.SISTEM_KLUBSKI_ELO)) {
            ratingi.put(stanje.getIgralec().getId(), stanje.getVrednost());
        }
        return ratingi;
    }
}
