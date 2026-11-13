/* Dostop do narocnin (ena vrstica na uporabnika - glej Narocnina). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.StatusNarocnine;

public interface NarocninaRepozitorij extends JpaRepository<Narocnina, Long> {

    Optional<Narocnina> findByUporabnikId(Long idUporabnik);

    Optional<Narocnina> findByStripeNarocninaId(String stripeNarocninaId);

    /* Preizkusi, ki obvestila o koncu se niso dobili (PreizkusPremiumStoritev). */
    List<Narocnina> findByStatusAndOpomnikPoslanObIsNull(StatusNarocnine status);

    /* Koliko igralcev Premium ZDAJ placuje - stetje v bazi namesto nalaganja
       vseh narocnin (oglas ga vprasa ob vsakem prikazu). Stejeta samo stanji
       placnika, po merilu Narocnina.jeVeljavna: AKTIVNA vedno, PREKLICANA do
       konca placanega obdobja (brez datuma ne). Preizkus ni placnik. Racun
       brez povezanega igralca ali igralec brez kluba v skupnem stevilu ostane
       (stik na igralca ima samo stetje po klubu). */
    @Query("""
            select count(n) from Narocnina n
              join n.uporabnik u
            where n.paket = si.turnirko.modeli.Paket.PREMIUM
              and u.vloga = si.turnirko.modeli.Vloga.IGRALEC
              and (n.status = si.turnirko.modeli.StatusNarocnine.AKTIVNA
                   or (n.status = si.turnirko.modeli.StatusNarocnine.PREKLICANA
                       and n.trenutnoObdobjeDo > :zdaj))
            """)
    long prestejVeljavnePremiumIgralce(LocalDateTime zdaj);

    /* Isto samo za racune, katerih igralec je v danem klubu. */
    @Query("""
            select count(n) from Narocnina n
              join n.uporabnik u
              join u.igralec i
            where n.paket = si.turnirko.modeli.Paket.PREMIUM
              and u.vloga = si.turnirko.modeli.Vloga.IGRALEC
              and i.klub.id = :idKluba
              and (n.status = si.turnirko.modeli.StatusNarocnine.AKTIVNA
                   or (n.status = si.turnirko.modeli.StatusNarocnine.PREKLICANA
                       and n.trenutnoObdobjeDo > :zdaj))
            """)
    long prestejVeljavnePremiumIgralceKluba(Long idKluba, LocalDateTime zdaj);

    /* Vse narocnine skupaj z racunom - en poizvedba za administratorjev seznam
       racunov. Brez join fetch bi vsak racun sprozil svojo poizvedbo za uporabnika. */
    @Query("select n from Narocnina n join fetch n.uporabnik")
    List<Narocnina> najdiVseZRacuni();
}
