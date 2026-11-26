package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.BranjeZapisnika;

public interface BranjeZapisnikaRepozitorij extends JpaRepository<BranjeZapisnika, Long> {

    /* Branje z vsem, kar bere DTO: nalagalec, natis, srecanje z ekipama in ligo. */
    @Query("""
            SELECT b FROM BranjeZapisnika b
            LEFT JOIN FETCH b.nalozil
            LEFT JOIN FETCH b.natis
            LEFT JOIN FETCH b.srecanje s
            LEFT JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub LEFT JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
            WHERE b.id = :id
            """)
    Optional<BranjeZapisnika> najdiPodrobno(@Param("id") Long id);

    /* Branja uporabnika od dneva naprej (seznam na strani branja), najnovejsa prva. */
    @Query("""
            SELECT b FROM BranjeZapisnika b
            LEFT JOIN FETCH b.natis
            LEFT JOIN FETCH b.srecanje s
            LEFT JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub LEFT JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
            WHERE b.nalozil.id = :idUporabnika AND b.ustvarjenoOb >= :od
            ORDER BY b.ustvarjenoOb DESC, b.id DESC
            """)
    List<BranjeZapisnika> zaUporabnika(@Param("idUporabnika") Long idUporabnika, @Param("od") LocalDateTime od);

    /* Odprta branja istega srecanja (dvojniki: isti list fotografiran vec krat). */
    @Query("""
            SELECT b FROM BranjeZapisnika b
            WHERE b.srecanje.id = :idSrecanje AND b.id <> :razen
              AND b.stanje IN (si.turnirko.modeli.BranjeZapisnika.Stanje.V_VRSTI,
                               si.turnirko.modeli.BranjeZapisnika.Stanje.PREBRANO,
                               si.turnirko.modeli.BranjeZapisnika.Stanje.NAPAKA)
            """)
    List<BranjeZapisnika> odprtaZaSrecanje(@Param("idSrecanje") Long idSrecanje, @Param("razen") Long razen);

    @Query("""
            SELECT b FROM BranjeZapisnika b
            LEFT JOIN FETCH b.srecanje
            WHERE b.nalozil.id = :idUporabnika AND b.kljucPosiljanja = :kljuc
            """)
    Optional<BranjeZapisnika> poKljucuPosiljanja(@Param("idUporabnika") Long idUporabnika,
                                                 @Param("kljuc") String kljuc);

    long countByNalozilIdAndUstvarjenoObAfter(Long idUporabnika, LocalDateTime od);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BranjeZapisnika b SET b.kljucPosiljanja = NULL
            WHERE b.kljucPosiljanja IS NOT NULL AND b.ustvarjenoOb < :meja
            """)
    int pobrisiKljuce(@Param("meja") LocalDateTime meja);

    /* Branja, katerih fotografijo je cas izbrisati (30 dni od nalaganja). */
    @Query("""
            SELECT b.id FROM BranjeZapisnika b
            WHERE b.slikaIzbrisanaOb IS NULL AND b.ustvarjenoOb < :meja
            """)
    List<Long> zaIzbrisSlike(@Param("meja") LocalDateTime meja);

    /* Branja, ki so ob ponovnem zagonu ostala v vrsti (izvajalec je bil v pomnilniku). */
    @Query("SELECT b.id FROM BranjeZapisnika b WHERE b.stanje = si.turnirko.modeli.BranjeZapisnika.Stanje.V_VRSTI")
    List<Long> vVrsti();
}
