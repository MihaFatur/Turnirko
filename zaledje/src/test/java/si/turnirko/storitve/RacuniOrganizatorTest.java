/* Onboarding organizatorja: registracija ustvari racun v stanju CAKA z vlogo
   ORGANIZATOR; administrator ga potrdi in mu (neobvezno) dodeli klub. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.UporabnikDto;
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

    @Autowired RacuniStoritev racuniStoritev;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;

    /* Registracija organizatorja -> CAKA + vloga ORGANIZATOR; potrditev s
       klubom -> POTRJEN + klub dodeljen. */
    @Test
    void registracijaInPotrditevOrganizatorja() {
        UporabnikDto reg = racuniStoritev.registriraj(new RegistracijaVnos(
                "NTK", "Vodja", null, "org@test.si", "geslo1234", true));
        assertEquals(Vloga.ORGANIZATOR, reg.vloga());
        assertEquals(StatusRacuna.CAKA, reg.status());

        Uporabnik racun = uporabnikRepozitorij.findByUporabniskoIme("org@test.si").orElseThrow();
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
        racuniStoritev.registriraj(new RegistracijaVnos(
                "Solo", "Vodja", null, "solo@test.si", "geslo1234", true));
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
        racuniStoritev.registriraj(new RegistracijaVnos(
                "Ana", "Igralka", null, "igralka@test.si", "geslo1234", false));
        Uporabnik igralec = uporabnikRepozitorij.findByUporabniskoIme("igralka@test.si").orElseThrow();
        assertEquals(Vloga.IGRALEC, igralec.getVloga());
        assertThrows(DomenskaIzjema.class,
                () -> racuniStoritev.potrdiOrganizatorja(igralec.getId(), null));

        racuniStoritev.registriraj(new RegistracijaVnos(
                "NTK", "Vodja", null, "org2@test.si", "geslo1234", true));
        Uporabnik organizator = uporabnikRepozitorij.findByUporabniskoIme("org2@test.si").orElseThrow();
        assertThrows(DomenskaIzjema.class,
                () -> racuniStoritev.potrdi(organizator.getId(), new PotrditevRacunaVnos(1L)));
    }
}
