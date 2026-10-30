/* Dostop do profilnih slik (V56). Bajti so v ProfilnaSlikaVsebinaRepozitorij. */
package si.turnirko.repozitoriji;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.ProfilnaSlika;
import si.turnirko.modeli.StanjeProfilneSlike;
import si.turnirko.modeli.StatusRacuna;

public interface ProfilnaSlikaRepozitorij extends JpaRepository<ProfilnaSlika, Long> {

    Optional<ProfilnaSlika> findByUporabnikId(Long idUporabnik);

    Optional<ProfilnaSlika> findByZgostitev(String zgostitev);

    /* Objavljene slike danih igralcev z aktivnim, potrjenim računom. Premium se
       preveri v storitvi (NarocninaStoritev.imaPremium) - pravilo veljavnosti
       naročnine živi na enem mestu. */
    @Query("""
            SELECT p FROM ProfilnaSlika p
            JOIN FETCH p.uporabnik u
            JOIN FETCH u.igralec i
            WHERE i.id IN :idji AND p.stanje = :stanje
              AND u.status = :status AND u.aktiven = true
            """)
    List<ProfilnaSlika> objavljeneZaIgralce(Collection<Long> idji, StanjeProfilneSlike stanje,
                                            StatusRacuna status);

    /* Vse slike z računom in igralcem - za adminov pregled (slik je toliko, kot je
       igralcev s Premium, zato v Javi izbere čakajoče in nedavne). */
    @Query("""
            SELECT p FROM ProfilnaSlika p
            JOIN FETCH p.uporabnik u
            LEFT JOIN FETCH u.igralec
            ORDER BY p.id DESC
            """)
    List<ProfilnaSlika> vseZRacunom();
}
