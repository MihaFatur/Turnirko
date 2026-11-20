package si.turnirko.repozitoriji;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import si.turnirko.modeli.MrezaStevkZapis;

public interface MrezaStevkRepozitorij extends JpaRepository<MrezaStevkZapis, Long> {

    Optional<MrezaStevkZapis> findFirstBySprejetaTrueOrderByIdDesc();

    Optional<MrezaStevkZapis> findFirstByOrderByIdDesc();
}
