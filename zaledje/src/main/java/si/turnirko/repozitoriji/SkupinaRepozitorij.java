/* Dostop do skupin (skupinski del sistema SKUPINE_IZLOCILNI). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.Skupina;

public interface SkupinaRepozitorij extends JpaRepository<Skupina, Long> {

    /* Skupine dogodka po vrsti. Oznake so od 23. 11. 2026 številke (»1« … »17«), a so
       zapisane kot besedilo, zato navadni ORDER BY oznaka da »10« pred »2«: najprej
       krajša oznaka, nato abecedno. Enako velja za črke starejših dogodkov (A, B …)
       in za SV regijo (»1A«, »1B«, »2A« …; oznake znotraj nivoja so enako dolge). */
    @Query("select s from Skupina s where s.dogodek.id = :idDogodek "
            + "order by length(s.oznaka), s.oznaka")
    List<Skupina> findByDogodekIdOrderByOznakaAsc(@Param("idDogodek") Long idDogodek);
}
