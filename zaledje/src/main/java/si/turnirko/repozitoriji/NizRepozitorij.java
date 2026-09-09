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

    /* Nizi CELEGA turnirja, urejeni po tekmi in zaporedju - za zavihek
       statistike (obrat, najdaljsi niz, sestevek tock).

       Zakaj po turnirju in ne po seznamu id-jev: turnir ima lahko nekaj sto
       tekem, sqlite pa ima omejitev stevila vezanih parametrov - seznam v IN
       bi pri velikem turnirju pocil. Zaporedna stevilka je v izbiri, ker brez
       vrstnega reda nizov ni mogoce prepoznati obrata. */
    @Query("""
            SELECT n.tekma.id, n.zaporednaSt, n.tocke1, n.tocke2
            FROM Niz n
            WHERE n.tekma.dogodek.turnir.id = :idTurnir
            ORDER BY n.tekma.id, n.zaporednaSt
            """)
    List<Object[]> tockeZaTurnir(@Param("idTurnir") Long idTurnir);
}
