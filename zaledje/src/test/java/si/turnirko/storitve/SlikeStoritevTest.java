/* Slike in logotip tekmovanja (V54, SlikeStoritev): razlicice, dvojniki,
   knjiznica, kdo sme katero sliko uporabiti in izbrisati. Pot skozi varnostno
   verigo (vloge, predpomnjenje) preverja SlikeDostopTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.SlikaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SlikaRazlicica;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.VelikostSlike;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.Vloga;
import si.turnirko.pomozno.ObdelavaSlik;
import si.turnirko.pomozno.TestneSlike;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SlikaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SlikeStoritevTest {

    @Autowired SlikeStoritev slikeStoritev;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired SlikaRepozitorij slikaRepozitorij;
    @Autowired LigaRepozitorij ligaRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired KlubRepozitorij klubRepozitorij;
    @Autowired OmejevalnikPoskusov omejevalnik;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
        omejevalnik.pocistiVse();
    }

    /* Ena datoteka = tri razlicice z merami po namenu; zgostitev je naslov. */
    @Test
    void nalaganjeZapiseVseTriRazlicice() throws IOException {
        prijava(racun("nalaga@test", Vloga.ORGANIZATOR, null));

        SlikaDto slika = slikeStoritev.nalozi(TestneSlike.png(3000, 1500));

        assertEquals(64, slika.zgostitev().length());
        assertEquals(1200, slika.sirina());
        assertEquals(600, slika.visina());
        SlikaRazlicica tisk = slikeStoritev.vsebina(slika.zgostitev(), VelikostSlike.TISK).orElseThrow();
        SlikaRazlicica zaslon = slikeStoritev.vsebina(slika.zgostitev(), VelikostSlike.ZASLON).orElseThrow();
        SlikaRazlicica deljenje = slikeStoritev.vsebina(slika.zgostitev(), VelikostSlike.DELJENJE).orElseThrow();
        assertEquals(ObdelavaSlik.PNG, tisk.getVrsta());
        assertEquals(480, zaslon.getSirina());
        assertEquals(240, zaslon.getVisina());
        assertEquals(1200, deljenje.getSirina());
        assertEquals(630, deljenje.getVisina());
        BufferedImage prebrana = ImageIO.read(new ByteArrayInputStream(zaslon.getVsebina()));
        assertEquals(480, prebrana.getWidth(), "bajti so prava slika");
        assertTrue(slikeStoritev.vsebina("0".repeat(64), VelikostSlike.TISK).isEmpty());
    }

    /* Ista datoteka drugic je ista slika; kdor jo nalozi znova, jo dobi v svojo
       knjiznico (s tem je pokazal, da datoteko ima). */
    @Test
    void istaDatotekaJeEnaSlika() {
        byte[] datoteka = TestneSlike.png(600, 300);
        Uporabnik prvi = racun("prvi@test", Vloga.ORGANIZATOR, null);
        Uporabnik drugi = racun("drugi@test", Vloga.ORGANIZATOR, null);

        prijava(prvi);
        SlikaDto a = slikeStoritev.nalozi(datoteka);
        prijava(drugi);
        SlikaDto b = slikeStoritev.nalozi(datoteka);

        assertEquals(a.id(), b.id());
        assertEquals(drugi.getId(), slikaRepozitorij.findById(a.id()).orElseThrow().getIdNalozil());
    }

    /* Lastnik doda logotip ligi; LigaDto ga nosi, odstranitev ga vzame. */
    @Test
    void lastnikDodaInOdstraniLogotipLige() {
        Uporabnik lastnik = racun("lastnik-lige@test", Vloga.ORGANIZATOR, null);
        Liga liga = liga(lastnik, null, null);
        prijava(lastnik);
        SlikaDto slika = nalozi(400);

        slikeStoritev.nastaviLogotipLige(liga.getId(), slika.id());
        SlikaDto vDto = ligaStoritev.najdi(liga.getId()).logotip();
        assertNotNull(vDto);
        assertEquals(slika.zgostitev(), vDto.zgostitev());

        slikeStoritev.odstraniLogotipLige(liga.getId());
        assertNull(ligaStoritev.najdi(liga.getId()).logotip());
    }

    @Test
    void tujOrganizatorLogotipaLigeNeNastavi() {
        Liga liga = liga(racun("lastnik@test", Vloga.ORGANIZATOR, null), null, null);
        prijava(racun("tuj@test", Vloga.ORGANIZATOR, null));
        SlikaDto slika = nalozi(400);

        assertThrows(PrepovedanoIzjema.class, () -> slikeStoritev.nastaviLogotipLige(liga.getId(), slika.id()));
        assertThrows(PrepovedanoIzjema.class, () -> slikeStoritev.odstraniLogotipLige(liga.getId()));
    }

    /* Logotip je prikaz, ne pravilo: lastnik ga sme dati tudi uvozeni ligi in
       turnirju (kot skrite razdelke Zanimivosti). */
    @Test
    void uvozenoTekmovanjeLogotipSme() {
        Uporabnik lastnik = racun("lastnik-uvoz@test", Vloga.ORGANIZATOR, null);
        Liga liga = liga(lastnik, null, VirTekmovanja.STUPA);
        Turnir turnir = turnir(lastnik, null, VirTekmovanja.STUPA);
        prijava(lastnik);
        SlikaDto slika = nalozi(400);

        slikeStoritev.nastaviLogotipLige(liga.getId(), slika.id());
        slikeStoritev.nastaviLogotipTurnirja(turnir.getId(), slika.id());

        assertEquals(slika.id(), turnirRepozitorij.findById(turnir.getId()).orElseThrow().getLogotip().getId());
    }

    /* Id-ji so zaporedni: tuje slike, ki je se nihce ni objavil, z ugibanjem ni
       mogoce prevzeti. Ko jo nosi kaksno tekmovanje, je javna in jo sme vsak. */
    @Test
    void tujoSlikoSmeUporabitiSeleKoJeJavna() {
        Uporabnik avtor = racun("avtor@test", Vloga.ORGANIZATOR, null);
        Uporabnik drug = racun("drug@test", Vloga.ORGANIZATOR, null);
        Liga ligaAvtorja = liga(avtor, null, null);
        Liga ligaDrugega = liga(drug, null, null);
        prijava(avtor);
        SlikaDto slika = nalozi(400);

        prijava(drug);
        assertThrows(PrepovedanoIzjema.class,
                () -> slikeStoritev.nastaviLogotipLige(ligaDrugega.getId(), slika.id()));

        prijava(avtor);
        slikeStoritev.nastaviLogotipLige(ligaAvtorja.getId(), slika.id());
        prijava(drug);
        slikeStoritev.nastaviLogotipLige(ligaDrugega.getId(), slika.id());
        assertEquals(slika.id(), ligaRepozitorij.findById(ligaDrugega.getId()).orElseThrow().getLogotip().getId());
    }

    /* Knjiznica: svoje slike in logotipi tekmovanj, ki jih organizator sme
       urejati (tudi klubskega kolege); tuje ne. Admin vidi vse. */
    @Test
    void knjiznicaJeSvojeInKlubsko() {
        Klub klub = klubRepozitorij.save(new Klub("Klub slik", null));
        Uporabnik jaz = racun("jaz@test", Vloga.ORGANIZATOR, klub);
        Uporabnik kolega = racun("kolega@test", Vloga.ORGANIZATOR, klub);
        Uporabnik tujec = racun("tujec@test", Vloga.ORGANIZATOR, null);
        Liga klubska = liga(kolega, klub, null);

        prijava(jaz);
        SlikaDto moja = nalozi(400);
        prijava(kolega);
        SlikaDto kolegova = nalozi(500);
        slikeStoritev.nastaviLogotipLige(klubska.getId(), kolegova.id());
        prijava(tujec);
        SlikaDto tuja = nalozi(600);

        prijava(jaz);
        List<Long> mojaKnjiznica = slikeStoritev.knjiznica().stream().map(SlikaDto::id).toList();
        assertTrue(mojaKnjiznica.contains(moja.id()));
        assertTrue(mojaKnjiznica.contains(kolegova.id()), "logotip klubske lige");
        assertTrue(!mojaKnjiznica.contains(tuja.id()), "tuja neobjavljena slika");

        prijava(racun("admin-slik@test", Vloga.ADMIN, null));
        List<Long> vse = slikeStoritev.knjiznica().stream().map(SlikaDto::id).toList();
        assertTrue(vse.containsAll(List.of(moja.id(), kolegova.id(), tuja.id())));
    }

    /* Izbris: samo svoje in samo, dokler je ne nosi nobeno tekmovanje. */
    @Test
    void izbrisSamoSvojeInNeVRabi() {
        Uporabnik avtor = racun("avtor-izbris@test", Vloga.ORGANIZATOR, null);
        Turnir turnir = turnir(avtor, null, null);
        prijava(avtor);
        SlikaDto slika = nalozi(400);
        slikeStoritev.nastaviLogotipTurnirja(turnir.getId(), slika.id());

        DomenskaIzjema vRabi = assertThrows(DomenskaIzjema.class, () -> slikeStoritev.izbrisi(slika.id()));
        assertTrue(vRabi.getMessage().contains("na 1 tekmovanju"), vRabi.getMessage());

        prijava(racun("tuj-izbris@test", Vloga.ORGANIZATOR, null));
        assertThrows(PrepovedanoIzjema.class, () -> slikeStoritev.izbrisi(slika.id()));

        prijava(avtor);
        slikeStoritev.odstraniLogotipTurnirja(turnir.getId());
        slikeStoritev.izbrisi(slika.id());
        assertTrue(slikaRepozitorij.findById(slika.id()).isEmpty());
        assertTrue(slikeStoritev.vsebina(slika.zgostitev(), VelikostSlike.TISK).isEmpty(), "bajti gredo z njo");
    }

    // ---------- Pomozno ----------

    /* Vsaka sirina je druga slika (druga zgostitev). */
    private SlikaDto nalozi(int sirina) {
        return slikeStoritev.nalozi(TestneSlike.png(sirina, 200));
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga, Klub klub) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setKlub(klub);
        return uporabnikRepozitorij.save(u);
    }

    private Liga liga(Uporabnik lastnik, Klub klub, VirTekmovanja vir) {
        Liga l = new Liga();
        l.setIme("Liga s logotipom");
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        l.setKlubLastnik(klub);
        l.setVir(vir);
        return ligaRepozitorij.save(l);
    }

    private Turnir turnir(Uporabnik lastnik, Klub klub, VirTekmovanja vir) {
        Turnir t = new Turnir();
        t.setIme("Turnir s logotipom");
        t.setUstvaril(lastnik);
        t.setKlubLastnik(klub);
        t.setVir(vir);
        return turnirRepozitorij.save(t);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }
}
