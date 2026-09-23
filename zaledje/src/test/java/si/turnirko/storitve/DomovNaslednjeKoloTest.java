/* "Naslednje kolo" na domaci strani ne sme kazati datuma, ki je ze mimo.

   Srecanje, ki je ostalo neodigrano (dogovorjena prestavitev brez vpisa v
   sistem), bi po starem ostalo prvo nekoncano in s tem "naslednje" cele tedne.
   Test drzi tri stvari: danasnji dan se steje, ze mimo ni nikoli naslednje,
   in prestavljeno srecanje starejsega kola ne prehiti kola, ki pride prej. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DomovLigaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

class DomovNaslednjeKoloTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private DomovStoritev domovStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;

    private static final int RAZMIK = 7;

    /* Kolo 1 je bilo pred tednom in eno njegovo srecanje ni bilo odigrano;
       kolo 2 je DANES. Naslednje je kolo 2 - in ker je danes, se mora steti. */
    @Test
    void zamujenoSrecanjeNiNaslednjeKolo() {
        LocalDate danes = LocalDate.now();
        Long idLige = pripraviLigo(danes.minusDays(RAZMIK));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        koncaj(srecanja, 1, 1);

        DomovLigaDto.Naslednje naslednje = naslednje(idLige);

        assertNotNull(naslednje);
        assertEquals(2, naslednje.kolo(), "zamujeno srecanje 1. kola ni naslednje");
        assertEquals(danes, naslednje.datum(), "kolo danes je se naslednje");
    }

    /* Liga, kjer je od neodigranega ostalo samo to, kar je ze mimo, naslednjega
       kola nima - vmesnik takrat vrstice ne izpise. */
    @Test
    void ceJeVseNeodigranoMimoNaslednjegaKolaNi() {
        Long idLige = pripraviLigo(LocalDate.now().minusDays(30));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        srecanja.stream().skip(1).forEach(s -> s.setStatus(StatusSrecanja.KONCANO));

        assertNull(naslednje(idLige));
    }

    /* Organizator je zamujeno srecanje uradno prestavil cez tri tedne: kolo 1
       je po zapisu se pred kolom 2, a se igra pozneje kot on. */
    @Test
    void prestavljenoSrecanjeNePrehitiKolaKiPridePrej() {
        LocalDate danes = LocalDate.now();
        Long idLige = pripraviLigo(danes.minusDays(RAZMIK));
        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(idLige);
        koncaj(srecanja, 1, 1);
        Srecanje prestavljeno = srecanja.stream()
                .filter(s -> s.getKolo() == 1 && s.getStatus() != StatusSrecanja.KONCANO)
                .findFirst().orElseThrow();
        prestavljeno.setPredvidenZacetek(danes.plusDays(21).atTime(18, 0));

        DomovLigaDto.Naslednje naslednje = naslednje(idLige);

        assertNotNull(naslednje);
        assertEquals(2, naslednje.kolo());
        assertEquals(danes, naslednje.datum());
    }

    /* Srecanje brez termina ni "ze mimo": ne vemo, da je. Liga brez datumov
       kaze kolo brez dneva, kot doslej. */
    @Test
    void ligaBrezTerminovKazeKoloBrezDatuma() {
        Long idLige = pripraviLigo(null);

        DomovLigaDto.Naslednje naslednje = naslednje(idLige);

        assertNotNull(naslednje);
        assertEquals(1, naslednje.kolo());
        assertNull(naslednje.datum());
    }

    // ---------- priprava ----------

    private DomovLigaDto.Naslednje naslednje(Long idLige) {
        return domovStoritev.povzetkiLig(List.of(idLige), List.of()).get(0).naslednje();
    }

    /* Konca vsa srecanja kol do vkljucno "doKola", razen zadnjih "izpusti" v
       tem kolu (jih pusti nekoncanih). */
    private static void koncaj(List<Srecanje> srecanja, int doKola, int izpusti) {
        long izpuscenih = 0;
        for (Srecanje s : srecanja) {
            if (s.getKolo() > doKola) {
                continue;
            }
            if (s.getKolo() == doKola && izpuscenih < izpusti) {
                izpuscenih++;
                continue;
            }
            s.setStatus(StatusSrecanja.KONCANO);
        }
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
