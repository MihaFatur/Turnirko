/* Skrivnost v QR kodi sodniskega listka (S1, SkrivnostListka, ListkiStoritev.kode).

   Domenska pravila, ki jih ta test varuje:
   - skrivnost je 8 znakov base32, za isto tekmo vedno ista, za drugo tekmo druga;
   - prava je samo za svojo tekmo; napacna dolzina, male crke ali null ne veljajo;
   - kljuc ostane v bazi: nova instanca (ponovni zagon) da iste skrivnosti;
   - zapis s skrivnostjo gre v QR verzije 1 pri ECC M do id-ja z 8 stevkami (na
     verzijo 1 racuna poravnava listka);
   - kode za tisk dobi samo lastnik turnirja ali admin, ne tuj organizator, in
     zajemajo vse tekme dogodka; povedo tudi, ali sodniki slikajo listke (S2:
     takrat ima list hrbtno stran s kodo do strani za sodnika). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;

import si.turnirko.dto.KodeListkovDto;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SodniskaPotrditev;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class SkrivnostListkaTest extends IntegracijskiTest {

    @Autowired SkrivnostListka skrivnost;
    @Autowired ListkiStoritev listkiStoritev;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skrivnostJeOsemZnakovBase32InStalna() {
        String s = skrivnost.za(48213);
        assertTrue(s.matches("[A-Z2-7]{8}"), s);
        assertEquals(s, skrivnost.za(48213));
        assertNotEquals(s, skrivnost.za(48214));
        assertEquals("TK:48213:" + s, skrivnost.vsebinaQr(48213));
    }

    @Test
    void veljaSamoZaSvojoTekmo() {
        String s = skrivnost.za(7);
        assertTrue(skrivnost.velja(7, s));
        assertFalse(skrivnost.velja(8, s));
        assertFalse(skrivnost.velja(7, s.toLowerCase()));
        assertFalse(skrivnost.velja(7, s.substring(1)));
        assertFalse(skrivnost.velja(7, null));
    }

    /* Ponovni zagon aplikacije: nova instanca prebere isti kljuc iz baze. */
    @Test
    void kljucOstaneVBazi() {
        String prej = skrivnost.za(123);
        SkrivnostListka znova = new SkrivnostListka(jdbc, transakcije);
        assertEquals(prej, znova.za(123));
    }

    @Test
    void zapisGreVQrVerzije1() throws Exception {
        for (long id : List.of(1L, 48213L, 99_999_999L)) {
            String vsebina = skrivnost.vsebinaQr(id);
            assertEquals(1, Encoder.encode(vsebina, ErrorCorrectionLevel.M).getVersion().getVersionNumber(), vsebina);
        }
    }

    /* RFC 4648: base32(»fooba«) = »MZXW6YTB«. */
    @Test
    void base32PoRfc4648() {
        assertEquals("MZXW6YTB", SkrivnostListka.base32("fooba".getBytes(StandardCharsets.US_ASCII), 8));
    }

    @Test
    void kodeDobiSamoLastnikAliAdmin() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(dogodek.getId());
        Uporabnik lastnik = racun("lastnik-kode@test", Vloga.ORGANIZATOR);
        racun("tujec-kode@test", Vloga.ORGANIZATOR);
        racun("admin-kode@test", Vloga.ADMIN);
        dogodek.getTurnir().setUstvaril(lastnik);
        turnirRepozitorij.save(dogodek.getTurnir());

        prijava("tujec-kode@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> listkiStoritev.kode(dogodek.getId()));

        prijava("lastnik-kode@test", "ROLE_ORGANIZATOR");
        KodeListkovDto odgovor = listkiStoritev.kode(dogodek.getId());
        // sodniki ne slikajo listkov: listki brez hrbtne strani
        assertNull(odgovor.sodnikiListki());
        Map<Long, String> kode = odgovor.kode();
        assertEquals(tekme.size(), kode.size());
        for (Tekma t : tekme) {
            String vsebina = kode.get(t.getId());
            assertEquals(skrivnost.vsebinaQr(t.getId()), vsebina);
            assertTrue(skrivnost.velja(t.getId(), vsebina.substring(vsebina.lastIndexOf(':') + 1)));
        }

        prijava("admin-kode@test", "ROLE_ADMIN");
        assertEquals(kode, listkiStoritev.kode(dogodek.getId()).kode());
    }

    /* S2: ko sodniki slikajo listke, vmesnik natisne hrbtno stran s kodo do /s. */
    @Test
    void kodePovedoStopnjoSodniskePotrditve() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        racun("admin-stopnja@test", Vloga.ADMIN);
        dogodek.getTurnir().setSodnikiListki(SodniskaPotrditev.PREDPREGLED);
        turnirRepozitorij.save(dogodek.getTurnir());

        prijava("admin-stopnja@test", "ROLE_ADMIN");
        assertEquals(SodniskaPotrditev.PREDPREGLED, listkiStoritev.kode(dogodek.getId()).sodnikiListki());
    }

    // ---------- Pomozno ----------

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
