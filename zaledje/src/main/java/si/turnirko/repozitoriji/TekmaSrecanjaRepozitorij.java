/* Dostop do posamicnih tekem srecanja. Vsi (do stirje) igralci se nalozijo
   vnaprej, ker jih bere DTO (zapisnik srecanja) izven transakcije. Vez ekipe
   na klub je LEVA: prosta ekipa kluba nima in notranji stik bi vse njene tekme
   izpustil iz profilov in lestvic. */
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
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            JOIN FETCH t.igralecDomaci JOIN FETCH t.igralecGost
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND ((t.igralecDomaci.id = :prvi AND t.igralecGost.id = :drugi)
                OR (t.igralecDomaci.id = :drugi AND t.igralecGost.id = :prvi))
            ORDER BY t.id DESC
            """)
    List<TekmaSrecanja> najdiDvoboje(@Param("prvi") Long prvi, @Param("drugi") Long drugi);

    /* Idji aktivnih igralcev z vsaj eno odigrano posamicno ligasko tekmo -
       prva polovica zreba nakljucnega para na domaci strani. Entitetni stik
       (JOIN Igralec i ON ...) zajame obe strani tekme v ENI poizvedbi.

       Merilo odigranosti mora biti isto kot v najdiDvoboje - sicer bi zreb
       ponudil par, ki mu pregled "1 na 1" pokaze 0 : 0. */
    @Query("""
            SELECT DISTINCT i.id FROM TekmaSrecanja t
            JOIN Igralec i ON i = t.igralecDomaci OR i = t.igralecGost
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND i.arhiviran = false
            """)
    List<Long> idjiZOdigranoTekmo();

    /* Idji aktivnih igralcev, s katerimi je dani igralec ze odigral posamicno
       ligasko tekmo - druga polovica zreba. */
    @Query("""
            SELECT DISTINCT CASE WHEN d.id = :id THEN g.id ELSE d.id END
            FROM TekmaSrecanja t
            JOIN t.igralecDomaci d JOIN t.igralecGost g
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND (d.id = :id OR g.id = :id)
              AND d.arhiviran = false AND g.arhiviran = false
            """)
    List<Long> nasprotniki(@Param("id") Long id);

    /* Vse odigrane POSAMICNE ligaske tekme enega igralca (doma ali v gosteh),
       najnovejse prve - za profil igralca. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s JOIN FETCH s.liga
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
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
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
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

    /* Izidi vseh odigranih POSAMICNIH tekem ene lige kot [idEkipaDomaci,
       idEkipaGost, idIgralecDomaci, idIgralecGost, zmagovalecStran] - iz tega
       se sesteje bilanca vsakega igralca PRI EKIPI, za katero je nastopil
       (prikaz kadra pod vrstico lestvice). Ekipi sta v izbiri zato, ker sme
       liga brez prepovedi dvojne registracije istega igralca voditi v dveh
       kadrih, njegov izkupicek pa tam ni isti. Dvojice odpadejo iz istega
       razloga kot pri ELO: para ni mogoce pripisati posamezniku.
       Vrnemo samo identifikatorje, zato "join fetch" ni potreben; JOIN vseeno
       zapisemo izrecno, da tekma brez postavljenega igralca izpade. */
    @Query("""
            SELECT s.ekipaDomaci.id, s.ekipaGost.id,
                   t.igralecDomaci.id, t.igralecGost.id, t.zmagovalecStran
            FROM TekmaSrecanja t
            JOIN t.srecanje s
            JOIN t.igralecDomaci JOIN t.igralecGost
            WHERE s.liga.id = :idLiga
              AND t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<Object[]> posamicniIzidiLige(@Param("idLiga") Long idLiga);

    /* Odigrane POSAMICNE tekme ene lige s celim kontekstom vrstice (oba igralca
       in obe ekipi) - za lestvico posameznikov te lige. Loceno od
       posamicniIzidiLige, ki vraca same identifikatorje: tam gre za sestevek
       bilanc, tu pa vrstica pokaze ime igralca in ekipo, za katero je nastopal.
       Nizi so v sami tekmi, zato dodatne poizvedbe ni. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            JOIN FETCH t.igralecDomaci JOIN FETCH t.igralecGost
            WHERE s.liga.id = :idLiga
              AND t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<TekmaSrecanja> najdiPosamicneLige(@Param("idLiga") Long idLiga);

    /* Odigrane tekme DVOJIC ene lige - za lestvico dvojic. Vsi stirje igralci so
       vezani z navadnim "join fetch" (in ne "left"), zato tekma, pri kateri par
       ni v celoti postavljen, izpade sama: brez obeh imen dvojice ni. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            JOIN FETCH t.igralecDomaci JOIN FETCH t.igralecDomaci2
            JOIN FETCH t.igralecGost JOIN FETCH t.igralecGost2
            WHERE s.liga.id = :idLiga
              AND t.tip = si.turnirko.modeli.TipTekmeSrecanja.DVOJICE
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<TekmaSrecanja> najdiDvojiceLige(@Param("idLiga") Long idLiga);

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
