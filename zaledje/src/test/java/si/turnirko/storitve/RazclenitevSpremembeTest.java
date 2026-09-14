/* Testi razclenitve spremembe: vrstica dnevnika mora znati razloziti svojo
   stevilko, in sicer s TISTIMI parametri, ki so jo naredili - ne s poznejsimi.

   Zakaj je to svoj test: razclenitev je edini del sistema, ki obljublja
   igralcu, da se izpisani obrazec sesteje. Ce se K, margina, teza in
   pricakovano ne ujemajo z zapisano spremembo, obljuba pade. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

class RazclenitevSpremembeTest extends IntegracijskiTest {

    @Autowired private RatingZgodovinaRepozitorij zgodovinaRepozitorij;
    @Autowired private ZrebStoritev zreb;
    @Autowired private RatingStoritev ratingStoritev;

    private Tekma prvaPripravljena(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    private List<RatingZgodovina> zapisi(Igralec igralec) {
        return zgodovinaRepozitorij.najdiZaIgralca(igralec.getId(), RatingStanje.SISTEM_TURNIRKO);
    }

    private Dogodek zZrebom(int igralcev, RavenTekmovanja raven) {
        Dogodek dogodek = pripraviDogodek(igralcev);
        Turnir turnir = dogodek.getTurnir();
        turnir.setRaven(raven);
        turnirRepozitorij.save(turnir);
        zreb.izvediZreb(dogodek.getId());
        return dogodek;
    }

    /* Osrednja obljuba: zapisane sestavine se zmnozijo natanko v zapisano
       spremembo. Zaokrozevanje je na sodo (Math.rint), zato primerjamo tako. */
    @Test
    void sestavineSeZmnozijoVZapisanoSpremembo() {
        Dogodek dogodek = zZrebom(4, RavenTekmovanja.URADNO);
        Tekma tekma = prvaPripravljena(dogodek.getId());
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));

        RatingZgodovina zapis = zapisi(zmagovalec).get(0);
        assertNotNull(zapis.getK(), "korak mora imeti sestavine");
        int izracunano = (int) Math.rint(zapis.getK() * zapis.getMargina() * zapis.getTeza()
                * (zapis.getTocke() - zapis.getPricakovano()));
        assertEquals(zapis.getSprememba(), izracunano,
                "obrazec K x margina x teza x (tocke - pricakovano) se mora sesteti");
    }

    /* Teza tekmovanja je del razclenitve in ne skrita: na klubskem turnirju
       mora pisati 0,75. */
    @Test
    void tezaTekmovanjaJeZapisana() {
        Dogodek dogodek = zZrebom(4, RavenTekmovanja.KLUBSKO);
        Tekma tekma = prvaPripravljena(dogodek.getId());
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));

        RatingZgodovina zapis = zapisi(zmagovalec).get(0);
        assertEquals(0.75, zapis.getTeza());
        assertEquals(56, zapis.getK(), "novinec ima K 56");
        assertEquals(1, zapis.getTocke(), "zmaga je ena tocka");
        assertEquals(0.5, zapis.getPricakovano(), 0.0001, "med enakima je pricakovanje pol");
    }

    /* Nasprotnikova vrstica ima SVOJ K in SVOJE pricakovanje (1 minus prvega),
       margina in teza pa sta skupni. */
    @Test
    void vsakaStranImaSvojKInSvojePricakovanje() {
        Dogodek dogodek = zZrebom(4, RavenTekmovanja.URADNO);
        Tekma tekma = prvaPripravljena(dogodek.getId());
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();
        Igralec porazenec = tekma.getPrijava2().getIgralec();
        nastaviRatinge(dogodek, 1600);

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));

        RatingZgodovina zmaga = zapisi(zmagovalec).stream()
                .filter(z -> z.getTekma() != null).findFirst().orElseThrow();
        RatingZgodovina poraz = zapisi(porazenec).stream()
                .filter(z -> z.getTekma() != null).findFirst().orElseThrow();

        assertEquals(zmaga.getMargina(), poraz.getMargina(), "margina je last tekme");
        assertEquals(zmaga.getTeza(), poraz.getTeza(), "teza je last tekme");
        assertEquals(1.0, zmaga.getPricakovano() + poraz.getPricakovano(), 0.0001,
                "pricakovanji se sestejeta v 1");
        assertEquals(1, zmaga.getTocke());
        assertEquals(0, poraz.getTocke());
    }

    /* Odigrana tekma z izidom 0 : 0 je tekma, pri kateri vir (uvoz Stupe) pozna
       samo zmagovalca. Rating je ne sme brati kot gladko zmago: margina je 1,
       enako kot pri predaji. Primerjava je ista tekma, zapisana 3 : 0, kjer
       margina 1 ni (med enakima igralcema je gladka zmaga presenecenje). */
    @Test
    void tekmaSamoZZmagovalcemNimaMargine() {
        Dogodek dogodek = zZrebom(4, RavenTekmovanja.URADNO);
        List<Tekma> pripravljene = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
        Tekma brezNizov = pripravljene.get(0);
        Tekma gladka = pripravljene.get(1);

        // tako tekmo zapise uvoz - vnos v aplikaciji je ne dovoli
        brezNizov.setDobljeniNizi1(0);
        brezNizov.setDobljeniNizi2(0);
        brezNizov.setZmagovalec(brezNizov.getPrijava1());
        brezNizov.setIzidTip(si.turnirko.modeli.IzidTekme.IGRANO);
        brezNizov.setStatus(StatusTekme.KONCANA);
        tekmaRepozitorij.save(brezNizov);
        ratingStoritev.obracunajZaTurnirsko(brezNizov);
        tekmaStoritev.vnesiRezultat(gladka.getId(), new VnosRezultata(null, 3, 0, null, null));

        RatingZgodovina zapisBrezNizov = zapisi(brezNizov.getPrijava1().getIgralec()).get(0);
        RatingZgodovina zapisGladke = zapisi(gladka.getPrijava1().getIgralec()).get(0);
        assertEquals(1.0, zapisBrezNizov.getMargina(), 0.0001, "brez nizov ni margine");
        assertTrue(zapisGladke.getMargina() != 1.0, "gladka zmaga margino ima");
        assertTrue(zapisBrezNizov.getSprememba() > 0, "zmaga vseeno steje");
    }

    /* Uvrstitev novinca ne nastane po obrazcu koraka, zato sestavin NIMA -
       zapisane bi lagale. Vmesnik iz tega izpelje svojo razlago. */
    @Test
    void uvrstitevNovincaNimaSestavin() {
        Dogodek dogodek = zZrebom(4, RavenTekmovanja.URADNO);
        Tekma polfinale = prvaPripravljena(dogodek.getId());
        Igralec zmagovalec = polfinale.getPrijava1().getIgralec();

        tekmaStoritev.vnesiRezultat(polfinale.getId(), new VnosRezultata(null, 3, 0, null, null));
        Tekma drugiPolfinale = prvaPripravljena(dogodek.getId());
        tekmaStoritev.vnesiRezultat(drugiPolfinale.getId(),
                new VnosRezultata(null, 3, 0, null, null));
        Tekma finale = prvaPripravljena(dogodek.getId());
        boolean prvi = finale.getPrijava1().getIgralec().getId().equals(zmagovalec.getId());
        tekmaStoritev.vnesiRezultat(finale.getId(), prvi
                ? new VnosRezultata(null, 3, 0, null, null)
                : new VnosRezultata(null, 0, 3, null, null));

        List<RatingZgodovina> vsi = zapisi(zmagovalec);
        RatingZgodovina prvaTekma = vsi.get(0);
        RatingZgodovina druga = vsi.get(1);

        assertNotNull(prvaTekma.getK(), "prva tekma prvega dne je se navaden korak");
        assertNull(druga.getK(), "druga tekma prvega dne je uvrstitev in nima obrazca");
        assertTrue(druga.getSprememba() != 0, "uvrstitev vseeno premakne rating");
    }
}
