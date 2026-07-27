/* Dostop do sifranta krajev. */
package si.turnirko.repozitoriji;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Kraj;

public interface KrajRepozitorij extends JpaRepository<Kraj, Integer> {
}
