/* Dostop do izbora igralcev, ki jih uporabnik spremlja. */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.SpremljanIgralec;

public interface SpremljanIgralecRepozitorij
        extends JpaRepository<SpremljanIgralec, SpremljanIgralec.Kljuc> {

    /* Id-ji igralcev, ki jih racun spremlja, v vrstnem redu dodajanja. */
    @Query("""
            SELECT s.idIgralec FROM SpremljanIgralec s
            WHERE s.idRacun = :idRacun
            ORDER BY s.dodanoOb, s.idIgralec
            """)
    List<Long> idjiZaRacun(Long idRacun);

    void deleteByIdRacunAndIdIgralec(Long idRacun, Long idIgralec);

    boolean existsByIdRacunAndIdIgralec(Long idRacun, Long idIgralec);

    long countByIdRacun(Long idRacun);

    /* Zapisi (racun, igralec) za dane igralce - kdo vse jih spremlja (obvestilo po
       turnirju). */
    List<SpremljanIgralec> findByIdIgralecIn(java.util.Collection<Long> idjiIgralcev);
}
