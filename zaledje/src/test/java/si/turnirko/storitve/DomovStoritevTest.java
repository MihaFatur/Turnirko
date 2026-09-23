/* Spremljanje lig (DomovStoritev.spremljaj) je Premium funkcija - brez nje
   je racun (tudi prijavljen) kot gost. Odjava spremljanja in branje izbora
   ostaneta odprta vsakemu prijavljenemu, zato ju ta test ne omejuje. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomovStoritevTest {

    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired DomovStoritev domov;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void brezplacenRacunNeSmeDodatiSpremljanja() {
        Liga liga = liga();
        Uporabnik u = igralec("brezplacno@test");

        prijava(u.getUporabniskoIme());
        assertThrows(PrepovedanoIzjema.class, () -> domov.spremljaj(liga.getId()));
    }

    @Test
    void premiumRacunSmeDodatiSpremljanje() {
        Liga liga = liga();
        Uporabnik u = igralec("premium@test");
        narocnina(u);

        prijava(u.getUporabniskoIme());
        List<Long> izbor = domov.spremljaj(liga.getId());
        assertTrue(izbor.contains(liga.getId()));
    }

    /* Admin nima paketa in ni nikoli omejen. */
    @Test
    void adminSmeDodatiSpremljanjeBrezPaketa() {
        Liga liga = liga();
        Uporabnik admin = new Uporabnik("admin@test", "{bcrypt}x", Vloga.ADMIN);
        uporabnikRepozitorij.save(admin);

        prijava(admin.getUporabniskoIme());
        assertDoesNotThrow(() -> domov.spremljaj(liga.getId()));
    }

    // ---------- Pomozno ----------

    private Uporabnik igralec(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        return uporabnikRepozitorij.save(u);
    }

    private void narocnina(Uporabnik u) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
    }

    private Liga liga() {
        Liga l = new Liga();
        l.setIme("Testna liga");
        l.setSpolKategorija(SpolKategorija.MESANO);
        return ligaRepozitorij.save(l);
    }

    private void prijava(String prijavnoIme) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority("ROLE_IGRALEC"))));
    }
}
