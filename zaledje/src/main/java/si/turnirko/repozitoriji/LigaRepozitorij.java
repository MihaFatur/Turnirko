/* Dostop do lig. Visja liga (za prehode) in lastnistvo (klub lastnik,
   ustvaril) se nalozijo vnaprej, ker jih bere DTO oz. preverba pravice izven
   transakcije. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Liga;

public interface LigaRepozitorij extends JpaRepository<Liga, Long> {

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.visjaLiga
            LEFT JOIN FETCH l.klubLastnik
            LEFT JOIN FETCH l.ustvaril
            ORDER BY l.id DESC
            """)
    List<Liga> najdiVse();

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.visjaLiga
            LEFT JOIN FETCH l.klubLastnik
            LEFT JOIN FETCH l.ustvaril
            WHERE l.id = :id
            """)
    Optional<Liga> najdiZVisjo(Long id);

    // ---------- Lastnistvo (za preverjanje pravice organizatorja) ----------

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE l.id = :id
            """)
    Optional<Liga> najdiZLastnistvom(Long id);

    @Query("""
            SELECT l FROM Ekipa e JOIN e.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE e.id = :idEkipa
            """)
    Optional<Liga> najdiZLastnistvomPoEkipi(Long idEkipa);

    @Query("""
            SELECT l FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE k.id = :idKader
            """)
    Optional<Liga> najdiZLastnistvomPoKadru(Long idKader);

    @Query("""
            SELECT l FROM Srecanje s JOIN s.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE s.id = :idSrecanje
            """)
    Optional<Liga> najdiZLastnistvomPoSrecanju(Long idSrecanje);

    @Query("""
            SELECT l FROM TekmaSrecanja ts JOIN ts.srecanje s JOIN s.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE ts.id = :idTekma
            """)
    Optional<Liga> najdiZLastnistvomPoTekmiSrecanja(Long idTekma);
}
