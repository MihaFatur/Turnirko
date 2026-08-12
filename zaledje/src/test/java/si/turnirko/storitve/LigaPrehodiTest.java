/* Testi prehodov med ligami (mesto lige v piramidi): povezavo je mogoce
   zapisati z obeh strani, ureja se tudi po zacetku tekmovanja, iz izbora
   izpuscene nizje lige se odvezejo, krogov pa ni mogoce ustvariti. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.LigaRepozitorij;

class LigaPrehodiTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;

    @Test
    void nizjaLigaSeVezeZVisje() {
        Long visja = ustvariLigo("Savinja liga B");
        Long nizja = ustvariLigo("Savinja liga C");

        // povezavo zapisemo z zgornje strani - stolpec dobi nizja liga
        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, List.of(nizja), 0, 2));

        assertEquals(visja, ligaStoritev.najdi(nizja).idVisjaLiga());
        assertEquals("Savinja liga B", ligaStoritev.najdi(nizja).visjaLigaIme());
        assertEquals(2, ligaStoritev.najdi(visja).stIzpade());
    }

    @Test
    void izpuscenaNizjaLigaSeOdveze() {
        Long visja = ustvariLigo("Savinja liga B");
        Long nizja = ustvariLigo("Savinja liga C");
        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, List.of(nizja), 0, 2));

        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, List.of(), 0, 0));

        assertNull(ligaStoritev.najdi(nizja).idVisjaLiga());
    }

    /* null pomeni "nizjih lig ne ureja" - obstojece povezave morajo ostati. */
    @Test
    void brezSeznamaNizjihOstanejoPovezaveNedotaknjene() {
        Long visja = ustvariLigo("Savinja liga B");
        Long nizja = ustvariLigo("Savinja liga C");
        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, List.of(nizja), 0, 2));

        ligaStoritev.nastaviPrehode(visja, new PrehodiVnos(null, null, 1, 2));

        assertEquals(visja, ligaStoritev.najdi(nizja).idVisjaLiga());
        assertEquals(1, ligaStoritev.najdi(visja).stNapreduje());
    }

    @Test
    void krogNiMogoc() {
        Long a = ustvariLigo("Liga A");
        Long b = ustvariLigo("Liga B");
        ligaStoritev.nastaviPrehode(b, new PrehodiVnos(a, null, 2, 0));

        // b je ze pod a; ce bi bila zdaj a pod b, bi nastal krog
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.nastaviPrehode(a, new PrehodiVnos(b, null, 0, 2)));
    }

    @Test
    void ligaNeMoreBitiSamaSebiVisjaAliNizja() {
        Long a = ustvariLigo("Liga A");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.nastaviPrehode(a, new PrehodiVnos(a, null, 0, 0)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.nastaviPrehode(a, new PrehodiVnos(null, List.of(a), 0, 0)));
    }

    /* Kljucno za rabo: pravila lige so po generiranju razporeda zaklenjena,
       mesto v piramidi pa se mora dati popraviti tudi sredi sezone. */
    @Test
    void prehodeJeMogoceUrejatiTudiKoLigaNiVecVPripravi() {
        Long visja = ustvariLigo("Savinja liga B");
        Long nizja = ustvariLigo("Savinja liga C");
        vTeku(nizja);

        ligaStoritev.nastaviPrehode(nizja, new PrehodiVnos(visja, null, 2, 0));

        assertEquals(visja, ligaStoritev.najdi(nizja).idVisjaLiga());
        // pravila iste lige so takrat ze zaklenjena
        LigaVnos pravila = new LigaVnos("Savinja liga C", "25/26", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false, true, null);
        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.uredi(nizja, pravila));
    }

    // ---------- pomozne metode ----------

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MOSKI, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, true, false, true, null);
        LigaDto liga = ligaStoritev.ustvari(v);
        return liga.id();
    }

    /* Razpored bi zahteval ekipe s kadrom; za to preverbo zadosca stanje. */
    private void vTeku(Long idLiga) {
        Liga liga = ligaRepozitorij.findById(idLiga).orElseThrow();
        liga.setStatus(StatusTekmovanja.V_TEKU);
        ligaRepozitorij.save(liga);
    }
}
