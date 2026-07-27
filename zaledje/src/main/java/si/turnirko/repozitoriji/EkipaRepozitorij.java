/* Dostop do ekip. Klub se nalozi vnaprej (join fetch), ker ga bere DTO
   (prikazano ime ekipe) izven transakcije. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Ekipa;

public interface EkipaRepozitorij extends JpaRepository<Ekipa, Long> {

    @Query("""
            SELECT e FROM Ekipa e JOIN FETCH e.klub
            WHERE e.liga.id = :idLiga
            ORDER BY e.klub.ime, e.zaporedna
            """)
    List<Ekipa> najdiZaLigo(Long idLiga);

    @Query("SELECT e FROM Ekipa e JOIN FETCH e.klub JOIN FETCH e.liga WHERE e.id = :id")
    Optional<Ekipa> najdiZKlubomInLigo(Long id);

    boolean existsByLigaIdAndKlubIdAndZaporedna(Long idLiga, Long idKlub, int zaporedna);

    long countByLigaId(Long idLiga);
}
