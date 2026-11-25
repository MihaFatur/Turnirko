/* Ucenje mreze stevk (UcenjeMreze, F6).

   Domenska pravila, ki jih ta test varuje:
   - ucenje se brez dovolj novih stevk ne zacne; z njimi zapise razlicico z vsemi
     meritvami (nacin, preizkusne, MNIST, zlati nabor, trajanje) in jo sprejme
     samo, ce ne bere slabse;
   - nova mreza je zavrnjena, ce je slabsa na preizkusnih stevkah, ima vec napacnih
     celic na zlatem naboru ali na MNIST pade za vec kot eno odstotno tocko (od
     vgrajene skupaj za vec kot dve, F6.3);
   - preizkusne stevke se delijo po listku (ista pisava ni v obeh delih); brez
     dovolj preizkusnih se ucenje odlozi brez zapisa;
   - prekinjeno ucenje (casovna meja) se zapise brez utezi, mreza ostane stara (F6.5);
   - »Vrni prejsnjo« umakne razlicico v uporabi in nalozi prejsnjo sprejeto ali
     vgrajeno; umaknjena se ob zagonu ne nalozi vec (F6.4);
   - hkrati tece najvec eno ucenje, »Uci zdaj« ima premor in vse je samo za admina;
   - hramba obdrzi najnovejse stevke in razlicice ter vedno mrezo, ki jo bralnik
     uporablja. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.MrezeStevkDto;
import si.turnirko.dto.RazlicicaMrezeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.MrezaStevkZapis;
import si.turnirko.modeli.MrezaStevkZapis.Nacin;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.MrezaStevkRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class UcenjeMrezeTest extends IntegracijskiTest {

    @Autowired UcenjeMreze ucenje;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired VirMrezeStevk vir;
    @Autowired MrezaStevkRepozitorij mreze;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired ZlatiNabor zlatiNabor;
    @Autowired LokalniBralnikListka bralnik;
    @Autowired UporabnikRepozitorij uporabniki;

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};

    @AfterEach
    void pospravi() {
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), "vgrajena");
        ReflectionTestUtils.setField(ucenje, "zadnjiZagon", null);
        ((AtomicBoolean) ReflectionTestUtils.getField(ucenje, "tece")).set(false);
        SecurityContextHolder.clearContext();
    }

    @Test
    void ucenjeZacneSeleZDovoljStevkamiInZapiseRazlicico() throws IOException {
        assertNull(ucenje.uci(Nacin.NOC, 2, 1, UcenjeMreze.NAJDALJ), "brez novih stevk ni ucenja");
        List<StevkaListka> nove = mnistStevke(UcenjeMreze.NAJMANJ_NOVIH + 100);

        MrezaStevkZapis zapis = ucenje.uci(Nacin.ROCNO, 2, 1, UcenjeMreze.NAJDALJ);
        assertNotNull(zapis);
        assertEquals(nove.size(), zapis.getVzorcev());
        assertTrue(zapis.getNatancnost() > 0.9);
        assertEquals(Nacin.ROCNO, zapis.getNacin());
        // brez listka se deli po id: vsaka peta je preizkusna
        assertEquals(nove.size() / 5, zapis.getPreizkusnih().intValue());
        assertNotNull(zapis.getMnist());
        assertNull(zapis.getZlatih(), "prazen zlati nabor ne odloca");
        assertNotNull(zapis.getTrajanjeMs());
        if (zapis.isSprejeta()) {
            // sprejeta je samo razlicica, ki na preizkusnih stevkah ne bere slabse
            assertTrue(zapis.getNatancnost() >= zapis.getNatancnostPrej());
            assertNull(zapis.getRazlog());
            assertEquals("ucena-" + zapis.getId(), vir.trenutna().oznaka());
        } else {
            assertNotNull(zapis.getRazlog());
        }
        assertNull(ucenje.uci(Nacin.NOC, 2, 1, UcenjeMreze.NAJDALJ), "brez novih stevk po ucenju ni ponovnega ucenja");
    }

    @Test
    void zgodovinaJeVStanjuBrezUtezi() {
        prijava("admin-mreze@test", Vloga.ADMIN);
        MrezaStevkZapis sprejeta = mreze.save(new MrezaStevkZapis(utezi(), 500, 0.9f, 0.93f, true,
                LocalDateTime.now()).meritve(Nacin.NOC, 100, 0.98f, 0.981f, 30, 0.95f, 0.96f, 4000, null));
        mreze.save(new MrezaStevkZapis(null, 500, 0.93f, null, false, LocalDateTime.now())
                .meritve(Nacin.ROCNO, 100, null, null, null, null, null, 1800000, "Prekinjeno po 30 min."));
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), VirMrezeStevk.PREDPONA_UCENE + sprejeta.getId());

        MrezeStevkDto s = ucenje.stanje();
        assertEquals(sprejeta.getId(), s.vUporabi());
        assertEquals(UcenjeMreze.NAJMANJ_NOVIH, s.najmanjNovih());
        assertNull(s.stevkNaListek(), "brez stevk listkov ni ocene stevk na listek");
        assertEquals(2, s.razlicice().size());
        RazlicicaMrezeDto prekinjena = s.razlicice().getFirst();
        assertTrue(prekinjena.prekinjena());
        assertNull(prekinjena.natancnost());
        assertEquals(Nacin.ROCNO, prekinjena.nacin());
        RazlicicaMrezeDto prva = s.razlicice().get(1);
        assertTrue(prva.sprejeta());
        assertEquals(30, prva.zlatih().intValue());
        assertEquals(0.96f, prva.zlati().floatValue());
    }

    /* Lastnik 24. 11. 2026: stevke pravih listkov stejejo pri ucenju vec kot vadnica
       (pisava med tekmo je tista, ki jo mora mreza brati). */
    @Test
    void stevkeListkovStejejoVecKotVadnica() {
        UcenjeStevk.Vzorec mnist = new UcenjeStevk.Vzorec(new byte[784], 1);
        UcenjeStevk.Vzorec vadnica = new UcenjeStevk.Vzorec(new byte[784], 2);
        UcenjeStevk.Vzorec listek = new UcenjeStevk.Vzorec(new byte[784], 3);
        UcenjeStevk.Vzorec mocen = new UcenjeStevk.Vzorec(new byte[784], 4);
        List<UcenjeStevk.Vzorec> mesano = UcenjeMreze.zaUcenje(List.of(mnist), List.of(vadnica, listek, mocen),
                List.of(mocen), List.of(listek, mocen));
        assertEquals(1, mesano.stream().filter(v -> v == mnist).count());
        assertEquals(UcenjeMreze.UTEZ_STEVK, mesano.stream().filter(v -> v == vadnica).count());
        assertEquals(UcenjeMreze.UTEZ_STEVK + UcenjeMreze.DODATNA_UTEZ_LISTKOV,
                mesano.stream().filter(v -> v == listek).count());
        assertEquals(2 * UcenjeMreze.UTEZ_STEVK + UcenjeMreze.DODATNA_UTEZ_LISTKOV,
                mesano.stream().filter(v -> v == mocen).count());
    }

    @Test
    void razlogZavrnitvePoMerilih() {
        UcenjeMreze.Zlati zlatiEnako = new UcenjeMreze.Zlati(30, 0.95f, 0.95f, 10, 10);
        assertNull(UcenjeMreze.razlogZavrnitve(0.95, 0.95, 0.98, 0.975, 0.98, zlatiEnako),
                "izenaceno in malo MNIST je v redu");
        assertNull(UcenjeMreze.razlogZavrnitve(0.95, 0.97, 0.98, 0.98, 0.98, null), "brez zlatega nabora");
        assertTrue(UcenjeMreze.razlogZavrnitve(0.95, 0.94, 0.98, 0.98, 0.98, null).startsWith("Na preizkusnih"));
        assertTrue(UcenjeMreze.razlogZavrnitve(0.95, 0.97, 0.98, 0.98, 0.98,
                new UcenjeMreze.Zlati(30, 0.95f, 0.94f, 10, 12)).startsWith("Na zlatem naboru"));
        assertTrue(UcenjeMreze.razlogZavrnitve(0.95, 0.97, 0.98, 0.965, 0.98, null).startsWith("Na MNIST slabša"));
    }

    /* Vsako ucenje sme vzeti do eno tocko MNIST, vse skupaj pa najvec dve od vgrajene:
       sicer bi mreza po nekaj ucenjih po malem pozabila splosne oblike stevk. */
    @Test
    void mnistNeSmeZdrsnitiVecKotDveTockiOdVgrajene() {
        // prva razlicica: -0,9 tocke od vgrajene (21. 11. 2026, 99,5 % -> 98,6 %)
        assertNull(UcenjeMreze.razlogZavrnitve(0.64, 0.96, 0.995, 0.986, 0.995, null));
        // druga: od mreze v uporabi -0,9 tocke (v redu), od vgrajene -1,8 (v redu)
        assertNull(UcenjeMreze.razlogZavrnitve(0.96, 0.97, 0.986, 0.977, 0.995, null));
        // tretja: od mreze v uporabi -0,5 tocke, a od vgrajene -2,3: zavrnjena
        String razlog = UcenjeMreze.razlogZavrnitve(0.97, 0.98, 0.977, 0.972, 0.995, null);
        assertNotNull(razlog);
        assertTrue(razlog.startsWith("Na MNIST za več kot 2 odstotni točki slabša od vgrajene"), razlog);
    }

    /* Zlati nabor odloca, ko ima vsaj NAJMANJ_ZLATIH listkov: obe mrezi se izmerita
       na istih listkih in meritev se zapise. */
    @Test
    void zlatiNaborSeIzmeriPriSprejemu() throws IOException {
        Dogodek dogodek = pripraviDogodek(4);
        LokalniBralnikListka.Poravnan p = bralnik.poravnaj(SimuliraneFotografijeTest.fotografijaIzida(IZID,
                new Random(30)));
        MeritevBranja.Resnica resnica = MeritevBranja.resnica(UcenjeIzListkovTest.vnos(IZID, false), false);
        for (int i = 0; i < UcenjeMreze.NAJMANJ_ZLATIH; i++) {
            BranjeListka b = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now()));
            assertTrue(zlatiNabor.zapisi(b.getId(), p, resnica, 5, "testni"));
        }
        mnistStevke(UcenjeMreze.NAJMANJ_NOVIH + 100);

        MrezaStevkZapis zapis = ucenje.uci(Nacin.NOC, 2, 1, UcenjeMreze.NAJDALJ);
        assertNotNull(zapis);
        assertEquals(UcenjeMreze.NAJMANJ_ZLATIH, zapis.getZlatih().intValue());
        assertNotNull(zapis.getZlatiPrej());
        assertNotNull(zapis.getZlati());
        assertTrue(zapis.getZlatiPrej() > 0.8, "vgrajena mreza bere simulirane listke: " + zapis.getZlatiPrej());
    }

    @Test
    void prekinjenoUcenjeNeZamenjaMreze() throws IOException {
        mnistStevke(UcenjeMreze.NAJMANJ_NOVIH + 100);
        MrezaStevkZapis zapis = ucenje.uci(Nacin.ROCNO, 2, 1, Duration.ZERO);
        assertNotNull(zapis);
        assertNull(zapis.getUtezi());
        assertNull(zapis.getNatancnost());
        assertTrue(!zapis.isSprejeta() && zapis.getRazlog().startsWith("Prekinjeno"));
        assertEquals("vgrajena", vir.trenutna().oznaka());
        assertNull(ucenje.uci(Nacin.NOC, 2, 1, UcenjeMreze.NAJDALJ), "prekinjeno ucenje porabi nove stevke");
    }

    @Test
    void preizkusneStevkeSeDelijoPoListku() {
        for (long idBranje = 1; idBranje <= 10; idBranje++) {
            boolean prva = UcenjeMreze.jePreizkusna(stevka(idBranje, 1));
            for (long id = 2; id <= 20; id++) {
                assertEquals(prva, UcenjeMreze.jePreizkusna(stevka(idBranje, id)),
                        "vse stevke istega listka so v istem delu");
            }
        }
        long preizkusnih = LongStream.rangeClosed(1, 10)
                .filter(b -> UcenjeMreze.jePreizkusna(stevka(b, 99))).count();
        assertEquals(2, preizkusnih, "petina listkov");
    }

    @Test
    void brezPreizkusnihStevkSeUcenjeOdlozi() {
        // vse stevke z enega listka, ki ni preizkusni: nove mreze ni s cim primerjati
        Dogodek dogodek = pripraviDogodek(4);
        Long id = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now())).getId();
        while (Math.floorMod(id, 5L) == 0) {
            id = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now())).getId();
        }
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < UcenjeMreze.NAJMANJ_NOVIH + 50; i++) {
            nove.add(new StevkaListka(id, new byte[784], i % 10, true, LocalDateTime.now()));
        }
        stevke.saveAll(nove);
        assertNull(ucenje.uci(Nacin.NOC, 2, 1, UcenjeMreze.NAJDALJ));
        assertEquals(0, mreze.count(), "odlozeno ucenje ne zapise razlicice");
    }

    /* Ocena »listki brez napake« na strani ucenja: povprecno stevk na potrjen listek
       (stevke vadnice ne stejejo). PostgreSQL vrne avg kot numeric - poizvedba ga mora
       pretvoriti v double (pregled kode 24. 11. 2026). */
    @Test
    void povprecnoStevkNaListek() {
        prijava("admin-povprecje@test", Vloga.ADMIN);
        Dogodek dogodek = pripraviDogodek(4);
        Long prvo = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now())).getId();
        Long drugo = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now())).getId();
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nove.add(new StevkaListka(prvo, new byte[784], i % 10, true, LocalDateTime.now()));
        }
        for (int i = 0; i < 20; i++) {
            nove.add(new StevkaListka(drugo, new byte[784], i % 10, true, LocalDateTime.now()));
        }
        nove.add(new StevkaListka(null, new byte[784], 3, true, LocalDateTime.now()));   // vadnica
        stevke.saveAll(nove);
        assertEquals(15.0, ucenje.stanje().stevkNaListek(), 1e-9);
    }

    @Test
    void vrniPrejsnjoUmakneInNaloziPrejsnjo() {
        prijava("admin-vrni@test", Vloga.ADMIN);
        MrezaStevkZapis starejsa = mreze.save(new MrezaStevkZapis(utezi(), 0, 0.9f, 0.91f, true, LocalDateTime.now()));
        mreze.save(new MrezaStevkZapis(null, 0, 0.91f, null, false, LocalDateTime.now()));
        MrezaStevkZapis novejsa = mreze.save(new MrezaStevkZapis(utezi(), 0, 0.91f, 0.92f, true, LocalDateTime.now()));
        vir.nalozi();
        assertEquals(novejsa.getId(), vir.idTrenutne());

        MrezeStevkDto po = ucenje.vrniPrejsnjo();
        assertEquals(starejsa.getId(), po.vUporabi());
        assertEquals(starejsa.getId(), vir.idTrenutne());
        assertNotNull(mreze.findById(novejsa.getId()).orElseThrow().getUmaknjenaOb());
        vir.nalozi();
        assertEquals(starejsa.getId(), vir.idTrenutne(), "umaknjena se ob zagonu ne nalozi");

        assertNull(ucenje.vrniPrejsnjo().vUporabi(), "pred prvo sprejeto je vgrajena");
        assertEquals("vgrajena", vir.trenutna().oznaka());
        DomenskaIzjema e = assertThrows(DomenskaIzjema.class, () -> ucenje.vrniPrejsnjo());
        assertTrue(e.getMessage().contains("vgrajena"));
    }

    @Test
    void uciZdajPoveZakajNe() {
        prijava("admin-uci@test", Vloga.ADMIN);
        DomenskaIzjema premalo = assertThrows(DomenskaIzjema.class, () -> ucenje.uciZdaj());
        assertTrue(premalo.getMessage().contains("novih števk"), premalo.getMessage());

        ReflectionTestUtils.setField(ucenje, "zadnjiZagon", LocalDateTime.now());
        DomenskaIzjema premor = assertThrows(DomenskaIzjema.class, () -> ucenje.uciZdaj());
        assertTrue(premor.getMessage().contains("Znova ga lahko poženeš"), premor.getMessage());
        assertNotNull(ucenje.stanje().uciZdajOd());
        ReflectionTestUtils.setField(ucenje, "zadnjiZagon", null);

        // med ucenjem ni drugega ucenja ne vrnitve prejsnje
        mnistStevke(UcenjeMreze.NAJMANJ_NOVIH + 10);
        ((AtomicBoolean) ReflectionTestUtils.getField(ucenje, "tece")).set(true);
        assertEquals("Učenje že teče.", assertThrows(DomenskaIzjema.class, () -> ucenje.uciZdaj()).getMessage());
        assertTrue(assertThrows(DomenskaIzjema.class, () -> ucenje.vrniPrejsnjo()).getMessage()
                .startsWith("Učenje teče"));
        assertTrue(ucenje.stanje().uciSe());
    }

    @Test
    void samoZaAdmina() {
        prijava("organizator-mreze@test", Vloga.ORGANIZATOR);
        assertThrows(PrepovedanoIzjema.class, () -> ucenje.stanje());
        assertThrows(PrepovedanoIzjema.class, () -> ucenje.uciZdaj());
        assertThrows(PrepovedanoIzjema.class, () -> ucenje.vrniPrejsnjo());
    }

    @Test
    void hrambaObdrziNajnovejseInTrenutnoMrezo() {
        List<StevkaListka> shranjene = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            shranjene.add(new StevkaListka(null, new byte[784], i % 10, true, LocalDateTime.now()));
        }
        shranjene = stevke.saveAll(shranjene);
        long mejaId = shranjene.get(20).getId();
        List<MrezaStevkZapis> sprejete = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sprejete.add(mreze.save(new MrezaStevkZapis(new byte[] {1}, 0, 0.9f, 0.91f, true, LocalDateTime.now())));
        }
        for (int i = 0; i < 4; i++) {
            mreze.save(new MrezaStevkZapis(new byte[] {1}, 0, 0.9f, 0.8f, false, LocalDateTime.now()));
        }
        // bralnik uporablja najstarejso sprejeto (npr. po vrnitvi na prejsnjo)
        long trenutna = sprejete.getFirst().getId();
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), VirMrezeStevk.PREDPONA_UCENE + trenutna);
        assertEquals(trenutna, vir.idTrenutne());
        int[] izbrisano = ucenje.pocisti(10, 2, 1);
        assertEquals(20, izbrisano[0]);
        // sprejete: 5 - 2 najnovejsi - trenutna = 2; zavrnjene: 4 - 1 = 3
        assertEquals(5, izbrisano[1]);
        assertEquals(10, stevke.count());
        assertTrue(stevke.findAll().stream().allMatch(s -> s.getId() >= mejaId), "ostanejo najnovejse");
        List<Long> ostale = mreze.findAll().stream().filter(MrezaStevkZapis::isSprejeta)
                .map(MrezaStevkZapis::getId).sorted().toList();
        assertEquals(List.of(trenutna, sprejete.get(3).getId(), sprejete.get(4).getId()), ostale);
        assertEquals(1, mreze.findAll().stream().filter(m -> !m.isSprejeta()).count());
        assertArrayEquals(new int[] {0, 0}, ucenje.pocisti(10, 2, 1), "drugic ni cesa brisati");
        vir.nastavi(VirMrezeStevk.vgrajena().mreza(), "vgrajena");
        assertNull(vir.idTrenutne(), "vgrajena nima zapisa");
    }

    // ---------- Pomozno ----------

    /* Stevke MNIST brez listka (deli se po id: vsaka peta je preizkusna). */
    private List<StevkaListka> mnistStevke(int koliko) {
        List<UcenjeStevk.Vzorec> mnist;
        try (InputStream v = UcenjeMrezeTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            mnist = UcenjeStevk.beriIzbor(v);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < koliko; i++) {
            UcenjeStevk.Vzorec s = mnist.get(i);
            nove.add(new StevkaListka(null, s.slika(), s.oznaka(), true, LocalDateTime.now()));
        }
        return stevke.saveAll(nove);
    }

    /* Utezi vgrajene mreze (berljive, za vrnitev prejsnje). */
    private static byte[] utezi() {
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        try {
            VirMrezeStevk.vgrajena().mreza().zapisi(izhod);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return izhod.toByteArray();
    }

    /* Stevka z danim listkom in id (brez baze, za razdelitev). */
    private static StevkaListka stevka(long idBranje, long id) {
        StevkaListka s = new StevkaListka(idBranje, new byte[784], 1, true, LocalDateTime.now());
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private void prijava(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(prijavnoIme,
                null, List.of(new SimpleGrantedAuthority("ROLE_" + vloga.name()))));
    }
}
