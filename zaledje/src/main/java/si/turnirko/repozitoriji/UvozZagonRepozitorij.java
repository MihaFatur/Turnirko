/* Dnevnik uvozov iz zunanjih virov (V27). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.UvozZagon;
import si.turnirko.modeli.VirTekmovanja;

public interface UvozZagonRepozitorij extends JpaRepository<UvozZagon, Long> {

    List<UvozZagon> findByVirOrderByIdDesc(VirTekmovanja vir);

    List<UvozZagon> findByVirAndZunanjiIdOrderByIdDesc(VirTekmovanja vir, String zunanjiId);
}
