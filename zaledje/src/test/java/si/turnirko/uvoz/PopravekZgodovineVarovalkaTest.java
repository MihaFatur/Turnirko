/* Varovalka ukaza "popravek" (najdba E3 pregleda kakovosti, nov 2026): ukaz
   brise in znova pise uvozeno zgodovino in rating, zazene pa se z istim
   programom in povezavo kot aplikacija. Brez izrecne potrditve z naslovom
   baze, ki se ujema z dejansko povezavo, se ne sme zagnati; na strezniku
   (profil splet) niti z njo.

   Ukaz je sestavljen z dejanskim virom podatkov testne baze in BREZ
   repozitorijev (null): ce bi varovalka zagon spustila naprej, bi test padel z
   drugo napako, v bazo pa ne bi mogel zapisati nicesar. */
package si.turnirko.uvoz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import si.turnirko.repozitoriji.IgralecRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class PopravekZgodovineVarovalkaTest {

    @Autowired private DataSource podatkovniVir;
    @Autowired private Environment okolje;
    @Autowired private IgralecRepozitorij igralecRepozitorij;

    @Test
    void brezPotrditveSeUkazNeZazene() {
        long igralcev = igralecRepozitorij.count();
        PopravekZgodovineUkaz ukaz = ukaz(null, okolje);

        IllegalStateException zavrnitev = assertThrows(IllegalStateException.class, () -> ukaz.run(null));

        String dejanska = VarovalkaBaze.naslovBaze(podatkovniVir);
        assertTrue(zavrnitev.getMessage().contains("se NE zazene"), zavrnitev.getMessage());
        assertTrue(zavrnitev.getMessage().contains("--" + PopravekZgodovineUkaz.POTRDITEV + "=" + dejanska),
                "sporocilo pove, kako zagon potrditi in za katero bazo: " + zavrnitev.getMessage());
        assertEquals(igralcev, igralecRepozitorij.count());
    }

    /* Potrjen za produkcijski naslov, povezan na drugo bazo (ali obratno): ne gre. */
    @Test
    void potrditevZaDrugoBazoSeNeZazene() {
        PopravekZgodovineUkaz ukaz = ukaz("baza:5432/turnirko", okolje);

        IllegalStateException zavrnitev = assertThrows(IllegalStateException.class, () -> ukaz.run(null));

        assertTrue(zavrnitev.getMessage().contains("baza:5432/turnirko"), zavrnitev.getMessage());
        assertTrue(zavrnitev.getMessage().contains(VarovalkaBaze.naslovBaze(podatkovniVir)),
                zavrnitev.getMessage());
    }

    /* Na strezniku (profil splet) tudi pravilna potrditev ne pomaga. */
    @Test
    void vProfiluStreznikaSeNeZazeneNitiSPotrditvijo() {
        MockEnvironment streznik = new MockEnvironment();
        streznik.setActiveProfiles("splet", "popravek");
        PopravekZgodovineUkaz ukaz = ukaz(VarovalkaBaze.naslovBaze(podatkovniVir), streznik);

        IllegalStateException zavrnitev = assertThrows(IllegalStateException.class, () -> ukaz.run(null));

        assertTrue(zavrnitev.getMessage().contains("profil \"splet\""), zavrnitev.getMessage());
    }

    /* Ujemajoca potrditev (velikost crk in presledki ne stejejo) spusti naprej. */
    @Test
    void ujemajocaPotrditevSpustiNaprej() {
        String dejanska = VarovalkaBaze.naslovBaze(podatkovniVir);
        PopravekZgodovineUkaz ukaz = ukaz("  " + dejanska.toUpperCase() + " ", okolje);

        assertDoesNotThrow(ukaz::zahtevajPotrjenoBazo);
    }

    @Test
    void naslovBazeJeStreznikVrataInIme() {
        assertEquals("localhost:5432/turnirko",
                VarovalkaBaze.izNaslovaJdbc("jdbc:postgresql://localhost:5432/turnirko"));
        assertEquals("baza:5432/turnirko",
                VarovalkaBaze.izNaslovaJdbc("jdbc:postgresql://baza:5432/turnirko?sslmode=disable"));
        assertTrue(VarovalkaBaze.naslovBaze(podatkovniVir).matches("[^/]+:\\d+/turnirko"),
                VarovalkaBaze.naslovBaze(podatkovniVir));
    }

    private PopravekZgodovineUkaz ukaz(String potrjenaBaza, Environment zaOkolje) {
        PopravekZgodovineUkaz ukaz = new PopravekZgodovineUkaz(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                podatkovniVir, zaOkolje);
        ukaz.potrjenaBaza = potrjenaBaza;
        return ukaz;
    }
}
