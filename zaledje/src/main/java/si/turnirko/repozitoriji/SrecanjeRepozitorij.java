/* Dostop do srecanj. Obe ekipi in njuna kluba se nalozijo vnaprej, ker jih
   bere DTO (prikazana imena) izven transakcije. Vez na klub je LEVA: prosta
   ekipa kluba nima in notranji stik bi celotno srecanje izpustil iz
   razporeda - glej ProstaEkipaTest.prostaInKlubskaEkipaVIstiLigi. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Srecanje;

public interface SrecanjeRepozitorij extends JpaRepository<Srecanje, Long> {

    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            WHERE s.liga.id = :idLiga
            ORDER BY s.kolo, s.id
            """)
    List<Srecanje> najdiZaLigo(Long idLiga);

    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            JOIN FETCH s.liga
            WHERE s.id = :id
            """)
    Optional<Srecanje> najdiPodrobno(Long id);

    boolean existsByLigaId(Long idLiga);

    /* Stanje kol po ligah: ena vrstica na (liga, kolo) - koliko srecanj ima
       in koliko jih je koncanih. [idLiga, kolo, srecanj, koncanih].

       Skupinsko, ker vrstica lige na telefonu nosi "7. od 18 kol" in palico
       napredka za VSAKO ligo v seznamu - poizvedba na ligo bi bila N+1. Kolo
       steje za odigrano sele, ko je koncano vsako njegovo srecanje (isto
       merilo kot stran lige), zato tu ne zadosca najvisje koncano kolo. */
    @Query("""
            SELECT s.liga.id, s.kolo, COUNT(s),
                   SUM(CASE WHEN s.status = si.turnirko.modeli.StatusSrecanja.KONCANO THEN 1 ELSE 0 END)
            FROM Srecanje s
            GROUP BY s.liga.id, s.kolo
            """)
    List<Object[]> stanjeKolPoLigah();

    /* Isto za eno ligo: [kolo, srecanj, koncanih]. */
    @Query("""
            SELECT s.kolo, COUNT(s),
                   SUM(CASE WHEN s.status = si.turnirko.modeli.StatusSrecanja.KONCANO THEN 1 ELSE 0 END)
            FROM Srecanje s
            WHERE s.liga.id = :idLiga
            GROUP BY s.kolo
            """)
    List<Object[]> stanjeKol(Long idLiga);
}
