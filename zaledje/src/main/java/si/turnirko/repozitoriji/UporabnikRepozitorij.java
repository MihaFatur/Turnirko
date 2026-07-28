/* Dostop do uporabnikov (administratorjev in igralcev). */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;

public interface UporabnikRepozitorij extends JpaRepository<Uporabnik, Long> {

    Optional<Uporabnik> findByUporabniskoIme(String uporabniskoIme);

    boolean existsByUporabniskoImeIgnoreCase(String uporabniskoIme);

    boolean existsByIgralecId(Long idIgralec);

    /* Prijavljeni uporabnik skupaj z igralcem, njegovim klubom in lastnim
       klubom (organizatorjeva pripadnost) - profil in preverba lastnistva jih
       berejo, pretvorba v DTO pa tece izven transakcije. */
    @Query("""
            SELECT u FROM Uporabnik u
            LEFT JOIN FETCH u.igralec i LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH u.klub
            LEFT JOIN FETCH u.klubZelja
            WHERE u.uporabniskoIme = :uporabniskoIme
            """)
    Optional<Uporabnik> najdiZVsem(String uporabniskoIme);

    @Query("""
            SELECT u FROM Uporabnik u
            LEFT JOIN FETCH u.igralec i LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH u.klub
            LEFT JOIN FETCH u.klubZelja
            WHERE u.id = :id
            """)
    Optional<Uporabnik> najdiZVsemPoId(Long id);

    /* Vsi racuni oseb (igralcev in organizatorjev - ne administratorjev, ki se
       ne potrjujejo), cakajoci najprej (administrator jih mora obdelati),
       znotraj tega najnovejsi na vrhu. Nalozi tudi (pri organizatorju potrjen)
       klub, ker ga bere DTO. */
    @Query("""
            SELECT u FROM Uporabnik u
            LEFT JOIN FETCH u.igralec i LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH u.klub
            LEFT JOIN FETCH u.klubZelja
            WHERE u.vloga IN (si.turnirko.modeli.Vloga.IGRALEC, si.turnirko.modeli.Vloga.ORGANIZATOR)
            ORDER BY CASE WHEN u.status = si.turnirko.modeli.StatusRacuna.CAKA THEN 0 ELSE 1 END,
                     u.id DESC
            """)
    List<Uporabnik> najdiRacuneOseb();

    long countByVlogaAndStatus(si.turnirko.modeli.Vloga vloga, StatusRacuna status);

    /* Vsi racuni v danem stanju (za stevec cakajocih - igralci IN organizatorji;
       administrator je vedno POTRJEN, zato v CAKA ne pade). */
    long countByStatus(StatusRacuna status);
}
