/* Dostop do prijav racunov (glej RacunPrijava). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.RacunPrijava;

public interface RacunPrijavaRepozitorij extends JpaRepository<RacunPrijava, Long> {

    /* Zadnja prijava in stevilo prijav od `od` naprej, za VSE racune naenkrat.
       Vrstice: id_racun, zadnja (besedilo ISO), stevilo. `od` je ISO niz iste
       oblike kot zapis (CasKotBesedilo), zato je primerjava nizov pravilna. */
    @Query(value = """
            SELECT id_racun, MAX(ob), SUM(CASE WHEN ob >= :od THEN 1 ELSE 0 END)
            FROM racun_prijava GROUP BY id_racun
            """, nativeQuery = true)
    List<Object[]> povzetekZaVse(String od);

    @Query(value = """
            SELECT id_racun, MAX(ob), SUM(CASE WHEN ob >= :od THEN 1 ELSE 0 END)
            FROM racun_prijava WHERE id_racun = :idRacun GROUP BY id_racun
            """, nativeQuery = true)
    List<Object[]> povzetekZaRacun(Long idRacun, String od);
}
