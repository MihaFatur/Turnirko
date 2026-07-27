/* Dostop do sifranta klubov. */
package si.turnirko.repozitoriji;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Klub;

public interface KlubRepozitorij extends JpaRepository<Klub, Long> {
}
