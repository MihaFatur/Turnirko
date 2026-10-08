/* Besedilo predogleda povezave (PredogledStoritev) za stran lige - najpogosteje
   deljeno povezavo (skupina ekipe na WhatsAppu). */
package si.turnirko.splet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.splet.PredogledStrani.Predogled;
import si.turnirko.storitve.IntegracijskiTest;
import si.turnirko.storitve.LigaStoritev;
import si.turnirko.storitve.PotekLige;

class PredogledStoritevTest extends IntegracijskiTest {

    @Autowired private PredogledStoritev predogledi;
    @Autowired private LigaStoritev ligaStoritev;

    @Test
    void ligaImaImeSSezonoInPotek() {
        Liga liga = pripraviLigo("Liga Savinja B");

        Predogled p = predogledi.liga(liga.id(), null).orElseThrow();

        assertEquals("Liga Savinja B · Sezona 8", p.naslov());
        assertTrue(p.opis().startsWith("Lestvica, razpored in izidi · 4 ekipe"), p.opis());
        assertTrue(p.opis().contains("od 3 kol"), p.opis());
        assertEquals("/lige/" + liga.id(), p.pot());
    }

    /* Okno ekipe (?ekipa=) je tisto, kar si igralci delijo: naslov je ekipa. */
    @Test
    void oknoEkipeNosiImeEkipe() {
        Liga liga = pripraviLigo("Liga Savinja B");
        EkipaDto ekipa = liga.ekipe().get(1);

        Predogled p = predogledi.liga(liga.id(), ekipa.id()).orElseThrow();

        assertEquals(ekipa.prikazanoIme() + " · Liga Savinja B", p.naslov());
        assertEquals("/lige/" + liga.id() + "?ekipa=" + ekipa.id(), p.pot());
        assertEquals("/lige/" + liga.id(), p.kanonicnaPot());
    }

    /* Ekipa iz druge lige (star ali rocno vpisan parameter) ne sme priti v
       naslov tuje lige - predogled je takrat predogled lige. */
    @Test
    void ekipaDrugeLigeSePrezre() {
        Liga prva = pripraviLigo("Prva");
        Liga druga = pripraviLigo("Druga");

        Predogled p = predogledi.liga(prva.id(), druga.ekipe().get(0).id()).orElseThrow();

        assertEquals("Prva · Sezona 8", p.naslov());
    }

    @Test
    void neobstojeceLigeNi() {
        assertEquals(Optional.empty(), predogledi.liga(999_999L, null));
    }

    @Test
    void stalneStraniInSklanjanje() {
        assertEquals("Lestvica igralcev", PredogledStoritev.stalna("/lestvica").orElseThrow().naslov());
        assertEquals(Optional.empty(), PredogledStoritev.stalna("/racuni"));
        assertEquals("ekipa", PredogledStoritev.ekip(1));
        assertEquals("ekipi", PredogledStoritev.ekip(2));
        assertEquals("ekipe", PredogledStoritev.ekip(4));
        assertEquals("ekip", PredogledStoritev.ekip(12));
        assertEquals("ekip", PredogledStoritev.ekip(112));
    }

    // ---------- priprava ----------

    private record Liga(Long id, List<EkipaDto> ekipe) {}

    /* Stiri ekipe, tri kola na teden; prvo kolo je bilo pred tednom. */
    private Liga pripraviLigo(String ime) {
        LigaVnos vnos = new LigaVnos(ime, "Sezona 8", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false, RavenTekmovanja.KLUBSKO, false, null,
                PotekLige.danes().minusDays(7).atTime(18, 30), 7);
        Long idLige = ligaStoritev.ustvari(vnos).id();
        List<EkipaDto> ekipe = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub(ime + " klub " + i, null));
            ekipe.add(ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null)));
        }
        ligaStoritev.generirajRazpored(idLige);
        return new Liga(idLige, ekipe);
    }
}
