/* Stran turnirja in vrstica v seznamu turnirjev morata reci isto.

   Stevce in besedni potek (faza, zmagovalec, zadnji izid) enega turnirja je
   stran turnirja do nov. 2026 dobila tako, da je sestela VSE turnirje in
   obdrzala eno vrstico (najdba C1 pregleda kakovosti). Zdaj jih prebere s
   poizvedbami, omejenimi na ta turnir. Test drzi, da ozja pot da natanko to,
   kar da pot cez vse turnirje - v vsakem stanju turnirja in tudi, kadar je v
   bazi vec turnirjev hkrati (filter ne sme zajeti soseda). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;

class PovzetkiEnegaTurnirjaTest extends IntegracijskiTest {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    @Autowired private PovzetkiStoritev povzetki;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    @Test
    void ozjaPotDaIstoKotPotCezVseTurnirje() {
        // v teku: mreza osmih, tri tekme odigrane
        Dogodek vTeku = pripraviDogodek(8);
        zrebStoritev.izvediZreb(vTeku.getId());
        odigraj(vTeku.getId(), 3);

        // zakljucen: mreza stirih, odigrana do konca
        Dogodek koncan = pripraviDogodek(4);
        zrebStoritev.izvediZreb(koncan.getId());
        odigraj(koncan.getId(), Integer.MAX_VALUE);

        // v pripravi: prijave brez zreba, ena odjavljena
        Dogodek priprava = pripraviDogodek(5);
        turnirjiStoritev.odjavi(prijavePoVrsti(priprava.getId()).get(0).getId());

        // zreb pred datumom turnirja: gledalcu se "V pripravi"
        Dogodek prihodnji = pripraviDogodek(4);
        prihodnji.getTurnir().setDatumZacetka(LocalDate.now(SLOVENIJA).plusDays(7));
        zrebStoritev.izvediZreb(prihodnji.getId());

        // dva dogodka v enem turnirju, oba v teku z ENAKIM stevilom neodigranih tekem
        Dogodek prvi = pripraviDogodek(4);
        Dogodek drugi = dodajDogodek(prvi.getTurnir(), 4);
        zrebStoritev.izvediZreb(prvi.getId());
        zrebStoritev.izvediZreb(drugi.getId());

        // brez dogodkov
        Turnir prazen = new Turnir();
        prazen.setIme("Prazen turnir");
        prazen.setDatumZacetka(LocalDate.now());
        turnirRepozitorij.save(prazen);

        List<Long> turnirji = new ArrayList<>(List.of(
                vTeku.getTurnir().getId(), koncan.getTurnir().getId(), priprava.getTurnir().getId(),
                prihodnji.getTurnir().getId(), prvi.getTurnir().getId(), prazen.getId()));

        Map<Long, TurnirDto.Stevci> vsiStevci = povzetki.zaVseTurnirje();
        Map<Long, TurnirDto.Potek> vsiPoteki = povzetki.potekiVsehTurnirjev();
        for (Long id : turnirji) {
            assertEquals(vsiStevci.getOrDefault(id, TurnirDto.Stevci.PRAZNI), povzetki.zaTurnir(id),
                    "stevci turnirja " + id);
            assertEquals(vsiPoteki.getOrDefault(id, TurnirDto.Potek.PRAZEN), povzetki.potekTurnirja(id),
                    "potek turnirja " + id);
        }

        /* Primerjava dveh enakih praznin ne bi dokazala nicesar - zato se
           stevilke, ki jih je mogoce prebrati iz priprave. */
        TurnirDto.Stevci stevciVTeku = povzetki.zaTurnir(vTeku.getTurnir().getId());
        assertEquals(7, stevciVTeku.vsehTekem());
        assertEquals(3, stevciVTeku.odigranihTekem());
        assertEquals(8, stevciVTeku.prijavljenihSkupaj());
        assertEquals(1, stevciVTeku.dogodkovVTeku());
        TurnirDto.Potek potekVTeku = povzetki.potekTurnirja(vTeku.getTurnir().getId());
        assertEquals("četrtfinale", potekVTeku.faza());
        assertNotNull(potekVTeku.zadnjiIzid());
        assertNull(potekVTeku.zmagovalec());

        TurnirDto.Potek potekKoncanega = povzetki.potekTurnirja(koncan.getTurnir().getId());
        assertNotNull(potekKoncanega.zmagovalec());
        assertNull(potekKoncanega.faza());

        TurnirDto.Stevci stevciPriprave = povzetki.zaTurnir(priprava.getTurnir().getId());
        assertEquals(4, stevciPriprave.prijavljenihSkupaj(), "odjavljeni ne steje");
        assertEquals(0, stevciPriprave.vsehTekem());
        assertEquals(1, stevciPriprave.dogodkovVPripravi());

        TurnirDto.Stevci stevciPrihodnjega = povzetki.zaTurnir(prihodnji.getTurnir().getId());
        assertEquals(0, stevciPrihodnjega.dogodkovVTeku());
        assertEquals(1, stevciPrihodnjega.dogodkovVPripravi());
        assertNull(povzetki.potekTurnirja(prihodnji.getTurnir().getId()).faza());

        assertEquals(2, povzetki.zaTurnir(prvi.getTurnir().getId()).steviloDogodkov());
        assertEquals(TurnirDto.Stevci.PRAZNI, povzetki.zaTurnir(prazen.getId()));
        assertEquals(TurnirDto.Potek.PRAZEN, povzetki.potekTurnirja(prazen.getId()));
    }

    /* Odigra najvec toliko pripravljenih tekem dogodka; zmaga prvi igralec. */
    private void odigraj(Long idDogodka, int najvec) {
        for (int i = 0; i < najvec; i++) {
            Optional<Tekma> tekma = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst();
            if (tekma.isEmpty()) {
                return;
            }
            tekmaStoritev.vnesiRezultat(tekma.get().getId(), new VnosRezultata(null, 3, 0, null, null));
        }
    }

    private Dogodek dodajDogodek(Turnir turnir, int steviloIgralcev) {
        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Drugi dogodek");
        dogodek.setSpolKategorija(SpolKategorija.MOSKI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(SistemTekmovanja.IZLOCILNI);
        dogodekRepozitorij.save(dogodek);
        for (int i = 1; i <= steviloIgralcev; i++) {
            prijavaRepozitorij.save(new Prijava(dogodek, noviIgralec("Drugi" + i, "Dogodkar" + i)));
        }
        return dogodek;
    }
}
