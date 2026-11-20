package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.StevkaListka;

public interface StevkaListkaRepozitorij extends JpaRepository<StevkaListka, Long> {

    long countByUstvarjenoObAfter(LocalDateTime od);

    /* Najnovejse stevke (ucenje vzame zgornjo mejo, da noc ostane kratka). */
    @Query("SELECT s FROM StevkaListka s ORDER BY s.id DESC")
    List<StevkaListka> najnovejse(Pageable stran);
}
