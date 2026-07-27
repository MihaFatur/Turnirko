/* Dostop do nizov (tock posameznih nizov tekme). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.Niz;

public interface NizRepozitorij extends JpaRepository<Niz, Long> {

    List<Niz> findByTekmaIdOrderByZaporednaStAsc(Long idTekma);

    /* Nizi vec tekem hkrati - za statistiko tock na profilu igralca.
       Vrne vrstice [idTekme, tocke1, tocke2]; entitet ne vracamo, ker bi
       vsaka potegnila se tekmo. */
    @Query("SELECT n.tekma.id, n.tocke1, n.tocke2 FROM Niz n WHERE n.tekma.id IN :idjiTekem")
    List<Object[]> tockeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem);
}
