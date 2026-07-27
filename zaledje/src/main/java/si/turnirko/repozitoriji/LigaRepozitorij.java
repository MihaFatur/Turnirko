/* Dostop do lig. Visja liga (za prehode) se nalozi vnaprej, ker jo bere DTO
   izven transakcije. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Liga;

public interface LigaRepozitorij extends JpaRepository<Liga, Long> {

    @Query("SELECT l FROM Liga l LEFT JOIN FETCH l.visjaLiga ORDER BY l.id DESC")
    List<Liga> najdiVse();

    @Query("SELECT l FROM Liga l LEFT JOIN FETCH l.visjaLiga WHERE l.id = :id")
    Optional<Liga> najdiZVisjo(Long id);
}
