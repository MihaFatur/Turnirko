/* Dostop do tekem, vkljucno z atomarnimi posodobitvami za napredovanje.

   Zakaj atomarne posodobitve: ko se koncata dve sosednji tekmi hkrati,
   oba niti vpisujeta zmagovalca v ISTO tekmo naslednjega kola (vsaka v svoj
   slot). Navadno "preberi-spremeni-shrani" bi lahko en vpis tiho izgubilo;
   pogojni UPDATE na ravni baze tega ne dopusca.

   DVOJICE: vsaka poizvedba, ki hrani statistiko ali medsebojne izide
   POSAMEZNIKA, mora omejiti disciplino na POSAMICNO. Izida para ni mogoce
   pripisati posamezniku (isto pravilo kot pri ELO in ligaskih dvojicah), pri
   dvojicah pa bi tako poizvedba upostevala se samo prvega clana para - drugi
   je v prijavi v drugem stolpcu. Poizvedbe, ki opisujejo POTEK tekmovanja
   (zadnji izid turnirja, zadnje kolo), pa dvojice nasprotno vkljucujejo in
   zato nalozijo tudi drugega clana. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

public interface TekmaRepozitorij extends JpaRepository<Tekma, Long> {

    boolean existsByDogodekId(Long idDogodek);

    long countByDogodekIdAndStatusNot(Long idDogodek, StatusTekme status);

    /* Napredek po turnirjih: [idTurnir, vseh tekem, odigranih]. Ena skupinska
       poizvedba za cel seznam turnirjev (brez nje bi pas "Danes v dvorani"
       delal N+1). Odigrana je tu vsaka koncana tekma - palica meri potek
       tekmovanja, ne statistike igralca, zato prosti prehod steje. */
    @Query("""
            SELECT t.dogodek.turnir.id, COUNT(t),
                   SUM(CASE WHEN t.status = si.turnirko.modeli.StatusTekme.KONCANA THEN 1 ELSE 0 END)
            FROM Tekma t
            GROUP BY t.dogodek.turnir.id
            """)
    List<Object[]> stejPoTurnirjih();

    /* Kje se turnirji trenutno igrajo: za vsak (turnir, dogodek, sistem, faza)
       najnizje se neodigrano kolo in koliko takih tekem je. Iz tega
       PovzetkiStoritev sestavi besedno fazo turnirja ("skupine",
       "cetrtfinale", "3. kolo") brez poizvedbe na vsak turnir posebej.
       Vrne [idTurnir, idDogodek, sistem, faza, najnizje kolo, stevilo]. */
    @Query("""
            SELECT t.dogodek.turnir.id, t.dogodek.id, t.dogodek.sistemTekmovanja, t.faza,
                   MIN(t.kolo), COUNT(t)
            FROM Tekma t
            WHERE t.status <> si.turnirko.modeli.StatusTekme.KONCANA
            GROUP BY t.dogodek.turnir.id, t.dogodek.id, t.dogodek.sistemTekmovanja, t.faza
            """)
    List<Object[]> neodigranaKolaPoDogodkih();

    /* Zadnje kolo glavnega (izlocilnega) dela vsakega dogodka: [idDogodek,
       najvisje kolo]. Sele razlika do njega pove, ali je kolo cetrtfinale ali
       osmina finala. */
    @Query("""
            SELECT t.dogodek.id, MAX(t.kolo) FROM Tekma t
            WHERE t.faza = si.turnirko.modeli.FazaTekme.GLAVNI
            GROUP BY t.dogodek.id
            """)
    List<Object[]> zadnjaKolaPoDogodkih();

    /* Zadnja dejansko odigrana tekma VSAKEGA turnirja (najvisji id v turnirju),
       z obema stranema - vrstica turnirja pokaze "Vrhovnik 3:1 Kramar".
       Dvojice stejejo (gre za potek turnirja, ne za statistiko igralca),
       zato je nalozen tudi drugi clan para. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec LEFT JOIN FETCH p1.igralec2
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec LEFT JOIN FETCH p2.igralec2
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.id IN (
                SELECT MAX(z.id) FROM Tekma z
                WHERE z.status = si.turnirko.modeli.StatusTekme.KONCANA
                  AND (z.izidTip IS NULL OR z.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                          si.turnirko.modeli.IzidTekme.PREDAJA))
                  AND z.prijava1 IS NOT NULL AND z.prijava2 IS NOT NULL
                GROUP BY z.dogodek.turnir.id)
            """)
    List<Tekma> najdiZadnjeVsakegaTurnirja();

    /* Isto po dogodkih enega turnirja: [idDogodek, vseh tekem, odigranih]. */
    @Query("""
            SELECT t.dogodek.id, COUNT(t),
                   SUM(CASE WHEN t.status = si.turnirko.modeli.StatusTekme.KONCANA THEN 1 ELSE 0 END)
            FROM Tekma t
            WHERE t.dogodek.turnir.id = :idTurnir
            GROUP BY t.dogodek.id
            """)
    List<Object[]> stejPoDogodkihTurnirja(Long idTurnir);

    /* Vse tekme dogodka z igralci in klubi v eni poizvedbi, urejene za
       prikaz mreze. Klub mora biti nalozen, ker gre rezultat v DTO
       izven transakcije (glej komentar v TurnirRepozitorij). */
    @Query("""
            SELECT t FROM Tekma t
            LEFT JOIN FETCH t.prijava1 p1 LEFT JOIN FETCH p1.igralec LEFT JOIN FETCH p1.klubObPrijavi
            LEFT JOIN FETCH p1.igralec2 LEFT JOIN FETCH p1.klubObPrijavi2
            LEFT JOIN FETCH t.prijava2 p2 LEFT JOIN FETCH p2.igralec LEFT JOIN FETCH p2.klubObPrijavi
            LEFT JOIN FETCH p2.igralec2 LEFT JOIN FETCH p2.klubObPrijavi2
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.dogodek.id = :idDogodek
            ORDER BY t.faza, t.kolo, t.pozicija
            """)
    List<Tekma> najdiZaDogodek(Long idDogodek);

    /* Tekma z vsem, kar potrebujeta vnos rezultata in izpis v DTO. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            LEFT JOIN FETCH t.prijava1 p1 LEFT JOIN FETCH p1.igralec LEFT JOIN FETCH p1.klubObPrijavi
            LEFT JOIN FETCH p1.igralec2 LEFT JOIN FETCH p1.klubObPrijavi2
            LEFT JOIN FETCH t.prijava2 p2 LEFT JOIN FETCH p2.igralec LEFT JOIN FETCH p2.klubObPrijavi
            LEFT JOIN FETCH p2.igralec2 LEFT JOIN FETCH p2.klubObPrijavi2
            WHERE t.id = :id
            """)
    Optional<Tekma> najdiZVsem(Long id);

    /* Tekme, v katere se napreduje iz dane tekme (eksplicitne povezave). */
    @Query("SELECT t FROM Tekma t WHERE t.idIzvorTekma1 = :idTekme OR t.idIzvorTekma2 = :idTekme")
    List<Tekma> najdiOdvisne(Long idTekme);

    /* Vse DEJANSKO ODIGRANE koncane tekme z igralci - za globalno statistiko
       in lestvico igralcev.

       Merilo je IzidTekme.jeOdigrana(): prosti prehod, neprihod (w.o.) in
       diskvalifikacija niso odigrane tekme in ne smejo v statistiko igralca.
       Isto merilo velja za vse poizvedbe v tej datoteki, ki hranijo
       statistiko ali prikazujejo rezultate kot odigrane.

       Dvojice so izpuscene - njihovega izida ni mogoce pripisati posamezniku. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN t.dogodek d
            LEFT JOIN FETCH t.prijava1 p1 LEFT JOIN FETCH p1.igralec
            LEFT JOIN FETCH t.prijava2 p2 LEFT JOIN FETCH p2.igralec
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
            """)
    List<Tekma> najdiVseOdigrane();

    /* Vse dejansko odigrane tekme enega turnirja, cez vse njegove dogodke -
       za zavihek statistike turnirja. Dvojice so tu VKLJUCENE: zavihek jih
       loci sam (posamicne vrstice berejo samo POSAMICNO), steje pa jih v
       "V stevilkah" in v vrstico najuspesnejse dvojice.

       Nalozeno je vse, kar bere DTO izven transakcije: dogodek (ime, sistem,
       disciplina), oba udelezenca z morebitnim soigralcem in kluboma OB
       PRIJAVI. Klub je posnetek in ne trenutni klub igralca - vrstica pove,
       za kateri klub je igralec takrat nastopal. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec LEFT JOIN FETCH p1.igralec2
            LEFT JOIN FETCH p1.klubObPrijavi LEFT JOIN FETCH p1.klubObPrijavi2
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec LEFT JOIN FETCH p2.igralec2
            LEFT JOIN FETCH p2.klubObPrijavi LEFT JOIN FETCH p2.klubObPrijavi2
            LEFT JOIN FETCH t.zmagovalec
            WHERE d.turnir.id = :idTurnir
              AND t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND t.zmagovalec IS NOT NULL
            ORDER BY t.id
            """)
    List<Tekma> najdiOdigraneZaTurnir(Long idTurnir);

    /* Najvisje kolo glavnega (izlocilnega) dela po dogodkih ENEGA turnirja:
       [idDogodek, najvisje kolo]. Sele razlika do njega pove, ali je kolo
       cetrtfinale ali osmina finala - statistika iz tega sestavi kontekst
       vrstice ("Clani - polfinale"). */
    @Query("""
            SELECT t.dogodek.id, MAX(t.kolo) FROM Tekma t
            WHERE t.dogodek.turnir.id = :idTurnir
              AND t.faza = si.turnirko.modeli.FazaTekme.GLAVNI
            GROUP BY t.dogodek.id
            """)
    List<Object[]> zadnjaKolaTurnirja(Long idTurnir);

    /* Vse odigrane tekme med dvema igralcema (v obeh smereh), najnovejse
       prve, s turnirjem in dogodkom - za pregled "1 na 1". */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
              AND ((p1.igralec.id = :prvi AND p2.igralec.id = :drugi)
                OR (p1.igralec.id = :drugi AND p2.igralec.id = :prvi))
            ORDER BY t.id DESC
            """)
    List<Tekma> najdiDvoboje(Long prvi, Long drugi);

    /* Idji aktivnih igralcev z vsaj eno odigrano turnirsko tekmo - prva
       polovica zreba nakljucnega para na domaci strani. Entitetni stik
       (JOIN Prijava p ON ...) zajame obe strani tekme v ENI poizvedbi;
       loceni poizvedbi za prijava1 in prijava2 bi bili ista poizvedba dvakrat.

       Merilo odigranosti mora biti isto kot v najdiDvoboje - sicer bi zreb
       ponudil par, ki mu pregled "1 na 1" pokaze 0 : 0. */
    @Query("""
            SELECT DISTINCT i.id FROM Tekma t
            JOIN t.dogodek d
            JOIN Prijava p ON p = t.prijava1 OR p = t.prijava2
            JOIN p.igralec i
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
              AND i.arhiviran = false
            """)
    List<Long> idjiZOdigranoTekmo();

    /* Idji aktivnih igralcev, s katerimi je dani igralec ze odigral turnirsko
       tekmo - druga polovica zreba. Obe strani morata biti aktivni: para z
       arhiviranim igralcem semafor ne zna izpisati. */
    @Query("""
            SELECT DISTINCT CASE WHEN i1.id = :id THEN i2.id ELSE i1.id END
            FROM Tekma t
            JOIN t.dogodek d
            JOIN t.prijava1 p1 JOIN p1.igralec i1
            JOIN t.prijava2 p2 JOIN p2.igralec i2
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
              AND (i1.id = :id OR i2.id = :id)
              AND i1.arhiviran = false AND i2.arhiviran = false
            """)
    List<Long> nasprotniki(Long id);

    /* Zadnje dejansko odigrane tekme cez vse dogodke (najnovejse prve),
       z igralci, klubi, dogodkom in turnirjem - za "Zadnji rezultati".
       Stevilo omeji Pageable (npr. prvih 8).

       Vrstica pokaze tudi spremembo ELO, ki je dvojice nimajo, zato so
       dvojice tu izpuscene; potek turnirja z dvojicami pove vrstica turnirja
       (najdiZadnjeVsakegaTurnirja). */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec LEFT JOIN FETCH p1.klubObPrijavi
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec LEFT JOIN FETCH p2.klubObPrijavi
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
            ORDER BY t.id DESC
            """)
    List<Tekma> najdiZadnje(Pageable strani);

    /* Vse odigrane POSAMICNE turnirske tekme enega igralca (v obeh slotih),
       najnovejse prve - za profil igralca. Nalozeno je vse, kar bere DTO:
       turnir, dogodek, oba igralca in njuna kluba ob prijavi. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec i1 LEFT JOIN FETCH i1.klub
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec i2 LEFT JOIN FETCH i2.klub
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.POSAMICNO
              AND (p1.igralec.id = :idIgralec OR p2.igralec.id = :idIgralec)
            ORDER BY t.id DESC
            """)
    List<Tekma> najdiZaIgralca(Long idIgralec);

    /* Vse odigrane tekme DVOJIC, v katerih je igralec nastopil - v katerem
       koli od stirih mest (nosilec ali soigralec na eni ali drugi strani).
       Za razdelek "Dvojice" na profilu; v posamicno bilanco in ELO te tekme
       ne stejejo. */
    @Query("""
            SELECT t FROM Tekma t
            JOIN FETCH t.dogodek d JOIN FETCH d.turnir
            JOIN FETCH t.prijava1 p1 JOIN FETCH p1.igralec i1 LEFT JOIN FETCH p1.igralec2 s1
            JOIN FETCH t.prijava2 p2 JOIN FETCH p2.igralec i2 LEFT JOIN FETCH p2.igralec2 s2
            LEFT JOIN FETCH t.zmagovalec
            WHERE t.status = si.turnirko.modeli.StatusTekme.KONCANA
              AND (t.izidTip IS NULL OR t.izidTip IN (si.turnirko.modeli.IzidTekme.IGRANO,
                                                     si.turnirko.modeli.IzidTekme.PREDAJA))
              AND d.disciplina = si.turnirko.modeli.Disciplina.DVOJICE
              AND (i1.id = :idIgralec OR s1.id = :idIgralec
                OR i2.id = :idIgralec OR s2.id = :idIgralec)
            ORDER BY t.id DESC
            """)
    List<Tekma> najdiDvojiceZaIgralca(Long idIgralec);

    /* Atomarno vpise udelezenca v slot 1, a samo ce je slot se prazen.
       Vrne 1, ce je vpis uspel. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Tekma t SET t.prijava1 = :prijava WHERE t.id = :idTekme AND t.prijava1 IS NULL")
    int vpisiVSlot1(Long idTekme, Prijava prijava);

    /* Atomarno vpise udelezenca v slot 2, a samo ce je slot se prazen. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Tekma t SET t.prijava2 = :prijava WHERE t.id = :idTekme AND t.prijava2 IS NULL")
    int vpisiVSlot2(Long idTekme, Prijava prijava);

    /* Atomarno oznaci tekmo kot PRIPRAVLJENA, ko sta znana oba udelezenca. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Tekma t SET t.status = si.turnirko.modeli.StatusTekme.PRIPRAVLJENA
            WHERE t.id = :idTekme
              AND t.prijava1 IS NOT NULL AND t.prijava2 IS NOT NULL
              AND t.status = si.turnirko.modeli.StatusTekme.CAKA
            """)
    int oznaciPripravljenoCeStaOba(Long idTekme);
}
