/* Dostop do uporabnikov (administratorjev in igralcev). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;

public interface UporabnikRepozitorij extends JpaRepository<Uporabnik, Long> {

    Optional<Uporabnik> findByUporabniskoIme(String uporabniskoIme);

    /* E-posta kot prijavno ime se ob registraciji shrani z malimi crkami; ta
       iskalnik je varovalka za vnos z velikimi (registracija, kode, geslo). */
    Optional<Uporabnik> findByUporabniskoImeIgnoreCase(String uporabniskoIme);

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

    /* Vsi racuni (igralci, organizatorji IN administratorji - kartoteka na strani
       Dostopi jih steje in kaze; administratorjev se ne potrjuje in ne ureja,
       zato jih mutacije zavrnejo), cakajoci najprej (administrator jih mora
       obdelati), znotraj tega najnovejsi na vrhu. Nalozi tudi (pri organizatorju
       potrjen) klub, ker ga bere DTO. */
    @Query("""
            SELECT u FROM Uporabnik u
            LEFT JOIN FETCH u.igralec i LEFT JOIN FETCH i.klub
            LEFT JOIN FETCH u.klub
            LEFT JOIN FETCH u.klubZelja
            ORDER BY CASE WHEN u.status = si.turnirko.modeli.StatusRacuna.CAKA THEN 0 ELSE 1 END,
                     u.id DESC
            """)
    List<Uporabnik> najdiRacuneOseb();

    long countByVlogaAndStatus(si.turnirko.modeli.Vloga vloga, StatusRacuna status);

    /* Vsi racuni v danem stanju (za stevec cakajocih - igralci IN organizatorji;
       administrator je vedno POTRJEN, zato v CAKA ne pade). */
    long countByStatus(StatusRacuna status);

    /* Racuni, ki cakajo na admina IN so za to sploh pripravljeni: naslov
       potrjen, skrbnik (ce je potreben) tudi. Nepotrjene registracije admina
       ne zaposlujejo - nocno ciscenje jih odnese. */
    @Query("""
            SELECT COUNT(u) FROM Uporabnik u
            WHERE u.status = si.turnirko.modeli.StatusRacuna.CAKA
              AND u.emailPotrjenOb IS NOT NULL
              AND (u.emailSkrbnika IS NULL OR u.skrbnikPotrjenOb IS NOT NULL)
            """)
    long steviloCakajocihNaAdmina();

    /* Registracije, pri katerih lastnik naslova kode ni vpisal do meje.
       Zavrnjeni racuni ostanejo: zavrnitev je adminova odlocitev, ki naslov
       zasede, dokler ga admin sam ne izbrise. */
    @Query("""
            SELECT u FROM Uporabnik u
            WHERE u.emailPotrjenOb IS NULL
              AND u.vloga <> si.turnirko.modeli.Vloga.ADMIN
              AND u.status <> si.turnirko.modeli.StatusRacuna.ZAVRNJEN
              AND u.ustvarjenOb < :meja
            """)
    List<Uporabnik> najdiNepotrjeneStarejseOd(LocalDateTime meja);

    /* Racuni mlajsih od 15 let, pri katerih skrbnik soglasja ni dal do meje. */
    @Query("""
            SELECT u FROM Uporabnik u
            WHERE u.emailSkrbnika IS NOT NULL
              AND u.skrbnikPotrjenOb IS NULL
              AND u.igralec IS NULL
              AND u.ustvarjenOb < :meja
            """)
    List<Uporabnik> najdiBrezSoglasjaStarejseOd(LocalDateTime meja);

    /* Racuni, povezani z igralci iz kadra katere koli ekipe lige - prejemniki
       obvestila po kolu (ObvestilaPoKoluStoritev), poleg tistih, ki ligo
       spremljajo. Igralec se nalozi vnaprej: obvestilo bere njegove tekme. */
    @Query("""
            SELECT DISTINCT u FROM Uporabnik u JOIN FETCH u.igralec i
            WHERE i.id IN (
                SELECT k.igralec.id FROM KaderEkipe k JOIN k.ekipa e JOIN e.liga l
                WHERE l.id = :idLiga)
            """)
    List<Uporabnik> najdiIgralceLige(Long idLiga);

    /* Racuni, ki ligo spremljajo (spremljana_liga), z igralcem, ce ga imajo. */
    @Query("""
            SELECT u FROM Uporabnik u LEFT JOIN FETCH u.igralec
            WHERE u.id IN (SELECT s.idRacun FROM SpremljanaLiga s WHERE s.idLiga = :idLiga)
            """)
    List<Uporabnik> najdiSpremljevalceLige(Long idLiga);

    Optional<Uporabnik> findByZetonOdjave(String zetonOdjave);

    /* Racun osebnega koledarja .ics po zetonu v naslovu (V58). */
    Optional<Uporabnik> findByZetonKoledarja(String zetonKoledarja);
}
