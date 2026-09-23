/* Dostop do narocnin (ena vrstica na uporabnika - glej Narocnina). */
package si.turnirko.repozitoriji;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.Narocnina;

public interface NarocninaRepozitorij extends JpaRepository<Narocnina, Long> {

    Optional<Narocnina> findByUporabnikId(Long idUporabnik);

    Optional<Narocnina> findByStripeNarocninaId(String stripeNarocninaId);
}
