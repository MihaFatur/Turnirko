/* Izbris racuna s placljivo narocnino (administrator in nocno ciscenje).

   Vrstica narocnine izgine z racunom (ON DELETE CASCADE). Do novembra 2026
   Stripa ob tem ni nihce obvestil: placniku je zaracunaval naprej, webhook pa
   narocnine ni vec nasel. Zdaj se obnova v Stripu ustavi PRED izbrisom, nocno
   ciscenje pa placanega racuna z nepotrjenim naslovom sploh ne izbrise.

   Stripe je nadomescen z laznim (StripeNarocnine): test preveri, KAJ storitev
   od njega zahteva in kaj se zgodi z racunom. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IzbrisRacunaZNarocninoTest {

    private static final String STRIPE_ID = "sub_test_izbris";

    @Autowired RacuniStoritev racuni;
    @Autowired CiscenjeRacunovStoritev ciscenje;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean StripeNarocnine stripe;

    // ---------- Izbris, ki ga sprozi administrator ----------

    @Test
    void izbrisRacunaZAktivnoNarocninoNajprejUstaviObnovoVStripu() {
        Uporabnik u = racun("aktivna@test");
        narocnina(u, StatusNarocnine.AKTIVNA, STRIPE_ID);
        izperi();

        racuni.zbrisi(u.getId());
        izperi();

        // urnik najprej: narocnino z urnikom vodi urnik in preklica sicer ne sprejme
        verify(stripe).sprostiUrnik(STRIPE_ID);
        verify(stripe).nastaviPreklicOKoncu(STRIPE_ID, true);
        assertTrue(uporabnikRepozitorij.findById(u.getId()).isEmpty(), "racun je izbrisan");
        assertTrue(narocninaRepozitorij.findByStripeNarocninaId(STRIPE_ID).isEmpty(),
                "vrstica narocnine gre z racunom");
    }

    @Test
    void zapadlaNarocninaSeObIzbrisuPravTakoUstavi() {
        Uporabnik u = racun("zapadla@test");
        narocnina(u, StatusNarocnine.ZAPADLA, STRIPE_ID);
        izperi();

        racuni.zbrisi(u.getId());

        // Stripe placilo zapadle narocnine se poskusa izterjati
        verify(stripe).nastaviPreklicOKoncu(STRIPE_ID, true);
    }

    @Test
    void ceStripeOdpoveRacunOstane() {
        Uporabnik u = racun("odpoved@test");
        narocnina(u, StatusNarocnine.AKTIVNA, STRIPE_ID);
        izperi();
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(true)))
                .thenThrow(new DomenskaIzjema("Narocnine trenutno ni mogoce spremeniti. Poskusi kasneje."));

        assertThrows(DomenskaIzjema.class, () -> racuni.zbrisi(u.getId()));
        izperi();

        assertTrue(uporabnikRepozitorij.findById(u.getId()).isPresent(),
                "racun, ki mu Stripe se zaracunava, ne sme izginiti iz aplikacije");
        assertTrue(narocninaRepozitorij.findByStripeNarocninaId(STRIPE_ID).isPresent());
    }

    @Test
    void preklicanaNarocninaInRacunBrezStripaNeKliceStripa() {
        Uporabnik preklican = racun("preklicana@test");
        narocnina(preklican, StatusNarocnine.PREKLICANA, STRIPE_ID);
        Uporabnik preizkus = racun("preizkus@test");
        narocnina(preizkus, StatusNarocnine.PREIZKUS, null);
        Uporabnik brez = racun("brez@test");
        izperi();

        racuni.zbrisi(preklican.getId());
        racuni.zbrisi(preizkus.getId());
        racuni.zbrisi(brez.getId());
        izperi();

        // preklicana se ne obnavlja vec, preizkus in racun brez paketa Stripa nimata
        verifyNoInteractions(stripe);
        assertEquals(0, uporabnikRepozitorij.findAllById(
                java.util.List.of(preklican.getId(), preizkus.getId(), brez.getId())).size());
    }

    // ---------- Nocno ciscenje ----------

    /* Placljiva registracija ustvari racun in narocnino ob placilu, se preden
       je naslov potrjen. Kdor kode ne vpise v 48 urah, je placnik in ne
       opuscena rezervacija naslova. */
    @Test
    void ciscenjePustiPlacanRacunZNepotrjenimNaslovom() {
        Uporabnik placan = nepotrjen("placan@test");
        narocnina(placan, StatusNarocnine.AKTIVNA, STRIPE_ID);
        Uporabnik neplacan = nepotrjen("neplacan@test");
        izperi();

        int izbrisanih = ciscenje.pocisti(LocalDateTime.now().plusDays(3));
        izperi();

        assertEquals(1, izbrisanih, "izbrise se samo racun brez narocnine");
        assertTrue(uporabnikRepozitorij.findById(placan.getId()).isPresent(), "placan racun ostane");
        assertTrue(narocninaRepozitorij.findByStripeNarocninaId(STRIPE_ID).isPresent());
        assertFalse(uporabnikRepozitorij.findById(neplacan.getId()).isPresent());
        verifyNoInteractions(stripe);
    }

    /* Racun mlajsega od 15 let brez soglasja skrbnika se po 30 dneh izbrise
       tudi, ce je placljiv - zato se obnova v Stripu najprej ustavi. */
    @Test
    void ciscenjeRacunaBrezSoglasjaUstaviObnovoInGaIzbrise() {
        Uporabnik u = brezSoglasja("otrok@test");
        narocnina(u, StatusNarocnine.AKTIVNA, STRIPE_ID);
        izperi();

        int izbrisanih = ciscenje.pocisti(LocalDateTime.now().plusDays(31));
        izperi();

        verify(stripe).nastaviPreklicOKoncu(STRIPE_ID, true);
        assertEquals(1, izbrisanih);
        assertTrue(uporabnikRepozitorij.findById(u.getId()).isEmpty());
    }

    @Test
    void ciscenjeRacunaBrezSoglasjaPocakaCeStripeOdpove() {
        Uporabnik placan = brezSoglasja("otrok-placan@test");
        narocnina(placan, StatusNarocnine.AKTIVNA, STRIPE_ID);
        Uporabnik neplacan = brezSoglasja("otrok-neplacan@test");
        izperi();
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(true)))
                .thenThrow(new DomenskaIzjema("Narocnine trenutno ni mogoce spremeniti. Poskusi kasneje."));

        int izbrisanih = ciscenje.pocisti(LocalDateTime.now().plusDays(31));
        izperi();

        // odpoved pri enem racunu ne ustavi ciscenja ostalih
        assertEquals(1, izbrisanih);
        assertTrue(uporabnikRepozitorij.findById(placan.getId()).isPresent(),
                "racun pocaka do naslednje noci");
        assertTrue(uporabnikRepozitorij.findById(neplacan.getId()).isEmpty());
        verify(stripe, never()).nastaviPreklicOKoncu(anyString(), eq(false));
    }

    // ---------- Pomozno ----------

    private Uporabnik racun(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        u.setEmailPotrjenOb(LocalDateTime.now());
        return uporabnikRepozitorij.save(u);
    }

    private Uporabnik nepotrjen(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        u.setEmailPotrjenOb(null);
        return uporabnikRepozitorij.save(u);
    }

    private Uporabnik brezSoglasja(String prijavnoIme) {
        Uporabnik u = racun(prijavnoIme);
        u.setEmailSkrbnika("skrbnik-" + prijavnoIme + ".si");
        u.setSkrbnikPotrjenOb(null);
        return uporabnikRepozitorij.save(u);
    }

    private Narocnina narocnina(Uporabnik u, StatusNarocnine status, String stripeId) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setCiklus(CiklusPlacila.MESECNO);
        n.setStatus(status);
        n.setStarejsiOd21(true);
        n.setCenaObSklenitvi(2.99);
        n.setStripeNarocninaId(stripeId);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(18));
        return narocninaRepozitorij.save(n);
    }

    /* Zapis v bazo in prazna seja: izbris mora delovati tudi, ko narocnine ni
       v predpomnilniku seje, in preverba mora brati bazo, ne predpomnilnika. */
    private void izperi() {
        em.flush();
        em.clear();
    }
}
