/* Dostop do prijav igralcev na dogodke. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.Prijava;

public interface PrijavaRepozitorij extends JpaRepository<Prijava, Long> {

    /* Prijava igralca na dogodek, ne glede na status (baza ima UNIQUE
       dogodek+igralec, zato je kvecjemu ena). Sluzi ponovni prijavi: ce je
       igralec odjavljen, se obstojeci zapis aktivira, ne podvaja. Igralec in
       klub sta nalozena vnaprej, ker gre rezultat takoj v DTO. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.klubObPrijavi
            WHERE p.dogodek.id = :idDogodek AND p.igralec.id = :idIgralec
            """)
    Optional<Prijava> najdiZaDogodekInIgralca(Long idDogodek, Long idIgralec);

    /* Vse prijave dogodka z igralci in klubi v eni poizvedbi. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec i
            LEFT JOIN FETCH p.klubObPrijavi
            WHERE p.dogodek.id = :idDogodek
            ORDER BY i.priimek, i.ime
            """)
    List<Prijava> najdiZaDogodek(Long idDogodek);

    /* Prijave dogodka z danim statusom (za zreb: PRIJAVLJEN).
       Klub je nalozen vnaprej, ker gredo ustvarjene tekme takoj v DTO. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.klubObPrijavi
            WHERE p.dogodek.id = :idDogodek AND p.status = :status
            """)
    List<Prijava> najdiZaDogodekSStatusom(Long idDogodek, Prijava.StatusPrijave status);

    /* Stevilo prijavljenih po turnirjih: [idTurnir, prijav]. Odjavljeni ne
       stejejo - so mehak izbris in ne igrajo. Ena skupinska poizvedba za cel
       seznam turnirjev. */
    @Query("""
            SELECT p.dogodek.turnir.id, COUNT(p) FROM Prijava p
            WHERE p.status <> :odjavljen
            GROUP BY p.dogodek.turnir.id
            """)
    List<Object[]> stejPoTurnirjih(Prijava.StatusPrijave odjavljen);

    /* Isto po dogodkih enega turnirja: [idDogodek, prijav]. */
    @Query("""
            SELECT p.dogodek.id, COUNT(p) FROM Prijava p
            WHERE p.dogodek.turnir.id = :idTurnir AND p.status <> :odjavljen
            GROUP BY p.dogodek.id
            """)
    List<Object[]> stejPoDogodkihTurnirja(Long idTurnir, Prijava.StatusPrijave odjavljen);

    /* Zmagovalci (1. mesto) po turnirjih: [idTurnir, polno ime]. Zakljucen
       turnir se v seznamu bere po zmagovalcu, ne po fazi; turnir z vec dogodki
       jih ima vec, klicatelj vzame prvega. */
    @Query("""
            SELECT p.dogodek.turnir.id, i.ime, i.priimek FROM Prijava p
            JOIN p.igralec i
            WHERE p.koncnoMesto = 1
            ORDER BY p.dogodek.id
            """)
    List<Object[]> zmagovalciPoTurnirjih();

    /* Ena prijava z igralcem in klubom - za izpis po odjavi. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.klubObPrijavi
            WHERE p.id = :id
            """)
    Optional<Prijava> najdiZIgralcem(Long id);
}
