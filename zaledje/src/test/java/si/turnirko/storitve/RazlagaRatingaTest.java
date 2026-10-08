/* Javna razlaga ratinga in njena preizkusa.

   Obljuba strani je ena: preizkus da natanko tisto, kar da obracun. Zato
   osrednji test isti prvi dan novinca najprej preizkusi in nato zares
   odigra - ce bi kdo spremenil uvrstitev novinca samo v obracunu, bi
   razlaga tiho lagala. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.IzracunTekmeDto;
import si.turnirko.dto.NapovedTekmeDto;
import si.turnirko.dto.PravilaRatingaDto;
import si.turnirko.dto.PrviDanNovincaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Tekma;

class RazlagaRatingaTest extends IntegracijskiTest {

    @Autowired private RazlagaRatingaStoritev razlaga;

    /* Novinec odigra krozni dogodek s tremi ustaljenimi igralci: zmaga proti
       najsibkejsemu, nato dva poraza. Prva tekma je korak, drugi dve
       uvrstitev iz vseh izidov dneva - preizkus mora po vsaki tekmi pokazati
       isto stevilko kot obracun. */
    @Test
    void prviDanPreizkusaJeTisti_kiGaObracunZaresDa() {
        Dogodek dogodek = pripraviDogodek(4, SistemTekmovanja.KROZNI);
        nastaviRatinge(dogodek, 1500, 1300, 1100); // cetrti je novinec brez ratinga
        zrebStoritev.izvediZreb(dogodek.getId());
        Igralec novinec = prijavePoVrsti(dogodek.getId()).get(3).getIgralec();
        int izhodisce = sidroZa(novinec);

        List<Tekma> tekmeNovinca = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> igra(t, novinec))
                .sorted(Comparator.comparingInt(Tekma::getKolo))
                .toList();
        assertEquals(3, tekmeNovinca.size());

        List<Integer> nasprotniki = new ArrayList<>();
        List<Boolean> zmage = new ArrayList<>();
        List<Integer> ratingiPo = new ArrayList<>();
        for (Tekma t : tekmeNovinca) {
            Igralec nasprotnik = nasprotnik(t, novinec);
            int ratingNasprotnika = stanje(nasprotnik);
            boolean zmaga = ratingNasprotnika == 1100;
            boolean prviZmaga = (t.getPrijava1().getIgralec().getId().equals(novinec.getId())) == zmaga;
            tekmaStoritev.vnesiRezultat(t.getId(),
                    new VnosRezultata(null, prviZmaga ? 3 : 1, prviZmaga ? 1 : 3, null, null));
            nasprotniki.add(ratingNasprotnika);
            zmage.add(zmaga);
            ratingiPo.add(stanje(novinec));
        }

        PrviDanNovincaDto preizkus = razlaga.prviDan(izhodisce, RavenTekmovanja.URADNO, nasprotniki, zmage);

        assertEquals(izhodisce, preizkus.izhodisce());
        for (int i = 0; i < ratingiPo.size(); i++) {
            assertEquals(ratingiPo.get(i), preizkus.koraki().get(i).rating(),
                    "po " + (i + 1) + ". tekmi se preizkus in obracun razideta");
        }
        assertFalse(preizkus.koraki().get(0).uvrstitev(), "prva tekma je navaden korak");
        assertTrue(preizkus.koraki().get(1).uvrstitev(), "od druge tekme naprej je uvrstitev");
        int prej = izhodisce;
        for (PrviDanNovincaDto.Korak k : preizkus.koraki()) {
            assertEquals(k.rating() - prej, k.sprememba());
            prej = k.rating();
        }
    }

    /* Kalkulator tekme je isti obrazec kot obracun: pri enakem K je vsota
       sprememb 0, zmaga proti mocnejsemu prinese vec, kot poraz vzame, in
       teza ravni spremembo skrajsa. */
    @Test
    void izracunTekmeSlediObrazcu() {
        IzracunTekmeDto i = razlaga.izracun(1200, 1400, 50, 50, false, false);

        assertEquals(24, i.pricakovanOdstotek(), "200 tock razlike je 24 % za sibkejsega");
        assertEquals(TurnirkoRatingStoritev.K_OSNOVNI, i.k());
        NapovedTekmeDto.Raven uradno = i.ravni().get(0);
        assertEquals(RavenTekmovanja.URADNO, uradno.raven());
        assertEquals(0, uradno.zmaga().sprememba() + uradno.zmaga().spremembaNasprotnika());
        assertTrue(uradno.zmaga().sprememba() > -uradno.poraz().sprememba());
        assertTrue(uradno.zmaga().sprememba() > i.ravni().get(1).zmaga().sprememba());
        assertTrue(i.ravni().get(1).zmaga().sprememba() > i.ravni().get(2).zmaga().sprememba());
    }

    @Test
    void novinecImaVecjiKOdUstaljenega() {
        IzracunTekmeDto i = razlaga.izracun(1200, 1200, 0, 100, false, true);

        assertEquals(TurnirkoRatingStoritev.K_OSNOVNI + TurnirkoRatingStoritev.K_PRIBITEK_NEUSTALJEN
                + TurnirkoRatingStoritev.K_PRIBITEK_NOVINEC, i.k());
        assertEquals(TurnirkoRatingStoritev.K_OSNOVNI + TurnirkoRatingStoritev.K_PRIBITEK_VRNITEV,
                i.kNasprotnika());
    }

    @Test
    void pravilaPovedoStevilkeObracuna() {
        PravilaRatingaDto p = razlaga.pravila();

        assertEquals(TurnirkoRatingStoritev.K_OSNOVNI, p.kOsnovni());
        assertEquals(List.of(new PravilaRatingaDto.Odbitek(6, 10), new PravilaRatingaDto.Odbitek(12, 25),
                new PravilaRatingaDto.Odbitek(24, 40)), p.odbitki());
        assertEquals(50, p.napovedi().get(0).odstotek());
        assertEquals(0.75, p.ravni().get(1).teza(), 0.0001);
        assertFalse(p.sidra().isEmpty(), "sidra pridejo iz tabele starostno_sidro");
    }

    @Test
    void preizkusZavrneNesmiselnoStevilko() {
        assertThrows(NeveljavenVnosIzjema.class, () -> razlaga.izracun(50, 1200, 10, 10, false, false));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> razlaga.prviDan(800, RavenTekmovanja.KLUBSKO, List.of(900, 1000), List.of(true)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> razlaga.prviDan(800, RavenTekmovanja.NE_STEJE, List.of(900), List.of(true)));
    }

    private static boolean igra(Tekma t, Igralec igralec) {
        return t.getPrijava1() != null && t.getPrijava2() != null
                && (t.getPrijava1().getIgralec().getId().equals(igralec.getId())
                    || t.getPrijava2().getIgralec().getId().equals(igralec.getId()));
    }

    private static Igralec nasprotnik(Tekma t, Igralec igralec) {
        return t.getPrijava1().getIgralec().getId().equals(igralec.getId())
                ? t.getPrijava2().getIgralec()
                : t.getPrijava1().getIgralec();
    }

    private int stanje(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .map(RatingStanje::getVrednost)
                .orElseGet(() -> sidroZa(igralec));
    }
}
