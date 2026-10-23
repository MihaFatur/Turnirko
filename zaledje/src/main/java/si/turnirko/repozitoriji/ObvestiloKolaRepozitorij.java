/* Dostop do obdelanih kol obvestila po kolu (V50). */
package si.turnirko.repozitoriji;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.ObvestiloKola;

public interface ObvestiloKolaRepozitorij extends JpaRepository<ObvestiloKola, ObvestiloKola.Kljuc> {

    /* Kola lige, ki so ze obdelana (poslana ali preskocena). */
    @Query("SELECT o.kolo FROM ObvestiloKola o WHERE o.idLiga = :idLiga")
    List<Integer> obdelanaKola(Long idLiga);
}
