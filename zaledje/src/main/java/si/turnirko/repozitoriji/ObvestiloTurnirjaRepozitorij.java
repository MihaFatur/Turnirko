/* Dostop do obdelanih turnirjev obvestila po turnirju in poizvedbe, ki jih
   opravilo potrebuje (zakljuceni turnirji brez obvestila, kdo je nastopil). */
package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.ObvestiloTurnirja;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Turnir;

public interface ObvestiloTurnirjaRepozitorij extends JpaRepository<ObvestiloTurnirja, Long> {

    /* Zakljuceni turnirji, ki so se koncali v oknu [od, do] in jih opravilo se ni
       obdelalo. Konec turnirja je datum konca, sicer zacetka; turnir brez
       datuma ni "pravkar zakljucen" in ne pride v postev. */
    @Query("""
            SELECT t FROM Turnir t
            WHERE t.status = si.turnirko.modeli.StatusTekmovanja.ZAKLJUCEN
              AND COALESCE(t.datumKonca, t.datumZacetka) >= :od
              AND COALESCE(t.datumKonca, t.datumZacetka) <= :doKdaj
              AND NOT EXISTS (SELECT 1 FROM ObvestiloTurnirja o WHERE o.idTurnir = t.id)
            ORDER BY COALESCE(t.datumKonca, t.datumZacetka), t.id
            """)
    List<Turnir> neobdelani(LocalDate od, LocalDate doKdaj);

    /* Igralci, prijavljeni na turnir (posamicno ali kot prvi igralec para), brez
       odjav. Z izrecnim JOIN: prijava ekipe nima igralca in bi sicer vrnila null. */
    @Query("""
            SELECT DISTINCT i.id FROM Prijava p JOIN p.igralec i
            WHERE p.dogodek.turnir.id = :idTurnir AND p.status <> :odjavljen
            """)
    List<Long> igralciPrijav(Long idTurnir, Prijava.StatusPrijave odjavljen);

    /* Drugi igralec para. */
    @Query("""
            SELECT DISTINCT i.id FROM Prijava p JOIN p.igralec2 i
            WHERE p.dogodek.turnir.id = :idTurnir AND p.status <> :odjavljen
            """)
    List<Long> soigralciPrijav(Long idTurnir, Prijava.StatusPrijave odjavljen);

    /* Igralci v kadru ekip ekipnega dogodka. */
    @Query("""
            SELECT DISTINCT k.igralec.id FROM KaderEkipe k JOIN k.ekipa e JOIN e.dogodek d
            WHERE d.turnir.id = :idTurnir
            """)
    List<Long> igralciKadra(Long idTurnir);
}
