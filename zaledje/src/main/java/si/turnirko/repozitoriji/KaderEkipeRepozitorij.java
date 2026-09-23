/* Dostop do kadra ekip. Igralec in njegov klub se nalozita vnaprej za DTO. */
package si.turnirko.repozitoriji;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KaderEkipe;

public interface KaderEkipeRepozitorij extends JpaRepository<KaderEkipe, Long> {

    /* Zacetki tekmovanj, v katerih je igralec v kadru ekipe (liga: prvo kolo,
       ekipni dogodek: dan turnirja) - za presojo najnovejsega nastopa pri
       uvozu. */
    @Query("""
            SELECT l.zacetekPrvegaKola FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l
            WHERE k.igralec.id = :idIgralec AND l.zacetekPrvegaKola IS NOT NULL
            """)
    List<java.time.LocalDateTime> zacetkiLigIgralca(Long idIgralec);

    @Query("""
            SELECT t.datumZacetka FROM KaderEkipe k JOIN k.ekipa e JOIN e.dogodek d JOIN d.turnir t
            WHERE k.igralec.id = :idIgralec
            """)
    List<java.time.LocalDate> datumiEkipnihTurnirjevIgralca(Long idIgralec);

    /* Kader po jakostnem vrstnem redu (brez mesta najprej, kot pri SQL ORDER BY),
       znotraj tega po priimku in imenu v slovenski abecedi - to uredi Java, ker
       SQLite besedilo primerja po kodnih tockah (Č, Š, Ž za Z). */
    default List<KaderEkipe> najdiZaEkipo(Long idEkipa) {
        List<KaderEkipe> kader = new ArrayList<>(najdiZaEkipoNeurejeno(idEkipa));
        kader.sort(Comparator
                .comparing(KaderEkipe::getVrstniRed, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
                .thenComparing(KaderEkipe::getIgralec, Igralec.PO_ABECEDI));
        return kader;
    }

    /* Le za najdiZaEkipo(): vrstni red je vrstni red vpisa. */
    @Query("""
            SELECT k FROM KaderEkipe k
            JOIN FETCH k.igralec i LEFT JOIN FETCH i.klub
            WHERE k.ekipa.id = :idEkipa
            ORDER BY k.id
            """)
    List<KaderEkipe> najdiZaEkipoNeurejeno(Long idEkipa);

    boolean existsByEkipaIdAndIgralecId(Long idEkipa, Long idIgralec);

    /* Velikost kadra vsake ekipe v ligi kot [idEkipa, stevilo] - seznam ekip to
       pokaze v vrstici, brez poizvedbe na kader vsake ekipe posebej. Ekipa s
       praznim kadrom v rezultatu ne nastopa. */
    @Query("""
            SELECT k.ekipa.id, COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.liga.id = :idLiga
            GROUP BY k.ekipa.id
            """)
    List<Object[]> steviloPoEkipah(Long idLiga);

    /* Isto za ekipe ekipnega dogodka turnirja: [idEkipa, stevilo]. */
    @Query("""
            SELECT k.ekipa.id, COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.dogodek.id = :idDogodek
            GROUP BY k.ekipa.id
            """)
    List<Object[]> steviloPoEkipahDogodka(Long idDogodek);

    /* Igralci kadrov danih ekip kot [idEkipa, idIgralec] - za predlog jakosti
       ekip po ratingu kadra (IzborStoritev). */
    @Query("""
            SELECT k.ekipa.id, k.igralec.id FROM KaderEkipe k
            WHERE k.ekipa.id IN :idjiEkip
            """)
    List<Object[]> igralciEkip(List<Long> idjiEkip);

    /* V katerih ligah igralci nastopajo: [idIgralca, idLige]. Filter "Moje
       lige" na lestvici mora vedeti, kdo v spremljanih ligah sploh igra.
       Ekipe turnirjev izpadejo po izrecnem notranjem stiku na ligo - pot
       k.ekipa.liga.id bi Hibernate prebral kar iz tujega kljuca in vrnil null. */
    @Query("SELECT DISTINCT k.igralec.id, l.id FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l")
    List<Object[]> ligePoIgralcih();

    /* Ekipa dogodka, v kadru katere je igralec (kvecjemu ena - igralec na
       ekipnem dogodku nastopa za eno ekipo). */
    @Query("""
            SELECT k FROM KaderEkipe k JOIN FETCH k.ekipa e LEFT JOIN FETCH e.klub
            WHERE e.dogodek.id = :idDogodek AND k.igralec.id = :idIgralec
            """)
    List<KaderEkipe> vKadruDogodka(Long idDogodek, Long idIgralec);

    /* Koliko ekip iste lige ima tega igralca v kadru - za prepoved
       dvojne registracije. */
    @Query("""
            SELECT COUNT(k) FROM KaderEkipe k
            WHERE k.ekipa.liga.id = :idLiga AND k.igralec.id = :idIgralec
            """)
    long steviloVLigi(Long idLiga, Long idIgralec);
}
