/* Testi napovedi "kaj prinese tekma proti njemu".

   Obljuba predela je ena sama in jo je mogoce preveriti: stevilka, ki jo
   igralec vidi PRED tekmo, mora biti natanko tista, ki jo dobi PO njej.
   Zato osrednji test isto tekmo najprej napove in nato zares odigra. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.NapovedTekmeDto;
import si.turnirko.dto.PotrditevRacunaVnos;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class NapovedTekmeTest extends IntegracijskiTest {

    @Autowired private NapovedTekmeStoritev napovedStoritev;
    @Autowired private RacuniStoritev racuniStoritev;
    @Autowired private RegistracijaStoritev registracijaStoritev;
    @Autowired private OmejevalnikPoskusov omejevalnik;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;

    /* Omejevalnik posiljanja kod zivi v pomnilniku cez vse teste. */
    @org.junit.jupiter.api.BeforeEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    /* Prva pripravljena tekma zrebanega dogodka s postavljenimi ratingi.
       Ratingi so POSTAVLJENI (nastaviRatinge), zato novincev tu ni in rating
       tece po korakih - natanko po obrazcu, ki ga napoved kaze. */
    private Tekma pripraviTekmo(int... ratingi) {
        Dogodek dogodek = pripraviDogodek(4);
        nastaviRatinge(dogodek, ratingi);
        zrebStoritev.izvediZreb(dogodek.getId());
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    private static NapovedTekmeDto.Raven raven(NapovedTekmeDto n, RavenTekmovanja raven) {
        return n.ravni().stream().filter(r -> r.raven() == raven).findFirst().orElseThrow();
    }

    @Test
    void napovedanaSpremembaJeTista_kiJoTekmaZaresPrinese() {
        Tekma tekma = pripraviTekmo(1600, 1400, 1200, 1000);
        Igralec prvi = tekma.getPrijava1().getIgralec();
        Igralec drugi = tekma.getPrijava2().getIgralec();

        NapovedTekmeDto n = napovedStoritev.napoved(
                prvi.getId(), drugi.getId(), adminIme());
        NapovedTekmeDto.Izid napovedan = raven(n, RavenTekmovanja.URADNO).zmaga();

        int prejPrvi = stanje(prvi);
        int prejDrugi = stanje(drugi);
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null));

        assertEquals(napovedan.sprememba(), stanje(prvi) - prejPrvi,
                "napovedana sprememba zmagovalca se mora ujemati z obracunano");
        assertEquals(napovedan.spremembaNasprotnika(), stanje(drugi) - prejDrugi,
                "napovedana sprememba porazenca se mora ujemati z obracunano");
        assertEquals(napovedan.rating(), stanje(prvi));
        assertEquals(napovedan.ratingNasprotnika(), stanje(drugi));
    }

    @Test
    void vsakaVrsticaSeZmnoziVSvojoSpremembo() {
        Tekma tekma = pripraviTekmo(1500, 1300, 1200, 1000);
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava1().getIgralec().getId(),
                tekma.getPrijava2().getIgralec().getId(), adminIme());

        double pricakovano = n.pricakovanOdstotek() / 100.0;
        for (NapovedTekmeDto.Raven raven : n.ravni()) {
            for (boolean zmaga : new boolean[] { true, false }) {
                NapovedTekmeDto.Izid i = zmaga ? raven.zmaga() : raven.poraz();
                double tocke = zmaga ? 1.0 : 0.0;
                int obrazec = (int) Math.rint(
                        n.jaz().k() * raven.teza() * (tocke - pricakovano));
                /* Odstopanje 1 tocke je dopustno: pricakovano je v odgovoru
                   zaokrozeno na cel odstotek, sam izracun pa tece z vso
                   natancnostjo. Vec kot to bi pomenilo drugacen obrazec. */
                assertTrue(Math.abs(obrazec - i.sprememba()) <= 1,
                        (zmaga ? "zmaga" : "poraz") + " pri " + raven.raven()
                                + " ne sledi obrazcu: " + obrazec + " proti " + i.sprememba());
            }
        }
    }

    @Test
    void zmagaProtiMocnejsemuPrineseVecOdPorazaVzame() {
        /* Prvi prijavljeni ima najvisji rating, zato je za drugega zmaga
           presenecenje (veliko prinese), poraz pa pricakovan (malo vzame). */
        Tekma tekma = pripraviTekmo(1700, 1300, 1200, 1000);
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava2().getIgralec().getId(),
                tekma.getPrijava1().getIgralec().getId(), adminIme());
        NapovedTekmeDto.Raven uradno = raven(n, RavenTekmovanja.URADNO);

        assertTrue(uradno.zmaga().sprememba() > 0 && uradno.poraz().sprememba() < 0);
        assertTrue(uradno.zmaga().sprememba() > -uradno.poraz().sprememba(),
                "zmaga avtsajderja prinese vec, kot mu poraz vzame");
        assertEquals(-uradno.zmaga().sprememba(), uradno.zmaga().spremembaNasprotnika(),
                "pri enakem K je vsota sprememb 0");
    }

    @Test
    void ravenTekmovanjaSkrajsaSpremembo() {
        Tekma tekma = pripraviTekmo(1800, 1500, 1200, 900);
        /* Napoved je z vidika SIBKEJSEGA: favoritova zmaga proti mnogo
           slabsemu je pricakovana in prinese skoraj nic, zato bi jo
           zaokrozevanje pri vseh treh ravneh zlilo v nic. */
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava2().getIgralec().getId(),
                tekma.getPrijava1().getIgralec().getId(), adminIme());

        assertEquals(List.of(RavenTekmovanja.URADNO, RavenTekmovanja.KLUBSKO,
                        RavenTekmovanja.REKREATIVNO),
                n.ravni().stream().map(NapovedTekmeDto.Raven::raven).toList(),
                "raven NE_STEJE v napovedi nima vrstice - tekma, ki ratinga ne premakne");

        int uradno = raven(n, RavenTekmovanja.URADNO).zmaga().sprememba();
        int klubsko = raven(n, RavenTekmovanja.KLUBSKO).zmaga().sprememba();
        int rekreativno = raven(n, RavenTekmovanja.REKREATIVNO).zmaga().sprememba();
        assertTrue(uradno > klubsko && klubsko > rekreativno,
                "teza mnozi spremembo: " + uradno + " > " + klubsko + " > " + rekreativno);
        assertEquals(0.75, raven(n, RavenTekmovanja.KLUBSKO).teza(), 0.0001);
    }

    @Test
    void novinecBrezRatingaDobiSidroInVecjiK() {
        Dogodek dogodek = pripraviDogodek(4);
        nastaviRatinge(dogodek, 1500); // samo prvi ima rating, ostali so novinci
        zrebStoritev.izvediZreb(dogodek.getId());
        Igralec zRatingom = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();
        Igralec novinec = prijavePoVrsti(dogodek.getId()).get(1).getIgralec();

        NapovedTekmeDto n = napovedStoritev.napoved(
                novinec.getId(), zRatingom.getId(), adminIme());

        assertEquals(List.of(NapovedTekmeDto.Opozorilo.BREZ_RATINGA), n.jaz().opozorila());
        assertEquals(sidroZa(novinec), n.jaz().rating(),
                "novinec vstopi pri starostnem sidru in ne pri 1000");
        assertEquals(0, n.jaz().stTekem());
        assertEquals(TurnirkoRatingStoritev.K_OSNOVNI
                        + TurnirkoRatingStoritev.K_PRIBITEK_NEUSTALJEN
                        + TurnirkoRatingStoritev.K_PRIBITEK_NOVINEC,
                n.jaz().k(), "brez tekem se sestejeta oba pribitka za negotovost");
    }

    @Test
    void napovedJeZasebnaInZahtevaDvaRazlicnaIgralca() {
        Tekma tekma = pripraviTekmo(1500, 1400, 1200, 1000);
        Igralec prvi = tekma.getPrijava1().getIgralec();
        Igralec drugi = tekma.getPrijava2().getIgralec();
        String prijavaPrvega = racunZa(prvi, "napoved@test.si");

        // svojo napoved sme videti
        assertEquals(3, napovedStoritev.napoved(prvi.getId(), drugi.getId(), prijavaPrvega)
                .ravni().size());
        // tuje ne
        assertThrows(PrepovedanoIzjema.class,
                () -> napovedStoritev.napoved(drugi.getId(), prvi.getId(), prijavaPrvega));
        // sam s sabo se nihce ne primerja
        assertThrows(NeveljavenVnosIzjema.class,
                () -> napovedStoritev.napoved(prvi.getId(), prvi.getId(), prijavaPrvega));
    }

    /* Napovedan poraz je tudi tisti, ki ga tekma zares vzame - ne glede na
       to, ali je bil gladek ali tesen (zmaga je zmaga). */
    @Test
    void napovedanPorazJeTisti_kiGaTekmaZaresVzame() {
        Tekma tekma = pripraviTekmo(1500, 1400, 1200, 1000);
        Igralec prvi = tekma.getPrijava1().getIgralec();
        Igralec drugi = tekma.getPrijava2().getIgralec();
        NapovedTekmeDto n = napovedStoritev.napoved(prvi.getId(), drugi.getId(), adminIme());
        NapovedTekmeDto.Izid poraz = raven(n, RavenTekmovanja.URADNO).poraz();

        int prejPrvi = stanje(prvi);
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 2, 3, null, null));

        assertEquals(poraz.sprememba(), stanje(prvi) - prejPrvi);
        assertEquals(poraz.rating(), stanje(prvi));
    }

    private int stanje(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    /* Napoved (kot zasebne analize profila) je Premium funkcija - racun tu
       dobi Premium takoj, ker ta test meri NAPOVED, ne placilni paket. */
    private String racunZa(Igralec igralec, String email) {
        registracijaStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, email, "geslo123", false,
                igralec.getDatumRojstva(), null), "127.0.0.1");
        Long idRacuna = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals(email)).findFirst().orElseThrow().id();
        racuniStoritev.potrdi(idRacuna, new PotrditevRacunaVnos(igralec.getId()));
        Uporabnik u = uporabnikRepozitorij.findByUporabniskoImeIgnoreCase(email).orElseThrow();
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        return email;
    }

    private String adminIme() {
        return uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getVloga() == Vloga.ADMIN)
                .map(Uporabnik::getUporabniskoIme)
                .findFirst().orElseThrow();
    }
}
