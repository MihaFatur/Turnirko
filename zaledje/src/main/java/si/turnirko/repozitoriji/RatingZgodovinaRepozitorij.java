/* Dostop do dnevnika sprememb ratinga.

   ISKANJE PO TEKMAH: poizvedbe, ki dnevnik berejo po tekmah (seznam id-jev,
   tekme turnirja ali lige), sistem primerjajo kot `concat(z.sistem, '')` in ne
   kot `z.sistem`. To ni okras. Baza nima statistik (ANALYZE), zato sqlite ne
   ve, da ima stolpec sistem eno samo vrednost, in enakost na njem sodi za zelo
   selektivno; vsak indeks, ki se zacne s sistem (V18, V21, V30), se mu zato
   zdi boljsi od indeksa po tekmi in poizvedba za deset tekem preleti ves
   dnevnik (193.755 vrstic). Izraz nad stolpcem sqlite pri izbiri indeksa
   izpusti, pomen pa ostane isti (sistem je NOT NULL besedilo).
   Izmerjeno na polni bazi (13. 9. 2026). sqlite3: deset tekem 17 ms -> 0,1 ms,
   rating pred 1147 tekmami igralca 49 ms -> 5 ms, tekme turnirja oz. lige za
   statistiko 44 ms -> 0,7-1,9 ms, brisanje vseh obracunov ob preracunu (237
   paketov) 3,6 s -> 1,8 s. HTTP: zadnje tekme 24 -> 7 ms, statistika turnirja
   74 -> 29 ms, profil igralca s 1149 zapisi 366 -> 318 ms; odgovori enaki.

   Zakaj ne drugace (EXPLAIN QUERY PLAN vseh poizvedb tega repozitorija na
   kopiji polne baze): sestavljen indeks (id_tekma, sistem) te poizvedbe
   popravi, samopovezavo uvrstitve novinca (izidiVOknu*) pa obrne, da zacne pri
   celem dnevniku; ANALYZE jih prav tako popravi, stetje tekmovalnih tekem pa
   odvrne s pokrivnega indeksa V30 in spremeni nacrte po vsej aplikaciji.
   Izraz spremeni nacrt samo poizvedbi, ki ga nosi. Varuje NacrtiDnevnikaTest;
   nova poizvedba po tekmah naj ga uporabi in se doda tja. */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;

public interface RatingZgodovinaRepozitorij extends JpaRepository<RatingZgodovina, Long> {

    /* Varovalka pred dvojnim obracunom: ce zapis za tekmo ze obstaja,
       se rating za to tekmo ne sme obracunati se enkrat. */
    boolean existsByTekmaId(Long idTekma);

    /* Ista varovalka za posamicne tekme ligaskih srecanj. */
    boolean existsByTekmaSrecanjaId(Long idTekmaSrecanja);

    /* Spremembe ratinga za posamicne tekme ligaskega srecanja, po igralcih.
       Vrne vrstice [idTekmeSrecanja, idIgralca, sprememba]. Iskanje po tekmah
       - glej opombo na vrhu. */
    @Query("""
            SELECT z.tekmaSrecanja.id, z.igralec.id, z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekmaSrecanja.id IN :idjiTekem AND concat(z.sistem, '') = :sistem
            """)
    List<Object[]> spremembeZaTekmeSrecanja(@Param("idjiTekem") Collection<Long> idjiTekem,
                                            @Param("sistem") String sistem);

    /* Celoten dnevnik ratinga enega igralca, od najstarejsega - za graf
       napredka na profilu. */
    @Query("""
            SELECT z FROM RatingZgodovina z
            WHERE z.igralec.id = :idIgralec AND z.sistem = :sistem
            ORDER BY z.veljaOb, z.id
            """)
    List<RatingZgodovina> najdiZaIgralca(@Param("idIgralec") Long idIgralec,
                                         @Param("sistem") String sistem);

