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

    void deleteBySrecanjeId(Long idSrecanje);

    long countBySrecanjeId(Long idSrecanje);
}
