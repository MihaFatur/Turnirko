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

    /* Nove stevke za ucenje. Stevkam vadnice se ob odobritvi cas nastavi na cas
       odobritve (odobri), zato stejejo tudi, ce je bil list fotografiran pred
       zadnjim ucenjem. */
    long countByOdobrenoTrueAndUstvarjenoObAfter(LocalDateTime od);

    long countByOdobrenoTrue();

    boolean existsByIdBranje(Long idBranje);

    /* Stevke potrjenih listkov (popravek izida tekme, F6.1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StevkaListka s WHERE s.idBranje = :idBranje")
    int izbrisiBranje(@Param("idBranje") Long idBranje);

    /* Celice listka, ki so bile mocne oznake (organizator jih je popravil ob potrditvi). */
    @Query("SELECT DISTINCT s.celica FROM StevkaListka s WHERE s.idBranje = :idBranje AND s.popravljena = true AND s.celica IS NOT NULL")
    List<Short> mocneCelice(@Param("idBranje") Long idBranje);

    /* Najnovejse odobrene stevke (ucenje vzame zgornjo mejo, da noc ostane
       kratka). Neodobrene stevke vadnice v ucenje ne gredo. Po casu (pri vadnici je
       to cas odobritve), ne po id: list, fotografiran davno in odobren danes, je nov. */
    @Query("SELECT s FROM StevkaListka s WHERE s.odobreno = true ORDER BY s.ustvarjenoOb DESC, s.id DESC")
    List<StevkaListka> najnovejse(Pageable stran);

    /* Stevke lista vadnice za pregled (po okencih, v okencu od leve). */
    List<StevkaListka> findByIdVadnicaOrderByCelicaAscIdAsc(Long idVadnica);

    long countByIdVadnica(Long idVadnica);

    /* Odobrene stevke vadnice (v ucenju, dokler jih hramba ne izbrise). */
    long countByIdVadnicaIsNotNullAndOdobrenoTrue();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StevkaListka s WHERE s.idVadnica = :idVadnica")
    int izbrisiVadnice(@Param("idVadnica") Long idVadnica);

    /* Izlocene stevke lista (napacno napisane ali narobe razrezane). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StevkaListka s WHERE s.idVadnica = :idVadnica AND s.id IN :id")
    int izlociVadnice(@Param("idVadnica") Long idVadnica, @Param("id") List<Long> id);

    /* Odobren list gre v ucenje: cas je cas odobritve (glej countBy...). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE StevkaListka s SET s.odobreno = true, s.ustvarjenoOb = :zdaj WHERE s.idVadnica = :idVadnica")
    int odobriVadnico(@Param("idVadnica") Long idVadnica, @Param("zdaj") LocalDateTime zdaj);

    /* Izbrise vse razen `ohrani` najnovejsih stevk (hramba). Ce jih je manj,
       podpoizvedba vrne NULL in ne izbrise nicesar. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM stevka_listka
            WHERE id <= (SELECT id FROM stevka_listka ORDER BY id DESC OFFSET :ohrani LIMIT 1)
            """, nativeQuery = true)
    int izbrisiRazenNajnovejsih(@Param("ohrani") int ohrani);

    /* Povprecno stevilo stevk na potrjen listek (ocena »listki brez napake« na strani
       ucenja); null, ce stevk listkov se ni. */
    @Query(value = """
            SELECT CAST(avg(n) AS double precision) FROM (SELECT count(*) AS n FROM stevka_listka
                                WHERE id_branje IS NOT NULL GROUP BY id_branje) t
            """, nativeQuery = true)
    Double povprecnoStevkNaListek();
}
