/* Testi zunanje uvrstitve: rating, prepisan z zunanje lestvice (ITTF, NTZS)
   za redkega gosta, ki pri nas odigra premalo tekem.

   Zakaj je to svoj test in ne razsiritev PostavitveniRatingTest: zunanja
   uvrstitev se od postavitve locuje po treh stvareh, ki jih je treba vsako
   posebej drzati - dovoljena je tudi PO odigranih tekmah, zahteva VIR in
   POJASNILO, in v casovni vrsti velja OB SVOJEM CASU (postavitev je
   izhodisce). Zadnje je najlazje tiho pokvariti: ce jo preracun odigra na
   napacnem mestu, je konec vsake ponovljivosti. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RazlogSpremembe;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;

class ZunanjaUvrstitevTest extends IntegracijskiTest {

    /* Turnir je nedaven namenoma: pri turnirju izpred dveh let bi zunanji
       uvrstitvi najprej zapadli odbitki za neaktivnost in pricakovane
       stevilke bi merile dve stvari hkrati. Prepletanje z odbitki drzi svoj
       test (zunanjaUvrstitevGaVrneNaJavnoLestvico). */
    private static final LocalDate DAN_TURNIRJA = LocalDate.now().minusMonths(1);
    private static final String VIR = "ITTF svetovna lestvica, september 2026";
    private static final String POJASNILO = "Igra skoraj samo mednarodno, pri nas dve tekmi na leto.";

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private PreracunRatingaStoritev preracunStoritev;
    @Autowired private NeaktivnostStoritev neaktivnostStoritev;
    @Autowired private RatingZgodovinaRepozitorij zgodovinaRepozitorij;

    /* Odigra cel izlocilni turnir stirih igralcev in vrne zmagovalca finala. */
    private Igralec zmagovalecTurnirja() {
        Dogodek dogodek = pripraviDogodek(4);
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(DAN_TURNIRJA);
        turnirRepozitorij.save(turnir);
        zrebStoritev.izvediZreb(dogodek.getId());

        Tekma zadnja = null;
        for (int i = 0; i < 3; i++) {
            zadnja = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst().orElseThrow();
            tekmaStoritev.vnesiRezultat(zadnja.getId(), new VnosRezultata(null, 3, 0, null, null));
        }
        return zadnja.getPrijava1().getIgralec();
    }

    private RatingStanje stanje(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow();
    }

    private List<RatingZgodovina> dnevnik(Igralec igralec) {
        return zgodovinaRepozitorij.najdiZaIgralca(igralec.getId(), RatingStanje.SISTEM_TURNIRKO);
    }

    private RatingZgodovina zapisUvrstitve(Igralec igralec) {
        return dnevnik(igralec).stream()
                .filter(z -> z.getRazlog() == RazlogSpremembe.ZUNANJA_UVRSTITEV)
                .findFirst().orElseThrow();
    }

    /* Osrednja razlika od postavitve: igralec ima tekme in kljub temu dobi
       stevilko od zunaj. Prav zaradi teh igralcev ta poseg obstaja. */
    @Test
    void dovoljenaJeTudiPoOdigranihTekmah() {
        Igralec gost = zmagovalecTurnirja();
        int poTekmah = stanje(gost).getVrednost();
        assertTrue(stanje(gost).getStTekem() > 0, "igralec mora imeti tekme");

        ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, POJASNILO);

        RatingStanje po = stanje(gost);
        assertEquals(2400, po.getVrednost());
        assertTrue(po.isPostavljen(), "stevilka od cloveka - uvrstitev novinca je ne sme povoziti");
        assertNotNull(po.getZunanjaUvrstitevOb(), "od tega trenutka je stevilka spet sveza");

        RatingZgodovina zapis = zapisUvrstitve(gost);
        assertEquals(2400, zapis.getNovaVrednost());
        assertEquals(2400 - poTekmah, zapis.getSprememba());
        assertEquals(VIR, zapis.getVir());
        assertEquals(POJASNILO, zapis.getPojasnilo());
    }

    /* Vir in pojasnilo sta edino, kar rocni poseg loci od samovolje, zato sta
       obvezna - in ne le v vmesniku. */
    @Test
    void brezViraAliPojasnilaJeZavrnjena() {
        Igralec gost = noviIgralec("Brez", "Vira");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ratingStoritev.zunanjaUvrstitev(gost, 2400, "  ", POJASNILO));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, null));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ratingStoritev.zunanjaUvrstitev(gost, 4000, VIR, POJASNILO));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ratingStoritev.zunanjaUvrstitev(gost, 50, VIR, POJASNILO));

        assertTrue(ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(gost.getId(), RatingStanje.SISTEM_TURNIRKO).isEmpty(),
                "zavrnjen poseg ne sme pustiti stanja");
    }

    /* Tocka celotne resitve: redki gost je bil z javne lestvice skrit, ker 18
       mesecev ni igral. Zunanja uvrstitev je nov podatek o njem, zato ga
       lestvica spet kaze - sicer bi zavrgli ravno tisto, kar smo vpisali. */
    @Test
    void zunanjaUvrstitevGaVrneNaJavnoLestvico() {
        Igralec gost = noviIgralec("Redki", "Gost");
        RatingStanje stanje = new RatingStanje(gost, RatingStanje.SISTEM_TURNIRKO, 1500);
        stanje.setZadnjaTekmaOb(LocalDateTime.now().minusMonths(30));
        stanje.setPrvaTekmaOb(LocalDateTime.now().minusMonths(60));
        stanje.setStTekem(40);
        ratingStanjeRepozitorij.save(stanje);

        assertFalse(naLestvici(gost), "30 mesecev brez tekme ga skrije");

        ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, POJASNILO);

        assertTrue(naLestvici(gost), "po zunanji uvrstitvi je stevilka spet sveza");
        assertEquals(2400, stanje(gost).getVrednost());
        /* Odbitki, ki so do tedaj zapadli, se res zgodili in v zgodovini
           ostanejo - stevilka pa nato skoci na zunanjo. */
        assertEquals(3, dnevnik(gost).stream()
                .filter(z -> z.getRazlog() == RazlogSpremembe.NEAKTIVNOST).count(),
                "trije odbitki pred uvrstitvijo ostanejo zapisani");
        assertEquals(2400 - 1460, zapisUvrstitve(gost).getSprememba(),
                "sprememba se meri od ze popravljene stevilke (1500 - 40)");
    }

    /* Stevilki, prepisani z zunanje lestvice, ni mogoce odbiti tock za
       odsotnost, ki jo ta lestvica ze uposteva. */
    @Test
    void poUvrstitviOdbitkaZaNeaktivnostNiVec() {
        Igralec gost = noviIgralec("Svez", "Gost");
        RatingStanje stanje = new RatingStanje(gost, RatingStanje.SISTEM_TURNIRKO, 1500);
        stanje.setZadnjaTekmaOb(LocalDateTime.now().minusMonths(30));
        stanje.setStTekem(40);
        ratingStanjeRepozitorij.save(stanje);

        ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, POJASNILO);

        assertEquals(0, neaktivnostStoritev.uveljavi(stanje(gost), LocalDateTime.now()),
                "premor se meri od zadnjega PODATKA, ne od zadnje tekme");
        assertEquals(2400, stanje(gost).getVrednost());
    }

    /* Ce preracun zunanjo uvrstitev odigra na napacnem mestu, ji sledece tekme
       (ali pri tem igralcu odsotnost teh) pomenijo, da stevilka ni vec tista,
       ki jo je clovek vpisal. Tu so vse tekme PRED uvrstitvijo, zato mora
       preracun konceti natanko pri vpisani vrednosti. */
    @Test
    void preracunOdZacetkaOhraniVpisanoStevilko() {
        Igralec gost = zmagovalecTurnirja();
        int poTekmah = stanje(gost).getVrednost();
        ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, POJASNILO);

        preracunStoritev.preracunajOd(null);

        RatingStanje po = stanje(gost);
        assertEquals(2400, po.getVrednost(),
                "uvrstitev je zadnji korak casovne vrste, zato obvelja njena vrednost");
        assertTrue(po.isPostavljen());
        assertNotNull(po.getZunanjaUvrstitevOb());

        RatingZgodovina zapis = zapisUvrstitve(gost);
        assertEquals(2400, zapis.getNovaVrednost(), "vpisana vrednost je nedotakljiva");
        assertEquals(2400 - poTekmah, zapis.getSprememba(),
                "sprememba se preracuna glede na stanje pred uvrstitvijo");
        assertEquals(VIR, zapis.getVir(), "vir in pojasnilo preracun prezivita");
        assertEquals(POJASNILO, zapis.getPojasnilo());
    }

    /* Uvrstitev PRED mejo preracuna se ne odigra znova - njeno posledico mora
       nositi obnovljeno stanje (postavljen, cas svezine). */
    @Test
    void preracunPoUvrstitviOhraniStanjeIzDnevnika() {
        Igralec gost = zmagovalecTurnirja();
        ratingStoritev.zunanjaUvrstitev(gost, 2400, VIR, POJASNILO);

        preracunStoritev.preracunajOd(LocalDate.now().plusDays(1));

        RatingStanje po = stanje(gost);
        assertEquals(2400, po.getVrednost());
        assertTrue(po.isPostavljen());
        assertNotNull(po.getZunanjaUvrstitevOb());
        assertEquals(1, dnevnik(gost).stream()
                .filter(z -> z.getRazlog() == RazlogSpremembe.ZUNANJA_UVRSTITEV).count(),
                "preracun uvrstitve ne sme podvojiti");
    }

    private boolean naLestvici(Igralec igralec) {
        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();
        return lestvica.stream()
                .anyMatch(v -> v.idIgralca().equals(igralec.getId()) && v.rating() != null);
    }
}
