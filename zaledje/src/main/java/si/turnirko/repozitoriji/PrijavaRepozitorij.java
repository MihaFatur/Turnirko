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

    /* Ena prijava z igralcem in klubom - za izpis po odjavi. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.klubObPrijavi
            WHERE p.id = :id
            """)
    Optional<Prijava> najdiZIgralcem(Long id);
}
