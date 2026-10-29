/* Dostop do opisov slik (V54). Bajti so v SlikaRazlicicaRepozitorij. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.NamenSlike;
import si.turnirko.modeli.Slika;

public interface SlikaRepozitorij extends JpaRepository<Slika, Long> {

    Optional<Slika> findByZgostitev(String zgostitev);

    /* Adminova knjiznica: vse slike namena, najnovejse zgoraj. */
    List<Slika> findByNamenOrderByIdDesc(NamenSlike namen);

    /* Organizatorjeva knjiznica: kar je nalozil sam, in logotipi tekmovanj, ki
       jih sme urejati (svoja in od svojega kluba - isto merilo kot
       LastnistvoStoritev). Logotip, ki ga je nalozil klubski kolega, je tako na
       voljo tudi njemu, ko ureja ligo istega kluba. */
    @Query("""
            SELECT s FROM Slika s
            WHERE s.namen = :namen AND (
                s.idNalozil = :idUporabnik
                OR s.id IN (SELECT l.logotip.id FROM Liga l
                            WHERE l.ustvaril.id = :idUporabnik
                               OR (:idKlub IS NOT NULL AND l.klubLastnik.id = :idKlub))
                OR s.id IN (SELECT t.logotip.id FROM Turnir t
                            WHERE t.ustvaril.id = :idUporabnik
                               OR (:idKlub IS NOT NULL AND t.klubLastnik.id = :idKlub)))
            ORDER BY s.id DESC
            """)
    List<Slika> knjiznicaOrganizatorja(NamenSlike namen, Long idUporabnik, Long idKlub);
}
