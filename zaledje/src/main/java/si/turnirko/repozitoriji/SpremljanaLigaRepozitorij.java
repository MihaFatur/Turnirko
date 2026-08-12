/* Dostop do izbora lig, ki jih uporabnik spremlja. */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.SpremljanaLiga;

public interface SpremljanaLigaRepozitorij
        extends JpaRepository<SpremljanaLiga, SpremljanaLiga.Kljuc> {

    /* Id-ji lig, ki jih racun spremlja, v vrstnem redu dodajanja. */
    @Query("""
            SELECT s.idLiga FROM SpremljanaLiga s
            WHERE s.idRacun = :idRacun
            ORDER BY s.dodanoOb, s.idLiga
            """)
    List<Long> idjiZaRacun(Long idRacun);

    void deleteByIdRacunAndIdLiga(Long idRacun, Long idLiga);

    boolean existsByIdRacunAndIdLiga(Long idRacun, Long idLiga);
}
