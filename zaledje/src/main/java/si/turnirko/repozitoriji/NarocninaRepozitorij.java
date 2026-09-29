/* Dostop do narocnin (ena vrstica na uporabnika - glej Narocnina). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Narocnina;

public interface NarocninaRepozitorij extends JpaRepository<Narocnina, Long> {

    Optional<Narocnina> findByUporabnikId(Long idUporabnik);

    Optional<Narocnina> findByStripeNarocninaId(String stripeNarocninaId);

    /* Narocnine igralcev s Premium, ki lahko se veljajo (AKTIVNA ali PREKLICANA;
       ali je preklicana res se veljavna, presodi Narocnina.jeVeljavna - potek
       obdobja je zapisan kot besedilo in ga ne primerjamo v poizvedbi). Igralec
       in klub sta nalozena vnaprej: klub je potreben za stetje po klubih, LEVI
       join pa zato, ker racun brez povezanega igralca ali igralec brez kluba ne
       smeta izpasti iz skupnega stevila. */
    @Query("""
            select n from Narocnina n
              join fetch n.uporabnik u
              left join fetch u.igralec i
              left join fetch i.klub
            where n.paket = si.turnirko.modeli.Paket.PREMIUM
              and u.vloga = si.turnirko.modeli.Vloga.IGRALEC
              and n.status in (si.turnirko.modeli.StatusNarocnine.AKTIVNA,
                               si.turnirko.modeli.StatusNarocnine.PREKLICANA)
            """)
    List<Narocnina> najdiPremiumIgralce();
}
