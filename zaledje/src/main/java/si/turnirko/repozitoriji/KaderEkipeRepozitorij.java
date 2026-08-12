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

    /* Velikost kadra vsake ekipe v ligi kot [idEkipa, stevilo] - seznam ekip to
       pokaze v vrstici, brez poizvedbe na kader vsake ekipe posebej. Ekipa s
       praznim kadrom v rezultatu ne nastopa. */
    @Query("""
            SELECT k.ekipa.id, COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.liga.id = :idLiga
            GROUP BY k.ekipa.id
            """)
    List<Object[]> steviloPoEkipah(Long idLiga);

    /* V katerih ligah igralci nastopajo: [idIgralca, idLige]. Filter "Moje
       lige" na lestvici mora vedeti, kdo v spremljanih ligah sploh igra. */
    @Query("SELECT DISTINCT k.igralec.id, k.ekipa.liga.id FROM KaderEkipe k")
    List<Object[]> ligePoIgralcih();

    /* Koliko ekip iste lige ima tega igralca v kadru - za prepoved
       dvojne registracije. */
    @Query("""
            SELECT COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.liga.id = :idLiga AND k.igralec.id = :idIgralec
            """)
    long steviloVLigi(Long idLiga, Long idIgralec);
}
