/* Dostop do prijav na dogodke.

   Vsaka poizvedba, ki gre v DTO, mora vnaprej naloziti TUDI drugega igralca
   para (igralec2, klubObPrijavi2) in EKIPO s klubom - pri dvojicah je prijava
   par, pri ekipnem dogodku ekipa, in bi del izpisa sicer sprozil leno
   nalaganje izven transakcije.

   Vez na igralca je pri seznamih prijav LEVA: ekipna prijava igralca nima in
   notranji stik bi celo ekipo tiho izpustil iz mreze in skupin (V28). */
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

    /* Vse prijave dogodka z igralci (ekipami) in klubi v eni poizvedbi.
       Urejene po priimku igralca oz. imenu ekipe. */
    @Query("""
            SELECT p FROM Prijava p
            LEFT JOIN FETCH p.igralec i
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.ekipa e LEFT JOIN FETCH e.klub ek
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek
            ORDER BY COALESCE(i.priimek, e.ime, ek.ime), i.ime, e.zaporedna
            """)
    List<Prijava> najdiZaDogodek(Long idDogodek);

    /* Prijave dogodka z danim statusom (za zreb: PRIJAVLJEN).
       Klub je nalozen vnaprej, ker gredo ustvarjene tekme takoj v DTO. */
    @Query("""
            SELECT p FROM Prijava p
            LEFT JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.ekipa e LEFT JOIN FETCH e.klub
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.dogodek.id = :idDogodek AND p.status = :status
            """)
    List<Prijava> najdiZaDogodekSStatusom(Long idDogodek, Prijava.StatusPrijave status);

    /* Ekipna prijava dane ekipe (ekipa je na dogodku prijavljena natanko enkrat). */
    @Query("""
            SELECT p FROM Prijava p
            JOIN FETCH p.ekipa e LEFT JOIN FETCH e.klub
            JOIN FETCH p.dogodek d JOIN FETCH d.turnir
            WHERE e.id = :idEkipa
            """)
    Optional<Prijava> najdiZaEkipo(Long idEkipa);

    /* Stevilo prijavljenih po turnirjih: [idTurnir, prijav]. Odjavljeni ne
       stejejo - so mehak izbris in ne igrajo. Ena skupinska poizvedba za cel
       seznam turnirjev.

       Steje se PRIJAVA (torej par ali ekipa kot ena enota), ne glave igralcev:
       stevec stoji ob mrezi in mora povedati, koliko je tekmovalcev v njej. */
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
       priimek2, imeEkipe]. Zakljucen turnir se v seznamu bere po zmagovalcu,
       ne po fazi; turnir z vec dogodki jih ima vec, klicatelj vzame prvega.
       Polji 3-4 sta zapolnjeni pri dvojicah - zmagovalec je par, ne igralec -,
       zadnje pa pri ekipnem dogodku (tam sta ime in priimek prazna). */
    @Query("""
            SELECT p.dogodek.turnir.id, i.ime, i.priimek, i2.ime, i2.priimek,
                   COALESCE(e.ime, CONCAT(ek.ime, ' ', e.zaporedna)) FROM Prijava p
            LEFT JOIN p.igralec i
            LEFT JOIN p.igralec2 i2
            LEFT JOIN p.ekipa e LEFT JOIN e.klub ek
            WHERE p.koncnoMesto = 1
            ORDER BY p.dogodek.id
            """)
    List<Object[]> zmagovalciPoTurnirjih();

    /* Zmagovalci (1. mesto) posameznih dogodkov ENEGA turnirja, z imenom
       dogodka - za vrstico "Prvi naslov" na zavihku statistike. Pri dvojicah
       je zmagovalec par, zato je nalozen tudi soigralec. Ekipni dogodki
       izpadejo (stik na igralca je notranji): naslov ekipe ni naslov igralca. */
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
       vrstica najveckrat pokaze. Ekipne prijave izpadejo po notranjem stiku
       na igralca. */
    @Query("""
            SELECT i.id, i2.id, t.id, t.datumZacetka FROM Prijava p
            JOIN p.dogodek d JOIN d.turnir t
            JOIN p.igralec i
            LEFT JOIN p.igralec2 i2
            WHERE p.koncnoMesto = 1
              AND (i.id IN :idjiIgralcev OR i2.id IN :idjiIgralcev)
            """)
    List<Object[]> naslovi(List<Long> idjiIgralcev);

    /* Ena prijava z igralcema (ekipo) in kluboma - za izpis po odjavi oz. povezavi. */
    @Query("""
            SELECT p FROM Prijava p
            LEFT JOIN FETCH p.igralec
            LEFT JOIN FETCH p.igralec2
            LEFT JOIN FETCH p.ekipa e LEFT JOIN FETCH e.klub
            LEFT JOIN FETCH p.klubObPrijavi
            LEFT JOIN FETCH p.klubObPrijavi2
            WHERE p.id = :id
            """)
    Optional<Prijava> najdiZIgralcem(Long id);

    /* Datumi turnirjev, na katerih je igralec nastopil posamicno ali v paru -
       uvoz po njih presodi, ali je dogodek najnovejsi nastop (in klub igralca
       sme zamenjati). */
    @Query("""
            SELECT t.datumZacetka FROM Prijava p JOIN p.dogodek d JOIN d.turnir t
            WHERE p.igralec.id = :idIgralec OR p.igralec2.id = :idIgralec
            """)
    List<java.time.LocalDate> datumiNastopov(Long idIgralec);
}
