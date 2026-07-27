/* Dostop do kadra ekip. Igralec in njegov klub se nalozita vnaprej za DTO. */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.KaderEkipe;

public interface KaderEkipeRepozitorij extends JpaRepository<KaderEkipe, Long> {

    @Query("""
            SELECT k FROM KaderEkipe k
            JOIN FETCH k.igralec i LEFT JOIN FETCH i.klub
            WHERE k.ekipa.id = :idEkipa
            ORDER BY k.vrstniRed, i.priimek, i.ime
            """)
    List<KaderEkipe> najdiZaEkipo(Long idEkipa);

    boolean existsByEkipaIdAndIgralecId(Long idEkipa, Long idIgralec);

    /* Koliko ekip iste lige ima tega igralca v kadru - za prepoved
       dvojne registracije. */
    @Query("""
            SELECT COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.liga.id = :idLiga AND k.igralec.id = :idIgralec
            """)
    long steviloVLigi(Long idLiga, Long idIgralec);
}
