/* Lastnistvo turnirjev in lig: kdo jih sme urejati.

   Pravilo (LastnistvoStoritev): administrator sme vse, organizator pa le svoje
   (ustvaril == on) ali od svojega kluba (klubLastnik == njegov klub);
   organizator brez kluba upravlja samo svoje. Preverba se izvaja v storitvah,
   zato jo ti testi (ki nastavijo prijavo v SecurityContext) normalno sprozijo. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.TurnirVnos;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LastnistvoTest {

    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired LastnistvoStoritev lastnistvo;
    @Autowired TurnirjiStoritev turnirjiStoritev;
    @Autowired LigaStoritev ligaStoritev;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    /* Organizator NE more urejati turnirja drugega kluba (403). */
    @Test
    void organizatorNeMoreUrejatiTujegaTurnirja() {
        Klub klubA = klub("Klub A");
        Klub klubB = klub("Klub B");
        Uporabnik orgA = organizator("orgA@test", klubA);
        organizator("orgB@test", klubB);

        Turnir turnir = turnir(orgA, klubA);

        prijava("orgB@test");
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriTurnirPoId(turnir.getId()));
    }

    /* Lastnik in klubski kolega ter administrator smejo. */
    @Test
    void lastnikKlubskiKolegaInAdminSmejo() {
        Klub klubA = klub("Klub A");
        Uporabnik orgA1 = organizator("orgA1@test", klubA);
        organizator("orgA2@test", klubA); // isti klub
        admin("admin@test");

        Turnir turnir = turnir(orgA1, klubA);

        prijava("orgA1@test");
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("orgA2@test"); // klubski kolega
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("admin@test"); // administrator sme vse
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));
    }

    /* Organizator brez kluba upravlja samo svoje; drug organizator ne. */
    @Test
    void organizatorBrezKlubaUpravljaSamoSvoje() {
        Uporabnik orgBrezKluba = organizator("solo@test", null);
        Klub klubB = klub("Klub B");
        organizator("orgB@test", klubB);

        Turnir turnir = turnir(orgBrezKluba, null);

        prijava("solo@test");
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("orgB@test"); // ni lastnik in klubLastnik je prazen -> ne sme
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriTurnirPoId(turnir.getId()));
    }

    /* Ob kreaciji se zabelezi lastnik in klub organizatorja. */
    @Test
    void kreacijaZabeleziLastnika() {
        Klub klubA = klub("Klub A");
        Uporabnik orgA = organizator("orgA@test", klubA);

        prijava("orgA@test");
        Turnir turnir = turnirjiStoritev.ustvari(new TurnirVnos("Nov turnir", null, null, null, null, null, null));

        assertEquals(orgA.getId(), turnir.getUstvaril().getId());
        assertEquals(klubA.getId(), turnir.getKlubLastnik().getId());
    }

    /* Brez prijave (interni klic/test) preverba ne omejuje; lastnik ostane prazen. */
    @Test
    void brezPrijaveNiOmejitve() {
        Turnir turnir = turnirjiStoritev.ustvari(new TurnirVnos("Anonimni turnir", null, null, null, null, null, null));
        assertNull(turnir.getUstvaril());
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));
    }

    /* Enak vzorec velja za lige: tuj organizator ne more izbrisati (403). */
    @Test
    void organizatorNeMoreBrisatiTujeLige() {
        Klub klubA = klub("Klub A");
        Klub klubB = klub("Klub B");
        Uporabnik orgA = organizator("ligaA@test", klubA);
        organizator("ligaB@test", klubB);

        Liga liga = liga(orgA, klubA);

        prijava("ligaB@test");
        assertThrows(PrepovedanoIzjema.class, () -> ligaStoritev.zbrisi(liga.getId()));

        prijava("ligaA@test"); // lastnik sme
        assertDoesNotThrow(() -> ligaStoritev.zbrisi(liga.getId()));
    }

    // ---------- Pomozno ----------

    private Klub klub(String ime) {
        return klubRepozitorij.save(new Klub(ime, null));
    }

    private Uporabnik organizator(String prijavnoIme, Klub klub) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setKlub(klub);
        return uporabnikRepozitorij.save(u);
    }

    private Uporabnik admin(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ADMIN);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private Turnir turnir(Uporabnik lastnik, Klub klubLastnik) {
        Turnir t = new Turnir();
        t.setIme("Testni turnir");
        t.setUstvaril(lastnik);
        t.setKlubLastnik(klubLastnik);
        return turnirRepozitorij.save(t);
    }

    private Liga liga(Uporabnik lastnik, Klub klubLastnik) {
        Liga l = new Liga();
        l.setIme("Testna liga");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        l.setKlubLastnik(klubLastnik);
        return ligaRepozitorij.save(l);
    }

    private void prijava(String prijavnoIme) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATOR"))));
    }
}
