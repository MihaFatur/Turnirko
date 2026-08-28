/* Dostop do turnirjev.

   POZOR: kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije,
   zato morajo biti kraj IN lastnistvo (klub lastnik, ustvaril) nalozeni ze v
   poizvedbi ("join fetch") - sicer dostop do njih sprozi
   LazyInitializationException. */
package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Turnir;

public interface TurnirRepozitorij extends JpaRepository<Turnir, Long> {

    /* Vsi turnirji s krajem in lastnistvom, najnovejsi najprej. */
    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            LEFT JOIN FETCH t.klubLastnik
            LEFT JOIN FETCH t.ustvaril
            ORDER BY t.ustvarjenOb DESC
            """)
    List<Turnir> najdiVseSKrajem();

    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            LEFT JOIN FETCH t.klubLastnik
            LEFT JOIN FETCH t.ustvaril
            WHERE t.id = :id
            """)
    Optional<Turnir> najdiSKrajem(Long id);

    /* Turnirji, ki se dotikajo obdobja - dovolj je, da vanj sega en sam dan.
       Turnir brez datuma zacetka v koledar ne sodi (nekaj takih je v uvozeni
       zgodovini); tak ostane le v seznamu turnirjev.

       COALESCE, ker je datum konca neobvezen: enodnevni turnir ima samo
       zacetek in bi se s praznim koncem iz vsakega obdobja izpustil. */
    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            LEFT JOIN FETCH t.klubLastnik
            WHERE t.datumZacetka IS NOT NULL
              AND t.datumZacetka <= :doKdaj
              AND COALESCE(t.datumKonca, t.datumZacetka) >= :od
            ORDER BY t.datumZacetka, t.id
            """)
    List<Turnir> najdiVObdobju(LocalDate od, LocalDate doKdaj);

    // ---------- Lastnistvo (za preverjanje pravice organizatorja) ----------
    // Vse variante nalozijo ustvaril + klubLastnik; po njiju LastnistvoStoritev
    // razsodi, kdo sme urejati. Otroske poti (dogodek/prijava/tekma) razresijo
    // pripadajoci turnir, da preverba deluje na vseh vstopnih tockah.

    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE t.id = :id
            """)
    Optional<Turnir> najdiZLastnistvom(Long id);

    @Query("""
            SELECT t FROM Dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE d.id = :idDogodek
            """)
    Optional<Turnir> najdiZLastnistvomPoDogodku(Long idDogodek);

    @Query("""
            SELECT t FROM Prijava p JOIN p.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE p.id = :idPrijava
            """)
    Optional<Turnir> najdiZLastnistvomPoPrijavi(Long idPrijava);

    @Query("""
            SELECT t FROM Tekma tk JOIN tk.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE tk.id = :idTekma
            """)
    Optional<Turnir> najdiZLastnistvomPoTekmi(Long idTekma);
}
