package si.turnirko.repozitoriji;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.MrezaStevkZapis;

public interface MrezaStevkRepozitorij extends JpaRepository<MrezaStevkZapis, Long> {

    Optional<MrezaStevkZapis> findFirstBySprejetaTrueOrderByIdDesc();

    Optional<MrezaStevkZapis> findFirstByOrderByIdDesc();

    /* Hramba: izbrise starejse razlicice (sprejete ali zavrnjene) razen `ohrani`
       najnovejsih in razen razlicice ohraniId, ki jo bralnik ta trenutek
       uporablja (-1, ce uporablja vgrajeno). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM mreza_stevk
            WHERE sprejeta = :sprejeta
              AND id <> :ohraniId
              AND id NOT IN (SELECT id FROM mreza_stevk WHERE sprejeta = :sprejeta
                             ORDER BY id DESC LIMIT :ohrani)
            """, nativeQuery = true)
    int izbrisiStarejse(@Param("sprejeta") boolean sprejeta, @Param("ohrani") int ohrani,
                        @Param("ohraniId") long ohraniId);
}
