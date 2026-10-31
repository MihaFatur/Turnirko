/* Druzina (V59, faza 2): navijac s paketom Navijac poveze do dva igralca do 21
   let; povezavo potrdi admin; zasebna statistika se odpre SELE po potrditvi in
   samo, dokler velja paket; igralcev racun dobi Premium. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.DomovIgralecDto;
import si.turnirko.dto.DruzinaDto;
import si.turnirko.dto.UporabnikDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.DruzinaPovezava;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.DruzinaPovezavaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class DruzinaTest extends IntegracijskiTest {

    @Autowired private DruzinaStoritev druzina;
    @Autowired private DostopDoProfila dostop;
    @Autowired private NarocninaStoritev narocninaStoritev;
    @Autowired private SpremljaniIgralciStoritev spremljani;
    @Autowired private RacuniStoritev racuni;
    @Autowired private OsebniKoledarStoritev koledar;
    @Autowired private DruzinaPovezavaRepozitorij povezave;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- zahteva ----------

    @Test
    void brezplacenNavijacDruzineNima() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        prijava(navijac("brez@test", null));
        assertThrows(PrepovedanoIzjema.class, () -> druzina.zahtevaj(otrok.getId()));
        assertFalse(druzina.pregled().smemZahtevati());
    }

    @Test
    void zahtevaNastaneVStanjuCakaInNicNeOdpre() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        Uporabnik stars = navijac("stars@test", StatusNarocnine.AKTIVNA);
        prijava(stars);

        DruzinaDto.Povezava p = druzina.zahtevaj(otrok.getId());

        assertEquals(DruzinaPovezava.Status.CAKA, p.status());
        assertFalse(p.veljavna());
        assertThrows(PrepovedanoIzjema.class, () -> dostop.preveriLastnistvo(otrok.getId(), "stars@test"),
                "zahteva zasebne statistike ne odpre: odloci admin");
        assertTrue(druzina.igralciDruzine(stars).isEmpty());
    }

    @Test
    void poPotrditviAdminaStarsVidiZasebnoStatistikoSamoDokljerVeljaPaket() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        Uporabnik stars = navijac("stars2@test", StatusNarocnine.AKTIVNA);
        prijava(stars);
        Long id = druzina.zahtevaj(otrok.getId()).id();

        druzina.potrdi(id);

        assertDoesNotThrow(() -> dostop.preveriLastnistvo(otrok.getId(), "stars2@test"));
        assertDoesNotThrow(() -> dostop.preveriNapoved(otrok.getId(), "stars2@test"));
        assertEquals(Set.of(otrok.getId()), druzina.igralciDruzine(stars));
        assertTrue(druzina.pregled().povezave().get(0).veljavna());

        // paket je potekel: povezava ostane zapisana, a nic ne odpre
        Narocnina n = narocninaRepozitorij.findByUporabnikId(stars.getId()).orElseThrow();
        n.setStatus(StatusNarocnine.ZAPADLA);
        narocninaRepozitorij.save(n);
        assertThrows(PrepovedanoIzjema.class, () -> dostop.preveriLastnistvo(otrok.getId(), "stars2@test"));
        assertFalse(druzina.pregled().povezave().get(0).veljavna());
        assertEquals(1, druzina.pregled().povezave().size());
    }

    @Test
    void tujNavijacZasebneStatistikeIgralcaNevidi() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        Uporabnik stars = navijac("pravi@test", StatusNarocnine.AKTIVNA);
        navijac("tuj@test", StatusNarocnine.AKTIVNA);
        prijava(stars);
        druzina.potrdi(druzina.zahtevaj(otrok.getId()).id());

        assertThrows(PrepovedanoIzjema.class, () -> dostop.preveriLastnistvo(otrok.getId(), "tuj@test"));
    }

    // ---------- pravila ----------

    @Test
    void odrasliIgralecInIgralecBrezDatumaNistaVDruzini() {
        Igralec odrasel = mladinec("Veliki", "Moz", 40);
        Igralec neznan = noviIgralec("Neznan", "Datum");
        prijava(navijac("stars3@test", StatusNarocnine.AKTIVNA));

        assertThrows(NeveljavenVnosIzjema.class, () -> druzina.zahtevaj(odrasel.getId()));
        assertThrows(NeveljavenVnosIzjema.class, () -> druzina.zahtevaj(neznan.getId()));
    }

    @Test
    void najvecDvaIgralca() {
        Igralec a = mladinec("Ana", "Prvi", 10);
        Igralec b = mladinec("Bor", "Drugi", 14);
        Igralec c = mladinec("Cene", "Tretji", 16);
        prijava(navijac("stars4@test", StatusNarocnine.AKTIVNA));

        druzina.zahtevaj(a.getId());
        Long idB = druzina.zahtevaj(b.getId()).id();
        assertThrows(DomenskaIzjema.class, () -> druzina.zahtevaj(c.getId()));

        druzina.umakni(idB);
        assertDoesNotThrow(() -> druzina.zahtevaj(c.getId()));
    }

    @Test
    void zavrnjenaZahtevaSeNeponavljaTiho() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        prijava(navijac("stars5@test", StatusNarocnine.AKTIVNA));
        Long id = druzina.zahtevaj(otrok.getId()).id();
        druzina.zavrni(id);

        assertThrows(DomenskaIzjema.class, () -> druzina.zahtevaj(otrok.getId()));
        assertThrows(DomenskaIzjema.class, () -> druzina.umakni(id), "odlocitve admina oseba ne zbrise");
    }

    @Test
    void igralecKiPrerasePasU21JeVDruzinoDoKoncaPlacanegaObdobja() {
        Igralec mladinec = mladinec("Nekdanji", "Mladinec", 40);
        Uporabnik stars = navijac("stars6@test", StatusNarocnine.AKTIVNA);
        // povezava je bila potrjena, ko je bil igralec se U21 (zdaj ga stara letnica ne kaze vec)
        DruzinaPovezava p = new DruzinaPovezava(stars.getId(), mladinec.getId(), LocalDateTime.now().minusDays(90));
        p.setStatus(DruzinaPovezava.Status.POTRJENA);
        povezave.save(p);
        assertTrue(druzina.jeClan(stars, mladinec.getId()), "do prve uskladitve velja");

        int spremenjenih = druzina.uskladiStaroste(LocalDateTime.now());

        assertEquals(1, spremenjenih);
        DruzinaPovezava po = povezave.findById(p.getId()).orElseThrow();
        assertNotNull(po.getVeljaDo(), "meja je konec placanega obdobja");
        assertTrue(po.veljaOb(LocalDateTime.now().plusDays(10)), "velja do konca obdobja (6 mesecev)");
        assertFalse(po.veljaOb(LocalDateTime.now().plusMonths(7)), "po koncu obdobja ne vec");
        assertEquals(0, druzina.uskladiStaroste(LocalDateTime.now()), "meja se ne premika");
    }

    // ---------- Premium otroka ----------

    @Test
    void otrokovRacunDobiPremiumSamoPoPotrditviInDokljerPlacnikPlacuje() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        Uporabnik racunOtroka = new Uporabnik("otrok@test", "{bcrypt}x", Vloga.IGRALEC);
        racunOtroka.setStatus(StatusRacuna.POTRJEN);
        racunOtroka.setIgralec(otrok);
        racunOtroka = uporabnikRepozitorij.save(racunOtroka);
        Uporabnik stars = navijac("stars7@test", StatusNarocnine.AKTIVNA);
        prijava(stars);
        Long id = druzina.zahtevaj(otrok.getId()).id();

        assertFalse(narocninaStoritev.imaPremium(racunOtroka), "pred potrditvijo ni Premium");
        druzina.potrdi(id);
        assertTrue(narocninaStoritev.imaPremium(racunOtroka));
        assertTrue(narocninaStoritev.imaDruzinskiPremium(racunOtroka));
        UporabnikDto dto = racuni.profil("otrok@test");
        assertEquals(Paket.PREMIUM, dto.paket());
        assertTrue(dto.paketAktiven(), "vmesnik ga bere kot lastnega");
        assertTrue(narocninaStoritev.imaSirokoSpremljanje(racunOtroka));

        Narocnina n = narocninaRepozitorij.findByUporabnikId(stars.getId()).orElseThrow();
        n.setStatus(StatusNarocnine.ZAPADLA);
        narocninaRepozitorij.save(n);
        assertFalse(narocninaStoritev.imaPremium(racunOtroka), "ko placnik ne placuje, Premium preneha");
    }

    // ---------- kam seze druzina ----------

    @Test
    void druzinaJeNaDomaciStraniKoledarjuInNeSteVMejoSpremljanih() {
        Igralec otrok = mladinec("Mala", "Kovac", 12);
        Igralec drug = noviIgralec("Drug", "Igralec");
        Uporabnik stars = navijac("stars8@test", StatusNarocnine.AKTIVNA);
        prijava(stars);
        druzina.potrdi(druzina.zahtevaj(otrok.getId()).id());
        spremljani.spremljaj(drug.getId());

        List<DomovIgralecDto> povzetki = spremljani.povzetki();

        assertEquals(2, povzetki.size());
        assertEquals(otrok.getId(), povzetki.get(0).idIgralec(), "druzina je prva");
        assertTrue(povzetki.get(0).druzina());
        assertFalse(povzetki.get(1).druzina());
        assertEquals(Set.of(otrok.getId()), druzina.igralciDruzine(stars));
        assertTrue(koledar.igralci(stars).stream().anyMatch(i -> i.getId().equals(otrok.getId())),
                "otrokovi termini so v koledarju");
    }

    // ---------- priprava ----------

    private Igralec mladinec(String ime, String priimek, int let) {
        Igralec i = noviIgralec(ime, priimek);
        i.setDatumRojstva(LocalDate.now().minusYears(let).minusMonths(2));
        return igralecRepozitorij.save(i);
    }

    private Uporabnik navijac(String prijavnoIme, StatusNarocnine status) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.NAVIJAC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setEmailPotrjenOb(LocalDateTime.now());
        u = uporabnikRepozitorij.save(u);
        if (status != null) {
            Narocnina n = new Narocnina(u, Paket.NAVIJAC);
            n.setStatus(status);
            n.setCiklus(CiklusPlacila.LETNO);
            n.setStripeNarocninaId("sub_" + prijavnoIme);
            n.setTrenutnoObdobjeDo(LocalDateTime.now().plusMonths(6));
            narocninaRepozitorij.save(n);
        }
        return u;
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_NAVIJAC"))));
    }
}
