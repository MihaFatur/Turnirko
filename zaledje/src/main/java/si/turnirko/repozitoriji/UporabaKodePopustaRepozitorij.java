/* Dostop do porab kod popusta (V53): kateri racun je vpisal katero kodo. */
package si.turnirko.repozitoriji;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import si.turnirko.modeli.UporabaKodePopusta;

public interface UporabaKodePopustaRepozitorij extends JpaRepository<UporabaKodePopusta, Long> {

    /* Koda je ze nalozena: klicatelji jo potrebujejo takoj, racun pa je lahko
       v drugi enoti dela. */
    @Query("select u from UporabaKodePopusta u join fetch u.koda where u.idUporabnik = :idUporabnik")
    Optional<UporabaKodePopusta> najdiZaUporabnika(Long idUporabnik);

    long countByKodaId(Long idKoda);

    /* Brez nalaganja vrstice: ko nepotrjen racun zamenja nova registracija, baza
       vrstico porabe odnese sama, SQLite pa njen id da novi vrstici - nalozena
       stara bi se v isti enoti dela ujela z novo (DuplicateKey). */
    boolean existsByIdUporabnikAndKodaId(Long idUporabnik, Long idKoda);

    /* [id kode, stevilo porab] za seznam kod (ena poizvedba, ne ena na kodo). */
    @Query("select u.koda.id, count(u) from UporabaKodePopusta u group by u.koda.id")
    List<Object[]> steviloPoKodah();
}
