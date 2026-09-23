/* Lige na domaci strani igralca s Premium: najprej tiste, v katerih igra
   (kader ekipe), sele nato adminov izbor.

   Testiramo stopnjo "igralceve lige" v vrstnem redu odlocanja
   DomovStoritev.povzetkiLig: stoji za izborom racuna (ta ostane prvi) in pred
   adminovim izborom, a samo za placnika - brez paketa in za gosta se nic ne
   spremeni. Lige se pripravijo PRED prijavo: dodajanje ekip zahteva pravico
   urejanja lige, ta pa ne velja za igralca. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class DomovLigeIgralcevihEkipTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private DomovStoritev domovStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void premiumIgralecVidiLigeSvojeEkipePredAdminovimi() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        Long moja = ligaZIgralcem("Rekreativna liga", igralec);
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");
        prijava(racun("premium@test", igralec, true));

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(), List.of());

        assertEquals(List.of(moja), idji(povzetki));
        assertTrue(povzetki.get(0).izEkipe(),
                "vmesnik po tem ve, da so to res njegove lige in sme reci 'Moje lige'");
        assertFalse(idji(povzetki).contains(izpostavljena));
    }

    /* Ekipa je pogoj, ne le racun s paketom: igralec, ki v nobeni ligi ne igra,
       vidi to, kar vsi. */
    @Test
    void premiumIgralecBrezEkipeVidiAdminovIzbor() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        ligaZIgralcem("Liga drugih", noviIgralec("Bor", "Kos"));
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");
        prijava(racun("premium@test", igralec, true));

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(), List.of());

        assertEquals(List.of(izpostavljena), idji(povzetki));
        assertFalse(povzetki.get(0).izEkipe(), "adminova liga ni 'moja' zato, ker jo je izbral admin");
    }

    /* Funkcija je del Premium: brez paketa racun ostane pri adminovem izboru. */
    @Test
    void igralecBrezPaketaVidiAdminovIzbor() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        ligaZIgralcem("Rekreativna liga", igralec);
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");
        prijava(racun("brezplacno@test", igralec, false));

        assertEquals(List.of(izpostavljena), idji(domovStoritev.povzetkiLig(List.of(), List.of())));
    }

    @Test
    void gostVidiAdminovIzbor() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        ligaZIgralcem("Rekreativna liga", igralec);
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");

        assertEquals(List.of(izpostavljena), idji(domovStoritev.povzetkiLig(List.of(), List.of())));
    }

    /* Racun, ki ga admin se ni povezal z igralcem, ne ve, katere ekipe so
       njegove - tudi ce je placal. */
    @Test
    void racunBrezPovezaveZIgralcemNimaSvojihLig() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        ligaZIgralcem("Rekreativna liga", igralec);
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");
        prijava(racun("nepovezan@test", null, true));

        assertEquals(List.of(izpostavljena), idji(domovStoritev.povzetkiLig(List.of(), List.of())));
    }

    /* Kdor si je sklop sestavil sam, ga vidi takega, kot ga je sestavil. */
    @Test
    void lastenIzborPrevladaNadLigamiSvojeEkipe() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        ligaZIgralcem("Rekreativna liga", igralec);
        Long izbrana = ligaNaDomaci("1. SNTL moski");
        prijava(racun("premium@test", igralec, true));

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(izbrana), List.of());

        assertEquals(List.of(izbrana), idji(povzetki));
        assertFalse(povzetki.get(0).izEkipe(), "to je njegov izbor in ne kader ekipe");
    }

    /* Domaca stran ni arhiv: uvozena zgodovina ima pri dolgoletnem igralcu
       ducat starih sezon. */
    @Test
    void zakljucenaLigaNiIgralcevaLiga() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        Long stara = ligaZIgralcem("Liga lani", igralec);
        stanje(stara, StatusTekmovanja.ZAKLJUCEN);
        Long izpostavljena = ligaNaDomaci("1. SNTL moski");
        prijava(racun("premium@test", igralec, true));

        assertEquals(List.of(izpostavljena), idji(domovStoritev.povzetkiLig(List.of(), List.of())));
    }

    /* Lige v teku so pred tistimi v pripravi, ne glede na vrstni red vpisa. */
    @Test
    void ligeVTekuSoPredLigamiVPripravi() {
        Igralec igralec = noviIgralec("Ana", "Novak");
        Long vPripravi = ligaZIgralcem("Liga A", igralec);
        Long vTeku = ligaZIgralcem("Liga B", igralec);
        stanje(vTeku, StatusTekmovanja.V_TEKU);
        prijava(racun("premium@test", igralec, true));

        assertEquals(List.of(vTeku, vPripravi),
                idji(domovStoritev.povzetkiLig(List.of(), List.of())));
    }

    // ---------- Pomozno ----------

    private static List<Long> idji(List<DomovLigaDto> povzetki) {
        return povzetki.stream().map(DomovLigaDto::id).toList();
    }

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }

    /* Liga z eno ekipo, v katere kadru je igralec. */
    private Long ligaZIgralcem(String ime, Igralec igralec) {
        Long idLiga = ustvariLigo(ime);
        Long idEkipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime + " ekipa")).id();
        ligaStoritev.dodajVKader(idEkipa, new KaderVnos(igralec.getId(), 1));
        return idLiga;
    }

    private Long ligaNaDomaci(String ime) {
        Long idLiga = ustvariLigo(ime);
        ligaStoritev.nastaviNaDomaci(idLiga, true);
        return idLiga;
    }

    private void stanje(Long idLiga, StatusTekmovanja status) {
        Liga liga = ligaRepozitorij.findById(idLiga).orElseThrow();
        liga.setStatus(status);
        ligaRepozitorij.save(liga);
    }

    private Uporabnik racun(String prijavnoIme, Igralec igralec, boolean premium) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        // shema ne dovoli potrjenega racuna igralca brez povezanega igralca
        u.setStatus(igralec == null ? StatusRacuna.CAKA : StatusRacuna.POTRJEN);
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
