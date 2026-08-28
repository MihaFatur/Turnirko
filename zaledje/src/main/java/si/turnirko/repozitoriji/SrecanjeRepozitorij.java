/* Dostop do srecanj. Obe ekipi in njuna kluba se nalozijo vnaprej, ker jih
   bere DTO (prikazana imena) izven transakcije. Vez na klub je LEVA: prosta
   ekipa kluba nima in notranji stik bi celotno srecanje izpustil iz
   razporeda - glej ProstaEkipaTest.prostaInKlubskaEkipaVIstiLigi. */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
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

    /* Srecanja s terminom v obdobju - vir koledarja. Poleg ekip nalozi tudi
       ligo z njenim klubom lastnikom, ker vnos koledarja izpise ime lige,
       sezono in klub.

       Meji sta casovni in ne datumski, ker stolpec nosi tudi uro (pretvornik
       CasKotBesedilo). Klicatelj ju namenoma razsiri za dan na vsako stran in
       nato natancno omeji po datumu: v starih bazah so zapisi brez ure
       ("2026-10-04"), ti pa so pri primerjavi nizov krajsi od "2026-10-04T00:00"
       in bi na prvi dan obdobja odpadli. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.liga l LEFT JOIN FETCH l.klubLastnik
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            WHERE s.predvidenZacetek IS NOT NULL
              AND s.predvidenZacetek >= :od
              AND s.predvidenZacetek < :doKdaj
            ORDER BY s.predvidenZacetek, s.liga.id, s.kolo, s.id
            """)
    List<Srecanje> najdiVObdobju(LocalDateTime od, LocalDateTime doKdaj);

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
