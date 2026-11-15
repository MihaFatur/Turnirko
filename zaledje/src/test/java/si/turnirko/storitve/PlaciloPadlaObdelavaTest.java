/* Obdelava placila pade z napako (najdba E1 pregleda kakovosti, nov 2026).

   Stripe dogodek ponavlja do 3 dni, cakajoca registracija pa se je po 4 dneh
   izbrisala - ce je obdelava ves cas padala, je placnik ostal brez sledi. Zdaj
   se vrstica ob prvi padli obdelavi oznaci kot placana (v svoji transakciji,
   ker se transakcija obdelave razveljavi) in je ciscenje ne pobrise; ob drugi
   padli obdelavi iste seje dobi admin e-posto.

   Namenoma BREZ @Transactional: preverja prav to, kar ostane po razveljavitvi.
   Zato test svoje vrstice pobrise sam. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.modeli.CakajocaRegistracija;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.CakajocaRegistracijaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class PlaciloPadlaObdelavaTest {

    private static final String ADMIN = "admin@turnirko.test";

    @Autowired private PlacilaStoritev placila;
    @Autowired private CakajocaRegistracijaRepozitorij cakajoce;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private PomnilniskiPosiljatelj posta;
    @Autowired private PlatformTransactionManager upravljalecTransakcij;

    private final String oznaka = "padla" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String zeton = "zeton-" + oznaka;
    private final String email = oznaka + "@test.si";

    @BeforeEach
    void pripravi() {
        posta.pocisti();
    }

    @AfterEach
    void pospravi() {
        cakajoce.findByZeton(zeton).ifPresent(cakajoce::delete);
        posta.pocisti();
    }

    @Test
    void padlaObdelavaOhraniPlacanoVrsticoInObvestiAdminaObPonovitvi() {
        LocalDateTime zdaj = LocalDateTime.now();
        // vrstica je starejsa od roka ciscenja (4 dni): brez oznake placila bi jo ciscenje pobrisalo
        cakajoce.save(new CakajocaRegistracija(zeton, "Ana", "Novak", null, email, "$2a$10$zgosceno", false,
                LocalDate.of(1990, 1, 1), null, zdaj.minusDays(5)));
        // paket, ki ga aplikacija ne pozna: obdelava pade PO tem, ko je racun v transakciji ze nastal
        String telo = TestniStripeDogodek.zakljucenaSeja(oznaka,
                TestniStripeDogodek.registracija(zeton, "NEOBSTOJEC_PAKET"));

        assertThrows(IllegalArgumentException.class,
                () -> placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo)),
                "napaka gre naprej, da Stripe dogodek ponovi");

        assertTrue(uporabnikRepozitorij.findByUporabniskoIme(email).isEmpty(), "transakcija je razveljavljena");
        CakajocaRegistracija c = cakajoce.findByZeton(zeton).orElseThrow();
        assertNotNull(c.getPlacanoOb(), "placilo je zabelezeno kljub razveljavitvi");
        assertEquals("cs_" + oznaka, c.getStripeSejaId());
        assertEquals("NAPAKA_OBDELAVE", c.getRazlogZastoja());
        assertTrue(adminu().isEmpty(), "prva napaka je lahko prehodna - admin se ne dobi poste");

        int pobrisanih = new TransactionTemplate(upravljalecTransakcij)
                .execute(stanje -> cakajoce.pobrisiStarejseOd(zdaj.minusDays(4)));
        assertEquals(0, pobrisanih);
        assertTrue(cakajoce.findByZeton(zeton).isPresent(), "ciscenje placane vrstice ne pobrise");

        // Stripe ponovi: obdelava pade znova
        assertThrows(IllegalArgumentException.class,
                () -> placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo)));
        List<PomnilniskiPosiljatelj.Sporocilo> adminu = adminu();
        assertEquals(1, adminu.size(), "ob drugi padli obdelavi admin dobi posto");
        assertTrue(adminu.get(0).zadeva().contains("cs_" + oznaka), adminu.get(0).zadeva());
        assertTrue(adminu.get(0).besedilo().contains("IllegalArgumentException"), adminu.get(0).besedilo());
        assertTrue(adminu.get(0).besedilo().contains(email), "admin mora vedeti, komu odgovoriti");

        // nadaljnje ponovitve niso nova sporocila
        assertThrows(IllegalArgumentException.class,
                () -> placila.obdelajDogodek(telo, TestniStripeDogodek.podpisi(telo)));
        assertEquals(1, adminu().size());
    }

    private List<PomnilniskiPosiljatelj.Sporocilo> adminu() {
        return posta.vsa().stream().filter(s -> s.prejemnik().equals(ADMIN)).toList();
    }
}