    /* Dnevnik VSEH igralcev od danega trenutka naprej, od najstarejsega:
       [idIgralca, kdaj, nova vrednost]. Iz njega lestvica narise crto gibanja
       rating ob vrstici - po eno poizvedbo na igralca bi pri stotih igralcih
       pomenilo sto poizvedb. */
    @Query("""
            SELECT z.igralec.id, z.veljaOb, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.sistem = :sistem AND z.veljaOb >= :od
            ORDER BY z.veljaOb, z.id
            """)
    List<Object[]> potekOd(@Param("sistem") String sistem, @Param("od") LocalDateTime od);

    /* Rating vsakega igralca, kakrsen je bil ob danem trenutku (zadnji zapis
       do takrat): [idIgralca, vrednost]. Zapisi so append-only, zato je
       najvisji id tudi najnovejsi. Sluzi izracunu premika na lestvici in
       izhodiscu crte, kadar igralec v zadnjem letu ni igral. */
    @Query("""
            SELECT z.igralec.id, z.novaVrednost FROM RatingZgodovina z
            WHERE z.id IN (
                SELECT MAX(y.id) FROM RatingZgodovina y
                WHERE y.sistem = :sistem AND y.veljaOb <= :mejnik
                GROUP BY y.igralec.id)
            """)
    List<Object[]> stanjeOb(@Param("sistem") String sistem, @Param("mejnik") LocalDateTime mejnik);

    /* Rating igralcev PRED danimi turnirskimi tekmami (nova vrednost minus
       sprememba). Potrebno za razclenitev "proti mocnejsim/sibkejsim", kjer
       steje moc nasprotnika v trenutku tekme, ne danes.
       Vrne vrstice [idTekme, idIgralca, ratingPred]. Profil igralca sem poda
       VSE njegove tekme (pri najbolj dejavnih vec kot tisoc) - iskanje po
       tekmah, glej opombo na vrhu. */
    @Query("""
            SELECT z.tekma.id, z.igralec.id, z.novaVrednost - z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekma.id IN :idjiTekem AND concat(z.sistem, '') = :sistem
            """)
    List<Object[]> ratingiPredTurnirskimi(@Param("idjiTekem") Collection<Long> idjiTekem,
                                          @Param("sistem") String sistem);

    /* Isto za posamicne tekme ligaskih srecanj. */
    @Query("""
            SELECT z.tekmaSrecanja.id, z.igralec.id, z.novaVrednost - z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekmaSrecanja.id IN :idjiTekem AND concat(z.sistem, '') = :sistem
            """)
    List<Object[]> ratingiPredLigaskimi(@Param("idjiTekem") Collection<Long> idjiTekem,
                                        @Param("sistem") String sistem);

