/* Dostop do dnevnika sprememb ratinga. */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import si.turnirko.modeli.RatingZgodovina;

public interface RatingZgodovinaRepozitorij extends JpaRepository<RatingZgodovina, Long> {

    /* Varovalka pred dvojnim obracunom: ce zapis za tekmo ze obstaja,
       se rating za to tekmo ne sme obracunati se enkrat. */
    boolean existsByTekmaId(Long idTekma);

    /* Ista varovalka za posamicne tekme ligaskih srecanj. */
    boolean existsByTekmaSrecanjaId(Long idTekmaSrecanja);

    /* Spremembe ratinga za posamicne tekme ligaskega srecanja, po igralcih.
       Vrne vrstice [idTekmeSrecanja, idIgralca, sprememba]. */
    @Query("""
            SELECT z.tekmaSrecanja.id, z.igralec.id, z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekmaSrecanja.id IN :idjiTekem AND z.sistem = :sistem
            """)
    List<Object[]> spremembeZaTekmeSrecanja(@Param("idjiTekem") Collection<Long> idjiTekem,
                                            @Param("sistem") String sistem);

    /* Celoten dnevnik ratinga enega igralca, od najstarejsega - za graf
       napredka na profilu. */
    @Query("""
            SELECT z FROM RatingZgodovina z
            WHERE z.igralec.id = :idIgralec AND z.sistem = :sistem
            ORDER BY z.ustvarjenOb, z.id
            """)
    List<RatingZgodovina> najdiZaIgralca(@Param("idIgralec") Long idIgralec,
                                         @Param("sistem") String sistem);

    /* Rating igralcev PRED danimi turnirskimi tekmami (nova vrednost minus
       sprememba). Potrebno za razclenitev "proti mocnejsim/sibkejsim", kjer
       steje moc nasprotnika v trenutku tekme, ne danes.
       Vrne vrstice [idTekme, idIgralca, ratingPred]. */
    @Query("""
            SELECT z.tekma.id, z.igralec.id, z.novaVrednost - z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekma.id IN :idjiTekem AND z.sistem = :sistem
            """)
    List<Object[]> ratingiPredTurnirskimi(@Param("idjiTekem") Collection<Long> idjiTekem,
                                          @Param("sistem") String sistem);

    /* Isto za posamicne tekme ligaskih srecanj. */
    @Query("""
            SELECT z.tekmaSrecanja.id, z.igralec.id, z.novaVrednost - z.sprememba
            FROM RatingZgodovina z
            WHERE z.tekmaSrecanja.id IN :idjiTekem AND z.sistem = :sistem
            """)
    List<Object[]> ratingiPredLigaskimi(@Param("idjiTekem") Collection<Long> idjiTekem,
                                        @Param("sistem") String sistem);

    /* Spremembe ratinga za dane tekme, po igralcih - za prikaz "+16 / -16"
       in ratinga PRED tekmo ob vsaki tekmi. Vrne vrstice
       [idTekme, idIgralca, sprememba, novaVrednost]; rating pred tekmo je
       novaVrednost - sprememba. */
    @Query("""
            SELECT z.tekma.id, z.igralec.id, z.sprememba, z.novaVrednost
            FROM RatingZgodovina z
            WHERE z.tekma.id IN :idjiTekem AND z.sistem = :sistem
            """)
    List<Object[]> spremembeZaTekme(@Param("idjiTekem") Collection<Long> idjiTekem,
                                    @Param("sistem") String sistem);
}
