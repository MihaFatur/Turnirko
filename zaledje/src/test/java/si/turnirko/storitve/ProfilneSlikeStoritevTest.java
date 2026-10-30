/* Profilna slika igralca (V56, ProfilneSlikeStoritev): kdo jo sme naložiti,
   kdaj je javna in kdaj čaka na admina, kaj se zgodi ob izteku Premium in ob
   zamenjavi ali izbrisu. Pot skozi varnostno verigo preverja SlikeDostopTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.MojaProfilnaSlikaDto;
import si.turnirko.dto.ProfilnaSlikaPregledDto;
import si.turnirko.dto.SlikaIgralcaDto;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.ProfilnaSlikaVsebina;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StanjeProfilneSlike;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VirPovezave;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.TestneSlike;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.ProfilnaSlikaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfilneSlikeStoritevTest {

    @Autowired ProfilneSlikeStoritev storitev;
    @Autowired ProfilnaSlikaRepozitorij slikaRepozitorij;
    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired PomnilniskiPosiljatelj posta;

    @BeforeEach
    void zacni() {
        posta.pocisti();
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
        omejevalnik.pocistiVse();
    }

    /* Odrasel igralec s Premium, ki ga je povezal admin: slika je javna takoj,
       admin dobi e-pošto (naknadna moderacija). */
    @Test
    void premiumZAdminovoPovezavoJeJavnaTakoj() {
        Igralec igralec = igralec("Ana", LocalDate.of(1995, 3, 1));
        prijava(racun("ana@test", igralec, true, VirPovezave.ADMIN));

        MojaProfilnaSlikaDto moja = storitev.nalozi(TestneSlike.png(600, 600));

        assertEquals(StanjeProfilneSlike.OBJAVLJENA, moja.stanje());
        assertTrue(moja.smeNalagati());
        assertNull(moja.pojasnilo());
        assertEquals(List.of(new SlikaIgralcaDto(igralec.getId(), moja.zgostitev())),
                storitev.vidne(List.of(igralec.getId())));
        assertTrue(posta.zadnje("admin@turnirko.test").orElseThrow().zadeva().startsWith("Nova profilna slika"));
    }

    /* Samodejna povezava (ime, priimek, datum rojstva) ni dokaz istovetnosti:
       slika čaka, dokler je admin ne objavi. */
    @Test
    void samodejnoPovezanRacunCakaNaAdmina() {
        Igralec igralec = igralec("Bor", LocalDate.of(1990, 1, 1));
        prijava(racun("bor@test", igralec, true, VirPovezave.SAMODEJNO));

        MojaProfilnaSlikaDto moja = storitev.nalozi(TestneSlike.png(500, 500));

        assertEquals(StanjeProfilneSlike.CAKA, moja.stanje());
        assertTrue(moja.pojasnilo().contains("administrator"), moja.pojasnilo());
        assertTrue(storitev.vidne(List.of(igralec.getId())).isEmpty(), "čakajoča ni javna");
        assertTrue(posta.zadnje("admin@turnirko.test").orElseThrow().zadeva().startsWith("Profilna slika čaka"));

        ProfilnaSlikaPregledDto vPregledu = storitev.pregled().stream()
                .filter(p -> igralec.getId().equals(p.idIgralec())).findFirst().orElseThrow();
        assertEquals(StanjeProfilneSlike.CAKA, vPregledu.stanje());
        assertFalse(vPregledu.javna());

        storitev.objavi(vPregledu.id());
        assertEquals(1, storitev.vidne(List.of(igralec.getId())).size());
    }

    /* Mlajši od 15 let: čaka tudi pri adminovi povezavi. */
    @Test
    void mladoletnikCakaTudiPriAdminoviPovezavi() {
        Igralec otrok = igralec("Cene", LocalDate.now().minusYears(12));
        prijava(racun("cene@test", otrok, true, VirPovezave.ADMIN));

        assertEquals(StanjeProfilneSlike.CAKA, storitev.nalozi(TestneSlike.png(400, 400)).stanje());
        assertTrue(storitev.vidne(List.of(otrok.getId())).isEmpty());
    }

    /* Datum 1. 1. 1900 pomeni »ni znan« (uvoz) - tak igralec velja za odraslega. */
    @Test
    void neznanDatumRojstvaNiMladoletnik() {
        assertFalse(ProfilneSlikeStoritev.mlajsiOd15(igralec("Dana", LocalDate.of(1900, 1, 1))));
        assertTrue(ProfilneSlikeStoritev.mlajsiOd15(igralec("Ema", LocalDate.now().minusYears(14))));
    }

    @Test
    void brezPremiumInBrezIgralcaNeSme() {
        prijava(racun("free@test", igralec("Filip", LocalDate.of(1990, 1, 1)), false, VirPovezave.ADMIN));
        MojaProfilnaSlikaDto moja = storitev.moja();
        assertFalse(moja.smeNalagati());
        assertTrue(moja.pojasnilo().contains("Premium"));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.nalozi(TestneSlike.png(400, 400)));

        Uporabnik organizator = new Uporabnik("org-slika@test", "{bcrypt}x", Vloga.ORGANIZATOR);
        organizator.setStatus(StatusRacuna.POTRJEN);
        prijava(uporabnikRepozitorij.save(organizator));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.nalozi(TestneSlike.png(400, 400)));
    }

    /* Ob izteku Premium se slika skrije, ne izbriše - ob obnovi je spet javna. */
    @Test
    void izteklPremiumSlikoSkrije() {
        Igralec igralec = igralec("Gal", LocalDate.of(1988, 1, 1));
        Uporabnik u = racun("gal@test", igralec, true, VirPovezave.ADMIN);
        prijava(u);
        storitev.nalozi(TestneSlike.png(400, 400));

        Narocnina n = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        n.setStatus(StatusNarocnine.PREKLICANA);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().minusDays(1));
        assertTrue(storitev.vidne(List.of(igralec.getId())).isEmpty());
        assertTrue(slikaRepozitorij.findByUporabnikId(u.getId()).isPresent(), "slika ostane shranjena");

        n.setStatus(StatusNarocnine.AKTIVNA);
        assertEquals(1, storitev.vidne(List.of(igralec.getId())).size());
    }

    /* Nova slika zamenja staro (račun ima eno); izbris vzame tudi bajte. */
    @Test
    void zamenjavaInIzbris() {
        prijava(racun("hana@test", igralec("Hana", LocalDate.of(1999, 1, 1)), true, VirPovezave.ADMIN));
        String prva = storitev.nalozi(TestneSlike.png(400, 400)).zgostitev();
        String druga = storitev.nalozi(TestneSlike.png(500, 500)).zgostitev();

        assertNotEquals(prva, druga);
        assertTrue(storitev.vsebina(prva, ProfilnaSlikaVsebina.VELIKA).isEmpty(), "stara je šla");
        assertTrue(storitev.vsebina(druga, ProfilnaSlikaVsebina.MALA).isPresent());

        storitev.izbrisiMojo();
        assertNull(storitev.moja().zgostitev());
        assertTrue(storitev.vsebina(druga, ProfilnaSlikaVsebina.VELIKA).isEmpty());
    }

    /* Nekvadratna slika postane kvadrat iz sredine (480 in 160 px, JPEG);
       premajhna se zavrne. */
    @Test
    void kvadratInMeriRazlicic() throws IOException {
        prijava(racun("ivo@test", igralec("Ivo", LocalDate.of(1990, 1, 1)), true, VirPovezave.ADMIN));
        String zgostitev = storitev.nalozi(TestneSlike.png(1200, 800)).zgostitev();

        BufferedImage velika = ImageIO.read(new ByteArrayInputStream(
                storitev.vsebina(zgostitev, ProfilnaSlikaVsebina.VELIKA).orElseThrow()));
        BufferedImage mala = ImageIO.read(new ByteArrayInputStream(
                storitev.vsebina(zgostitev, ProfilnaSlikaVsebina.MALA).orElseThrow()));
        assertEquals(480, velika.getWidth());
        assertEquals(480, velika.getHeight());
        assertEquals(160, mala.getWidth());

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.nalozi(TestneSlike.png(300, 120)));
    }

    /* Isto fotografijo ne more imeti drug račun (ne kaže se pri dveh igralcih). */
    @Test
    void istaFotografijaNeGreNaDrugRacun() {
        byte[] fotografija = TestneSlike.png(640, 640);
        prijava(racun("jan@test", igralec("Jan", LocalDate.of(1990, 1, 1)), true, VirPovezave.ADMIN));
        storitev.nalozi(fotografija);

        prijava(racun("kaja@test", igralec("Kaja", LocalDate.of(1990, 1, 1)), true, VirPovezave.ADMIN));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.nalozi(fotografija));
    }

    // ---------- Pomozno ----------

    private Igralec igralec(String ime, LocalDate rojen) {
        Igralec i = new Igralec();
        i.setIme(ime);
        i.setPriimek("Slikovnik");
        i.setSpol(Spol.ZENSKI);
        i.setDatumRojstva(rojen);
        return igralecRepozitorij.save(i);
    }

    private Uporabnik racun(String prijavnoIme, Igralec igralec, boolean premium, VirPovezave vir) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
        u.setVirPovezave(vir);
        uporabnikRepozitorij.save(u);
        if (premium) {
            Narocnina n = new Narocnina(u, Paket.PREMIUM);
            n.setStatus(StatusNarocnine.AKTIVNA);
            narocninaRepozitorij.save(n);
        }
        return u;
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }
}
