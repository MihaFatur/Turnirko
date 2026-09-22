/* Dostop do potrditvenih kod (e-posta, skrbnik, pozabljeno geslo). */
package si.turnirko.repozitoriji;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.NamenKode;
import si.turnirko.modeli.PotrditvenaKoda;

public interface PotrditvenaKodaRepozitorij extends JpaRepository<PotrditvenaKoda, Long> {

    /* Vse kode racuna za dani namen (ziva naj bi bila najvec ena; starejse
       so ostanki, ki jih nova koda odnese). */
    List<PotrditvenaKoda> findByRacunIdAndNamen(Long idRacun, NamenKode namen);

    /* Vse kode racuna - pred izbrisom racuna jih je treba pobrisati tudi v
       enoti dela, ne le v bazi (ON DELETE CASCADE), sicer Hibernate ob
       izpisu naleti na kodo, ki kaze na ze izbrisan racun. */
    List<PotrditvenaKoda> findByRacunId(Long idRacun);

    /* Kode, ki so potekle pred mejo - nocno ciscenje jih pobrise, da tabela
       ne raste s poskusi, ki se niso nikoli koncali. Masovni izbris gre mimo
       enote dela, zato se ta pred njim izpise in po njem izprazni: sicer bi
       v njej ostale kode, ki jih v bazi ni vec, in bi ob naslednjem izpisu
       kazale na izbrisane racune. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PotrditvenaKoda k WHERE k.poteceOb < :meja")
    int pobrisiPotekle(LocalDateTime meja);
}
