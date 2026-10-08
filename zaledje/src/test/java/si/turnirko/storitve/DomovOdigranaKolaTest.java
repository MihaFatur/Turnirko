/* "N. od M kol" na domaci strani steje kola po DATUMU, ne po prvem koncanem
   srecanju.

   Ekipe se neuradno dogovorijo za menjavo terminov in odigrajo srecanje, ki
   spada v pozno kolo, ze zdaj. Po starem je to zadostovalo, da je kolo (in z
   njim vsa pred njim) veljalo za odigrano: ko je imelo vsako kolo eno tako
   srecanje, je domaca stran trdila, da je odigrano vse, v resnici pa je bilo
   le eno. Test drzi: (1) taka menjava kola ne "odigra", (2) danasnji dan se
   ne steje (kolo je se naslednje), (3) kolo brez datuma je odigrano sele, ko
   je koncano vsako njegovo srecanje. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

class DomovOdigranaKolaTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private DomovStoritev domovStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;

    private static final int RAZMIK = 7;

    /* Kolo 1 je bilo pred tednom, kolo 2 je danes, kolo 3 cez teden. V vsakem
       kolu je ze koncano eno srecanje (neuradna menjava): odigrano je le 1. */
    @Test
    void neuradneMenjaveNeOdigrajoKol() {
        Long idLige = pripraviLigo(PotekLige.danes().minusDays(RAZMIK));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        for (int kolo = 1; kolo <= 3; kolo++) {
            koncajEno(srecanja, kolo);
        }

        DomovLigaDto liga = liga(idLige);

        assertEquals(1, liga.odigranihKol(), "odigrano je samo kolo, ki je ze mimo");
        assertEquals(3, liga.vsehKol());
    }

    /* Danasnji dan se ne steje: kolo, ki je danes, je se "naslednje". Datum
       odloca sam, tudi ce nihce ni vpisal izida. */
    @Test
    void danasnjeKoloNiOdigrano() {
        Long idLige = pripraviLigo(PotekLige.danes().minusDays(RAZMIK));

        assertEquals(1, liga(idLige).odigranihKol());
    }

    @Test
    void kolaSePoDatumuPreberejoVsa() {
        Long idLige = pripraviLigo(PotekLige.danes().minusDays(3L * RAZMIK));

        DomovLigaDto liga = liga(idLige);

        assertEquals(3, liga.odigranihKol());
        assertEquals(3, liga.vsehKol());
    }

    @Test
    void kolaPredZacetkomSezoneNisoOdigrana() {
        Long idLige = pripraviLigo(PotekLige.danes().plusDays(1));

        assertEquals(0, liga(idLige).odigranihKol());
    }

    /* Brez datuma je edino merilo koncanost: kolo je odigrano, ko je koncano
       vsako njegovo srecanje - eno koncano srecanje ne zadosca. */
    @Test
    void koloBrezDatumaJeOdigranoKoJeKoncanoVsakoSrecanje() {
        Long idLige = pripraviLigo(null);
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        srecanja.stream().filter(s -> s.getKolo() == 1).forEach(s -> s.setStatus(StatusSrecanja.KONCANO));
        koncajEno(srecanja, 2);
        koncajEno(srecanja, 3);

        assertEquals(1, liga(idLige).odigranihKol());
    }

    /* Domaca stran, seznam lig in stran lige odgovorijo ENAKO: koliko kol je
       za nami in katero je naslednje. Prej sta stran in seznam stela kolo
       sele, ko je bilo koncano vsako srecanje - ob enem nevpisanem izidu je
       stran lige tedne kazala "naslednje" z datumom, ki je bil ze mimo. */
    @Test
    void domacaStranSeznamInStranLigeRecejoIsto() {
        Long idLige = pripraviLigo(PotekLige.danes().minusDays(RAZMIK));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        koncajEno(srecanja, 1); // drugo srecanje 1. kola ostane brez izida

        DomovLigaDto domov = liga(idLige);
        LigaDto stran = ligaStoritev.najdi(idLige);
        LigaDto vSeznamu = ligaStoritev.vse().stream()
                .filter(l -> l.id().equals(idLige)).findFirst().orElseThrow();

        assertEquals(1, domov.odigranihKol());
        for (LigaDto l : List.of(stran, vSeznamu)) {
            assertEquals(domov.odigranihKol(), l.odigranihKol());
            assertEquals(domov.vsehKol(), l.steviloKol());
            assertEquals(List.of(1), l.odigranaKola());
            assertEquals(domov.naslednje().kolo(), l.naslednje().kolo());
            assertEquals(domov.naslednje().datum(), l.naslednje().datum());
        }
        assertEquals(2, stran.naslednje().kolo(), "danasnje kolo je naslednje, ne 1. z nevpisanim izidom");
        assertEquals(PotekLige.danes(), stran.naslednje().datum());
    }

    // ---------- priprava ----------

    private DomovLigaDto liga(Long idLige) {
        return domovStoritev.povzetkiLig(List.of(idLige), List.of()).get(0);
    }

    /* Konca eno srecanje kola (prvo po vrsti). */
    private static void koncajEno(List<Srecanje> srecanja, int kolo) {
        srecanja.stream()
                .filter(s -> s.getKolo() == kolo)
                .findFirst().orElseThrow()
                .setStatus(StatusSrecanja.KONCANO);
    }

    /* Liga s stirimi ekipami: tri kola, v vsakem dve srecanji, na sedem dni. */
    private Long pripraviLigo(LocalDate prvoKolo) {
        LigaVnos vnos = new LigaVnos("Test liga", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                prvoKolo == null ? null : prvoKolo.atTime(18, 0), prvoKolo == null ? null : RAZMIK);
        Long idLige = ligaStoritev.ustvari(vnos).id();

        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub K" + i, null));
            var ekipa = ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 3; j++) {
                Igralec igralec = noviIgralec("Ime" + i + j, "Priimek" + i + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(igralec.getId(), j));
            }
        }
        ligaStoritev.generirajRazpored(idLige);
        return idLige;
    }
}
