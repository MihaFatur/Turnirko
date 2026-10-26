/* Dostop do cakajocih registracij (placljiv paket, placilo se ni potrjeno). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.CakajocaRegistracija;

public interface CakajocaRegistracijaRepozitorij extends JpaRepository<CakajocaRegistracija, Long> {

    Optional<CakajocaRegistracija> findByZeton(String zeton);

    /* Placljive registracije, ki drzijo kodo popusta, dokler placilo ni potrjeno
       (rezervacija mesta pri najvec uporab, KodePopustaStoritev). */
    long countByIdKoda(Long idKoda);

    /* Isto za en naslov: opuscen nakup iste osebe ne sme zasedati mesta njenemu ponovnemu poskusu. */
    long countByIdKodaAndEmail(Long idKoda, String email);

    /* [id kode, stevilo cakajocih] za seznam kod. */
    @Query("select c.idKoda, count(c) from CakajocaRegistracija c where c.idKoda is not null group by c.idKoda")
    List<Object[]> steviloPoKodah();

    /* Registracije, ki do meje niso dobile placila (opuscen nakup) - nocno
       ciscenje jih pobrise, da zgosceno geslo in datum rojstva ne ostaneta v
       bazi brez racuna. Masovni izbris gre mimo enote dela (glej
       PotrditvenaKodaRepozitorij.pobrisiPotekle). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CakajocaRegistracija r WHERE r.ustvarjenaOb < :meja")
    int pobrisiStarejseOd(LocalDateTime meja);
}
