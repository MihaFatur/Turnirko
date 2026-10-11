/* Administratorjev seznam racunov nosi naročnino vsakega racuna: igralec brez
   zapisa je Free, organizator brez zapisa nima paketa, placan paket pa ostane
   viden tudi, ko racun se caka na potrditev (plačilo je prišlo, admin ga se ni
   potrdil). Datum konca obdobja mora priti skozi bazo, zato se seja pred
   branjem izprazni. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import si.turnirko.dto.RacunIgralcaDto;
import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RacuniNarocninaTest {

    private static final String IP = "127.0.0.1";

    @Autowired RacuniStoritev racuniStoritev;
    @Autowired RegistracijaStoritev registracijaStoritev;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @Autowired EntityManager em;

    @BeforeEach
    void pocistiOmejitve() {
        omejevalnik.pocistiVse();
    }

    private static RegistracijaVnos organizator(String ime, String priimek, String email) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", true, null, null);
    }

    private static RegistracijaVnos igralec(String ime, String priimek, String email) {
        return new RegistracijaVnos(ime, priimek, null, email, "geslo1234", false,
                LocalDate.of(1990, 1, 1), null);
    }

    private Uporabnik racun(String email) {
        return uporabnikRepozitorij.findByUporabniskoIme(email).orElseThrow();
    }

    /* Opoldne, da datum ostane isti ne glede na pas streznika in Ljubljane. */
    private static LocalDateTime konecObdobja(int dni) {
        return LocalDateTime.of(LocalDate.now().plusDays(dni), LocalTime.NOON);
    }

    @Test
    void seznamNosiNarocnoVsakegaRacuna() {
        registracijaStoritev.registriraj(igralec("Free", "Igralec", "free@test.si"), IP);
        registracijaStoritev.registriraj(igralec("Prem", "Igralka", "premium@test.si"), IP);
        registracijaStoritev.registriraj(organizator("Brez", "Paketa", "brez@test.si"), IP);
        registracijaStoritev.registriraj(organizator("Plus", "Vodja", "plus@test.si"), IP);

        Narocnina premium = new Narocnina(racun("premium@test.si"), Paket.PREMIUM);
        premium.setStatus(StatusNarocnine.AKTIVNA);
        premium.setCiklus(CiklusPlacila.LETNO);
        premium.setTrenutnoObdobjeDo(konecObdobja(180));
        narocninaRepozitorij.save(premium);

        // organizator je placal, racun pa se caka na administratorjevo potrditev
        Narocnina plus = new Narocnina(racun("plus@test.si"), Paket.ORGANIZATOR_PLUS);
        plus.setStatus(StatusNarocnine.PREKLICANA);
        plus.setCiklus(CiklusPlacila.MESECNO);
        plus.setTrenutnoObdobjeDo(konecObdobja(10));
        narocninaRepozitorij.save(plus);

        // zapis mora prestati skozi bazo, ne le skozi predpomnilnik seje
        em.flush();
        em.clear();

        Map<String, RacunIgralcaDto> poEmailu = racuniStoritev.racuni().stream()
                .collect(Collectors.toMap(RacunIgralcaDto::email, r -> r));

        RacunIgralcaDto.NarocninaRacuna free = poEmailu.get("free@test.si").narocnina();
        assertEquals(Paket.BREZPLACNO, free.paket());
        assertNull(free.status());
        assertFalse(free.veljavna());

        assertNull(poEmailu.get("brez@test.si").narocnina().paket(), "organizator brez zapisa nima paketa");

        RacunIgralcaDto.NarocninaRacuna premiumPregled = poEmailu.get("premium@test.si").narocnina();
        assertEquals(Paket.PREMIUM, premiumPregled.paket());
        assertEquals(StatusNarocnine.AKTIVNA, premiumPregled.status());
        assertEquals(CiklusPlacila.LETNO, premiumPregled.ciklus());
        assertEquals(LocalDate.now().plusDays(180), premiumPregled.obdobjeDo());
        assertTrue(premiumPregled.veljavna());

        RacunIgralcaDto.NarocninaRacuna plusPregled = poEmailu.get("plus@test.si").narocnina();
        assertEquals(Paket.ORGANIZATOR_PLUS, plusPregled.paket());
        assertEquals(StatusNarocnine.PREKLICANA, plusPregled.status());
        assertTrue(plusPregled.veljavna(), "preklicana ostane veljavna do konca placanega obdobja");
    }

    /* Odgovor mutacije mora nositi isti paket kot seznam, sicer bi se po
       zavrnitvi organizatorja izpisal drug paket kot pred njo. */
    @Test
    void odgovorMutacijeNosiIstoNarocnino() {
        registracijaStoritev.registriraj(organizator("Plus", "Vodja", "mutacija@test.si"), IP);
        Narocnina plus = new Narocnina(racun("mutacija@test.si"), Paket.ORGANIZATOR_PLUS);
        plus.setStatus(StatusNarocnine.AKTIVNA);
        plus.setCiklus(CiklusPlacila.LETNO);
        plus.setTrenutnoObdobjeDo(konecObdobja(300));
        narocninaRepozitorij.save(plus);
        em.flush();
        em.clear();

        RacunIgralcaDto zavrnjen = racuniStoritev.zavrni(racun("mutacija@test.si").getId());

        assertEquals(Paket.ORGANIZATOR_PLUS, zavrnjen.narocnina().paket());
        assertEquals(StatusNarocnine.AKTIVNA, zavrnjen.narocnina().status());
        assertEquals(LocalDate.now().plusDays(300), zavrnjen.narocnina().obdobjeDo());
    }
}
