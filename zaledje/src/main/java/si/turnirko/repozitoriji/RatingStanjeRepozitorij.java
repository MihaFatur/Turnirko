/* Dostop do trenutnih vrednosti ratingov. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.RatingStanje;

public interface RatingStanjeRepozitorij extends JpaRepository<RatingStanje, Long> {

    Optional<RatingStanje> findByIgralecIdAndSistem(Long idIgralec, String sistem);

    List<RatingStanje> findByIgralecIdInAndSistem(List<Long> idjiIgralcev, String sistem);

    /* Ratingi danih igralcev kot vrstice [idIgralca, vrednost]. Projekcija
       (in ne entitete), ker se bere tudi izven transakcije - tako ni nobene
       lene povezave, ki bi lahko pocila. */
    @Query("""
            SELECT i.id, r.vrednost
            FROM RatingStanje r JOIN r.igralec i
            WHERE r.sistem = :sistem AND i.id IN :idjiIgralcev
            """)
    List<Object[]> ratingiIgralcev(@Param("idjiIgralcev") List<Long> idjiIgralcev,
                                   @Param("sistem") String sistem);

    /* Ratingi vseh neaarhiviranih igralcev v danem sistemu, za uvrstitev in
       percentil na profilu. Vrne vrstice [idIgralca, idKluba (lahko null),
       vrednost]; "left join" je nujen, ker igralec morda ni v klubu. */
    @Query("""
            SELECT i.id, k.id, r.vrednost
            FROM RatingStanje r JOIN r.igralec i LEFT JOIN i.klub k
            WHERE r.sistem = :sistem AND i.arhiviran = false
            """)
    List<Object[]> vsiRatingi(@Param("sistem") String sistem);
}
