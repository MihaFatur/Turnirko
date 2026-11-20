package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.StanjeBranja;

public interface BranjeListkaRepozitorij extends JpaRepository<BranjeListka, Long> {

    /* Branje s turnirjem (lastnistvo) in tekmo. */
    @Query("""
            SELECT b FROM BranjeListka b
            JOIN FETCH b.turnir
            LEFT JOIN FETCH b.tekma
            WHERE b.id = :id
            """)
    Optional<BranjeListka> najdiZTurnirjem(@Param("id") Long id);

    /* Vsa branja turnirja, najnovejsa prva (vrsta na strani Branje listkov). */
    @Query("""
            SELECT b FROM BranjeListka b
            LEFT JOIN FETCH b.tekma
            WHERE b.turnir.id = :idTurnir
            ORDER BY b.ustvarjenoOb DESC, b.id DESC
            """)
    List<BranjeListka> najdiZaTurnir(@Param("idTurnir") Long idTurnir);

    /* Koliko fotografij je uporabnik poslal od danega trenutka (omejitev hitrosti,
       vsako branje stane). */
    long countByNalozilIdAndUstvarjenoObAfter(Long idNalozil, LocalDateTime od);

    /* Branja, ki so ostala v vrsti ob ponovnem zagonu streznika (nit, ki jih je
       brala, ne obstaja vec). */
    List<BranjeListka> findByStanje(StanjeBranja stanje);

    /* Branja s fotografijo, ki je starejsa od 30 dni po turnirju. Pogoja sta
       dva: datum turnirja (vecdnevni po zadnjem dnevu) in cas nalaganja, ker se
       listki vcasih vnasajo sele dneve po turnirju. */
    @Query("""
            SELECT b.id FROM BranjeListka b JOIN b.turnir t
            WHERE b.slikaIzbrisanaOb IS NULL
              AND COALESCE(t.datumKonca, t.datumZacetka) < :mejaDatuma
              AND b.ustvarjenoOb < :mejaCasa
            """)
    List<Long> zaIzbrisSlike(@Param("mejaDatuma") LocalDate mejaDatuma,
                             @Param("mejaCasa") LocalDateTime mejaCasa);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BranjeListka b SET b.slikaIzbrisanaOb = :ob WHERE b.id IN :idji")
    int oznaciIzbrisaneSlike(@Param("idji") List<Long> idji, @Param("ob") LocalDateTime ob);
}
