package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.StanjeBranja;

public interface BranjeListkaRepozitorij extends JpaRepository<BranjeListka, Long> {

    /* Branja obdobja za meritev (od vkljucno, do izkljucno), po casu nastanka.
       Klicatelj omeji stevilo vrstic (stran), da obdobje ne napolni pomnilnika. */
    @Query("""
            SELECT new si.turnirko.repozitoriji.VrsticaMeritveBranja(t.id, t.ime, t.datumZacetka,
                b.stanje, b.prepoznava, b.prebrano, b.potrjeno, b.popravljeno, b.pripravljen,
                b.kontrola, b.casPregledaMs, b.nacinPotrditve, b.model)
            FROM BranjeListka b JOIN b.turnir t
            WHERE b.ustvarjenoOb >= :od AND b.ustvarjenoOb < :doo
            ORDER BY b.ustvarjenoOb, b.id
            """)
    List<VrsticaMeritveBranja> zaMeritev(@Param("od") LocalDateTime od, @Param("doo") LocalDateTime doo,
                                         Pageable stran);

    @Query("""
            SELECT new si.turnirko.repozitoriji.VrsticaMeritveBranja(t.id, t.ime, t.datumZacetka,
                b.stanje, b.prepoznava, b.prebrano, b.potrjeno, b.popravljeno, b.pripravljen,
                b.kontrola, b.casPregledaMs, b.nacinPotrditve, b.model)
            FROM BranjeListka b JOIN b.turnir t
            WHERE b.ustvarjenoOb >= :od AND b.ustvarjenoOb < :doo AND t.id = :idTurnir
            ORDER BY b.ustvarjenoOb, b.id
            """)
    List<VrsticaMeritveBranja> zaMeritevTurnirja(@Param("od") LocalDateTime od, @Param("doo") LocalDateTime doo,
                                                 @Param("idTurnir") Long idTurnir, Pageable stran);

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

    /* Branja iste tekme v danih stanjih (dvojniki istega listka). */
    List<BranjeListka> findByTekmaIdAndStanjeIn(Long idTekma, Collection<StanjeBranja> stanja);

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

    /* Zadnje potrjeno branje tekme, ki ima se fotografijo (F4.4, »Listek« v oknu tekme). */
    @Query("""
            SELECT b.id FROM BranjeListka b
            WHERE b.tekma.id = :idTekme AND b.stanje = :stanje AND b.slikaIzbrisanaOb IS NULL
            ORDER BY b.id DESC
            """)
    List<Long> zSlikoZaTekmo(@Param("idTekme") Long idTekme, @Param("stanje") si.turnirko.modeli.StanjeBranja stanje,
                             org.springframework.data.domain.Pageable stran);
}
