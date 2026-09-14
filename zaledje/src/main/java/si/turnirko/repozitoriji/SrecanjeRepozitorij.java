/* Dostop do srecanj. Obe ekipi in njuna kluba se nalozijo vnaprej, ker jih
   bere DTO (prikazana imena) izven transakcije. Vez na klub je LEVA: prosta
   ekipa kluba nima in notranji stik bi celotno srecanje izpustil iz
   razporeda - glej ProstaEkipaTest.prostaInKlubskaEkipaVIstiLigi.

   Srecanje pripada ligi ALI ekipni tekmi turnirja (V28). Poizvedbe po ligi
   ekipne tekme turnirjev izpustijo same; kjer srecanje ni izbrano po ligi, se
   liga in tekma nalozita LEVO. Srecanja koncnice (serija) so srecanja lige,
   a ne rednega dela - kola, termini po kolih in lestvica jih izpustijo. */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Srecanje;

public interface SrecanjeRepozitorij extends JpaRepository<Srecanje, Long> {

    /* Vsa srecanja lige - redni del in koncnica - s serijo koncnice.

       V kolu po zacetku in sele nato po id: pri ligi z urami srecanj (V31) je
       kolo vecer z vec urami in organizator jih sme srecanjem zamenjati -
       razpored in "naslednje srecanje" morata slediti uri, ne vrstnemu redu
       zapisa. Zacetek je besedilo ISO (CasKotBesedilo), zato je abecedni red
       tudi casovni. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH s.serija
            WHERE s.liga.id = :idLiga
            ORDER BY s.kolo, s.predvidenZacetek, s.id
            """)
    List<Srecanje> najdiZaLigo(Long idLiga);

    /* Srecanja rednega dela lige (brez koncnice), urejena kot najdiZaLigo. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            WHERE s.liga.id = :idLiga AND s.serija IS NULL
            ORDER BY s.kolo, s.predvidenZacetek, s.id
            """)
    List<Srecanje> najdiRednaZaLigo(Long idLiga);

    /* Srecanja koncnice lige, urejena po krogu, paru in tekmi v seriji. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.serija sk
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            WHERE s.liga.id = :idLiga
            ORDER BY sk.krog, sk.par, s.tekmaVSeriji
            """)
    List<Srecanje> najdiKoncnicoLige(Long idLiga);

    /* Srecanja ene serije koncnice po vrsti. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            WHERE s.serija.id = :idSerija
            ORDER BY s.tekmaVSeriji
            """)
    List<Srecanje> najdiZaSerijo(Long idSerija);

    /* Srecanje z vsem, kar bere zapisnik: ekipi s klubom, ligo ALI ekipno
       tekmo turnirja z dogodkom in turnirjem ter serijo koncnice. */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
            LEFT JOIN FETCH s.serija
            WHERE s.id = :id
            """)
    Optional<Srecanje> najdiPodrobno(Long id);

    /* Srecanje ekipne tekme turnirja (kvecjemu eno). */
    @Query("""
            SELECT s FROM Srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            JOIN FETCH s.tekma tk JOIN FETCH tk.dogodek dg JOIN FETCH dg.turnir
            WHERE tk.id = :idTekma
            """)
    Optional<Srecanje> najdiZaTekmo(Long idTekma);

    /* Srecanja ekipnih tekem enega dogodka: [idTekma, idSrecanje, status,
       dobljeneDomaci, dobljeneGost] - mreza in skupine ob vsaki ekipni tekmi
       pokazejo povezavo na zapisnik brez poizvedbe na tekmo. */
    @Query("""
            SELECT s.tekma.id, s.id, s.status, s.dobljeneDomaci, s.dobljeneGost
            FROM Srecanje s
            WHERE s.tekma.dogodek.id = :idDogodek
            """)
    List<Object[]> srecanjaDogodka(Long idDogodek);

    boolean existsByLigaId(Long idLiga);

    /* Srecanja s terminom v obdobju - vir koledarja. Poleg ekip nalozi tudi
       ligo z njenim klubom lastnikom, ker vnos koledarja izpise ime lige,
       sezono in klub. Ekipne tekme turnirjev izpadejo (stik na ligo je
       notranji) - v koledarju ze stoji njihov turnir.

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
            LEFT JOIN FETCH s.serija
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
       merilo kot stran lige), zato tu ne zadosca najvisje koncano kolo.
       Koncnica ni kolo rednega dela in ne steje. */
    @Query("""
            SELECT s.liga.id, s.kolo, COUNT(s),
                   SUM(CASE WHEN s.status = si.turnirko.modeli.StatusSrecanja.KONCANO THEN 1 ELSE 0 END)
            FROM Srecanje s
            WHERE s.liga IS NOT NULL AND s.serija IS NULL
            GROUP BY s.liga.id, s.kolo
            """)
    List<Object[]> stanjeKolPoLigah();

    /* Isto za eno ligo: [kolo, srecanj, koncanih]. */
    @Query("""
            SELECT s.kolo, COUNT(s),
                   SUM(CASE WHEN s.status = si.turnirko.modeli.StatusSrecanja.KONCANO THEN 1 ELSE 0 END)
            FROM Srecanje s
            WHERE s.liga.id = :idLiga AND s.serija IS NULL
            GROUP BY s.kolo
            """)
    List<Object[]> stanjeKol(Long idLiga);
}
