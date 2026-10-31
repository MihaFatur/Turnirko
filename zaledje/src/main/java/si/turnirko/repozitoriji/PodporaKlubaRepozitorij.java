/* Dostop do podpore klubom (V61). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.PodporaKluba;

public interface PodporaKlubaRepozitorij extends JpaRepository<PodporaKluba, Long> {

    List<PodporaKluba> findByIdKlub(Long idKlub);
}
