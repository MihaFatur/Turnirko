/* Testi postavitvenega (zacetnega) ratinga za novince: admin sme igralcu
   dolociti vstopni klubski ELO, dokler ta ni odigral nobene ratinske tekme. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;

class PostavitveniRatingTest extends IntegracijskiTest {

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private IgralciStoritev igralciStoritev;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    @Test
    void adminPostaviZacetniRatingInSeZabelezi() {
        Igralec igralec = noviIgralec("Nova", "Igralka");

        ratingStoritev.nastaviZacetniRating(igralec, 1400);

        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElseThrow();
        assertEquals(1400, stanje.getVrednost());
        assertEquals(0, stanje.getStTekem(), "postavitev ne steje kot tekma");

        // v dnevniku je zapis brez tekme (postavitveni), z novo vrednostjo
        List<RatingZgodovina> dnevnik = ratingZgodovinaRepozitorij
                .najdiZaIgralca(igralec.getId(), RatingStanje.SISTEM_KLUBSKI_ELO);
        assertEquals(1, dnevnik.size());
        assertEquals(1400, dnevnik.get(0).getNovaVrednost());
        assertNull(dnevnik.get(0).getTekma(), "postavitveni zapis ni vezan na tekmo");
        assertNull(dnevnik.get(0).getTekmaSrecanja());
    }

    @Test
    void zacetniRatingIzvenMejaJeZavrnjen() {
        Igralec igralec = noviIgralec("Nova", "Igralka");
        assertThrows(NeveljavenVnosIzjema.class, () -> ratingStoritev.nastaviZacetniRating(igralec, 50));
        assertThrows(NeveljavenVnosIzjema.class, () -> ratingStoritev.nastaviZacetniRating(igralec, 5000));
    }

    @Test
    void zacetnegaRatingaNiMogocePostavitiPoOdigraniTekmi() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        Long idIgralca = tekma.getPrijava1().getIgralec().getId();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new si.turnirko.dto.VnosRezultata(null, 3, 0, null, null));

        Igralec igralec = igralecRepozitorij.findById(idIgralca).orElseThrow();
        assertThrows(DomenskaIzjema.class, () -> ratingStoritev.nastaviZacetniRating(igralec, 1500),
                "po odigrani tekmi rating dolocajo samo rezultati");
    }

    @Test
    void postavljeniRatingVstopiKotVstopnaMoc() {
        // Postavljen na 1400, nato prek storitve preberemo, da drzi in da je
        // steviloTekem 0 (torej se je mogoce postaviti / je se provizoricen).
        Igralec igralec = noviIgralec("Mocni", "Novinec");
        igralciStoritev.nastaviZacetniRating(igralec.getId(), 1400);

        var dto = igralciStoritev.najdi(igralec.getId());
        assertEquals(1400, dto.rating());
        assertEquals(0, dto.steviloTekem());
        assertTrue(dto.rating() > EloStoritev.ZACETNI_RATING);
    }
}
