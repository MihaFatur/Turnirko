/* Dostop do posamicnih tekem srecanja. Vsi (do stirje) igralci se nalozijo
   vnaprej, ker jih bere DTO (zapisnik srecanja) izven transakcije. Vez ekipe
   na klub je LEVA: prosta ekipa kluba nima in notranji stik bi vse njene tekme
   izpustil iz profilov in lestvic.

   Srecanje pripada ligi ALI ekipni tekmi turnirja (V28). Poizvedbe, ki
   stejejo tekme POSAMEZNIKA ne glede na tekmovanje (profil, dvoboji,
   obracun), zato nalozijo ligo in tekmo z dogodkom in turnirjem LEVO -
   notranji stik na ligo bi tiho izpustil vse tekme ekipnih turnirjev.
   Poizvedbe po eni ligi (lestvice, statistika lige) jih izpustijo same. */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.TipTekmeSrecanja;

public interface TekmaSrecanjaRepozitorij extends JpaRepository<TekmaSrecanja, Long> {

    @Query("""
            SELECT t FROM TekmaSrecanja t
            LEFT JOIN FETCH t.igralecDomaci LEFT JOIN FETCH t.igralecDomaci2
            LEFT JOIN FETCH t.igralecGost LEFT JOIN FETCH t.igralecGost2
            WHERE t.srecanje.id = :idSrecanje
            ORDER BY t.zaporedje
            """)
    List<TekmaSrecanja> najdiZaSrecanje(Long idSrecanje);

    /* Za obracun (rezultat/rating): posamicna igralca, srecanje in liga vnaprej. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            LEFT JOIN FETCH t.igralecDomaci LEFT JOIN FETCH t.igralecGost
            JOIN FETCH t.srecanje s LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
            WHERE t.id = :id
            """)
    Optional<TekmaSrecanja> najdiZaObracun(Long id);

    void deleteBySrecanjeId(Long idSrecanje);

    /* Koliko tekem je igralec ze odigral (ali ima postavljenih) za ekipo -
       igralca, ki je za ekipo ze nastopil, iz kadra ne smemo odstraniti. */
    @Query("""
            SELECT COUNT(t) FROM TekmaSrecanja t JOIN t.srecanje s
            WHERE (s.ekipaDomaci.id = :idEkipa
                   AND (t.igralecDomaci.id = :idIgralec OR t.igralecDomaci2.id = :idIgralec))
               OR (s.ekipaGost.id = :idEkipa
                   AND (t.igralecGost.id = :idIgralec OR t.igralecGost2.id = :idIgralec))
            """)
    long steviloNastopovZaEkipo(@Param("idEkipa") Long idEkipa, @Param("idIgralec") Long idIgralec);

    /* Izidi vseh odigranih POSAMICNIH tekem srecanj - za zmage/poraze na
       lestvici igralcev: [idIgralcaDomaci, idIgralcaGost, zmagovalnaStran].
       Dvojic ne stejemo, ker so ekipni izid dveh igralcev na stran in jih ne
       moremo pripisati posamezniku (enako velja ze za rating). Tekme brez
       postavljenih igralcev izpadejo (pot ".id" bere tuji kljuc brez stika,
       zato pogoj IS NOT NULL), statusa NEODIGRANA (predcasni konec srecanja)
       pa ne zajamemo, ker se ni igrala.

       Stevilke in ne entitete iz istega razloga kot TekmaRepozitorij.
       izidiVsehOdigranih: vsaka nalozena tekma je nosila se posrednika
       srecanja, lestvica pa potrebuje samo igralca in stran zmagovalca. */
    @Query("""
            SELECT t.igralecDomaci.id, t.igralecGost.id, t.zmagovalecStran
            FROM TekmaSrecanja t
            WHERE t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
              AND t.igralecDomaci IS NOT NULL AND t.igralecGost IS NOT NULL
            """)
    List<Object[]> izidiVsehOdigranihPosamicnih();

