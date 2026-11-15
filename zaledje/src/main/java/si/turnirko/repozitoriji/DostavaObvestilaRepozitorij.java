/* Dostop do dostav obvestil po prejemnikih (V66, DostavaObvestilStoritev). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.DostavaObvestila;

public interface DostavaObvestilaRepozitorij extends JpaRepository<DostavaObvestila, Long> {

    List<DostavaObvestila> findByIdLigaAndKolo(Long idLiga, Integer kolo);

    List<DostavaObvestila> findByIdTurnir(Long idTurnir);

    Optional<DostavaObvestila> findByIdLigaAndKoloAndIdRacun(Long idLiga, Integer kolo, Long idRacun);

    Optional<DostavaObvestila> findByIdTurnirAndIdRacun(Long idTurnir, Long idRacun);
}
