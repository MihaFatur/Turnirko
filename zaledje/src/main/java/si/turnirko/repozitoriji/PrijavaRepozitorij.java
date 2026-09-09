/* Dostop do prijav igralcev na dogodke.

   Vsaka poizvedba, ki gre v DTO, mora vnaprej naloziti TUDI drugega igralca
   para (igralec2, klubObPrijavi2) - pri dvojicah je prijava par in bi
   polovica izpisa sicer sprozila leno nalaganje izven transakcije. */
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
       klub sta nalozena vnaprej, ker gre rezultat takoj v DTO.

       Isce SAMO po nosilcu prijave; kdo nastopa kot soigralec para, pove
       najdiPoSoigralcu. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek AND p.igralec.id = :idIgralec
            """)
    Optional<Prijava> najdiZaDogodekInIgralca(Long idDogodek, Long idIgralec);

    /* Prijava, v kateri je dani igralec SOIGRALEC para. Skupaj z zgornjo
       poizvedbo pove, ali igralec na dogodku ze kje nastopa. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek AND p.igralec2.id = :idIgralec
            """)
    Optional<Prijava> najdiPoSoigralcu(Long idDogodek, Long idIgralec);

    /* Vse prijave dogodka z igralci in klubi v eni poizvedbi. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec i
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek
            ORDER BY i.priimek, i.ime
            """)
    List<Prijava> najdiZaDogodek(Long idDogodek);

    /* Prijave dogodka z danim statusom (za zreb: PRIJAVLJEN).
       Klub je nalozen vnaprej, ker gredo ustvarjene tekme takoj v DTO. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek AND p.status = :status
            """)
    List<Prijava> najdiZaDogodekSStatusom(Long idDogodek, Prijava.StatusPrijave status);

    /* Stevilo prijavljenih po turnirjih: [idTurnir, prijav]. Odjavljeni ne
       stejejo - so mehak izbris in ne igrajo. Ena skupinska poizvedba za cel
       seznam turnirjev.

       Steje se PRIJAVA (torej par kot ena enota), ne glave igralcev: stevec
       stoji ob mrezi in mora povedati, koliko je tekmovalcev v njej. */
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

    /* Zmagovalci (1. mesto) po turnirjih: [idTurnir, ime, priimek, ime2,
       priimek2]. Zakljucen turnir se v seznamu bere po zmagovalcu, ne po fazi;
       turnir z vec dogodki jih ima vec, klicatelj vzame prvega. Zadnji dve
       polji sta zapolnjeni pri dvojicah - zmagovalec je par, ne igralec. */
    @Query("""
            SELECT p.dogodek.turnir.id, i.ime, i.priimek, i2.ime, i2.priimek FROM Prijava p
            JOIN p.igralec i
            LEFT JOIN p.igralec2 i2
            WHERE p.koncnoMesto = 1
            ORDER BY p.dogodek.id
            """)
    List<Object[]> zmagovalciPoTurnirjih();

    /* Zmagovalci (1. mesto) posameznih dogodkov ENEGA turnirja, z imenom
       dogodka - za vrstico "Prvi naslov" na zavihku statistike. Pri dvojicah
       je zmagovalec par, zato je nalozen tudi soigralec. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.dogodek d
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            WHERE d.turnir.id = :idTurnir AND p.koncnoMesto = 1
            ORDER BY d.id
            """)
    List<Prijava> zmagovalciTurnirja(Long idTurnir);

    /* Vsi naslovi (1. mesto) danih igralcev: [idIgralca, idSoigralca,
       idTurnirja, datumZacetkaTurnirja]. Igralec je lahko v katerem koli od
       obeh mest prijave (pri dvojicah je soigralec v drugem), zato vrnemo obe
       polji in klicatelj pogleda tisto, ki ga zanima. Datum je v izbiri, ker
       je "prvi naslov" vprasanje o casu in ne o stevilu naslovov.

       Vez na soigralca mora biti IZRECNO LEVA: pri posamicni prijavi je
       igralec2 prazen, pisava "p.igralec2.id" pa v JPQL pomeni notranji stik
       in bi tiho izpustila vse posamicne naslove - torej ravno tiste, ki jih
       vrstica najveckrat pokaze. */
    @Query("""
            SELECT i.id, i2.id, t.id, t.datumZacetka FROM Prijava p
            JOIN p.dogodek d JOIN d.turnir t
            JOIN p.igralec i
            LEFT JOIN p.igralec2 i2
            WHERE p.koncnoMesto = 1
              AND (i.id IN :idjiIgralcev OR i2.id IN :idjiIgralcev)
            """)
    List<Object[]> naslovi(List<Long> idjiIgralcev);

    /* Ena prijava z igralcema in kluboma - za izpis po odjavi oz. povezavi. */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.id = :id
            """)
    Optional<Prijava> najdiZIgralcem(Long id);
}
