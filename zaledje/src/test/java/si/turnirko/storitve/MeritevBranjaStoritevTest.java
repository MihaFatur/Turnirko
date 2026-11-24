/* Meritev branj iz baze (MeritevBranjaStoritev): skupine po turnirjih in
   mrezah, obdobje, turnir, meja vrstic in dostop samo za admina. Izracun celic
   pokriva MeritevBranjaTest, pot skozi HTTP MeritevBranjaDostopTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class MeritevBranjaStoritevTest extends IntegracijskiTest {

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};

    @Autowired MeritevBranjaStoritev meritev;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired UporabnikRepozitorij uporabniki;
    @Autowired JdbcTemplate jdbc;

    private Turnir prvi;
    private Turnir drugi;

    @BeforeEach
    void pripravi() {
        prvi = pripraviDogodek(4).getTurnir();
        drugi = pripraviDogodek(4).getTurnir();
        racun("admin-meritev@test", Vloga.ADMIN);
        prijava("admin-meritev@test", "ROLE_ADMIN");
    }

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skupineTurnirjevInMrez() {
        potrjeno(prvi, "vgrajena", false, 2000);
        potrjeno(prvi, "ucena-3", true, 8000);
        potrjeno(drugi, "ucena-3", false, 3000);
        BranjeListka napaka = new BranjeListka(drugi, null, LocalDateTime.now());
        napaka.setStanje(StanjeBranja.NAPAKA);
        // fotografija sodnika (S2), ki je bralnik ni prebral
        napaka.setPoslalSodnik(true);
        branja.save(napaka);

        MeritevBranjaDto m = meritev.meritev(null, null, null);
        // po viru: sodnik ima samo neuspelo branje, pult vse potrjene
        assertEquals(2, m.poViru().size());
        assertEquals(1, m.poViru().get(0).branj());
        assertEquals(1, m.poViru().get(0).neuspelih());
        assertEquals(3, m.poViru().get(1).branj());
        assertEquals(3, m.poViru().get(1).potrjenih());
        assertEquals(4, m.skupaj().branj());
        assertEquals(3, m.skupaj().potrjenih());
        assertEquals(1, m.skupaj().neuspelih());
        assertEquals(2, m.skupaj().brezPopravka());
        assertEquals(new MeritevBranjaDto.Casi(3, 3000, 8000, 2, 2000, 1, 8000), m.skupaj().casi());
        assertEquals(2, m.poTurnirjih().size());
        assertEquals(List.of("vgrajena", "ucena-3"), m.poMrezah().stream().map(MeritevBranjaDto.Skupina::oznaka).toList());
        assertEquals(2, m.poMrezah().get(1).potrjenih());
        // neuspelo branje nima mreze: ni v nobeni skupini mrez
        assertEquals(3, m.poMrezah().stream().mapToInt(MeritevBranjaDto.Skupina::branj).sum());

        MeritevBranjaDto samoDrugi = meritev.meritev(null, null, drugi.getId());
        assertEquals(2, samoDrugi.skupaj().branj());
        assertEquals(1, samoDrugi.poTurnirjih().size());

        // obdobje brez branj (oba dneva vkljucno)
        LocalDate vceraj = LocalDate.now().minusDays(1);
        assertEquals(0, meritev.meritev(vceraj.minusDays(10), vceraj, null).skupaj().branj());
        assertThrows(NeveljavenVnosIzjema.class, () -> meritev.meritev(LocalDate.now(), vceraj, null));
        // skrajni datumi so 400, ne napaka baze
        assertThrows(NeveljavenVnosIzjema.class, () -> meritev.meritev(null, LocalDate.of(999_999, 12, 31), null));
    }

    @Test
    void predolgoObdobjeSeZavrne() {
        jdbc.update("""
                INSERT INTO branje_listka (id_turnir, stanje, ustvarjeno_ob)
                SELECT ?, 'NAPAKA', now() FROM generate_series(1, ?)
                """, prvi.getId(), MeritevBranjaStoritev.NAJVEC_BRANJ + 1);
        assertThrows(NeveljavenVnosIzjema.class, () -> meritev.meritev(null, null, null));
    }

    @Test
    void samoAdmin() {
        racun("organizator-meritev@test", Vloga.ORGANIZATOR);
        prijava("organizator-meritev@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> meritev.meritev(null, null, null));
        SecurityContextHolder.clearContext();
        assertThrows(PrepovedanoIzjema.class, () -> meritev.meritev(null, null, null));
    }

    // ---------- Pomozno ----------

    private void potrjeno(Turnir turnir, String model, boolean popravljeno, int casMs) {
        BranjeListka b = new BranjeListka(turnir, null, LocalDateTime.now());
        b.setStanje(StanjeBranja.POTRJENO);
        b.setPrepoznava(PrepoznavaTekme.KODA);
        java.util.List<PrebranListek.Niz> nizi = new java.util.ArrayList<>();
        for (int[] n : IZID) {
            nizi.add(new PrebranListek.Niz(n[0], n[1], false, false));
        }
        b.setPrebrano(JsonBranja.zapisi(new PrebranListek(true, null, null, null, false, null, null, nizi, 3, 1,
                false, "NI", null, null)));
        b.setPotrjeno(JsonBranja.zapisi(MeritevBranjaTest.vnos(IZID, false)));
        b.setPopravljeno(popravljeno);
        b.setPripravljen(true);
        b.setCasPregledaMs(casMs);
        b.setNacinPotrditve(casMs < 5000 ? NacinPotrditve.EN_DOTIK : NacinPotrditve.OBRAZEC);
        b.setModel(model);
        branja.save(b);
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
    }

    private static void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
