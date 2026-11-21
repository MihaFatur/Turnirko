package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.dto.RazlicicaMrezeDto;
import si.turnirko.modeli.MrezaStevkZapis;

public interface MrezaStevkRepozitorij extends JpaRepository<MrezaStevkZapis, Long> {

    /* Razlicica, ki jo bralnik nalozi ob zagonu: zadnja sprejeta, ki je admin ni umaknil. */
    Optional<MrezaStevkZapis> findFirstBySprejetaTrueAndUmaknjenaObIsNullOrderByIdDesc();

    /* »Vrni prejsnjo«: zadnja sprejeta, neumaknjena pred dano. */
    Optional<MrezaStevkZapis> findFirstBySprejetaTrueAndUmaknjenaObIsNullAndIdLessThanOrderByIdDesc(Long id);

    /* Cas zadnjega ucenja (tudi zavrnjenega ali prekinjenega); null, ce ga se ni bilo.
       Stevke po njem so nove. */
    @Query("SELECT max(m.ustvarjenoOb) FROM MrezaStevkZapis m")
    LocalDateTime zadnjeUcenjeOb();

    /* Zgodovina brez utezi (~320 kB vsaka razlicica). */
    @Query("""
            SELECT new si.turnirko.dto.RazlicicaMrezeDto(m.id, m.ustvarjenoOb, m.nacin, m.vzorcev,
                   m.preizkusnih, m.natancnostPrej, m.natancnost, m.mnistPrej, m.mnist, m.zlatih,
                   m.zlatiPrej, m.zlati, m.trajanjeMs, m.sprejeta,
                   CASE WHEN m.utezi IS NULL THEN true ELSE false END, m.razlog, m.umaknjenaOb)
            FROM MrezaStevkZapis m ORDER BY m.id DESC
            """)
    List<RazlicicaMrezeDto> zgodovina(Pageable stran);

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
