/* Dostop do serij koncnice lige. Ekipe s klubi se nalozijo vnaprej, ker jih
   bere DTO izven transakcije; vez je LEVA, ker sta ekipi serije visjega kroga
   prazni, dokler ju ne doloci prejsnji krog. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.SerijaKoncnice;

public interface SerijaKoncniceRepozitorij extends JpaRepository<SerijaKoncnice, Long> {

    @Query("""
            SELECT s FROM SerijaKoncnice s
            LEFT JOIN FETCH s.ekipa1 e1 LEFT JOIN FETCH e1.klub
            LEFT JOIN FETCH s.ekipa2 e2 LEFT JOIN FETCH e2.klub
            LEFT JOIN FETCH s.zmagovalec
            WHERE s.liga.id = :idLiga
            ORDER BY s.krog, s.par
            """)
    List<SerijaKoncnice> najdiZaLigo(Long idLiga);

    @Query("""
            SELECT s FROM SerijaKoncnice s
            JOIN FETCH s.liga
            LEFT JOIN FETCH s.ekipa1 e1 LEFT JOIN FETCH e1.klub
            LEFT JOIN FETCH s.ekipa2 e2 LEFT JOIN FETCH e2.klub
            WHERE s.id = :id
            """)
    Optional<SerijaKoncnice> najdiZLigo(Long id);

    Optional<SerijaKoncnice> findByLigaIdAndKrogAndPar(Long idLiga, int krog, int par);

    boolean existsByLigaId(Long idLiga);
}
