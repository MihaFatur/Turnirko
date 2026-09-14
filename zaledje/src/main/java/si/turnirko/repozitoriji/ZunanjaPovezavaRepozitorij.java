/* Povezave zapisov Turnirka z zapisi zunanjih virov (V27). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.ZunanjaPovezava;

public interface ZunanjaPovezavaRepozitorij extends JpaRepository<ZunanjaPovezava, Long> {

    Optional<ZunanjaPovezava> findByVirAndVrstaAndZunanjiId(VirTekmovanja vir, ZunanjaPovezava.Vrsta vrsta,
                                                           String zunanjiId);

    List<ZunanjaPovezava> findByVirAndVrsta(VirTekmovanja vir, ZunanjaPovezava.Vrsta vrsta);

    List<ZunanjaPovezava> findByVrstaAndIdLokalni(ZunanjaPovezava.Vrsta vrsta, Long idLokalni);

    boolean existsByVirAndVrstaAndIdLokalni(VirTekmovanja vir, ZunanjaPovezava.Vrsta vrsta, Long idLokalni);

    /* Povezave zapisov, ki jih je ponovni uvoz pobrisal (npr. srecanja lige,
       ki jih vir ne pozna vec). */
    @Modifying
    @Query("DELETE FROM ZunanjaPovezava z WHERE z.vrsta = :vrsta AND z.idLokalni IN :idji")
    void pobrisiZaLokalne(ZunanjaPovezava.Vrsta vrsta, Collection<Long> idji);
}
