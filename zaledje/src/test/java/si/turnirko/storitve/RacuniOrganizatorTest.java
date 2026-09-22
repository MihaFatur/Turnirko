/* Onboarding organizatorja: registracija ustvari racun v stanju CAKA z vlogo
   ORGANIZATOR (in nepotrjenim naslovom); administrator ga potrdi in mu
   (neobvezno) dodeli klub. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RacuniOrganizatorTest {

    private static final String IP = "127.0.0.1";

    @Autowired RacuniStoritev racuniStoritev;
    @Autowired RegistracijaStoritev registracijaStoritev;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;

    /* Omejevalnik zivi v pomnilniku cez vse teste; isti naslovi se tu
       registrirajo veckrat zapored. */
    @BeforeEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    private static RegistracijaVnos organizator(String ime, String priimek, String email) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", true, null, null);
    }

    private static RegistracijaVnos igralec(String ime, String priimek, String email) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", false,
                LocalDate.of(1990, 1, 1), null);
    }

    /* Registracija organizatorja -> CAKA + vloga ORGANIZATOR; potrditev s
       klubom -> POTRJEN + klub dodeljen. */
    @Test
    void registracijaInPotrditevOrganizatorja() {
        registracijaStoritev.registriraj(organizator("NTK", "Vodja", "org@test.si"), IP);

        Uporabnik racun = uporabnikRepozitorij.findByUporabniskoIme("org@test.si").orElseThrow();
        assertEquals(Vloga.ORGANIZATOR, racun.getVloga());
        assertEquals(StatusRacuna.CAKA, racun.getStatus());
        assertFalse(racun.jeEmailPotrjen(), "naslov je potrjen sele s kodo");
        Klub klub = klubRepozitorij.save(new Klub("Namizni klub", "NK"));

        racuniStoritev.potrdiOrganizatorja(racun.getId(), klub.getId());

        Uporabnik potrjen = uporabnikRepozitorij.najdiZVsemPoId(racun.getId()).orElseThrow();
        assertEquals(StatusRacuna.POTRJEN, potrjen.getStatus());
        assertEquals(Vloga.ORGANIZATOR, potrjen.getVloga());
        assertEquals(klub.getId(), potrjen.getKlub().getId());
    }

    /* Organizator je lahko potrjen tudi brez kluba (upravlja samo svoje). */
    @Test
    void potrditevOrganizatorjaBrezKluba() {
        registracijaStoritev.registriraj(organizator("Solo", "Vodja", "solo@test.si"), IP);
        Uporabnik racun = uporabnikRepozitorij.findByUporabniskoIme("solo@test.si").orElseThrow();

        racuniStoritev.potrdiOrganizatorja(racun.getId(), null);

        Uporabnik potrjen = uporabnikRepozitorij.najdiZVsemPoId(racun.getId()).orElseThrow();
        assertEquals(StatusRacuna.POTRJEN, potrjen.getStatus());
        assertNull(potrjen.getKlub());
    }

    /* Poti potrjevanja se ne smeta mesati: igralca ni mogoce potrditi kot
       organizatorja in obratno. */
    @Test
    void potiPotrjevanjaSeNeMesajo() {
        registracijaStoritev.registriraj(igralec("Ana", "Igralka", "igralka@test.si"), IP);
        Uporabnik igralec = uporabnikRepozitorij.findByUporabniskoIme("igralka@test.si").orElseThrow();
        assertEquals(Vloga.IGRALEC, igralec.getVloga());
        assertThrows(DomenskaIzjema.class,
                () -> racuniStoritev.potrdiOrganizatorja(igralec.getId(), null));

        registracijaStoritev.registriraj(organizator("NTK", "Vodja", "org2@test.si"), IP);
        Uporabnik organizator = uporabnikRepozitorij.findByUporabniskoIme("org2@test.si").orElseThrow();
        assertThrows(DomenskaIzjema.class,
                () -> racuniStoritev.potrdi(organizator.getId(), new PotrditevRacunaVnos(1L)));
    }
}
