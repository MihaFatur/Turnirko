/* Prijava, odjava in ponovna prijava igralca na dogodek.

   Kljucno domensko pravilo: odjava je le mehak izbris (status ODJAVLJEN), zato
   se mora igralca dati ponovno prijaviti - obstojeci zapis se aktivira, ne
   podvaja (baza ima UNIQUE dogodek+igralec). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;

class PrijaveTest extends IntegracijskiTest {

    /* Prijava -> odjava -> ponovna prijava: igralec spet igra, zapis je en sam. */
    @Test
    void ponovnaPrijavaPoOdjaviAktivirajObstojeciZapis() {
        Dogodek dogodek = pripraviDogodek(0);
        Igralec igralec = noviIgralec("Ana", "Novak");

        Prijava prva = turnirjiStoritev.prijaviIgralce(
                dogodek.getId(), List.of(igralec.getId())).get(0);
        turnirjiStoritev.odjavi(prva.getId());

        List<Prijava> ponovne = turnirjiStoritev.prijaviIgralce(
                dogodek.getId(), List.of(igralec.getId()));

        assertEquals(1, ponovne.size());
        assertEquals(Prijava.StatusPrijave.PRIJAVLJEN, ponovne.get(0).getStatus());
        // Isti zapis (UNIQUE dogodek+igralec) - odjava ni pustila mrtvega dvojnika.
        assertEquals(prva.getId(), ponovne.get(0).getId());
        assertEquals(1, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).size());
    }

    /* Ponovna prijava ze prijavljenega (aktivnega) igralca ostane napaka. */
    @Test
    void ponovnaPrijavaZePrijavljenegaJeNapaka() {
        Dogodek dogodek = pripraviDogodek(0);
        Igralec igralec = noviIgralec("Bojan", "Kovac");
        turnirjiStoritev.prijaviIgralce(dogodek.getId(), List.of(igralec.getId()));

        assertThrows(DomenskaIzjema.class, () ->
                turnirjiStoritev.prijaviIgralce(dogodek.getId(), List.of(igralec.getId())));
    }
}
