/* Dostop do igralcev. */
package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Igralec;

public interface IgralecRepozitorij extends JpaRepository<Igralec, Long> {

    /* Vsi aktivni (nearhivirani) igralci s klubom in krajem v eni poizvedbi -
       "join fetch" prepreci N+1 poizvedb pri izpisu seznama. */
    @Query("""
            SELECT i FROM Igralec i
            LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH i.kraj
            WHERE i.arhiviran = false
            ORDER BY i.priimek, i.ime
            """)
    List<Igralec> najdiAktivne();

    @Query("""
            SELECT i FROM Igralec i
            LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH i.kraj
            WHERE i.id = :id
            """)
    Optional<Igralec> najdiZVsem(Long id);

    /* Istovetnost pri uvozu (IdentitetaStupe): licenca NTZS poveze osebo
       samo skupaj z datumom rojstva in spolom, zato vrne igralca, ne
       odlocitve. */
    Optional<Igralec> findByNtzsLicenca(String ntzsLicenca);

    boolean existsByNtzsLicenca(String ntzsLicenca);

    /* Kandidati za isto osebo po datumu rojstva (pravi datum ali 1. januar
       letnika, ki ga je zapisala stara stran NTZS). */
    @Query("SELECT i FROM Igralec i LEFT JOIN FETCH i.klub WHERE i.datumRojstva IN :datumi")
    List<Igralec> najdiPoDatumihRojstva(Collection<LocalDate> datumi);

    /* Razdeljena imena registra - slovar za razdelitev novih imen. */
    @Query("SELECT i.ime, i.priimek FROM Igralec i")
    List<Object[]> imenaInPriimki();
}
