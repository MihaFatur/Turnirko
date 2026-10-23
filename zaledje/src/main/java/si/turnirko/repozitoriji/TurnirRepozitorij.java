/* Dostop do turnirjev.

   POZOR: kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije,
   zato morajo biti kraj IN lastnistvo (klub lastnik, ustvaril) nalozeni ze v
   poizvedbi ("join fetch") - sicer dostop do njih sprozi
   LazyInitializationException. */
package si.turnirko.repozitoriji;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;

public interface TurnirRepozitorij extends JpaRepository<Turnir, Long> {

    /* Koliko turnirjev je ta organizator ustvaril od zacetka tekoce sezone -
       meja placilnega paketa (NarocninaStoritev). */
    long countByUstvarilIdAndUstvarjenObGreaterThanEqual(Long idUstvaril, LocalDateTime odSezone);

    /* Prazni turnirji iz koledarja, ki jih sme prevzeti uvoz iz Stupe
       (PrevzemKoledarja): brez vira, v danem stanju (PRIPRAVA), brez dogodkov,
       z zacetkom v danem obdobju. */
    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            WHERE t.vir IS NULL AND t.status = :status
              AND t.datumZacetka BETWEEN :od AND :doKdaj
              AND NOT EXISTS (SELECT d.id FROM Dogodek d WHERE d.turnir = t)
            ORDER BY t.datumZacetka, t.id
            """)
    List<Turnir> prazniBrezVira(LocalDate od, LocalDate doKdaj, StatusTekmovanja status);

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

    /* Turnirji, ki imajo zreb (dejanski V_TEKU), a se ne zacnejo do danes - gledalcu
       se kazejo "V pripravi" (StatusTekmovanja.zaPrikaz). Vrne samo id-je. */
    @Query("""
            SELECT t.id FROM Turnir t
            WHERE t.status = si.turnirko.modeli.StatusTekmovanja.V_TEKU
              AND t.datumZacetka > :danes
            """)
    List<Long> idjiZrebanihPrihodnjih(LocalDate danes);

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

    /* Turnir ekipne tekme, katere izid je dano srecanje (prazno pri ligi). */
    @Query("""
            SELECT t FROM Srecanje s JOIN s.tekma tk JOIN tk.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE s.id = :idSrecanje
            """)
    Optional<Turnir> najdiZLastnistvomPoSrecanju(Long idSrecanje);

    @Query("""
            SELECT t FROM TekmaSrecanja ts JOIN ts.srecanje s JOIN s.tekma tk
            JOIN tk.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE ts.id = :idTekma
            """)
    Optional<Turnir> najdiZLastnistvomPoTekmiSrecanja(Long idTekma);

    @Query("""
            SELECT t FROM Ekipa e JOIN e.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE e.id = :idEkipa
            """)
    Optional<Turnir> najdiZLastnistvomPoEkipi(Long idEkipa);

    @Query("""
            SELECT t FROM KaderEkipe k JOIN k.ekipa e JOIN e.dogodek d JOIN d.turnir t
            LEFT JOIN FETCH t.ustvaril
            LEFT JOIN FETCH t.klubLastnik
            WHERE k.id = :idKader
            """)
    Optional<Turnir> najdiZLastnistvomPoKadru(Long idKader);

    /* Vsi turnirji, ki jih je racun USTVARIL (ne tudi klubski), s krajem - vir
       organizatorskega pregleda. Isto merilo kot meja paketa (ustvaril), ne
       kot pravica urejanja (ustvaril ALI klub). Vrstni red ni zagotovljen. */
    @Query("""
            SELECT t FROM Turnir t
            LEFT JOIN FETCH t.kraj
            WHERE t.ustvaril.id = :idUstvaril
            """)
    List<Turnir> najdiZaUstvarjalca(Long idUstvaril);
}
