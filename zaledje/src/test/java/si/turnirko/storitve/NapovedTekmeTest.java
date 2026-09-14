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
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class NapovedTekmeTest extends IntegracijskiTest {

    @Autowired private NapovedTekmeStoritev napovedStoritev;
    @Autowired private RacuniStoritev racuniStoritev;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;

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

    /* Vrstica danega izida z vidika lastnika profila. */
    private static NapovedTekmeDto.Izid izid(NapovedTekmeDto.Raven raven, int moji, int njegovi) {
        return raven.izidi().stream()
                .filter(i -> i.mojiNizi() == moji && i.nizovNasprotnika() == njegovi)
                .findFirst().orElseThrow();
    }

    @Test
    void napovedanaSpremembaJeTista_kiJoTekmaZaresPrinese() {
        Tekma tekma = pripraviTekmo(1600, 1400, 1200, 1000);
        Igralec prvi = tekma.getPrijava1().getIgralec();
        Igralec drugi = tekma.getPrijava2().getIgralec();

        NapovedTekmeDto n = napovedStoritev.napoved(
                prvi.getId(), drugi.getId(), null, adminIme());
        NapovedTekmeDto.Izid napovedan = izid(raven(n, RavenTekmovanja.URADNO), 3, 1);

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
                tekma.getPrijava2().getIgralec().getId(), null, adminIme());

        double pricakovano = n.pricakovanOdstotek() / 100.0;
        for (NapovedTekmeDto.Raven raven : n.ravni()) {
            for (NapovedTekmeDto.Izid i : raven.izidi()) {
                double tocke = i.zmaga() ? 1.0 : 0.0;
                int obrazec = (int) Math.rint(
                        n.jaz().k() * i.margina() * raven.teza() * (tocke - pricakovano));
                /* Odstopanje 1 tocke je dopustno: pricakovano je v odgovoru
                   zaokrozeno na cel odstotek, sam izracun pa tece z vso
                   natancnostjo. Vec kot to bi pomenilo drugacen obrazec. */
                assertTrue(Math.abs(obrazec - i.sprememba()) <= 1,
                        "izid " + i.mojiNizi() + ":" + i.nizovNasprotnika()
                                + " pri " + raven.raven() + " ne sledi obrazcu: "
                                + obrazec + " proti " + i.sprememba());
            }
        }
    }

    @Test
    void gladkaZmagaProtiMocnejsemuPrinesVecOdTesne() {
        /* Prvi prijavljeni ima najvisji rating, zato je za drugega zmaga
           presenecenje - in gladka zmaga vecje od tesne. */
        Tekma tekma = pripraviTekmo(1700, 1300, 1200, 1000);
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava2().getIgralec().getId(),
                tekma.getPrijava1().getIgralec().getId(), null, adminIme());
        NapovedTekmeDto.Raven uradno = raven(n, RavenTekmovanja.URADNO);

        assertTrue(izid(uradno, 3, 0).sprememba() > izid(uradno, 3, 2).sprememba(),
                "3:0 proti mocnejsemu mora prinesti vec od 3:2");
        /* Poraz avtsajderja je pricakovan, zato je odbitek majhen - in tesen
           poraz ga stane manj od gladkega. */
        assertTrue(izid(uradno, 2, 3).sprememba() > izid(uradno, 0, 3).sprememba(),
                "poraz 2:3 mora stati manj od 0:3");
        assertTrue(izid(uradno, 3, 1).sprememba() > 0 && izid(uradno, 1, 3).sprememba() < 0);
    }

    @Test
    void ravenTekmovanjaSkrajsaSpremembo() {
        Tekma tekma = pripraviTekmo(1800, 1500, 1200, 900);
        /* Napoved je z vidika SIBKEJSEGA: favoritova zmaga proti mnogo
           slabsemu je pricakovana in prinese skoraj nic, zato bi jo
           zaokrozevanje pri vseh treh ravneh zlilo v nic. */
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava2().getIgralec().getId(),
                tekma.getPrijava1().getIgralec().getId(), null, adminIme());

        assertEquals(List.of(RavenTekmovanja.URADNO, RavenTekmovanja.KLUBSKO,
                        RavenTekmovanja.REKREATIVNO),
                n.ravni().stream().map(NapovedTekmeDto.Raven::raven).toList(),
                "raven NE_STEJE v napovedi nima vrstice - tekma, ki ratinga ne premakne");

        int uradno = izid(raven(n, RavenTekmovanja.URADNO), 3, 1).sprememba();
        int klubsko = izid(raven(n, RavenTekmovanja.KLUBSKO), 3, 1).sprememba();
        int rekreativno = izid(raven(n, RavenTekmovanja.REKREATIVNO), 3, 1).sprememba();
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
                novinec.getId(), zRatingom.getId(), null, adminIme());

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
        assertEquals(3, napovedStoritev.napoved(prvi.getId(), drugi.getId(), null, prijavaPrvega)
                .ravni().size());
        // tuje ne
        assertThrows(PrepovedanoIzjema.class,
                () -> napovedStoritev.napoved(drugi.getId(), prvi.getId(), null, prijavaPrvega));
        // sam s sabo se nihce ne primerja
        assertThrows(NeveljavenVnosIzjema.class,
                () -> napovedStoritev.napoved(prvi.getId(), prvi.getId(), null, prijavaPrvega));
        // tekma se igra na 3, 5 ali 7 nizov
        assertThrows(NeveljavenVnosIzjema.class,
                () -> napovedStoritev.napoved(prvi.getId(), drugi.getId(), 4, prijavaPrvega));
    }

    @Test
    void izidiTecejoOdNajboljseZmageDoNajhujsegaPoraza() {
        Tekma tekma = pripraviTekmo(1500, 1400, 1200, 1000);
        NapovedTekmeDto n = napovedStoritev.napoved(
                tekma.getPrijava1().getIgralec().getId(),
                tekma.getPrijava2().getIgralec().getId(), null, adminIme());

        List<NapovedTekmeDto.Izid> izidi = raven(n, RavenTekmovanja.URADNO).izidi();
        assertEquals(6, izidi.size(), "tri zmage in trije porazi pri tekmi na 5 nizov");
        for (int i = 1; i < izidi.size(); i++) {
            assertTrue(izidi.get(i - 1).sprememba() >= izidi.get(i).sprememba(),
                    "vrstice morajo padati: " + izidi);
        }
        assertEquals(5, n.steviloNizov());
    }

    private int stanje(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    private String racunZa(Igralec igralec, String email) {
        racuniStoritev.registriraj(new RegistracijaVnos(
                igralec.getIme(), igralec.getPriimek(), null, email, "geslo123", false));
        Long idRacuna = racuniStoritev.racuni().stream()
                .filter(r -> r.email().equals(email)).findFirst().orElseThrow().id();
        racuniStoritev.potrdi(idRacuna, new PotrditevRacunaVnos(igralec.getId()));
        return email;
    }

    private String adminIme() {
        return uporabnikRepozitorij.findAll().stream()
                .filter(u -> u.getVloga() == Vloga.ADMIN)
                .map(Uporabnik::getUporabniskoIme)
                .findFirst().orElseThrow();
    }
}
