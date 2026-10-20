/* Dostop do cakajocih registracij (placljiv paket, placilo se ni potrjeno). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.CakajocaRegistracija;

public interface CakajocaRegistracijaRepozitorij extends JpaRepository<CakajocaRegistracija, Long> {

    Optional<CakajocaRegistracija> findByZeton(String zeton);

    /* Registracije, ki do meje niso dobile placila (opuscen nakup) - nocno
       ciscenje jih pobrise, da zgosceno geslo in datum rojstva ne ostaneta v
       bazi brez racuna. Masovni izbris gre mimo enote dela (glej
       PotrditvenaKodaRepozitorij.pobrisiPotekle). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CakajocaRegistracija r WHERE r.ustvarjenaOb < :meja")
    int pobrisiStarejseOd(LocalDateTime meja);
}