    /* Spremembe ratinga za dane tekme, po igralcih - za prikaz "+16 / -16"
       in ratinga PRED tekmo ob vsaki tekmi. Vrne vrstice
       [idTekme, idIgralca, sprememba, novaVrednost]; rating pred tekmo je
       novaVrednost - sprememba. Iskanje po tekmah - glej opombo na vrhu. */
    @Query("""
            SELECT z.tekma.id, z.igralec.id, z.sprememba, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.tekma.id IN :idjiTekem AND concat(z.sistem, '') = :sistem
            """)
    List<Object[]> spremembeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem,
                                    @Param("sistem") String sistem);

    /* Iste vrstice za CEL turnir oz. CELO ligo - za zavihek statistike
       tekmovanja. Po tekmovanju in ne po seznamu id-jev tekem: velik turnir
       ima nekaj sto tekem, sqlite pa ima omejitev stevila vezanih parametrov.
       Vrstni red je vrstni red obracuna, zato je zadnja vrstica igralca hkrati
       njegov rating ob koncu tekmovanja.

       Iskanje po tekmah (glej opombo na vrhu): tekme se najdejo prek dogodkov
       oz. srecanj, nekaj sto vrstic pa se nato razvrsti. S `z.sistem` je sqlite
       raje preletel ves dnevnik po casu, samo da mu ni bilo treba razvrscati. */
    @Query("""
            SELECT z.tekma.id, z.igralec.id, z.sprememba, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.tekma.dogodek.turnir.id = :idTurnir AND concat(z.sistem, '') = :sistem
            ORDER BY z.veljaOb, z.id
            """)
    List<Object[]> spremembeZaTurnir(@Param("idTurnir") Long idTurnir,
                                     @Param("sistem") String sistem);

    @Query("""
            SELECT z.tekmaSrecanja.id, z.igralec.id, z.sprememba, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.tekmaSrecanja.srecanje.liga.id = :idLiga AND concat(z.sistem, '') = :sistem
            ORDER BY z.veljaOb, z.id
            """)
    List<Object[]> spremembeZaLigo(@Param("idLiga") Long idLiga,
                                   @Param("sistem") String sistem);

    /* --- ponovni preracun ratinga ---------------------------------------- */

    /* Pobrise obracune danih turnirskih tekem. Po paketih, ker ima sqlite
       omejeno stevilo vezanih parametrov. Postavitveni zapisi (brez tekme)
       ostanejo - niso posledica rezultata in se ne preracunavajo.
       Iskanje po tekmah - glej opombo na vrhu: z `z.sistem` je vsak paket
       preletel ves dnevnik. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RatingZgodovina z WHERE concat(z.sistem, '') = :sistem AND z.tekma.id IN :idji")
    int pobrisiZaTekme(@Param("sistem") String sistem, @Param("idji") Collection<Long> idji);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RatingZgodovina z WHERE concat(z.sistem, '') = :sistem AND z.tekmaSrecanja.id IN :idji")
    int pobrisiZaTekmeSrecanja(@Param("sistem") String sistem, @Param("idji") Collection<Long> idji);

    /* Zadnja zabelezena vrednost vsakega igralca: [idIgralca, vrednost].
       Dnevnik je append-only, zato je najvisji id tudi najnovejsi zapis.
       Iz tega se po ciscenju obnovi rating_stanje.

       Zunanje uvrstitve OD MEJE NAPREJ so izvzete, ceprav v dnevniku ostanejo:
       preracun jih odigra na njihovem mestu v casovni vrsti (skupaj s tekmami,
       ki so bile pred njimi), zato jih stanje pred mejo se ne sme vsebovati.
       Postavitev (POSTAVITEV) je nasprotno IZHODISCE igralca in tu ostane.

       Pogoj je napisan s tremi clenami namenoma: pri NULL razlogu bi
       "NOT (razlog = ... AND ...)" dalo NULL in vrstica bi tiho odpadla. */
    @Query("""
            SELECT z.igralec.id, z.novaVrednost FROM RatingZgodovina z
            WHERE z.id IN (
                SELECT MAX(y.id) FROM RatingZgodovina y
                WHERE y.sistem = :sistem
                  AND (y.razlog IS NULL
                       OR y.razlog <> si.turnirko.modeli.RazlogSpremembe.ZUNANJA_UVRSTITEV
                       OR y.veljaOb < :od)
                GROUP BY y.igralec.id)
            """)
    List<Object[]> zadnjeVrednosti(@Param("sistem") String sistem,
                                   @Param("od") LocalDateTime od);

    /* Vse zunanje uvrstitve v casovnem zaporedju: [id zapisa, idIgralca,
       veljaOb, novaVrednost]. Preracun jih razdeli na tiste pred mejo (te
       obnovijo stanje) in tiste od meje naprej (te odigra znova med tekmami). */
    @Query("""
            SELECT z.id, z.igralec.id, z.veljaOb, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.sistem = :sistem
              AND z.razlog = si.turnirko.modeli.RazlogSpremembe.ZUNANJA_UVRSTITEV
            ORDER BY z.veljaOb, z.id
            """)
    List<Object[]> zunanjeUvrstitve(@Param("sistem") String sistem);

    /* Igralci, ki jim je rating kdaj POSTAVIL clovek: postavitveni zapis je
       tisti brez tekme. Iz tega ponovni preracun obnovi zastavico
       rating_stanje.postavljen - postavljenega igralca uvrstitev ne povozi. */
    @Query("""
            SELECT DISTINCT z.igralec.id FROM RatingZgodovina z
            WHERE z.sistem = :sistem AND z.razlog = si.turnirko.modeli.RazlogSpremembe.POSTAVITEV
            """)
    List<Long> igralciSPostavitvijo(@Param("sistem") String sistem);

    /* Casi vseh obracunanih tekem po igralcih: [idIgralca, veljaOb], urejeni
       po igralcu in casu. Iz tega zaporedja ponovni preracun obnovi sledilnik
       vrnitve (kdaj je igralec nazadnje igral in koliko tekem s povisanim K mu
       je se ostalo) - to je edino stanje, ki se ga iz zadnje vrstice dnevnika
       ne da prebrati, ker je posledica celotnega zaporedja. */
    @Query("""
            SELECT z.igralec.id, z.veljaOb FROM RatingZgodovina z
            WHERE z.sistem = :sistem
              AND (z.tekma IS NOT NULL OR z.tekmaSrecanja IS NOT NULL)
            ORDER BY z.igralec.id, z.veljaOb
            """)
    List<Object[]> casiObracunanihTekem(@Param("sistem") String sistem);
    /* Izidi igralca v danem casovnem oknu: [rating nasprotnika PRED tekmo,
       tocke (1/0)]. Sluzi uvrstitvi novinca po prvem dnevu igranja.

       Rating nasprotnika pred tekmo je v NJEGOVI vrstici iste tekme
       (novaVrednost - sprememba), zato je to samosklop dnevnika.

       Turnirske in ligaske vrstice sta dve LOCENI poizvedbi in ne ena z OR:
       pri `o.tekma = z.tekma OR o.tekmaSrecanja = z.tekmaSrecanja` sqlite ne
       more uporabiti indeksa po tekmi in za vsako vrstico prebere celoten
       dnevnik - izmerjeno je to preracun podaljsalo z minut na ure. */
    @Query("""
            SELECT o.novaVrednost - o.sprememba, z.tocke
            FROM RatingZgodovina z JOIN RatingZgodovina o ON o.tekma = z.tekma
            WHERE z.sistem = :sistem AND z.igralec.id = :idIgralec
              AND z.tekma IS NOT NULL AND z.tocke IS NOT NULL
              AND z.veljaOb >= :od AND z.veljaOb < :do
              AND o.sistem = :sistem AND o.igralec.id <> z.igralec.id
            ORDER BY z.id
            """)
    List<Object[]> izidiVOknuTurnirski(@Param("sistem") String sistem,
                                       @Param("idIgralec") Long idIgralec,
                                       @Param("od") LocalDateTime od,
                                       @Param("do") LocalDateTime doKdaj);

    @Query("""
            SELECT o.novaVrednost - o.sprememba, z.tocke
            FROM RatingZgodovina z
                 JOIN RatingZgodovina o ON o.tekmaSrecanja = z.tekmaSrecanja
            WHERE z.sistem = :sistem AND z.igralec.id = :idIgralec
              AND z.tekmaSrecanja IS NOT NULL AND z.tocke IS NOT NULL
              AND z.veljaOb >= :od AND z.veljaOb < :do
              AND o.sistem = :sistem AND o.igralec.id <> z.igralec.id
            ORDER BY z.id
            """)
    List<Object[]> izidiVOknuLigaski(@Param("sistem") String sistem,
                                     @Param("idIgralec") Long idIgralec,
                                     @Param("od") LocalDateTime od,
                                     @Param("do") LocalDateTime doKdaj);

    /* --- tekmovalci in rekreativci ---------------------------------------- */

    /* Koliko tekem je igralec odigral na tekmovanjih danih ravni:
       [idIgralca, stevilo]. Po tem se loci tekmovalca od rekreativca.
       Raven je last tekmovanja in ne dnevnika, zato poizvedba seze do turnirja
       oz. lige - s tem pa tudi sledi kasnejsi spremembi ravni tekmovanja.

       Tece ob vsakem ogledu lestvice in profila cez ves dnevnik, zato je
       napisana tako, da iz dnevnika bere SAMO pokrivni indeks
       idx_rating_zgodovina_igralec_tekme (V30): tekme pravih ravni se zberejo v
       podpoizvedbah (sqlite ju izracuna enkrat, kot seznam), dnevnik pa se
       preleti po indeksu in vsak zapis le preveri v seznamu. Prej sta bili dve
       poizvedbi s stikom z dnevnika na tekmo, sqlite pa je za vsak zapis bral
       se celotno vrstico dnevnika: skupaj 1,13 s, zdaj 0,09 s.

       OR med obema viroma tu indeksa NE ubije (kot bi ga pri uvrstitvi novinca,
       kjer se isce po tekmi): obseg "sistem = ?" se prebere v celoti v vsakem
       primeru in OR je samo preverba nad ze prebranim zapisom indeksa. Zato je
       ena poizvedba in ne dve - dnevnik se preleti enkrat.

       Srecanja so ligaska in ekipne tekme turnirjev (V28). Raven je pri ligi
       last lige, pri ekipni tekmi pa turnirja - stika sta zato LEVA, sicer bi
       notranji stik na ligo izpustil ekipne turnirje. */
    @Query("""
            SELECT z.igralec.id, COUNT(z) FROM RatingZgodovina z
            WHERE z.sistem = :sistem
              AND (z.tekma.id IN (
                       SELECT t.id FROM Tekma t JOIN t.dogodek d JOIN d.turnir tu
                       WHERE tu.raven IN :ravni)
                   OR z.tekmaSrecanja.id IN (
                       SELECT ts.id FROM TekmaSrecanja ts JOIN ts.srecanje s
                       LEFT JOIN s.liga l
                       LEFT JOIN s.tekma tk LEFT JOIN tk.dogodek dg LEFT JOIN dg.turnir tu2
                       WHERE COALESCE(l.raven, tu2.raven) IN :ravni))
            GROUP BY z.igralec.id
            """)
    List<Object[]> tekmovalnihTekem(@Param("sistem") String sistem,
                                    @Param("ravni") Collection<RavenTekmovanja> ravni);

    /* --- odbitek za neaktivnost ------------------------------------------- */

    /* Koliko stopenj odbitka je igralec ze dobil za TEKOCI premor (po zadnji
       tekmi). Iz tega se ve, katere stopnje je se treba uveljaviti - isti
       odbitek se ne sme zapisati dvakrat. */
    @Query("""
            SELECT COUNT(z) FROM RatingZgodovina z
            WHERE z.sistem = :sistem AND z.igralec.id = :idIgralec
              AND z.razlog = si.turnirko.modeli.RazlogSpremembe.NEAKTIVNOST
              AND z.veljaOb > :poTekmi
            """)
    long steviloOdbitkovPo(@Param("sistem") String sistem,
                           @Param("idIgralec") Long idIgralec,
                           @Param("poTekmi") LocalDateTime poTekmi);

    /* Pobrise odbitke za neaktivnost od danega trenutka naprej. Odbitek je
       IZPELJANKA iz zaporedja tekem (za razliko od postavitve, ki je
       odlocitev cloveka), zato ga ponovni preracun zavrze in izracuna znova. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM RatingZgodovina z
            WHERE z.sistem = :sistem
              AND z.razlog = si.turnirko.modeli.RazlogSpremembe.NEAKTIVNOST
              AND z.veljaOb >= :od
            """)
    int pobrisiOdbitke(@Param("sistem") String sistem, @Param("od") LocalDateTime od);

}
