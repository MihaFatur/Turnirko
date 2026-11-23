/* Dostop do postav srecanja. Igralec se nalozi vnaprej za DTO. */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.PostavaSrecanja;

public interface PostavaSrecanjaRepozitorij extends JpaRepository<PostavaSrecanja, Long> {

    @Query("""
            SELECT p FROM PostavaSrecanja p JOIN FETCH p.igralec
            WHERE p.srecanje.id = :idSrecanje
            ORDER BY p.stran, p.pozicija
            """)
    List<PostavaSrecanja> najdiZaSrecanje(Long idSrecanje);

    /* Igralci v postavah srecanj ekipnih tekem turnirja (stran Mize: kdo igra):
       [idTekma, stran, idIgralec, ime, priimek]. */
    @Query("""
            SELECT s.tekma.id, p.stran, i.id, i.ime, i.priimek
            FROM PostavaSrecanja p JOIN p.srecanje s JOIN p.igralec i
            WHERE s.tekma.id IN :idjiTekem
            """)
    List<Object[]> osebePostavZaTekme(java.util.Collection<Long> idjiTekem);

    void deleteBySrecanjeId(Long idSrecanje);

    long countBySrecanjeId(Long idSrecanje);
}
