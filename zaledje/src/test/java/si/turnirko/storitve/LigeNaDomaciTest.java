/* Izbor lig za domaco stran: admin postavi najvec dve ligi, ki ju vidi gost
   in vsak, ki si svojega izbora ni sestavil.

   Testiramo vrstni red odlocanja v DomovStoritev.povzetkiLig - ta je edini
   razlog, zakaj sta parametra dva (izbor racuna in spomin brskalnika) in ne
   en sam seznam id-jev. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.LigaRepozitorij;

class LigeNaDomaciTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private DomovStoritev domovStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;

    @Test
    void tretjaLigaNaDomaciSeZavrne() {
        Long prva = ustvariLigo("1. SNTL moski");
        Long druga = ustvariLigo("1. SNTL zenske");
        Long tretja = ustvariLigo("2. SNTL");

        ligaStoritev.nastaviNaDomaci(prva, true);
        ligaStoritev.nastaviNaDomaci(druga, true);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.nastaviNaDomaci(tretja, true));
        /* Sporocilo mora povedati, katera liga je na poti - drugace admin
           ugiba, kaj naj odkljuka. */
        assertTrue(napaka.getMessage().contains("1. SNTL moski"), napaka.getMessage());
    }

    /* Ponovno postavljanje ze postavljene lige ni tretja liga. */
    @Test
    void ponovnaPostavitevIstelIgeNeZapolniMesta() {
        Long prva = ustvariLigo("1. SNTL moski");
        Long druga = ustvariLigo("1. SNTL zenske");
        ligaStoritev.nastaviNaDomaci(prva, true);
        ligaStoritev.nastaviNaDomaci(prva, true);

        ligaStoritev.nastaviNaDomaci(druga, true);
        assertEquals(2, ligaRepozitorij.najdiNaDomaci().size());
    }

    @Test
    void umikSprostiMesto() {
        Long prva = ustvariLigo("1. SNTL moski");
        Long druga = ustvariLigo("1. SNTL zenske");
        Long tretja = ustvariLigo("2. SNTL");
        ligaStoritev.nastaviNaDomaci(prva, true);
        ligaStoritev.nastaviNaDomaci(druga, true);

        ligaStoritev.nastaviNaDomaci(prva, false);
        LigaDto po = ligaStoritev.nastaviNaDomaci(tretja, true);

        assertTrue(po.naDomaci());
        assertEquals(List.of(druga, tretja),
                ligaRepozitorij.najdiNaDomaci().stream().map(Liga::getId).toList());
    }

    /* Brez izbora gost vidi to, kar je postavil admin - in ne prve lige v teku
       po vrstnem redu vpisa. */
    @Test
    void brezIzboraSePokazeAdminovIzbor() {
        Long vTeku = ustvariLigo("2. SNTL");
        vTeku(vTeku);
        Long izpostavljena = ustvariLigo("1. SNTL moski");
        ligaStoritev.nastaviNaDomaci(izpostavljena, true);

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(), List.of());

        assertEquals(List.of(izpostavljena), povzetki.stream().map(DomovLigaDto::id).toList());
    }

    /* Ogled ni izbira: adminova uredniska odlocitev ne sme odpasti zato, ker
       je gost pred tednom odprl neko ligo. */
    @Test
    void adminovIzborPrevladaNadOgledanimi() {
        Long ogledana = ustvariLigo("2. SNTL");
        Long izpostavljena = ustvariLigo("1. SNTL moski");
        ligaStoritev.nastaviNaDomaci(izpostavljena, true);

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(), List.of(ogledana));

        assertEquals(List.of(izpostavljena), povzetki.stream().map(DomovLigaDto::id).toList());
    }

    /* Kdor si je sklop sestavil sam, ga vidi takega, kot ga je sestavil. */
    @Test
    void lastenIzborPrevladaNadAdminovim() {
        Long moja = ustvariLigo("Rekreativna liga");
        Long izpostavljena = ustvariLigo("1. SNTL moski");
        ligaStoritev.nastaviNaDomaci(izpostavljena, true);

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(moja), List.of());

        assertEquals(List.of(moja), povzetki.stream().map(DomovLigaDto::id).toList());
    }

    /* Ce admin ni izbral nicesar, obvelja spomin brskalnika - a najvec dve
       ligi, ker je sklop na domaci strani dolg dve vrstici. */
    @Test
    void brezAdminovegaIzboraObveljajoOgledaneInSeOdrezejo() {
        Long a = ustvariLigo("Liga A");
        Long b = ustvariLigo("Liga B");
        Long c = ustvariLigo("Liga C");

        List<DomovLigaDto> povzetki = domovStoritev.povzetkiLig(List.of(), List.of(a, b, c));

        assertEquals(List.of(a, b), povzetki.stream().map(DomovLigaDto::id).toList());
    }

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, true, false, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }

    /* Razpored bi zahteval ekipe s kadrom; za to preverbo zadosca stanje. */
    private void vTeku(Long idLiga) {
        Liga liga = ligaRepozitorij.findById(idLiga).orElseThrow();
        liga.setStatus(StatusTekmovanja.V_TEKU);
        ligaRepozitorij.save(liga);
    }
}
