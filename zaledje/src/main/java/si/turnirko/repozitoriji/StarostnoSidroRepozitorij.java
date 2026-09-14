/* Dostop do starostnega sidra (glej StarostnoSidro). */
package si.turnirko.repozitoriji;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.StarostnoSidro;

public interface StarostnoSidroRepozitorij
        extends JpaRepository<StarostnoSidro, StarostnoSidro.Kljuc> {
}
