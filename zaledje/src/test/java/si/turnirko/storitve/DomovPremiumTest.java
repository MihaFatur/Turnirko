/* Domaca stran igralca s Premium: razpredelnica lige okoli njegove ekipe in
   turnirji, ki se ga ticejo. Brez paketa (in gostu) ostane, kar je bilo -
   vrh lestvice in najnovejsi turnirji. Pravili sami sta v IzborTurnirjevTest;
   tu je vez z bazo, racunom in paketom. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.DomovTurnirDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class DomovPremiumTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private DomovStoritev domovStoritev;
    @Autowired private DomaciTurnirjiStoritev domaciTurnirji;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    /* Liga s petimi ekipami; igralceva je ena od njih. S paketom razpredelnica
       nosi njegovo ekipo (oznaceno) in je okno treh vrstic okoli nje. */
    @Test
    void premiumIgralecVidiSvojoEkipoInSosede() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        Long idLiga = ligaSPetimiEkipami(igralec);
        prijava(racun("premium@test", igralec, true));

        DomovLigaDto liga = domovStoritev.povzetkiLig(List.of(idLiga), List.of()).get(0);

        assertEquals(5, liga.ekip());
        assertEquals(3, liga.vrh().size());
        assertEquals(1, liga.vrh().stream().filter(DomovLigaDto.Vrh::moja).count());
        DomovLigaDto.Vrh moja = liga.vrh().stream().filter(DomovLigaDto.Vrh::moja).findFirst().orElseThrow();
        assertEquals("Moja ekipa", moja.ekipa());
        int prvo = liga.vrh().get(0).mesto();
        for (int i = 0; i < liga.vrh().size(); i++) {
            assertEquals(prvo + i, liga.vrh().get(i).mesto(), "okno so zaporedna mesta");
        }
    }

    /* Brez paketa je razpredelnica vrh lestvice in nobena vrstica ni "moja". */
    @Test
    void brezPaketaOstaneVrhLestvice() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        Long idLiga = ligaSPetimiEkipami(igralec);
        prijava(racun("brezplacno@test", igralec, false));

        DomovLigaDto liga = domovStoritev.povzetkiLig(List.of(idLiga), List.of()).get(0);

        assertEquals(List.of(1, 2, 3), liga.vrh().stream().map(DomovLigaDto.Vrh::mesto).toList());
        assertFalse(liga.vrh().stream().anyMatch(DomovLigaDto.Vrh::moja));
    }

    /* Turnirji: igralec s paketom vidi turnir, na katerem je nazadnje nastopil;
       gost in igralec brez paketa dobita prazen izbor (vmesnik pokaze
       najnovejse). */
    @Test
    void turnirjiZaPremiumIgralcaInPraznoZaDruge() {
        Dogodek dogodek = pripraviDogodek(4);
        Turnir turnir = dogodek.getTurnir();
        turnir.setStatus(StatusTekmovanja.ZAKLJUCEN);
        turnir.setDatumZacetka(LocalDate.now().minusDays(10));
        turnirRepozitorij.save(turnir);
        Igralec igralec = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();

        assertTrue(domaciTurnirji.zaPrijavljenega().isEmpty(), "gost");

        Uporabnik u = racun("igralec@test", igralec, false);
        prijava(u);
        assertTrue(domaciTurnirji.zaPrijavljenega().isEmpty(), "brez paketa");

        // isti racun (igralec ima en sam racun) nato kupi Premium
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        List<DomovTurnirDto> izbor = domaciTurnirji.zaPrijavljenega();
        assertTrue(izbor.contains(new DomovTurnirDto(turnir.getId(), DomovTurnirDto.Razlog.ZADNJI)),
                "turnir, na katerem je nastopil, je njegov zadnji");
    }

    // ---------- Pomozno ----------

    /* Pet prostih ekip; igralec je v kadru ekipe "Moja ekipa". */
    private Long ligaSPetimiEkipami(Igralec igralec) {
        LigaVnos v = new LigaVnos("Testna liga", "26/27", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.KLUBSKO, false, null, null, null);
        Long idLiga = ligaStoritev.ustvari(v).id();
        for (String ime : List.of("Ekipa A", "Ekipa B", "Moja ekipa", "Ekipa D", "Ekipa E")) {
            Long idEkipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime)).id();
            Igralec clan = ime.equals("Moja ekipa") ? igralec : noviIgralec("Clan", ime);
            ligaStoritev.dodajVKader(idEkipa, new KaderVnos(clan.getId(), 1));
        }
        return idLiga;
    }

    private Uporabnik racun(String prijavnoIme, Igralec igralec, boolean premium) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
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
                        List.of(new SimpleGrantedAuthority("ROLE_IGRALEC"))));
    }
}
