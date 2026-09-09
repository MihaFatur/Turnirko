/* Dostop do nizov ligaskih tekem (tocke posameznih nizov). */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.NizSrecanja;

public interface NizSrecanjaRepozitorij extends JpaRepository<NizSrecanja, Long> {

    /* Nizi vseh tekem enega srecanja hkrati - zapisnik jih izpise v vrstici
       vsake tekme, zato ena poizvedba namesto ene na tekmo. Vrne vrstice
       [idTekme, zaporedna, tockeDomaci, tockeGost]; entitet ne vracamo, ker bi
       vsaka potegnila se tekmo. */
    @Query("""
            SELECT n.tekma.id, n.zaporednaSt, n.tockeDomaci, n.tockeGost
            FROM NizSrecanja n
            WHERE n.tekma.srecanje.id = :idSrecanje
            ORDER BY n.tekma.id, n.zaporednaSt
            """)
    List<Object[]> tockeZaSrecanje(@Param("idSrecanje") Long idSrecanje);

    /* Enako za nabor tekem - za statistiko tock na profilu igralca. */
    @Query("""
            SELECT n.tekma.id, n.tockeDomaci, n.tockeGost
            FROM NizSrecanja n
            WHERE n.tekma.id IN :idjiTekem
            """)
    List<Object[]> tockeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem);

    /* Nizi CELE lige, urejeni po tekmi in zaporedju - za zavihek statistike
       lige. Dvojnik tockeZaTurnir v NizRepozitorij; po ligi in ne po seznamu
       id-jev iz istega razloga (omejitev stevila vezanih parametrov). */
    @Query("""
            SELECT n.tekma.id, n.zaporednaSt, n.tockeDomaci, n.tockeGost
            FROM NizSrecanja n
            WHERE n.tekma.srecanje.liga.id = :idLiga
            ORDER BY n.tekma.id, n.zaporednaSt
            """)
    List<Object[]> tockeZaLigo(@Param("idLiga") Long idLiga);

    List<NizSrecanja> findByTekmaIdOrderByZaporednaStAsc(Long idTekma);
}
