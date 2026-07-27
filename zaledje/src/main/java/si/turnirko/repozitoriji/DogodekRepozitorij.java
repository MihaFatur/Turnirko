/* Dostop do dogodkov (posameznih tekmovanj na turnirjih). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Dogodek;

public interface DogodekRepozitorij extends JpaRepository<Dogodek, Long> {

    List<Dogodek> findByTurnirIdOrderByImeAsc(Long idTurnir);

    /* Dogodek skupaj s turnirjem v eni poizvedbi. */
    @Query("SELECT d FROM Dogodek d JOIN FETCH d.turnir WHERE d.id = :id")
    Optional<Dogodek> najdiSTurnirjem(Long id);
}
