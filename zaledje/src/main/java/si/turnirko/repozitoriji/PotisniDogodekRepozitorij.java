/* Poslani potisni dogodki (V60) in poizvedba po novih tekmah v dnevniku ratinga.
   Poizvedba stoji tu in ne v RatingZgodovinaRepozitorij: ta je last obracuna in
   se ga potisna obvestila ne dotikajo. */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.PotisniDogodek;
import si.turnirko.modeli.RatingZgodovina;

public interface PotisniDogodekRepozitorij extends JpaRepository<PotisniDogodek, String> {

    /* Najvecji id v dnevniku ratinga: zacetek, od koder naprej se obvestila sproti
       zbirajo (starih tekem ob zagonu strezniku ni treba oznanjati). */
    @Query("SELECT COALESCE(MAX(z.id), 0) FROM RatingZgodovina z")
    long najvecjiIdDnevnika();

    /* Odigrane tekme (ne postavitve, odbitki, uvrstitve) od danega id-ja naprej, ki
       veljajo od `od` naprej. Meja casa je druga obramba pred preracunom: ta tekme znova
       zapise z novimi id-ji, a njihov cas je star. Filter po sistemu z concat, kot pri
       drugih poizvedbah po tekmah (NacrtiDnevnikaTest). */
    @Query("""
            SELECT z FROM RatingZgodovina z
            WHERE z.id > :odId AND z.razlog IS NULL
              AND CONCAT(z.sistem, '') = :sistem
              AND (z.tekma IS NOT NULL OR z.tekmaSrecanja IS NOT NULL)
              AND z.veljaOb >= :od
            ORDER BY z.id
            """)
    List<RatingZgodovina> novaTekme(long odId, LocalDateTime od, String sistem);

    @Modifying
    @Query("DELETE FROM PotisniDogodek d WHERE d.poslanoOb < :pred")
    int pobrisiStareje(LocalDateTime pred);
}
