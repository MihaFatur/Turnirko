/* Dostop do lig. Visja liga (za prehode) in lastnistvo (klub lastnik,
   ustvaril) se nalozijo vnaprej, ker jih bere DTO oz. preverba pravice izven
   transakcije. */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Liga;
import si.turnirko.pomozno.SlovenskaAbeceda;

public interface LigaRepozitorij extends JpaRepository<Liga, Long> {

    /* Koliko lig je ta organizator ustvaril od zacetka tekoce sezone - meja
       placilnega paketa (NarocninaStoritev). Kvalifikacije med ligami ne
       stejejo: so podaljsek lig, ki jih je ze stela, in ne novo tekmovanje. */
    long countByUstvarilIdAndUstvarjenObGreaterThanEqualAndKvalifikacijeVisjaIsNull(
            Long idUstvaril, LocalDateTime odSezone);

    /* Liga kvalifikacij med danima ligama, ce je ze ustvarjena. */
    @Query("""
            SELECT l FROM Liga l
            WHERE l.kvalifikacijeVisja.id = :idVisja AND l.kvalifikacijeNizja.id = :idNizja
            """)
    Optional<Liga> najdiKvalifikacije(Long idVisja, Long idNizja);

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.visjaLiga
            LEFT JOIN FETCH l.kvalifikacijeVisja
            LEFT JOIN FETCH l.kvalifikacijeNizja
            LEFT JOIN FETCH l.klubLastnik
            LEFT JOIN FETCH l.ustvaril
            ORDER BY l.id DESC
            """)
    List<Liga> najdiVse();

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.visjaLiga
            LEFT JOIN FETCH l.kvalifikacijeVisja
            LEFT JOIN FETCH l.kvalifikacijeNizja
            LEFT JOIN FETCH l.klubLastnik
            LEFT JOIN FETCH l.ustvaril
            WHERE l.id = :id
            """)
    Optional<Liga> najdiZVisjo(Long id);

    /* Lige, ki jih je admin postavil na domaco stran. Vrstni red je vrstni red
       vpisa (id navzgor), da se par ob vsakem obisku izpise enako. */
    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.visjaLiga
            LEFT JOIN FETCH l.kvalifikacijeVisja
            LEFT JOIN FETCH l.kvalifikacijeNizja
            LEFT JOIN FETCH l.klubLastnik
            LEFT JOIN FETCH l.ustvaril
            WHERE l.naDomaci = TRUE
            ORDER BY l.id
            """)
    List<Liga> najdiNaDomaci();

    /* Lige, ki se niso zakljucene in imajo igralca v kadru katere od ekip -
       za domaco stran igralca s Premium. Zakljucene izpadejo: uvozena
       zgodovina ima pri dolgoletnem igralcu ducat starih sezon in domaca stran
       ni arhiv. Ekipe turnirjev izpadejo po izrecnem notranjem stiku na ligo
       (glej KaderEkipeRepozitorij.ligePoIgralcih). */
    @Query("""
            SELECT DISTINCT l FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l
            WHERE k.igralec.id = :idIgralec
              AND l.status <> si.turnirko.modeli.StatusTekmovanja.ZAKLJUCEN
            ORDER BY l.id
            """)
    List<Liga> najdiNezakljuceneZaIgralca(Long idIgralec);

    /* Lige, ki kazejo na dano ligo kot na svojo visjo - torej nivo pod njo.
       Bere jih urejanje prehodov, ko usklajuje izbor nizjih lig. */
    default List<Liga> najdiNizje(Long idVisja) {
        List<Liga> lige = new ArrayList<>(najdiNizjeNeurejene(idVisja));
        lige.sort(Comparator.comparing(Liga::getIme, SlovenskaAbeceda.RED));
        return lige;
    }

    /* Le za najdiNizje(): imena po slovenski abecedi uredi Java, ker SQLite
       besedilo primerja po kodnih tockah (Č, Š, Ž za Z). */
    @Query("SELECT l FROM Liga l WHERE l.visjaLiga.id = :idVisja ORDER BY l.id")
    List<Liga> najdiNizjeNeurejene(Long idVisja);

    // ---------- Lastnistvo (za preverjanje pravice organizatorja) ----------

    @Query("""
            SELECT l FROM Liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE l.id = :id
            """)
    Optional<Liga> najdiZLastnistvom(Long id);

    @Query("""
            SELECT l FROM Ekipa e JOIN e.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE e.id = :idEkipa
            """)
    Optional<Liga> najdiZLastnistvomPoEkipi(Long idEkipa);

    @Query("""
            SELECT l FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE k.id = :idKader
            """)
    Optional<Liga> najdiZLastnistvomPoKadru(Long idKader);

    @Query("""
            SELECT l FROM Srecanje s JOIN s.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE s.id = :idSrecanje
            """)
    Optional<Liga> najdiZLastnistvomPoSrecanju(Long idSrecanje);

    @Query("""
            SELECT l FROM TekmaSrecanja ts JOIN ts.srecanje s JOIN s.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE ts.id = :idTekma
            """)
    Optional<Liga> najdiZLastnistvomPoTekmiSrecanja(Long idTekma);

    @Query("""
            SELECT l FROM SerijaKoncnice sk JOIN sk.liga l
            LEFT JOIN FETCH l.ustvaril
            LEFT JOIN FETCH l.klubLastnik
            WHERE sk.id = :idSerija
            """)
    Optional<Liga> najdiZLastnistvomPoSeriji(Long idSerija);

    /* Vse lige, ki jih je racun USTVARIL - vir organizatorskega pregleda
       (glej TurnirRepozitorij.najdiZaUstvarjalca). */
    @Query("SELECT l FROM Liga l WHERE l.ustvaril.id = :idUstvaril")
    List<Liga> najdiZaUstvarjalca(Long idUstvaril);
}
