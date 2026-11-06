/* Dostop do dodatnih dostopov organizatorjev (V62) in stevcev mej paketa.

   Stevca ustvarjenih tekmovanj in dodeljenih tekmovanj sta locena, ker imata
   vsak svoj cas, od katerega steje v sezono: ustvarjeno steje od nastanka,
   dodeljeno od dodelitve. Vsota je meja paketa (NarocninaStoritev). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.DostopOrganizatorja;

public interface DostopOrganizatorjaRepozitorij extends JpaRepository<DostopOrganizatorja, Long> {

    Optional<DostopOrganizatorja> findByIdTurnirAndIdRacun(Long idTurnir, Long idRacun);

    Optional<DostopOrganizatorja> findByIdLigaAndIdRacun(Long idLiga, Long idRacun);

    List<DostopOrganizatorja> findByIdTurnir(Long idTurnir);

    List<DostopOrganizatorja> findByIdLiga(Long idLiga);

    boolean existsByIdTurnirAndIdRacun(Long idTurnir, Long idRacun);

    boolean existsByIdLigaAndIdRacun(Long idLiga, Long idRacun);

    /* Turnirji in lige, do katerih ima racun dodaten dostop - vmesnik po njih
       pokaze urejanje (UporabnikDto). */
    @Query("SELECT d.idTurnir FROM DostopOrganizatorja d WHERE d.idRacun = :idRacun AND d.idTurnir IS NOT NULL")
    List<Long> idjiTurnirjev(Long idRacun);

    @Query("SELECT d.idLiga FROM DostopOrganizatorja d WHERE d.idRacun = :idRacun AND d.idLiga IS NOT NULL")
    List<Long> idjiLig(Long idRacun);

    // ---------- Meja paketa ----------

    /* Turnirji, ki jih je racun ustvaril v sezoni in jih admin ni izvzel iz
       meje (vrstica o lastniku s stejeVMejo = false). Brez vrstice steje. */
    @Query("""
            SELECT COUNT(t) FROM Turnir t
            WHERE t.ustvaril.id = :idRacun AND t.ustvarjenOb >= :odSezone
              AND NOT EXISTS (SELECT d.id FROM DostopOrganizatorja d
                              WHERE d.idTurnir = t.id AND d.idRacun = :idRacun AND d.stejeVMejo = false)
            """)
    long steviloUstvarjenihTurnirjev(Long idRacun, LocalDateTime odSezone);

    /* Turnirji, ki jih je racunu dodelil admin kot stetje v mejo, dodeljeni v
       sezoni. Tekmovanje, ki ga je racun sam ustvaril, steje zgoraj in ne tu
       (sicer bi se stelo dvakrat). */
    @Query("""
            SELECT COUNT(d) FROM DostopOrganizatorja d, Turnir t
            WHERE d.idTurnir = t.id AND d.idRacun = :idRacun AND d.stejeVMejo = true
              AND d.dodeljenOb >= :odSezone
              AND (t.ustvaril IS NULL OR t.ustvaril.id <> :idRacun)
            """)
    long steviloDodeljenihTurnirjev(Long idRacun, LocalDateTime odSezone);

    /* Kot za turnirje; kvalifikacije med ligami ne stejejo nikoli - so
       podaljsek lige, ki jo je racun ze stel. */
    @Query("""
            SELECT COUNT(l) FROM Liga l
            WHERE l.ustvaril.id = :idRacun AND l.ustvarjenOb >= :odSezone
              AND l.kvalifikacijeVisja IS NULL
              AND NOT EXISTS (SELECT d.id FROM DostopOrganizatorja d
                              WHERE d.idLiga = l.id AND d.idRacun = :idRacun AND d.stejeVMejo = false)
            """)
    long steviloUstvarjenihLig(Long idRacun, LocalDateTime odSezone);

    @Query("""
            SELECT COUNT(d) FROM DostopOrganizatorja d, Liga l
            WHERE d.idLiga = l.id AND d.idRacun = :idRacun AND d.stejeVMejo = true
              AND d.dodeljenOb >= :odSezone AND l.kvalifikacijeVisja IS NULL
              AND (l.ustvaril IS NULL OR l.ustvaril.id <> :idRacun)
            """)
    long steviloDodeljenihLig(Long idRacun, LocalDateTime odSezone);
}
