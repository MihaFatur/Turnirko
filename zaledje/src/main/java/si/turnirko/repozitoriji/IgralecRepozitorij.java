/* Dostop do igralcev. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Igralec;

public interface IgralecRepozitorij extends JpaRepository<Igralec, Long> {

    /* Vsi aktivni (nearhivirani) igralci s klubom in krajem v eni poizvedbi -
       "join fetch" prepreci N+1 poizvedb pri izpisu seznama. */
    @Query("""
            SELECT i FROM Igralec i
            LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH i.kraj
            WHERE i.arhiviran = false
            ORDER BY i.priimek, i.ime
            """)
    List<Igralec> najdiAktivne();

    @Query("""
            SELECT i FROM Igralec i
            LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH i.kraj
            WHERE i.id = :id
            """)
    Optional<Igralec> najdiZVsem(Long id);
}
