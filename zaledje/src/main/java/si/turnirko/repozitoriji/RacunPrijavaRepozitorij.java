/* Dostop do prijav racunov (glej RacunPrijava). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.RacunPrijava;

public interface RacunPrijavaRepozitorij extends JpaRepository<RacunPrijava, Long> {

    /* Zadnja prijava in stevilo prijav od `od` naprej, za VSE racune naenkrat.
       Vrstice: id_racun, zadnja (cas, glej CasBaze), stevilo. */
    @Query(value = """
            SELECT id_racun, MAX(ob), SUM(CASE WHEN ob >= :od THEN 1 ELSE 0 END)
            FROM racun_prijava GROUP BY id_racun
            """, nativeQuery = true)
    List<Object[]> povzetekZaVse(LocalDateTime od);

    @Query(value = """
            SELECT id_racun, MAX(ob), SUM(CASE WHEN ob >= :od THEN 1 ELSE 0 END)
            FROM racun_prijava WHERE id_racun = :idRacun GROUP BY id_racun
            """, nativeQuery = true)
    List<Object[]> povzetekZaRacun(Long idRacun, LocalDateTime od);
}
