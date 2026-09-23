/* Dostop do ekip. Klub se nalozi vnaprej (join fetch), ker ga bere DTO
   (prikazano ime ekipe) izven transakcije. Vez je LEVA: prosta ekipa kluba
   nima in bi jo notranji stik tiho izpustil iz razporeda in lestvice. */
package si.turnirko.repozitoriji;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Ekipa;

public interface EkipaRepozitorij extends JpaRepository<Ekipa, Long> {

    /* Razvrsca po imenu kluba, prosto ekipo pa po njenem lastnem imenu, da se
       oba tipa v seznamu prepletata po abecedi in ne v dveh kupih. Abecedo
       (slovensko) uredi Java, ker SQLite besedilo primerja po kodnih tockah
       (Č, Š, Ž za Z) - glej Ekipa.PO_ABECEDI. */
    default List<Ekipa> najdiZaLigo(Long idLiga) {
        List<Ekipa> ekipe = new ArrayList<>(najdiZaLigoNeurejene(idLiga));
        ekipe.sort(Ekipa.PO_ABECEDI);
        return ekipe;
    }

    /* Le za najdiZaLigo(): vrstni red je vrstni red vpisa. */
    @Query("""
            SELECT e FROM Ekipa e LEFT JOIN FETCH e.klub
            WHERE e.liga.id = :idLiga
            ORDER BY e.id
            """)
    List<Ekipa> najdiZaLigoNeurejene(Long idLiga);

    /* Ekipe lige po jakostnem vrstnem redu (1 = najmocnejsa). Uporablja se pri
       ligi z enakomerno razvrstitvijo - tam seznam ni abecedni sifrant, ampak
       jakostna lestvica, iz katere zreb sestavi pare. Ekipe brez mesta gredo na
       konec (stare lige, ki mest nimajo), za njimi odloca vrstni red vpisa. */
    @Query("""
            SELECT e FROM Ekipa e LEFT JOIN FETCH e.klub k
            WHERE e.liga.id = :idLiga
            ORDER BY CASE WHEN e.stNosilca IS NULL THEN 1 ELSE 0 END, e.stNosilca, e.id
            """)
    List<Ekipa> najdiZaLigoPoJakosti(Long idLiga);

    @Query("SELECT e FROM Ekipa e LEFT JOIN FETCH e.klub JOIN FETCH e.liga WHERE e.id = :id")
    Optional<Ekipa> najdiZKlubomInLigo(Long id);

    /* Ekipe ekipnega dogodka turnirja (V28), po abecedi kot pri ligi. */
    default List<Ekipa> najdiZaDogodek(Long idDogodek) {
        List<Ekipa> ekipe = new ArrayList<>(najdiZaDogodekNeurejene(idDogodek));
        ekipe.sort(Ekipa.PO_ABECEDI);
        return ekipe;
    }

    /* Le za najdiZaDogodek(): vrstni red je vrstni red vpisa. */
    @Query("""
            SELECT e FROM Ekipa e LEFT JOIN FETCH e.klub
            WHERE e.dogodek.id = :idDogodek
            ORDER BY e.id
            """)
    List<Ekipa> najdiZaDogodekNeurejene(Long idDogodek);

    @Query("""
            SELECT e FROM Ekipa e LEFT JOIN FETCH e.klub
            JOIN FETCH e.dogodek d JOIN FETCH d.turnir
            WHERE e.id = :id
            """)
    Optional<Ekipa> najdiZKlubomInDogodkom(Long id);

    boolean existsByLigaIdAndKlubIdAndZaporedna(Long idLiga, Long idKlub, int zaporedna);

    long countByLigaId(Long idLiga);
}
