/* Kdo sme videti zasebni del profila (DostopDoProfila): administrator vedno,
   igralec samo SVOJ profil in samo s Premium paketom - brez njega je (tudi
   lastniku) na voljo enako kot gostu. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DostopDoProfilaTest {

    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired DostopDoProfila dostop;

    /* Brezplacen racun ne vidi niti SVOJE zasebne statistike - enako kot gost. */
    @Test
    void brezplacenLastnikNeVidiZasebnegaProfila() {
        Igralec igralec = igralec();
        Uporabnik u = racun("brezplacno@test", igralec);

        assertThrows(PrepovedanoIzjema.class,
                () -> dostop.preveriLastnistvo(igralec.getId(), u.getUporabniskoIme()));
    }

    @Test
    void premiumLastnikVidiSvojZasebniProfil() {
        Igralec igralec = igralec();
        Uporabnik u = racun("premium@test", igralec);
        narocnina(u, StatusNarocnine.AKTIVNA);

        assertDoesNotThrow(() -> dostop.preveriLastnistvo(igralec.getId(), u.getUporabniskoIme()));
    }

    /* Premium ne odpre TUJEGA profila - paket vpliva samo na LASTNEGA. */
    @Test
    void premiumNeOdpreTujegaProfila() {
        Igralec lastnik = igralec();
        Igralec drug = igralec();
        Uporabnik u = racun("premiumTuj@test", lastnik);
        narocnina(u, StatusNarocnine.AKTIVNA);

        assertThrows(PrepovedanoIzjema.class,
                () -> dostop.preveriLastnistvo(drug.getId(), u.getUporabniskoIme()));
    }

    @Test
    void adminVidiVsakegaBrezPaketa() {
        Igralec igralec = igralec();
        Uporabnik admin = new Uporabnik("admin@test", "{bcrypt}x", Vloga.ADMIN);
        uporabnikRepozitorij.save(admin);

        assertDoesNotThrow(() -> dostop.preveriLastnistvo(igralec.getId(), admin.getUporabniskoIme()));
    }

    // ---------- Pomozno ----------

    private Igralec igralec() {
        Igralec i = new Igralec();
        i.setIme("Ana");
        i.setPriimek("Novak");
        i.setSpol(Spol.ZENSKI);
        i.setDatumRojstva(LocalDate.of(2000, 1, 1));
        return igralecRepozitorij.save(i);
    }

    private Uporabnik racun(String prijavnoIme, Igralec igralec) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
        return uporabnikRepozitorij.save(u);
    }

    private void narocnina(Uporabnik u, StatusNarocnine status) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(status);
        narocninaRepozitorij.save(n);
    }
}
