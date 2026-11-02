/* Dostop do druzinskih povezav (V59). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.DruzinaPovezava;

public interface DruzinaPovezavaRepozitorij extends JpaRepository<DruzinaPovezava, Long> {

    List<DruzinaPovezava> findByIdRacunOrderById(Long idRacun);

    Optional<DruzinaPovezava> findByIdRacunAndIdIgralec(Long idRacun, Long idIgralec);

    /* Povezave z danim stanjem za enega igralca (kdo ga ima v druzini). */
    List<DruzinaPovezava> findByIdIgralecAndStatus(Long idIgralec, DruzinaPovezava.Status status);

    List<DruzinaPovezava> findByIdIgralecInAndStatus(Collection<Long> idjiIgralcev, DruzinaPovezava.Status status);

    List<DruzinaPovezava> findByStatusInOrderByZahtevanaObDescIdDesc(Collection<DruzinaPovezava.Status> stanja);

    long countByStatus(DruzinaPovezava.Status status);
}
