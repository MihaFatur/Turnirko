/* Dostava mnozicnih obvestil po poti, ki tece na strezniku (najdba E2, V66):
   kanal, ki posiljanje odlozi do potrditve transakcije, dostava na izvajalcu
   obvestilaIzvajalec, vsak prejemnik zabelezen v svoji transakciji.

   Testi obvestil po kolu in po turnirju tecejo s pomnilniskim kanalom, kjer je
   dostava takojsnja in v transakciji testa - te poti ne vidijo. Zato je ta
   test namenoma BREZ @Transactional in s svojim kanalom; storitev sestavi sam
   (isti repozitorij, izvajalec in upravljalec transakcij kot aplikacija).
   Svoje vrstice (racuni, turnir) pobrise sam. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import si.turnirko.modeli.DostavaObvestila;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.IzidDostave;
import si.turnirko.posta.PosiljateljPoste;
import si.turnirko.posta.SporocilaPoste;
import si.turnirko.repozitoriji.DostavaObvestilaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class DostavaObvestilPoPotrditviTest {

    @Autowired private DostavaObvestilaRepozitorij repozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private TurnirRepozitorij turnirRepozitorij;
    @Autowired private PlatformTransactionManager upravljalecTransakcij;
    @Autowired @Qualifier("obvestilaIzvajalec") private Executor izvajalec;

    /* Kanal kot pravi streznik: posiljanje odlozi do potrditve (privzeto),
       izid dostave doloci test po naslovu. */
    private static final class Kanal implements PosiljateljPoste {
        final Map<String, IzidDostave> izidi = new ConcurrentHashMap<>();
        final List<String> klici = new CopyOnWriteArrayList<>();
        final List<String> niti = new CopyOnWriteArrayList<>();
        volatile CountDownLatch zapora;

        @Override
        public void poslji(String prejemnik, String zadeva, String besedilo) {
            throw new UnsupportedOperationException("mnozicna obvestila gredo skozi dostavi()");
        }

        @Override
        public IzidDostave dostavi(String prejemnik, SporocilaPoste.Besedilo besedilo) {
            klici.add(prejemnik);
            niti.add(Thread.currentThread().getName());
            CountDownLatch cakaj = zapora;
            if (cakaj != null) {
                try {
                    cakaj.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException prekinitev) {
                    Thread.currentThread().interrupt();
                }
            }
            return izidi.getOrDefault(prejemnik, IzidDostave.POSLANO);
        }
    }

    private final String oznaka = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final Kanal kanal = new Kanal();
    private final List<Uporabnik> racuni = new ArrayList<>();
    private DostavaObvestilStoritev dostava;
    private TransactionTemplate transakcija;
    private Turnir turnir;
    private DostavaObvestilStoritev.Kljuc kljuc;

    @BeforeEach
    void pripravi() {
        dostava = new DostavaObvestilStoritev(kanal, repozitorij, izvajalec, upravljalecTransakcij);
        transakcija = new TransactionTemplate(upravljalecTransakcij);
        turnir = new Turnir();
        turnir.setIme("Dostava " + oznaka);
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnir = turnirRepozitorij.save(turnir);
        kljuc = DostavaObvestilStoritev.Kljuc.turnir(turnir.getId());
        for (int i = 1; i <= 5; i++) {
            Uporabnik u = new Uporabnik("dostava" + i + "-" + oznaka + "@test.si", "{bcrypt}x", Vloga.NAVIJAC);
            u.setStatus(StatusRacuna.CAKA);
            u.setEmailPotrjenOb(LocalDateTime.now());
            racuni.add(uporabnikRepozitorij.save(u));
        }
    }

    @AfterEach
    void pospravi() {
        if (kanal.zapora != null) {
            kanal.zapora.countDown();
        }
        pocakajNaKonec();
        // vrstice dostave gredo z racunom in turnirjem (ON DELETE CASCADE)
        uporabnikRepozitorij.deleteAll(racuni);
        turnirRepozitorij.delete(turnir);
    }

    /* Do potrditve se ne poslje nic; po njej gre dostava na izvajalca obvestil
       (ne na nit klicalca) in vsak prejemnik dobi svoj zapis: poslano ali
       zavrnjen poskus. Zavrnjeni caka naprej, poslana ne. */
    @Test
    void poPotrditviDostaviNaSvojemIzvajalcuInZabeleziVsakegaPosebej() {
        kanal.izidi.put(naslov(1), IzidDostave.ZAVRNJENO);

        transakcija.executeWithoutResult(stanje -> {
            assertEquals(3, dostava.oddaj(kljuc, posiljke(0, 1, 2)));
            assertTrue(kanal.klici.isEmpty(), "pred potrditvijo transakcije se ne poslje nic");
            assertTrue(dostava.vTeku(kljuc));
        });
        pocakajNaKonec();

        assertEquals(List.of(naslov(0), naslov(1), naslov(2)), kanal.klici);
        assertTrue(kanal.niti.stream().allMatch(n -> n.startsWith("obvestila-")), kanal.niti.toString());
        List<DostavaObvestila> zapisi = repozitorij.findByIdTurnir(turnir.getId());
        assertEquals(3, zapisi.size());
        assertTrue(zapis(zapisi, 0).jePoslano());
        assertFalse(zapis(zapisi, 1).jePoslano());
        assertEquals(1, zapis(zapisi, 1).getPoskusov());
        assertTrue(zapis(zapisi, 2).jePoslano());
        assertEquals(2, dostava.poslanih(kljuc));
        assertEquals(Set.of(id(1)), dostava.cakajo(kljuc, Set.of(id(0), id(1), id(2))));
    }

    /* Razveljavljena transakcija opravila: sporocila ne gredo ven (zeton odjave
       v njih ne bi bil zapisan) in dostava ni vec "v teku". */
    @Test
    void razveljavljenaTransakcijaNePosljeNicesar() throws InterruptedException {
        transakcija.executeWithoutResult(stanje -> {
            dostava.oddaj(kljuc, posiljke(0, 1));
            stanje.setRollbackOnly();
        });
        Thread.sleep(200);

        assertTrue(kanal.klici.isEmpty());
        assertFalse(dostava.vTeku(kljuc));
        assertTrue(repozitorij.findByIdTurnir(turnir.getId()).isEmpty());
    }

    /* Streznik poste ne dela: dostava se po nekaj zaporednih izpadih prekine,
       nikomur se ne steje poskus in vsi cakajo naprej. */
    @Test
    void izpadKanalaPrekineDostavoInSeNeStejeNikomur() {
        for (int i = 0; i < 5; i++) {
            kanal.izidi.put(naslov(i), IzidDostave.KANAL_NE_DELA);
        }

        transakcija.executeWithoutResult(stanje -> dostava.oddaj(kljuc, posiljke(0, 1, 2, 3, 4)));
        pocakajNaKonec();

        assertEquals(DostavaObvestilStoritev.NAJVEC_ZAPOREDNIH_IZPADOV, kanal.klici.size(),
                "po izpadih se ostalih ne poskusa");
        assertTrue(repozitorij.findByIdTurnir(turnir.getId()).isEmpty());
        assertEquals(5, dostava.cakajo(kljuc, Set.of(id(0), id(1), id(2), id(3), id(4))).size());
    }

    /* Dokler dostava tece, se ista (naslednji zagon opravila) ne odda se enkrat:
       prejemniki brez zapisa bi sicer dobili sporocilo dvakrat. */
    @Test
    void medDostavoSeIstaNeOddaSeEnkrat() {
        kanal.zapora = new CountDownLatch(1);

        transakcija.executeWithoutResult(stanje -> dostava.oddaj(kljuc, posiljke(0, 1)));
        pocakaj(() -> !kanal.klici.isEmpty());
        assertTrue(dostava.vTeku(kljuc));
        int znova = transakcija.execute(stanje -> dostava.oddaj(kljuc, posiljke(0, 1)));
        assertEquals(0, znova, "dostava v teku se ne podvoji");

        kanal.zapora.countDown();
        pocakajNaKonec();
        assertEquals(List.of(naslov(0), naslov(1)), kanal.klici, "vsak prejemnik natanko enkrat");
        assertEquals(2, dostava.poslanih(kljuc));
    }

    // ---------- Pomozno ----------

    private String naslov(int i) {
        return racuni.get(i).getUporabniskoIme();
    }

    private Long id(int i) {
        return racuni.get(i).getId();
    }

    private List<DostavaObvestilStoritev.Posiljka> posiljke(int... kateri) {
        List<DostavaObvestilStoritev.Posiljka> posiljke = new ArrayList<>();
        for (int i : kateri) {
            posiljke.add(new DostavaObvestilStoritev.Posiljka(id(i), naslov(i),
                    new SporocilaPoste.Besedilo("Zadeva", "Besedilo")));
        }
        return posiljke;
    }

    private DostavaObvestila zapis(List<DostavaObvestila> zapisi, int i) {
        return zapisi.stream().filter(z -> z.getIdRacun().equals(id(i))).findFirst().orElseThrow();
    }

    private void pocakajNaKonec() {
        pocakaj(() -> !dostava.vTeku(kljuc));
    }

    private static void pocakaj(java.util.function.BooleanSupplier pogoj) {
        long rok = System.currentTimeMillis() + 10_000;
        while (!pogoj.getAsBoolean()) {
            if (System.currentTimeMillis() > rok) {
                throw new AssertionError("cakanje na dostavo je poteklo");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException prekinitev) {
                Thread.currentThread().interrupt();
                throw new AssertionError(prekinitev);
            }
        }
    }
}
