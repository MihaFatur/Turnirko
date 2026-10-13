/* Popravek imena in pravil dogodka pred zrebom.

   Domenska pravila, ki jih ta test varuje:
   - dokler je dogodek v pripravi, se ime in pravila lahko popravijo (napacen vnos
     ob ustvarjanju je del vsakdana),
   - po zrebu se pravila zaklenejo, ker so iz njih nastale tekme,
   - vrste dogodka (posamicno, dvojice) ni mogoce spremeniti, ce so ze prijave,
   - kategorija ne sme postaviti ze prijavljenega igralca izven pravil,
   - popravek sistema ne pusti nastavitev prejsnjega sistema (skupine). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;

class DogodekUrediTest extends IntegracijskiTest {

    /* Ime in stevilo nizov se popravita, sistem pa se vrne nazaj: nastavitve skupin
       prejsnjega sistema ne smejo ostati. */
    @Test
    void popravekImenaInPravilPredZrebomVelja() {
        Dogodek dogodek = pripraviDogodek(4);
        Long id = dogodek.getId();

        turnirjiStoritev.urediDogodek(id, vnos("Clani odprto", SpolKategorija.MOSKI, 7,
                null, SistemTekmovanja.SKUPINE, 2, 2));
        Dogodek popravljen = dogodekRepozitorij.findById(id).orElseThrow();
        assertEquals("Clani odprto", popravljen.getIme());
        assertEquals(7, popravljen.getPrivzetoSteviloNizov());
        assertEquals(2, popravljen.getSteviloSkupin());

        turnirjiStoritev.urediDogodek(id, vnos("Clani odprto", SpolKategorija.MOSKI, 7,
                null, SistemTekmovanja.IZLOCILNI, null, null));
        assertEquals(SistemTekmovanja.IZLOCILNI, popravljen.getSistemTekmovanja());
        assertNull(popravljen.getSteviloSkupin());
        assertNull(popravljen.getVelikostSkupine());
    }

    /* Zreb iz pravil naredi tekme. Po njem popravek ni vec dovoljen, tudi ce bi bil
       sicer veljaven. */
    @Test
    void poZrebuSePravilaZaklenejo() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());

        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.urediDogodek(dogodek.getId(),
                vnos("Clani po zrebu", SpolKategorija.MOSKI, 7, null, SistemTekmovanja.IZLOCILNI,
                        null, null)));
        assertEquals("Clani posamicno", dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getIme());
    }

    /* Ko so na dogodku prijave, vrste (posamicno -> dvojice) ni mogoce spremeniti, ker
       prijava ni pretvorljiva v par brez organizatorjeve odlocitve. Brez prijav gre. */
    @Test
    void vrstaDogodkaSeSpremeniSamoBrezPrijav() {
        Dogodek dogodek = pripraviDogodek(0);
        Long id = dogodek.getId();

        turnirjiStoritev.urediDogodek(id, vnos("Clani dvojice", SpolKategorija.MOSKI, 5,
                Disciplina.DVOJICE, SistemTekmovanja.IZLOCILNI, null, null));
        assertEquals(Disciplina.DVOJICE, dogodekRepozitorij.findById(id).orElseThrow().getDisciplina());

        turnirjiStoritev.prijaviIgralce(id, List.of(noviIgralec("Ana", "Prva", Spol.MOSKI).getId()));
        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.urediDogodek(id,
                vnos("Clani dvojice", SpolKategorija.MOSKI, 5, Disciplina.POSAMICNO,
                        SistemTekmovanja.IZLOCILNI, null, null)));
    }

    /* Igralka je prijavljena na odprto kategorijo. Zozitev na moske je zato zavrnjena,
       ker bi bila prijava tiho neveljavna. */
    @Test
    void kategorijaNeSmeOdPrijavljenihIgralcevIzPravil() {
        Dogodek dogodek = pripraviDogodek(0);
        Long id = dogodek.getId();
        turnirjiStoritev.urediDogodek(id, vnos("Odprto", SpolKategorija.KDORKOLI, 5,
                null, SistemTekmovanja.IZLOCILNI, null, null));
        turnirjiStoritev.prijaviIgralce(id, List.of(noviIgralec("Ana", "Prva", Spol.ZENSKI).getId()));

        assertThrows(DomenskaIzjema.class, () -> turnirjiStoritev.urediDogodek(id,
                vnos("Odprto", SpolKategorija.MOSKI, 5, null, SistemTekmovanja.IZLOCILNI, null, null)));
    }

    private static DogodekVnos vnos(String ime, SpolKategorija spol, int nizi, Disciplina disciplina,
                                    SistemTekmovanja sistem, Integer steviloSkupin, Integer velikostSkupine) {
        return new DogodekVnos(ime, spol, null, nizi, null, null, disciplina, sistem,
                steviloSkupin, velikostSkupine);
    }
}
