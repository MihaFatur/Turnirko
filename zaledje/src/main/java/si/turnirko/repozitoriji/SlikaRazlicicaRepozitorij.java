/* Bajti razlicic slik (V54). */
package si.turnirko.repozitoriji;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.SlikaRazlicica;
import si.turnirko.modeli.VelikostSlike;

public interface SlikaRazlicicaRepozitorij extends JpaRepository<SlikaRazlicica, Long> {

    /* Razlicica po javnem naslovu slike (zgostitvi) - edina pot do bajtov. */
    @Query("""
            SELECT r FROM SlikaRazlicica r, Slika s
            WHERE r.idSlika = s.id AND s.zgostitev = :zgostitev AND r.velikost = :velikost
            """)
    Optional<SlikaRazlicica> najdi(String zgostitev, VelikostSlike velikost);

    @Modifying
    @Query("DELETE FROM SlikaRazlicica r WHERE r.idSlika = :idSlika")
    void pobrisiZaSliko(Long idSlika);
}
