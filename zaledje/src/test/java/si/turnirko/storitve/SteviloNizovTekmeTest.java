/* Stevilo nizov posamezne tekme - finale "prvi do 3" na dogodku "prvi do 2".

   Zakaj to obstaja: dogodek ob nastanku doloci privzeto stevilo nizov, odlocitev
   za drugo pa pride sele, ko je tekma na mizi. Tekma zato nosi svoje stevilo in
   ga organizator lahko prepise ob vnosu rezultata.

   Kar mora test cuvati:
   1. prepisano stevilo velja za preverbo izida (pri "prvi do 3" je 2 : 1 napaka,
      3 : 1 pa veljaven izid) in ga tekma nosi naprej,
   2. brez prepisa ostane stevilo dogodka,
   3. dovoljena so samo 3, 5 in 7 (enako kot pri dogodku),
   4. popravek odigrane tekme prepisa ne sprejme - izid se ne sme preverjati
      proti novi meji,
   5. JSON nese stevilo nizov (kontroler dobi vnos prek rekordnega konstruktorja). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class SteviloNizovTekmeTest extends IntegracijskiTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    /* Dogodek "prvi do 2" (3 nizi) z dvema igralcema; ena tekma, pripravljena za vnos. */
    private Tekma pripraviTekmo() {
        Dogodek dogodek = pripraviDogodek(2);
        dogodek.setPrivzetoSteviloNizov(3);
        zrebStoritev.izvediZreb(dogodek.getId());
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    @Test
    void prepisanoStevilo_zaZmagoVeljaZaNovoMejo() {
        Tekma tekma = pripraviTekmo();

        // "prvi do 3": dva zmagana niza ne zadostujeta
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(
                tekma.getId(), new VnosRezultata(null, 2, 1, null, null, 5)));
    }

    @Test
    void prepisanoStevilo_ostaneNaTekmi() {
        Tekma tekma = pripraviTekmo();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null, 5));

        Tekma koncana = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(5, koncana.getSteviloNizov(), "tekma nosi prepisano stevilo");
        assertEquals(3, koncana.getDobljeniNizi1());
        assertEquals(1, koncana.getDobljeniNizi2());
    }

    @Test
    void brezPrepisaOstaneStevilaDogodka() {
        Tekma tekma = pripraviTekmo();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 2, 1, null, null));

        assertEquals(3, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getSteviloNizov());
    }

    @Test
    void dovoljenaSoSamoTriPetSedem() {
        Tekma tekma = pripraviTekmo();

        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(
                tekma.getId(), new VnosRezultata(null, 2, 0, null, null, 4)));
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(
                tekma.getId(), new VnosRezultata(null, 2, 0, null, null, 9)));
    }

    @Test
    void popravekOdigraneTekmeNeSprejmePrepisa() {
        Tekma tekma = pripraviTekmo();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 2, 1, null, null));

        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.popraviRezultat(
                tekma.getId(), new VnosRezultata(null, 2, 1, null, null, 5)));
    }

    @Test
    void jsonNeseSteviloNizov() throws Exception {
        VnosRezultata s = objectMapper.readValue(
                "{\"dobljeniNizi1\":3,\"dobljeniNizi2\":1,\"steviloNizov\":5}", VnosRezultata.class);
        assertEquals(Integer.valueOf(5), s.steviloNizov());

        VnosRezultata brez = objectMapper.readValue(
                "{\"dobljeniNizi1\":3,\"dobljeniNizi2\":1}", VnosRezultata.class);
        assertNull(brez.steviloNizov());
    }
}