    /* Vse odigrane posamicne tekme med dvema igralcema (v obeh smereh),
       najnovejse prve - za pregled "1 na 1". Liga, kolo in obe ekipi so
       nalozeni, ker jih bere DTO (oznaka tekmovanja v zgodovini dvoboja). */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
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
            JOIN FETCH t.srecanje s LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
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
       para). Ne stejejo v rating ne v osebne zmage, so pa svoj sklop profila. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s LEFT JOIN FETCH s.liga
            LEFT JOIN FETCH s.tekma tk LEFT JOIN FETCH tk.dogodek dg LEFT JOIN FETCH dg.turnir
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
       razloga kot pri ratingu: para ni mogoce pripisati posamezniku.
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

    /* Isto za srecanja ekipnih tekem enega dogodka turnirja (V28). */
    @Query("""
            SELECT s.ekipaDomaci.id, s.ekipaGost.id,
                   t.igralecDomaci.id, t.igralecGost.id, t.zmagovalecStran
            FROM TekmaSrecanja t
            JOIN t.srecanje s JOIN s.tekma tk
            JOIN t.igralecDomaci JOIN t.igralecGost
            WHERE tk.dogodek.id = :idDogodek
              AND t.tip = si.turnirko.modeli.TipTekmeSrecanja.POSAMICNA
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<Object[]> posamicniIzidiDogodka(@Param("idDogodek") Long idDogodek);

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

    /* Vse odigrane tekme ene lige - POSAMICNE in DVOJICE skupaj, za zavihek
       statistike lige. Dvojice zavihek loci sam (posamicne vrstice berejo samo
       POSAMICNA), steje pa jih v "V stevilkah" in v vrstico najuspesnejse
       dvojice.

       Vez ekipe na klub je LEVA (prosta ekipa kluba nima), igralci pa so
       vezani levo, ker tekma nepostavljene strani se vedno nosi izid srecanja
       in mora steti v napredek; vrstice brez imen statistika izpusti sama.
       Vrstni red je vrstni red igranja (kolo, srecanje, zaporedje) - iz njega
       se prepozna, katera tekma je srecanje odlocila. */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH t.igralecDomaci d1 LEFT JOIN FETCH d1.klub
            LEFT JOIN FETCH t.igralecDomaci2 d2 LEFT JOIN FETCH d2.klub
            LEFT JOIN FETCH t.igralecGost g1 LEFT JOIN FETCH g1.klub
            LEFT JOIN FETCH t.igralecGost2 g2 LEFT JOIN FETCH g2.klub
            WHERE s.liga.id = :idLiga
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            ORDER BY s.kolo, s.id, t.zaporedje
            """)
    List<TekmaSrecanja> najdiOdigraneZaLigo(@Param("idLiga") Long idLiga);

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

    /* Vse posamicne ligaske tekme, ki stejejo v Turnirko rating, s podatki za
       razvrstitev v casovno vrsto: [id, odigrano ob, predviden zacetek, kolo,
       zaporedje v srecanju]. Dvojice nikoli - izida para ni mogoce pripisati
       posamezniku. Sluzi ponovnemu preracunu ratinga. */
    @Query("""
            SELECT t.id, t.srecanje.odigranOb, t.srecanje.predvidenZacetek,
                   t.srecanje.kolo, t.zaporedje
            FROM TekmaSrecanja t
            WHERE t.tip = :tip
              AND t.srecanje.liga.raven <> si.turnirko.modeli.RavenTekmovanja.NE_STEJE
              AND t.izidTip IN :izidi
              AND t.igralecDomaci IS NOT NULL
              AND t.igralecGost IS NOT NULL
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<Object[]> ratinskeTekme(@Param("tip") TipTekmeSrecanja tip,
                                 @Param("izidi") Collection<IzidTekme> izidi);

    /* Isto za posamicne tekme EKIPNIH TEKEM TURNIRJEV (V28): [id, datum
       turnirja, faza tekme, stopnja skupine (null zunaj skupin), kolo,
       pozicija tekme, zaporedje v srecanju]. Tekma velja na dan turnirja kot
       vse turnirske tekme; vrstni red v dnevu nosijo faza, stopnja, kolo in
       mesto - brez stopnje bi kolo 1 finalne skupine padlo pred kolo 2
       predtekmovanja. */
    @Query("""
            SELECT t.id, dg.turnir.datumZacetka, tk.faza, sk.stopnja, tk.kolo, tk.pozicija, t.zaporedje
            FROM TekmaSrecanja t
            JOIN t.srecanje s JOIN s.tekma tk JOIN tk.dogodek dg
            LEFT JOIN Skupina sk ON sk.id = tk.idSkupina
            WHERE t.tip = :tip
              AND dg.turnir.raven <> si.turnirko.modeli.RavenTekmovanja.NE_STEJE
              AND t.izidTip IN :izidi
              AND t.igralecDomaci IS NOT NULL
              AND t.igralecGost IS NOT NULL
              AND t.zmagovalecStran IS NOT NULL
            """)
    List<Object[]> ratinskeTekmeTurnirskihSrecanj(@Param("tip") TipTekmeSrecanja tip,
                                                  @Param("izidi") Collection<IzidTekme> izidi);

    /* Vse odigrane tekme srecanj enega ekipnega DOGODKA turnirja - za zavihek
       statistike turnirja (isti obseg kot najdiOdigraneZaLigo pri ligi). */
    @Query("""
            SELECT t FROM TekmaSrecanja t
            JOIN FETCH t.srecanje s JOIN FETCH s.tekma tk JOIN FETCH tk.dogodek dg
            JOIN FETCH s.ekipaDomaci ed LEFT JOIN FETCH ed.klub
            JOIN FETCH s.ekipaGost eg LEFT JOIN FETCH eg.klub
            LEFT JOIN FETCH t.igralecDomaci d1 LEFT JOIN FETCH d1.klub
            LEFT JOIN FETCH t.igralecDomaci2 d2 LEFT JOIN FETCH d2.klub
            LEFT JOIN FETCH t.igralecGost g1 LEFT JOIN FETCH g1.klub
            LEFT JOIN FETCH t.igralecGost2 g2 LEFT JOIN FETCH g2.klub
            WHERE dg.turnir.id = :idTurnir
              AND t.status = si.turnirko.modeli.StatusTekmeSrecanja.KONCANA
              AND t.zmagovalecStran IS NOT NULL
            ORDER BY tk.id, t.zaporedje
            """)
    List<TekmaSrecanja> najdiOdigraneZaTurnir(@Param("idTurnir") Long idTurnir);

}
