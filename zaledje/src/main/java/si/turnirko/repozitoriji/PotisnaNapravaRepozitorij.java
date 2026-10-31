/* Dostop do naprav s potisnimi obvestili (V60). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.PotisnaNaprava;

public interface PotisnaNapravaRepozitorij extends JpaRepository<PotisnaNaprava, Long> {

    Optional<PotisnaNaprava> findByEndpoint(String endpoint);

    List<PotisnaNaprava> findByIdRacun(Long idRacun);

    List<PotisnaNaprava> findByIdRacunIn(Collection<Long> idjiRacunov);

    void deleteByEndpointAndIdRacun(String endpoint, Long idRacun);
}
