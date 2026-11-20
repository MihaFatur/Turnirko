package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.SlikaBranjaListka;

public interface SlikaBranjaListkaRepozitorij extends JpaRepository<SlikaBranjaListka, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SlikaBranjaListka s WHERE s.idBranje IN :idji")
    int izbrisi(@Param("idji") List<Long> idji);
}
