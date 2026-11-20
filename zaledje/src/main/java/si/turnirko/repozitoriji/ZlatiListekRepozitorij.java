package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.ZlatiListek;

public interface ZlatiListekRepozitorij extends JpaRepository<ZlatiListek, Long> {

    boolean existsByIdBranje(Long idBranje);

    /* Koliko listkov ima manjsi kljuc: novi listek, ki bi bil izven vzorca, se ne zapise. */
    long countByKljucLessThan(double kljuc);

    /* Stran nabora po id (meritev in izvoz tecejo po straneh, ne vse naenkrat). */
    List<ZlatiListek> findByIdGreaterThanOrderByIdAsc(Long id, Pageable stran);

    /* Vzorec: ostane `ohrani` listkov z najmanjsimi kljuci, ostali se izbrisejo. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM zlati_listek
            WHERE id IN (SELECT id FROM zlati_listek ORDER BY kljuc ASC, id ASC OFFSET :ohrani)
            """, nativeQuery = true)
    int obdrziNajmanjse(@Param("ohrani") int ohrani);
}
