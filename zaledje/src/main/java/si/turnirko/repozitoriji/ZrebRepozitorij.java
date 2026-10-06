/* Dostop do zrebov nivojev (sistem SV_REGIJA). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Zreb;

public interface ZrebRepozitorij extends JpaRepository<Zreb, Long> {

    /* Zrebi dogodka po nivoju in indeksu: glavni zreb prvega nivoja je prvi. */
    List<Zreb> findByDogodekIdOrderByNivoAscIndeksAsc(Long idDogodek);

    List<Zreb> findByDogodekIdAndNivoOrderByIndeksAsc(Long idDogodek, int nivo);

    /* Koliko zrebov dogodka se nima zgrajenih tekem. */
    long countByDogodekIdAndZgrajenFalse(Long idDogodek);

    void deleteByDogodekId(Long idDogodek);
}
