package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.dto.VadnicaDto;
import si.turnirko.modeli.Vadnica;

public interface VadnicaRepozitorij extends JpaRepository<Vadnica, Long> {

    /* Seznam brez slik: najprej neodobreni (cakajo na pregled), nato najnovejsi. */
    @Query("""
            SELECT new si.turnirko.dto.VadnicaDto(v.id, v.stevk, v.praznih, v.preskocenih, v.odobrena,
                   v.ustvarjenoOb, v.odobrenoOb)
            FROM Vadnica v
            ORDER BY v.odobrena ASC, v.ustvarjenoOb DESC, v.id DESC
            """)
    List<VadnicaDto> seznam(Pageable stran);

    /* Odobritev samo neodobrenega lista (hkratni odobritvi: druga dobi 0). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Vadnica v SET v.odobrena = true, v.odobrenoOb = :zdaj, v.stevk = :stevk
            WHERE v.id = :id AND v.odobrena = false
            """)
    int odobri(@Param("id") Long id, @Param("stevk") int stevk, @Param("zdaj") LocalDateTime zdaj);

    /* Stevka lista je izlocena iz ucenja (napake mreze, F6.6): stevilo na seznamu
       listov ostane enako stevkam v bazi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Vadnica v SET v.stevk = v.stevk - 1 WHERE v.id = :id AND v.stevk > 0")
    int odstejStevko(@Param("id") Long id);
}
