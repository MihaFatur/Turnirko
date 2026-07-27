/* Sifrant krajev: postna stevilka je primarni kljuc, zato bi save()
   ob podvojenem vnosu obstojeci kraj TIHO posodobil. Ta test varuje
   pravilo, da se podvojen vnos izrecno zavrne. */
package si.turnirko.kontrolerji;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import si.turnirko.dto.KrajDto;
import si.turnirko.izjeme.DomenskaIzjema;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KrajiKontrolerTest {

    @Autowired KrajiKontroler krajiKontroler;

    @Test
    void podvojenaPostnaStevilkaSeZavrne() {
        krajiKontroler.ustvari(new KrajDto(8888, "Prvi"));

        assertThrows(DomenskaIzjema.class,
                () -> krajiKontroler.ustvari(new KrajDto(8888, "Drugi")));

        // obstojeci kraj je ostal nedotaknjen
        assertEquals("Prvi", krajiKontroler.seznam().stream()
                .filter(kraj -> kraj.postnaSt() == 8888)
                .findFirst().orElseThrow().ime());
    }
}
