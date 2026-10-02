/* Rekreativni vstop (V36): igralec, ki ga clovek ob vpisu oznaci kot
   rekreativca, zacne pri 800 namesto pri starostnem sidru.

   Zakaj: sidro je mediana registriranih igralcev NTZS (odrasel moski 1597).
   V Savinja ligi so novinci, ki so prvi vecer igrali samo proti novincem,
   obstali pri 1500-1700 - pred vecino ligasev NTZS. Test cuva tri stvari:
   800 je VSTOPNA vrednost, 800 je tudi izhodisce, proti kateremu vlece
   uvrstitev novinca, in oznaka, dodana pozneje, obvelja s preracunom. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.IgralecVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class RekreativniVstopTest extends IntegracijskiTest {

    @Autowired private IgralciStoritev igralciStoritev;
    @Autowired private PreracunRatingaStoritev preracunRatinga;

    private int rating(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    private void oznaci(Dogodek dogodek) {
        for (Prijava p : prijavePoVrsti(dogodek.getId())) {
            p.getIgralec().setRekreativniVstop(true);
            igralecRepozitorij.save(p.getIgralec());
        }
    }

    /* Odigra vse tekme izlocilne mreze; zmaga vedno prvi v paru. */
    private void odigrajMrezo(Dogodek dogodek) {
        zrebStoritev.izvediZreb(dogodek.getId());
        while (true) {
            Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst().orElse(null);
            if (tekma == null) {
                return;
            }
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null));
        }
    }

    @Test
    void rekreativecZacnePri800NeGledeNaStarost() {
        Igralec odrasel = noviIgralec("Odrasel", "Novinec");
        Igralec rekreativec = noviIgralec("Rekreativni", "Novinec");
        rekreativec.setRekreativniVstop(true);

        assertEquals(SidroStoritev.REKREATIVNI_ZACETEK,
                sidroStoritev.zacetniRating(rekreativec, LocalDate.now()));
        assertTrue(sidroStoritev.zacetniRating(odrasel, LocalDate.now()) > 1200,
                "brez oznake ostane starostno sidro odraslega");
    }

    /* Prva tekma je navaden korak - od 800 in ne od sidra. */
    @Test
    void rekreativecVstopiPri800() {
        Dogodek dogodek = pripraviDogodek(2);
        oznaci(dogodek);
        odigrajMrezo(dogodek);

        List<Igralec> igralca = prijavePoVrsti(dogodek.getId()).stream()
                .map(Prijava::getIgralec).toList();
        int prvi = rating(igralca.get(0));
        int drugi = rating(igralca.get(1));
        assertEquals(2 * SidroStoritev.REKREATIVNI_ZACETEK, prvi + drugi,
                "oba zacneta pri 800 z enakim K - vsota ostane 1600");
        assertTrue(Math.max(prvi, drugi) > SidroStoritev.REKREATIVNI_ZACETEK);
    }

    /* Prvi dan tece uvrstitev novinca: rating, ki najbolje razlozi izide, z
       dvema navideznima izenacenima tekmama proti IZHODISCU. Pri rekreativcu je
       izhodisce 800, zato ostane cel turnir novincev okoli 800 - brez oznake
       pa isti turnir pristane okoli 1500. */
    @Test
    void uvrstitevRekreativcaVleceProti800() {
        Dogodek rekreativci = pripraviDogodek(4);
        oznaci(rekreativci);
        odigrajMrezo(rekreativci);

        Dogodek odrasli = pripraviDogodek(4);
        odigrajMrezo(odrasli);

        for (Prijava p : prijavePoVrsti(rekreativci.getId())) {
            int r = rating(p.getIgralec());
            assertTrue(r > 500 && r < 1100, "rekreativec novinec ostane okoli 800, ima pa " + r);
        }
        for (Prijava p : prijavePoVrsti(odrasli.getId())) {
            int r = rating(p.getIgralec());
            assertTrue(r > 1200, "odrasel novinec brez oznake ostane okoli sidra, ima pa " + r);
        }
    }

    /* Tako se popravijo igralci, ki so ze igrali: admin v obrazcu doda oznako,
       nato preracun od dneva njihove prve tekme. Oznaka sama rating ne
       premakne (en preracun za vec oznacenih igralcev), preracun pa ga. */
    @Test
    void oznakaIzObrazcaObveljaSPreracunom() {
        Dogodek dogodek = pripraviDogodek(2);
        odigrajMrezo(dogodek);
        List<Igralec> igralca = prijavePoVrsti(dogodek.getId()).stream()
                .map(Prijava::getIgralec).toList();
        int predOznako = rating(igralca.get(0)) + rating(igralca.get(1));
        assertTrue(predOznako > 2 * 1200, "brez oznake zacneta pri sidru");

        for (Igralec ig : igralca) {
            igralciStoritev.posodobi(ig.getId(), new IgralecVnos(ig.getIme(), ig.getPriimek(),
                    ig.getSpol(), ig.getDatumRojstva(), null, null, null, null, "SLO", null,
                    null, null, true));
        }
        assertEquals(predOznako, rating(igralca.get(0)) + rating(igralca.get(1)),
                "oznaka sama rating ne spremeni");
        assertTrue(igralciStoritev.najdiPodrobno(igralca.get(0).getId()).rekreativniVstop());

        preracunRatinga.preracunajOd(null);

        assertEquals(2 * SidroStoritev.REKREATIVNI_ZACETEK,
                rating(igralca.get(0)) + rating(igralca.get(1)),
                "po preracunu zacneta pri 800");
    }
}
