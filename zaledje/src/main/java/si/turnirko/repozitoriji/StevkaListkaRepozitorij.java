package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.StevkaListka;

public interface StevkaListkaRepozitorij extends JpaRepository<StevkaListka, Long> {

    long countByUstvarjenoObAfter(LocalDateTime od);

    /* Najnovejse stevke (ucenje vzame zgornjo mejo, da noc ostane kratka). */
    @Query("SELECT s FROM StevkaListka s ORDER BY s.id DESC")
    List<StevkaListka> najnovejse(Pageable stran);

    /* Izbrise vse razen `ohrani` najnovejsih stevk (hramba). Ce jih je manj,
       podpoizvedba vrne NULL in ne izbrise nicesar. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM stevka_listka
            WHERE id <= (SELECT id FROM stevka_listka ORDER BY id DESC OFFSET :ohrani LIMIT 1)
            """, nativeQuery = true)
    int izbrisiRazenNajnovejsih(@Param("ohrani") int ohrani);
}
