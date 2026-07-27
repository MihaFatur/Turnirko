/* Dostop do srecanj. Obe ekipi in njuna kluba se nalozijo vnaprej, ker jih
   bere DTO (prikazana imena) izven transakcije. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Srecanje;

public interface SrecanjeRepozitorij extends JpaRepository<Srecanje, Long> {

    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg JOIN FETCH eg.klub
            WHERE s.liga.id = :idLiga
            ORDER BY s.kolo, s.id
            """)
    List<Srecanje> najdiZaLigo(Long idLiga);

    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg JOIN FETCH eg.klub
            JOIN FETCH s.liga
            WHERE s.id = :id
            """)
    Optional<Srecanje> najdiPodrobno(Long id);

    boolean existsByLigaId(Long idLiga);
}
