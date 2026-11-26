package si.turnirko.repozitoriji;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.SlikaBranjaZapisnika;

public interface SlikaBranjaZapisnikaRepozitorij extends JpaRepository<SlikaBranjaZapisnika, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SlikaBranjaZapisnika s WHERE s.idBranje IN :idji")
    int izbrisi(@Param("idji") Collection<Long> idji);
}
