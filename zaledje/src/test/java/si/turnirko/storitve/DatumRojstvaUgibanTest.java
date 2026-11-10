/* Znacka »datum rojstva ugiban« (V64): igralec, ki mu pravega datuma ne
   poznamo, dobi ocenjen datum (1. 1. letnika), da ga starostni pas uvrsti v
   kategorijo, v kateri igra. Test cuva tri stvari: znacka se prenese ob vnosu
   in urejanju, ugiban datum ne vpliva na primerjavo podobnih igralcev, in
   starostni pas se iz njega vseeno izpelje. (Samodejno povezavo racuna varuje
   RegistracijaTest, združitev pa IgralciZdruzitevTest.) */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.IgralecDto;
import si.turnirko.dto.IgralecJavniDto;
import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.PodobenIgralecDto;
import si.turnirko.dto.PodobniIgralciVnos;
import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StarostniPas;

class DatumRojstvaUgibanTest extends IntegracijskiTest {

    @Autowired private IgralciStoritev igralciStoritev;

    private static IgralecVnos vnos(String ime, String priimek, LocalDate datum, Boolean ugiban) {
        return new IgralecVnos(ime, priimek, Spol.MOSKI, datum, null, null, null, null, "SLO",
                null, null, null, null, ugiban);
    }

    @Test
    void znackaSePrenesePriVnosuInPriUrejanju() {
        IgralecJavniDto javni = igralciStoritev.ustvari(
                vnos("Ugiban", "Prvi", LocalDate.of(2016, 1, 1), true));

        IgralecDto zapis = igralciStoritev.najdiPodrobno(javni.id());
        assertTrue(zapis.datumRojstvaUgiban(), "znacka se zapise ob vnosu");
        assertEquals(LocalDate.of(2016, 1, 1), zapis.datumRojstva());

        // urejanje brez znacke jo odstrani: admin je datum popravil na pravega
        igralciStoritev.posodobi(javni.id(), vnos("Ugiban", "Prvi", LocalDate.of(2016, 6, 15), null));
        IgralecDto popravljen = igralciStoritev.najdiPodrobno(javni.id());
        assertFalse(popravljen.datumRojstvaUgiban(), "znacke ni vec, ko je datum pravi");
        assertEquals(LocalDate.of(2016, 6, 15), popravljen.datumRojstva());
    }

    @Test
    void vnosBrezZnackeJeTocenDatum() {
        IgralecJavniDto javni = igralciStoritev.ustvari(
                vnos("Tocen", "Datum", LocalDate.of(1990, 5, 20), null));

        assertFalse(igralciStoritev.najdiPodrobno(javni.id()).datumRojstvaUgiban());
    }

    @Test
    void starostniPasSeIzpeljeIzUgibanegaDatuma() {
        // 1. 1. 2016 je 10 let na dan 31. 12. 2026: kategorija U11
        IgralecJavniDto javni = igralciStoritev.ustvari(
                vnos("Pas", "Ugiban", LocalDate.of(2016, 1, 1), true));

        assertEquals(StarostniPas.izpelji(LocalDate.of(2016, 1, 1), LocalDate.now()), javni.starostniPas());
    }

    /* Letniki, ki jih za igralce brez znanega datuma uporablja uvoz mladinskih
       turnirjev (1. MRNTZ, nov. 2026): 1. 1. letnika, ki je ZGORNJA meja
       kategorije, da igralec spada v kategorijo, v kateri igra (sezona 2026/27). */
    @Test
    void letnikZgornjeMejeKategorijeUvrstiVKategorijo() {
        LocalDate danes = LocalDate.of(2026, 11, 10);

        assertEquals(StarostniPas.U11, StarostniPas.izpelji(LocalDate.of(2016, 1, 1), danes));
        assertEquals(StarostniPas.U13, StarostniPas.izpelji(LocalDate.of(2014, 1, 1), danes));
        assertEquals(StarostniPas.U15, StarostniPas.izpelji(LocalDate.of(2012, 1, 1), danes));
        assertEquals(StarostniPas.U19, StarostniPas.izpelji(LocalDate.of(2008, 1, 1), danes));
    }

    @Test
    void ugibanDatumObstojecegaNiPrimerjaveDatuma() {
        igralciStoritev.ustvari(vnos("Primerjalni", "Ugibanovic", LocalDate.of(2014, 1, 1), true));
        igralciStoritev.ustvari(vnos("Primerjalni", "Tocnovic", LocalDate.of(2014, 1, 1), null));

        List<PodobenIgralecDto> ugibanZadetek = igralciStoritev.najdiPodobnePodrobno(
                new PodobniIgralciVnos("Primerjalni", "Ugibanovic", Spol.MOSKI, LocalDate.of(2014, 1, 1)));
        List<PodobenIgralecDto> tocenZadetek = igralciStoritev.najdiPodobnePodrobno(
                new PodobniIgralciVnos("Primerjalni", "Tocnovic", Spol.MOSKI, LocalDate.of(2014, 1, 1)));

        assertEquals(1, ugibanZadetek.size());
        assertNull(ugibanZadetek.get(0).datum(),
                "enak 1. 1. pri ugibanem datumu ni dokaz za isto osebo");
        assertEquals(1, tocenZadetek.size());
        assertEquals(PrimerjavaDatuma.ENAK, tocenZadetek.get(0).datum(),
                "pri tocnem datumu primerjava ostane");
    }
}
