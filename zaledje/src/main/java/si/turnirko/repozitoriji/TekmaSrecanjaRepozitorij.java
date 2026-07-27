/* Dostop do posamicnih tekem srecanja. Vsi (do stirje) igralci se nalozijo
   vnaprej, ker jih bere DTO (zapisnik srecanja) izven transakcije. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.TekmaSrecanja;

public interface TekmaSrecanjaRepozitorij extends JpaRepository<TekmaSrecanja, Long> {

    @Query("""
            SELECT t FROM TekmaSrecanja t
            LEFT JOIN FETCH t.igralecDomaci LEFT JOIN FETCH t.igralecDomaci2
            LEFT JOIN FETCH t.igralecGost LEFT JOIN FETCH t.igralecGost2
            WHERE t.srecanje.id = :idSrecanje
            ORDER BY t.zaporedje
            """)
    List<TekmaSrecanja> najdiZaSrecanje(Long idSrecanje);

    /* Za obracun (rezultat/ELO): posamicna igralca, srecanje in liga vnaprej. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            LEFT JOIN FETCH t.igralecDomaci LEFT JOIN FETCH t.igralecGost
            JOIN FETCH t.srecanje s JOIN FETCH s.liga
            WHERE t.id = :id
            """)
    Optional<TekmaSrecanja> najdiZaObracun(Long id);

    void deleteBySrecanjeId(Long idSrecanje);

    /* Vse odigrane POSAMICNE tekme lig - za zmage/poraze na lestvici igralcev.
       Dvojic ne stejemo, ker so ekipni izid dveh igralcev na stran in jih ne
       moremo pripisati posamezniku (enako velja ze za ELO). Notranji "join
       fetch" obenem izpusti tekme brez postavljenih igralcev, statusa
       NEODIGRANA (predcasni konec srecanja) pa ne zajamemo, ker se ni igrala. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.igralecDomaci JOIN FETCH t.igralecGost
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<TekmaSrecanja> najdiVseOdigranePosamicne();

    /* Vse odigrane posamicne tekme med dvema igralcema (v obeh smereh),
       najnovejse prve - za pregled "1 na 1". Liga, kolo in obe ekipi so
       nalozeni, ker jih bere DTO (oznaka tekmovanja v zgodovini dvoboja). */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s JOIN FETCH s.liga
            JOIN FETCH s.ekipaDomaci ed JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg JOIN FETCH eg.klub
            JOIN FETCH t.igralecDomaci JOIN FETCH t.igralecGost
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND ((t.igralecDomaci.id = :prvi AND t.igralecGost.id = :drugi)
                OR (t.igralecDomaci.id = :drugi AND t.igralecGost.id = :prvi))
            ORDER BY t.id DESC
            """)
    List<TekmaSrecanja> najdiDvoboje(@Param("prvi") Long prvi, @Param("drugi") Long drugi);

    /* Vse odigrane POSAMICNE ligaske tekme enega igralca (doma ali v gosteh),
       najnovejse prve - za profil igralca. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s JOIN FETCH s.liga
            JOIN FETCH s.ekipaDomaci ed JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg JOIN FETCH eg.klub
            JOIN FETCH t.igralecDomaci id LEFT JOIN FETCH id.klub
            JOIN FETCH t.igralecGost ig LEFT JOIN FETCH ig.klub
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND (t.igralecDomaci.id = :idIgralec OR t.igralecGost.id = :idIgralec)
            ORDER BY t.id DESC
            """)
    List<TekmaSrecanja> najdiPosamicneZaIgralca(@Param("idIgralec") Long idIgralec);

    /* Odigrane ligaske DVOJICE, v katerih je igralec nastopil (kot eden od
       para). Ne stejejo v ELO ne v osebne zmage, so pa svoj sklop profila. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s JOIN FETCH s.liga
            JOIN FETCH s.ekipaDomaci ed JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg JOIN FETCH eg.klub
            LEFT JOIN FETCH t.igralecDomaci LEFT JOIN FETCH t.igralecDomaci2
            LEFT JOIN FETCH t.igralecGost LEFT JOIN FETCH t.igralecGost2
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.DVOJICE
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND (t.igralecDomaci.id = :idIgralec OR t.igralecDomaci2.id = :idIgralec
                OR t.igralecGost.id = :idIgralec OR t.igralecGost2.id = :idIgralec)
            ORDER BY t.id DESC
            """)
    List<TekmaSrecanja> najdiDvojiceZaIgralca(@Param("idIgralec") Long idIgralec);

    /* Sestevek dobljenih nizov po srecanjih lige (samo koncane tekme) - za
       kriterij izenacenja "razlika nizov". Vrne [idSrecanja, niziDomaci, niziGost]. */
    @Query("""
            SELECT t.srecanje.id, SUM(t.dobljeniNiziDomaci), SUM(t.dobljeniNiziGost)
            FROM TekmaSrecanja t
            WHERE t.srecanje.liga.id = :idLiga
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
            GROUP BY t.srecanje.id
            """)
    List<Object[]> niziPoSrecanjih(@Param("idLiga") Long idLiga);
}
