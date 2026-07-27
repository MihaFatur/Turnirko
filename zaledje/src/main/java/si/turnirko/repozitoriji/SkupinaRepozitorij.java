/* Dostop do skupin (skupinski del sistema SKUPINE_IZLOCILNI). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Skupina;

public interface SkupinaRepozitorij extends JpaRepository<Skupina, Long> {

    List<Skupina> findByDogodekIdOrderByOznakaAsc(Long idDogodek);
}
