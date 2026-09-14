/* Ponovni preracun Turnirko ratinga.

   Zakaj je to pomembno: rating je izpeljana kolicina in edini vir resnice so
   izidi. Ce preracun iz istih izidov ne da istih vrednosti, potem dnevnik ni
   rekonstruirljiv in noben popravek rezultata ni mogoc. Zato je osrednji test
   ponovljivost: preracun cez ista tekmovanja mora dati natanko iste ratinge. */
package si.turnirko.storitve;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;

class PreracunRatingaTest extends IntegracijskiTest {

    @Autowired private PreracunRatingaStoritev preracunStoritev;

    private static final LocalDate DAN_TURNIRJA = LocalDate.of(2026, 3, 14);

    /* Odigra cel izlocilni turnir stirih igralcev in vrne dogodek. */
    private Dogodek odigranTurnir() {
        Dogodek dogodek = pripraviDogodek(4);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(DAN_TURNIRJA);
        turnirRepozitorij.save(turnir);

        zrebStoritev.izvediZreb(dogodek.getId());
        // polfinala, nato finale - vsakic prva pripravljena tekma
        for (int i = 0; i < 3; i++) {
            Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst().orElseThrow();
            tekmaStoritev.vnesiRezultat(tekma.getId(),
                    new VnosRezultata(null, 3, i % 3, null, null));
        }
        return dogodek;
    }

    /* Trenutni ratingi vseh igralcev: idIgralca -> [vrednost, st. tekem].
       Seznam in ne tabela, ker se stanja primerjajo z equals. */
    private Map<Long, List<Integer>> stanja() {
        Map<Long, List<Integer>> po = new HashMap<>();
        for (RatingStanje s : ratingStanjeRepozitorij
                .findBySistem(RatingStanje.SISTEM_TURNIRKO)) {
            po.put(s.getIgralec().getId(), List.of(s.getVrednost(), s.getStTekem()));
        }
        return po;
    }

    @Test
    void preracunOdZacetkaDaNatankoIsteRatinge() {
        odigranTurnir();
        Map<Long, List<Integer>> pred = stanja();
        long zapisovPred = ratingZgodovinaRepozitorij.count();
        assertFalse(pred.isEmpty(), "turnir mora dati ratinge");

        PreracunRatingaStoritev.Porocilo porocilo = preracunStoritev.preracunajOd(null);

        assertEquals(3, porocilo.obracunanihTekem(), "turnir stirih ima tri ratinske tekme");
        assertEquals(zapisovPred, ratingZgodovinaRepozitorij.count(),
                "preracun ne sme podvojiti ali izgubiti zapisov v dnevniku");
        assertEquals(pred, stanja(), "isti izidi morajo dati iste ratinge");
    }

    @Test
    void preracunPoDatumuTurnirjaPustiRatingeNedotaknjene() {
        odigranTurnir();
        Map<Long, List<Integer>> pred = stanja();

        PreracunRatingaStoritev.Porocilo porocilo =
                preracunStoritev.preracunajOd(DAN_TURNIRJA.plusDays(1));

        assertEquals(0, porocilo.obracunanihTekem(), "po turnirju ni vec ratinskih tekem");
        assertEquals(pred, stanja());
    }

    @Test
    void preracunOdDnevaTurnirjaTurnirVkljuci() {
        odigranTurnir();
        Map<Long, List<Integer>> pred = stanja();

        PreracunRatingaStoritev.Porocilo porocilo = preracunStoritev.preracunajOd(DAN_TURNIRJA);

        assertEquals(3, porocilo.obracunanihTekem(), "meja je vkljucujoca");
        assertEquals(pred, stanja());
    }

    @Test
    void dnevnikNosiDatumTekmeInNeDatumaVnosa() {
        odigranTurnir();
        List<si.turnirko.modeli.RatingZgodovina> zapisi = ratingZgodovinaRepozitorij.findAll();
        assertFalse(zapisi.isEmpty());
        for (si.turnirko.modeli.RatingZgodovina z : zapisi) {
            assertEquals(DAN_TURNIRJA.atStartOfDay(), z.getVeljaOb(),
                    "zapis mora veljati na dan tekme, ne na dan vnosa");
        }
    }

    @Test
    void casovnaVrstaJeUrejenaPoDatumuInFazi() {
        odigranTurnir();
        List<VrstaRatinskeTekme> vrsta = preracunStoritev.vsaVrsta();
        assertEquals(3, vrsta.size());
        for (int i = 1; i < vrsta.size(); i++) {
            assertTrue(VrstaRatinskeTekme.VRSTNI_RED.compare(vrsta.get(i - 1), vrsta.get(i)) <= 0,
                    "vrsta mora biti urejena");
        }
        // skupinskega dela ni, zato so vse tekme izlocilne in si sledijo po kolih
        assertTrue(vrsta.get(0).zaporedje() < vrsta.get(2).zaporedje());
    }
}
