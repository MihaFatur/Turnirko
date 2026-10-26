/* Dostop do kod popusta (V53). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.KodaPopusta;

public interface KodaPopustaRepozitorij extends JpaRepository<KodaPopusta, Long> {

    /* Koda je v bazi vedno normalizirana (KodePopustaStoritev.normaliziraj). */
    Optional<KodaPopusta> findByKoda(String koda);

    boolean existsByKoda(String koda);

    /* Najnovejse zgoraj; po id-ju, ne po besedilu (glej CLAUDE.md, slovenski vrstni red). */
    List<KodaPopusta> findAllByOrderByIdDesc();
}
