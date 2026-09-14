/* Uvozeno tekmovanje je samo za branje (V27): vsaka mutacija turnirja ali lige
   z virom se zavrne - tudi administratorju in brez varnostnega konteksta,
   ker je vir resnice zveza. Opis lige v piramidi (prehodi) ostane dovoljen,
   ker ga vir ne pozna. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.repozitoriji.LigaRepozitorij;

class SamoZaBranjeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;

    @Test
    void uvozenegaTurnirjaNiMogoceSpreminjati() {
        Dogodek dogodek = pripraviDogodek(4);
        Turnir turnir = dogodek.getTurnir();
        turnir.setVir(VirTekmovanja.STUPA);
        turnirRepozitorij.save(turnir);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.dodajDogodek(
                turnir.getId(), new DogodekVnos("Nov dogodek", SpolKategorija.MOSKI, null, 5, null, null,
                        null, SistemTekmovanja.IZLOCILNI, null, null)));
        assertTrue(napaka.getMessage().contains("samo za branje"));
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediZreb(dogodek.getId()));
        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.odjavi(
                prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).get(0).getId()));
    }

    @Test
    void uvozeneLigeNiMogoceUrejatiPiramidoPaLahko() {
        LigaVnos v = new LigaVnos("1. SNTL", "2026/27", SpolKategorija.MOSKI, FormatSrecanja.SNTL_PRVA, 5,
                4, true, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, LocalDate.now().atStartOfDay(), 7);
        Long idLiga = ligaStoritev.ustvari(v).id();
        Long idNizje = ligaStoritev.ustvari(new LigaVnos("2. SNTL", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, 6, true, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                null, null)).id();
        Liga liga = ligaRepozitorij.findById(idLiga).orElseThrow();
        liga.setVir(VirTekmovanja.STUPA);
        ligaRepozitorij.save(liga);

        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.uredi(idLiga, v));
        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, "Ekipa")));
        assertDoesNotThrow(() -> ligaStoritev.nastaviPrehode(idLiga,
                new PrehodiVnos(null, java.util.List.of(idNizje), 0, 2)));
        assertDoesNotThrow(() -> ligaStoritev.nastaviNaDomaci(idLiga, true));
    }
}
