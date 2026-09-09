/* Podatki, ki jih domaca stran bere in jih vmesnik sam ne more izpeljati:
   besedno stanje turnirja (kje je, kdo ga je dobil, kaj je bil zadnji izid),
   crta gibanja ELO ob vrstici lestvice in osebni izbor spremljanih lig.

   Vsi trije so izpeljanke iz obstojecih zapisov, zato jih varuje test: ce se
   zreb, obracun ali imenovanje kol spremeni, mora to tu pociti. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class DomacaStranTest extends IntegracijskiTest {

    @Autowired private PovzetkiStoritev povzetkiStoritev;
    @Autowired private DomovStoritev domovStoritev;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    /* Osem igralcev v izlocilnem sistemu: po prvem kolu se turnir igra v
       polfinalu, po polfinalu v finalu. Vrstica turnirja to napise z besedo. */
    @Test
    void fazaTurnirjaSlediIzlocilnemuKolu() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        Long idTurnirja = dogodek.getTurnir().getId();

        assertEquals("četrtfinale", potek(idTurnirja).faza(),
                "pred prvim kolom je turnir v cetrtfinalu (4 tekme)");

        odigrajKolo(dogodek.getId());
        assertEquals("polfinale", potek(idTurnirja).faza());

        odigrajKolo(dogodek.getId());
        assertEquals("finale", potek(idTurnirja).faza());
    }

    /* Skupinski del se imenuje "skupine" - imena izlocilnih kol tam ne
       pomenijo nicesar. */
    @Test
    void skupinskiDelSeImenujeSkupine() {
        Dogodek dogodek = pripraviDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertEquals("skupine", potek(dogodek.getTurnir().getId()).faza());
    }

    /* Zadnji izid je zadnja DEJANSKO odigrana tekma turnirja, zapisana s
       priimkoma ("Testni1 3:0 Testni2"). */
    @Test
    void zadnjiIzidJeZadnjaOdigranaTekma() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        Long idTurnirja = dogodek.getTurnir().getId();

        assertNull(potek(idTurnirja).zadnjiIzid(), "pred prvo tekmo izida ni");

        Tekma prva = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        String pricakovano = prva.getPrijava1().getIgralec().getPriimek()
                + " 3:1 " + prva.getPrijava2().getIgralec().getPriimek();
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 1, null, null));

        assertEquals(pricakovano, potek(idTurnirja).zadnjiIzid());
    }

    /* Ko je turnir odigran do konca, se vrstica bere po zmagovalcu. */
    @Test
    void zakljucenTurnirPokazeZmagovalca() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        TurnirDto.Potek potek = potek(dogodek.getTurnir().getId());
        assertNotNull(potek.zmagovalec(), "zmagovalca doloci koncno mesto 1");
        assertNull(potek.faza(), "odigranega turnirja ni vec kje igrati");
    }

    /* Crta ELO ob vrstici lestvice: dokler igralec nima vsaj dveh zabelezenih
       vrednosti, crte ni - ena tocka bi obljubljala zgodovino, ki je ni. */
    @Test
    void crtaEloNastaneSeleZDvemaZabelezenimaVrednostma() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertTrue(statistikaStoritev.globalnaLestvica().stream()
                        .allMatch(v -> v.eloZgodovina().isEmpty()),
                "pred prvo obracunano tekmo dnevnika ratinga ni");

        odigrajVse(dogodek.getId());

        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();
        assertTrue(lestvica.stream().anyMatch(v -> v.eloZgodovina().size() >= 2),
                "po treh tekmah ima igralec vec zabelezenih vrednosti");
        assertTrue(lestvica.stream().allMatch(v -> v.eloZgodovina().size() <= 7),
                "crta ima najvec sedem tock");
        // zadnja tocka crte je vedno trenutni rating
        lestvica.stream().filter(v -> !v.eloZgodovina().isEmpty()).forEach(v ->
                assertEquals(v.rating(), v.eloZgodovina().get(v.eloZgodovina().size() - 1)));
    }

    /* Vrstica lestvice nosi ime in priimek loceno; polno ime je slovensko
       "Ime Priimek", locena polja pa sluzijo urejanju po priimku. */
    @Test
    void vrsticaLestviceNosiImeInPriimekLoceno() {
        pripraviDogodek(2, SistemTekmovanja.KROZNI);

        LestvicaIgralcaDto prva = statistikaStoritev.globalnaLestvica().get(0);
        assertNotNull(prva.ime());
        assertNotNull(prva.priimek());
        assertEquals(prva.ime() + " " + prva.priimek(), prva.polnoIme());
    }

    /* Stolpca "Gib." in "Δ 30 dni" merita isto obdobje: dokler igralec pred
       mesecem ratinga se ni imel, sta oba prazna (in ne 0, kar bi pomenilo
       "nic se ni spremenilo"). Kategorija je izpeljana in je vedno tu. */
    @Test
    void premikInSpremembaRatingaStaPrazniBrezStanjaIzpredMeseca() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();
        assertTrue(lestvica.stream().allMatch(v -> v.premik() == null),
                "vse tekme so od danes, zato stanja izpred meseca ni");
        assertTrue(lestvica.stream().allMatch(v -> v.spremembaRatinga() == null),
                "delta ratinga tece iz istega stanja kot premik");
        assertTrue(lestvica.stream().allMatch(
                        v -> v.kategorija() == si.turnirko.modeli.KategorijaIgralca.CLANI),
                "testni igralci so moski, rojeni 2000 - torej clani");
    }

    /* Izbor spremljanih lig je last racuna: brez prijave ga ni. */
    @Test
    void spremljanjeLigZahtevaPrijavo() {
        assertThrows(PrepovedanoIzjema.class, () -> domovStoritev.mojeLige());
        assertThrows(PrepovedanoIzjema.class, () -> domovStoritev.spremljaj(1L));
    }

    /* Brez izbranih lig - in dokler admin domace strani ni uredil - sklop
       pokaze lige, ki so v teku. Adminov izbor je v LigeNaDomaciTest. */
    @Test
    void brezIzbranihLigPokazeLigeVTeku() {
        assertFalse(domovStoritev.povzetkiLig(List.of(), List.of()).stream()
                        .anyMatch(l -> l.status() != si.turnirko.modeli.StatusTekmovanja.V_TEKU),
                "privzeti izbor so lige v teku");
    }

    private TurnirDto.Potek potek(Long idTurnirja) {
        Map<Long, TurnirDto.Potek> poteki = povzetkiStoritev.potekiVsehTurnirjev();
        return poteki.getOrDefault(idTurnirja, TurnirDto.Potek.PRAZEN);
    }

    /* Odigra tekme, ki so pripravljene TA TRENUTEK (eno kolo). */
    private void odigrajKolo(Long idDogodka) {
        List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
        for (Tekma tekma : pripravljene) {
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        }
    }

    private void odigrajVse(Long idDogodka) {
        boolean seIgra = true;
        while (seIgra) {
            List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            seIgra = !pripravljene.isEmpty();
            for (Tekma tekma : pripravljene) {
                tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
    }
}
