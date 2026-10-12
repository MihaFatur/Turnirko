/* Dostop do dnevnika racunov (glej RacunDogodek). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.RacunDogodek;

public interface RacunDogodekRepozitorij extends JpaRepository<RacunDogodek, Long> {

    /* Najnovejsih `najvec` dogodkov VSAKEGA racuna - ena poizvedba za cel
       administratorjev seznam, ne ena na racun. Vrstice: id_racun, ob, tekst
       (ob je besedilo, ISO; razclenitev je v RacunDnevnikStoritev). Najnovejsi
       prvi, pri enakem casu pozneje zapisani. */
    @Query(value = """
            SELECT id_racun, ob, tekst FROM (
                SELECT id_racun, ob, tekst, id,
                       ROW_NUMBER() OVER (PARTITION BY id_racun ORDER BY ob DESC, id DESC) AS vrstni
                FROM racun_dogodek
            ) WHERE vrstni <= :najvec
            ORDER BY id_racun, ob DESC, id DESC
            """, nativeQuery = true)
    List<Object[]> najnovejsiZaVse(int najvec);

    @Query(value = """
            SELECT id_racun, ob, tekst FROM racun_dogodek
            WHERE id_racun = :idRacun
            ORDER BY ob DESC, id DESC
            LIMIT :najvec
            """, nativeQuery = true)
    List<Object[]> najnovejsiZaRacun(Long idRacun, int najvec);
}
