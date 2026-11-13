/* Predpomnilnik javnih agregatov na pravih poteh: vrne isto kot izracun in ga
   vsak zapis razveljavi.

   Lestvica, uvrstitev na profilu, kandidati nakljucnega para in seznam
   turnirjev pridejo iz PredpomnilnikBranja. Test drzi obljubi, od katerih je
   odvisno, da gledalec ne vidi zastarelega stanja:

   - shranjena vrednost je ENAKA sveze izracunani (izracun v transakciji, ki
     pise, gre mimo predpomnilnika - to je primerjalna pot);
   - po zapisu, ki agregat spremeni (vnos izida in z njim obracun ratinga,
     arhiviranje igralca, nov turnir), naslednje branje ze vidi novo stanje.

   Razred NAMENOMA ni @Transactional: predpomnilnik se uporablja samo zunaj
   transakcij, ki pisejo, razveljavi pa ga potrditev - oboje se v transakciji
   testa, ki se na koncu razveljavi, ne zgodi. Zato test pocisti za seboj. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.ProfilDto;
import si.turnirko.dto.TurnirDto;
import si.turnirko.dto.TurnirVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class PredpomnilnikJavnihPotiTest {

    private static final int IGRALCEV = 8;

    @Autowired StatistikaStoritev statistika;
    @Autowired ProfilStoritev profil;
    @Autowired RekreativecStoritev rekreativec;
    @Autowired PovzetkiStoritev povzetki;
    @Autowired IgralciStoritev igralciStoritev;
    @Autowired TurnirjiStoritev turnirjiStoritev;
    @Autowired ZrebStoritev zrebStoritev;
    @Autowired TekmaStoritev tekmaStoritev;
    @Autowired PlatformTransactionManager transakcije;

    @Autowired IgralecRepozitorij igralecRepozitorij;
    @Autowired TurnirRepozitorij turnirRepozitorij;
    @Autowired DogodekRepozitorij dogodekRepozitorij;
    @Autowired PrijavaRepozitorij prijavaRepozitorij;
    @Autowired TekmaRepozitorij tekmaRepozitorij;
    @Autowired NizRepozitorij nizRepozitorij;
    @Autowired RatingStanjeRepozitorij ratingStanjeRepozitorij;
    @Autowired RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;

    private TransactionTemplate pisanje;
    private List<Long> igralci;
    private Turnir turnir;
    private Dogodek dogodek;

    @BeforeEach
    void pripravi() {
        pisanje = new TransactionTemplate(transakcije);
        zrebStoritev.nastaviNakljucje(new Random(42));

        igralci = new ArrayList<>();
        for (int i = 1; i <= IGRALCEV; i++) {
            igralci.add(novIgralec("Predpomnjen" + i).getId());
        }
        /* URADNO: tekme stejejo med tekmovalne, po treh igralec ni vec
           rekreativec. */
        turnir = turnirjiStoritev.ustvari(new TurnirVnos(
                "Turnir predpomnilnika", null, null, LocalDate.now(), null, null, RavenTekmovanja.URADNO));
        dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(),
                new DogodekVnos("Clani", SpolKategorija.MOSKI, null, 5, null, null, null, null, null, null));
        turnirjiStoritev.prijaviIgralce(dogodek.getId(), igralci);
        zrebStoritev.izvediZreb(dogodek.getId());
    }

    @AfterEach
    void pocisti() {
        // vrstni red sledi tujim kljucem; tekme od finala proti prvemu kolu
        nizRepozitorij.deleteAll();
        ratingZgodovinaRepozitorij.deleteAll();
        ratingStanjeRepozitorij.deleteAll();
        tekmaRepozitorij.findAll().stream()
                .sorted(Comparator.comparingInt(Tekma::getKolo).reversed())
                .forEach(tekmaRepozitorij::delete);
        prijavaRepozitorij.deleteAll();
        dogodekRepozitorij.deleteAll();
        turnirRepozitorij.deleteAll();
        igralecRepozitorij.deleteAll();
    }

    @Test
    void lestvicaPrideIzPredpomnilnikaInJoVnosIzidaRazveljavi() {
        odigrajNaslednjo();

        List<LestvicaIgralcaDto> prva = statistika.globalnaLestvica();
        assertSame(prva, statistika.globalnaLestvica(), "drugo branje ne racuna znova");
        assertEquals(svezaLestvica(), prva, "shranjena je enaka sveze izracunani");

        Tekma druga = naslednja();
        Long zmagovalec = druga.getPrijava1().getIgralec().getId();
        assertEquals(0, vrstica(prva, zmagovalec).zmage());
        assertNull(vrstica(prva, zmagovalec).rating());
        odigraj(druga);

        List<LestvicaIgralcaDto> po = statistika.globalnaLestvica();
        assertNotSame(prva, po);
        assertEquals(1, vrstica(po, zmagovalec).zmage(), "vnos izida je viden v naslednjem branju");
        assertNotNull(vrstica(po, zmagovalec).rating(), "in z njim obracun ratinga");
        assertEquals(svezaLestvica(), po);
    }

    /* Uvrstitev na profilu bere ratinge vseh igralcev in stetje tekmovalnih
       tekem - oboje iz predpomnilnika. Finalist po tretji tekmi ni vec
       rekreativec, arhiviran igralec pa z lestvice izpade. */
    @Test
    void uvrstitevNaProfiluSlediObracunuInSpremembiIgralca() {
        for (int i = 0; i < 6; i++) {
            odigrajNaslednjo();
        }
        Tekma finale = naslednja();
        Long finalist = finale.getPrijava1().getIgralec().getId();
        Long drugiFinalist = finale.getPrijava2().getIgralec().getId();

        ProfilDto.Uvrstitev pred = profil.profil(finalist).uvrstitev();
        assertTrue(pred.rekreativec(), "dve tekmi sta premalo");
        assertTrue(rekreativec.rekreativci(igralci).contains(finalist));
        assertEquals(svezaUvrstitev(finalist), pred);
        assertEquals(pred, profil.profil(finalist).uvrstitev());

        odigraj(finale);

        ProfilDto.Uvrstitev poFinalu = profil.profil(finalist).uvrstitev();
        assertFalse(poFinalu.rekreativec(), "tretja tekmovalna tekma ga naredi tekmovalca");
        assertFalse(rekreativec.rekreativci(igralci).contains(finalist));
        assertEquals(2, poFinalu.skupajIgralcev(), "med tekmovalci sta finalista");
        assertEquals(svezaUvrstitev(finalist), poFinalu);

        igralciStoritev.arhiviraj(drugiFinalist);

        ProfilDto.Uvrstitev poArhivu = profil.profil(finalist).uvrstitev();
        assertEquals(1, poArhivu.skupajIgralcev(), "arhivirani z lestvice izpade");
        assertEquals(svezaUvrstitev(finalist), poArhivu);
    }

    @Test
    void kandidatiNakljucnegaParaSledijoPrviOdigraniTekmi() {
        Tekma prva = naslednja();
        Long a = prva.getPrijava1().getIgralec().getId();
        Long b = prva.getPrijava2().getIgralec().getId();

        List<Long> pred = statistika.kandidatiZaPar();
        assertSame(pred, statistika.kandidatiZaPar());
        assertFalse(pred.contains(a));
        assertFalse(pred.contains(b));

        odigraj(prva);

        List<Long> po = statistika.kandidatiZaPar();
        assertTrue(po.contains(a) && po.contains(b), "igralca odigrane tekme sta kandidata");
        assertEquals(sveze(() -> statistika.kandidatiZaPar()), po);
    }

    @Test
    void seznamTurnirjevSlediIzidomInNovemuTurnirju() {
        Map<Long, TurnirDto.Stevci> stevci = povzetki.zaVseTurnirje();
        Map<Long, TurnirDto.Potek> poteki = povzetki.potekiVsehTurnirjev();
        assertSame(stevci, povzetki.zaVseTurnirje());
        assertSame(poteki, povzetki.potekiVsehTurnirjev());
        assertEquals(0, stevci.get(turnir.getId()).odigranihTekem());
        assertNull(poteki.get(turnir.getId()).zadnjiIzid());

        odigrajNaslednjo();

        assertEquals(1, povzetki.zaVseTurnirje().get(turnir.getId()).odigranihTekem());
        assertNotNull(povzetki.potekiVsehTurnirjev().get(turnir.getId()).zadnjiIzid());
        assertEquals(sveze(() -> povzetki.zaVseTurnirje()), povzetki.zaVseTurnirje());
        assertEquals(sveze(() -> povzetki.potekiVsehTurnirjev()), povzetki.potekiVsehTurnirjev());
        // stran turnirja (ozja poizvedba, brez predpomnilnika) rece isto kot seznam
        assertEquals(povzetki.zaTurnir(turnir.getId()), povzetki.zaVseTurnirje().get(turnir.getId()));
        assertEquals(povzetki.potekTurnirja(turnir.getId()),
                povzetki.potekiVsehTurnirjev().get(turnir.getId()));

        Turnir nov = turnirjiStoritev.ustvari(new TurnirVnos(
                "Drugi turnir predpomnilnika", null, null, LocalDate.now(), null, null, null));
        turnirjiStoritev.dodajDogodek(nov.getId(),
                new DogodekVnos("Clani", SpolKategorija.MOSKI, null, 5, null, null, null, null, null, null));

        assertEquals(1, povzetki.zaVseTurnirje().get(nov.getId()).steviloDogodkov(),
                "nov turnir z dogodkom je v seznamu");
    }

    /* Transakcija, ki pise, predpomnilnika ne polni: po njeni razveljavitvi v
       njem ne sme ostati igralec, ki ga v bazi ni. */
    @Test
    void razveljavljenaTransakcijaNeZastrupiPredpomnilnika() {
        List<LestvicaIgralcaDto> pred = statistika.globalnaLestvica();

        pisanje.executeWithoutResult(stanje -> {
            Igralec zacasen = novIgralec("Zacasen");
            assertNotNull(vrstica(statistika.globalnaLestvica(), zacasen.getId()),
                    "v svoji transakciji ga izracun vidi");
            stanje.setRollbackOnly();
        });

        assertSame(pred, statistika.globalnaLestvica());
        assertEquals(svezaLestvica(), statistika.globalnaLestvica());
    }

    // ---------- Pomozno ----------

    private Igralec novIgralec(String priimek) {
        Igralec igralec = new Igralec();
        igralec.setIme("Igralec");
        igralec.setPriimek(priimek);
        igralec.setSpol(Spol.MOSKI);
        igralec.setDatumRojstva(LocalDate.of(1995, 1, 1));
        return igralecRepozitorij.save(igralec);
    }

    /* Prva pripravljena tekma dogodka (z nalozenima stranema). */
    private Tekma naslednja() {
        return tekmaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .min(Comparator.comparing(Tekma::getId))
                .orElseThrow();
    }

    private void odigraj(Tekma tekma) {
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
    }

    private void odigrajNaslednjo() {
        odigraj(naslednja());
    }

    /* Izracun mimo predpomnilnika: v transakciji, ki sme pisati. */
    private <T> T sveze(java.util.function.Supplier<T> izracun) {
        return pisanje.execute(stanje -> izracun.get());
    }

    private List<LestvicaIgralcaDto> svezaLestvica() {
        return sveze(() -> statistika.globalnaLestvica());
    }

    private ProfilDto.Uvrstitev svezaUvrstitev(Long idIgralca) {
        return sveze(() -> profil.profil(idIgralca).uvrstitev());
    }

    private static LestvicaIgralcaDto vrstica(List<LestvicaIgralcaDto> lestvica, Long idIgralca) {
        return lestvica.stream().filter(v -> v.idIgralca().equals(idIgralca)).findFirst().orElse(null);
    }
}
