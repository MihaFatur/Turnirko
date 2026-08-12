/* Dostop do dogodkov (posameznih tekmovanj na turnirjih). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Dogodek;

public interface DogodekRepozitorij extends JpaRepository<Dogodek, Long> {

    /* Vrstni red dogodkov je vrstni red nastanka (id), ne abeceda in ne status:
       sodnik jih je vpisal v vrstnem redu, v katerem tecejo v dvorani. */
    List<Dogodek> findByTurnirIdOrderByIdAsc(Long idTurnir);

    /* Dogodek skupaj s turnirjem v eni poizvedbi. */
    @Query("SELECT d FROM Dogodek d JOIN FETCH d.turnir WHERE d.id = :id")
    Optional<Dogodek> najdiSTurnirjem(Long id);

    /* Stevilo dogodkov po turnirjih: skupaj, v teku, v pripravi.
       Ena skupinska poizvedba namesto ene na turnir - seznam turnirjev je
       najpogosteje odprta stran in ne sme delati N+1. Vrstica je
       [idTurnir, skupaj, vTeku, vPripravi]. */
    @Query("""
            SELECT d.turnir.id, COUNT(d),
                   SUM(CASE WHEN d.status = si.turnirko.modeli.StatusTekmovanja.V_TEKU THEN 1 ELSE 0 END),
                   SUM(CASE WHEN d.status = si.turnirko.modeli.StatusTekmovanja.PRIPRAVA THEN 1 ELSE 0 END)
            FROM Dogodek d
            GROUP BY d.turnir.id
            """)
    List<Object[]> stejPoTurnirjih();
}
