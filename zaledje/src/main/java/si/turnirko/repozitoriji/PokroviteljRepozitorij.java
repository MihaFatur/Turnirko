/* Dostop do pokroviteljev lig in turnirjev (V56). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Pokrovitelj;

public interface PokroviteljRepozitorij extends JpaRepository<Pokrovitelj, Long> {

    /* Seznam se zapiše v celoti: stari se pobriše prek teh entitet in izpere
       (PokroviteljiStoritev) - množični DELETE bi pustil stare v seji, SQLite pa
       njihove id-je da novim vrsticam. */
    List<Pokrovitelj> findByLigaIdOrderByVrstniRed(Long idLiga);

    List<Pokrovitelj> findByTurnirIdOrderByVrstniRed(Long idTurnir);

    /* Koliko pokroviteljev nosi sliko - del pogoja za izbris iz knjižnice. */
    long countBySlikaId(Long idSlika);
}
