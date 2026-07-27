/* Dostop do turnirjev.

   POZOR: kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije,
   zato mora biti kraj nalozen ze v poizvedbi ("join fetch") - sicer
   dostop do njega sprozi LazyInitializationException. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Turnir;

public interface TurnirRepozitorij extends JpaRepository<Turnir, Long> {

    /* Vsi turnirji s krajem, najnovejsi najprej. */
    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            ORDER BY t.ustvarjenOb DESC
            """)
    List<Turnir> najdiVseSKrajem();

    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            WHERE t.id = :id
            """)
    Optional<Turnir> najdiSKrajem(Long id);
}
