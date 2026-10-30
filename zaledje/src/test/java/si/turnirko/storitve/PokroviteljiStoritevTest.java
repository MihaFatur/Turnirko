/* Pokrovitelji lige in turnirja (V56, PokroviteljiStoritev): seznam se shrani v
   celoti, preveri pred izbrisom starega, povezava je samo spletni naslov, logotip
   pokrovitelja je v rabi (ga ni mogoče izbrisati iz knjižnice). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
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

import si.turnirko.dto.PokroviteljDto;
import si.turnirko.dto.PokroviteljiVnos.Pokrovitelj;
import si.turnirko.dto.SlikaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.TestneSlike;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PokroviteljiStoritevTest {

    @Autowired PokroviteljiStoritev storitev;
    @Autowired SlikeStoritev slikeStoritev;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
        omejevalnik.pocistiVse();
    }

    /* Lastnik shrani trak; vrstni red je vrstni red vnosa, povezava brez sheme
       dobi https://, prazna ostane brez povezave. */
    @Test
    void lastnikShraniTrakLige() {
        Uporabnik lastnik = organizator("lastnik-pokr@test");
        Liga liga = liga(lastnik, null);
        prijava(lastnik);
        SlikaDto a = slikeStoritev.nalozi(TestneSlike.png(400, 200));
        SlikaDto b = slikeStoritev.nalozi(TestneSlike.png(300, 300));

        List<PokroviteljDto> shranjeni = storitev.shraniZaLigo(liga.getId(), List.of(
                new Pokrovitelj(a.id(), "  Petrol ", "petrol.si", true),
                new Pokrovitelj(b.id(), "Mesarija Kos", " ", false)));

        assertEquals(2, shranjeni.size());
        assertEquals("Petrol", shranjeni.get(0).ime());
        assertEquals("https://petrol.si", shranjeni.get(0).povezava());
        assertTrue(shranjeni.get(0).glavni());
        assertNull(shranjeni.get(1).povezava());
        assertEquals(List.of("Petrol", "Mesarija Kos"),
                storitev.zaLigo(liga.getId()).stream().map(PokroviteljDto::ime).toList());

        // nov seznam zamenja starega v celoti; prazen pokrovitelje odstrani
        storitev.shraniZaLigo(liga.getId(), List.of(new Pokrovitelj(b.id(), "Kos", null, false)));
        assertEquals(List.of("Kos"), storitev.zaLigo(liga.getId()).stream().map(PokroviteljDto::ime).toList());
        storitev.shraniZaLigo(liga.getId(), List.of());
        assertTrue(storitev.zaLigo(liga.getId()).isEmpty());
    }

    /* Napaka v enem pokrovitelju zavrne vse - trak ne ostane prazen. */
    @Test
    void napacenVnosZavrneVseInStaroOstane() {
        Uporabnik lastnik = organizator("lastnik-napaka@test");
        Turnir turnir = turnir(lastnik);
        prijava(lastnik);
        SlikaDto a = slikeStoritev.nalozi(TestneSlike.png(400, 200));
        storitev.shraniZaTurnir(turnir.getId(), List.of(new Pokrovitelj(a.id(), "Petrol", null, false)));

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.shraniZaTurnir(turnir.getId(), List.of(
                new Pokrovitelj(a.id(), "Novi", null, false),
                new Pokrovitelj(a.id(), "Skript", "javascript:alert(1)", false))));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.shraniZaTurnir(turnir.getId(), List.of(
                new Pokrovitelj(a.id(), "Prvi", null, true),
                new Pokrovitelj(a.id(), "Drugi", null, true))));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.shraniZaTurnir(turnir.getId(),
                List.of(new Pokrovitelj(a.id(), " ", null, false))));
        List<Pokrovitelj> devet = new ArrayList<>(Collections.nCopies(9, new Pokrovitelj(a.id(), "X", null, false)));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.shraniZaTurnir(turnir.getId(), devet));

        assertEquals(List.of("Petrol"), storitev.zaTurnir(turnir.getId()).stream().map(PokroviteljDto::ime).toList());
    }

    @Test
    void tujOrganizatorTrakaNeUreja() {
        Liga liga = liga(organizator("lastnik-tuj@test"), null);
        prijava(organizator("tuj-pokr@test"));
        SlikaDto a = slikeStoritev.nalozi(TestneSlike.png(400, 200));

        assertThrows(PrepovedanoIzjema.class, () -> storitev.shraniZaLigo(liga.getId(),
                List.of(new Pokrovitelj(a.id(), "Petrol", null, false))));
    }

    /* Prikaz, ne pravilo: tudi uvožena liga sme imeti pokrovitelje. */
    @Test
    void uvozenaLigaSmeImetiPokrovitelje() {
        Uporabnik lastnik = organizator("lastnik-uvoz-pokr@test");
        Liga liga = liga(lastnik, VirTekmovanja.STUPA);
        prijava(lastnik);
        SlikaDto a = slikeStoritev.nalozi(TestneSlike.png(400, 200));

        assertEquals(1, storitev.shraniZaLigo(liga.getId(),
                List.of(new Pokrovitelj(a.id(), "Petrol", null, false))).size());
    }

    /* Logotip pokrovitelja je javna raba: iz knjižnice ga ni mogoče izbrisati. */
    @Test
    void logotipPokroviteljaJeVRabi() {
        Uporabnik lastnik = organizator("lastnik-raba@test");
        Liga liga = liga(lastnik, null);
        prijava(lastnik);
        SlikaDto a = slikeStoritev.nalozi(TestneSlike.png(400, 200));
        storitev.shraniZaLigo(liga.getId(), List.of(new Pokrovitelj(a.id(), "Petrol", null, false)));

        assertThrows(DomenskaIzjema.class, () -> slikeStoritev.izbrisi(a.id()));
    }

    // ---------- Pomozno ----------

    private Uporabnik organizator(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private Liga liga(Uporabnik lastnik, VirTekmovanja vir) {
        Liga l = new Liga();
        l.setIme("Liga s pokrovitelji");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        l.setVir(vir);
        return ligaRepozitorij.save(l);
    }

    private Turnir turnir(Uporabnik lastnik) {
        Turnir t = new Turnir();
        t.setIme("Turnir s pokrovitelji");
        t.setUstvaril(lastnik);
        return turnirRepozitorij.save(t);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }
}
