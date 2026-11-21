/* Ucenje bralnika iz potrjenih listkov (UcenjeIzListkov).

   Domenska pravila, ki jih ta test varuje:
   - ob potrditvi se iz fotografije zapisejo stevke s potrjenimi vrednostmi
     (celice nizov in rezultata, brez imen); celica, ki je razrez ne razdeli na
     pravo stevilo stevk (sklenjena »11«), se preskoci - napacna oznaka bi ucenje
     pokvarila;
   - posebni izidi (brez boja) ne dajo stevk;
   - pri listku brez kode (neznana smer stolpcev) se izbere smer, v kateri se
     mreza strinja s potrjenim izidom;
   - nocno ucenje se brez dovolj novih stevk ne zacne; z njimi zapise razlicico
     in jo sprejme samo, ce na preizkusnih stevkah ne bere slabse;
   - preizkusne stevke se delijo po listku (ista pisava ni v obeh delih); brez
     dovolj preizkusnih se ucenje odlozi brez zapisa;
   - hramba obdrzi najnovejse stevke in razlicice ter vedno mrezo, ki jo bralnik
     uporablja. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.MrezaStevkZapis;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.MrezaStevkRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;

class UcenjeIzListkovTest extends IntegracijskiTest {

    @Autowired UcenjeIzListkov ucenje;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired SlikaBranjaListkaRepozitorij slike;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired VirMrezeStevk vir;
    @Autowired MrezaStevkRepozitorij mreze;

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};
    // 11 7 | 9 11 | 11 5 | 11 8 = 12 stevk v nizih, rezultat 3 | 1 = 2
    private static final int STEVK = 14;
    // sklenjeno napisanih dvomestnih vrednosti razrez vcasih ne razdeli
    private static final int NAJMANJ = 10;

    @Test
    void potrjenListekDaStevke() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(4)));
        int zapisanih = ucenje.zabelezi(id, vnos(IZID, false), false, 5, "testni");
        assertTrue(zapisanih >= NAJMANJ && zapisanih <= STEVK, "zapisanih " + zapisanih);
        List<StevkaListka> vse = stevke.findAll();
        assertEquals(zapisanih, vse.size());
        assertTrue(vse.stream().allMatch(s -> s.getSlika().length == 784 && s.getIdBranje().equals(id)));
        // oznake so prave: mreza se z vecino strinja
        assertTrue(vse.stream().filter(StevkaListka::isMrezaPrav).count() >= zapisanih - 2);
    }

    @Test
    void brezSmeriSeIzbereTaKiSeUjemaZMrezo() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(5)));
        // potrjen izid v straneh tekme je obrnjen (prijava 1 je desni stolpec listka)
        int zapisanih = ucenje.zabelezi(id, vnos(IZID, true), null, 5, "testni");
        assertTrue(zapisanih >= NAJMANJ, "zapisanih " + zapisanih);
        assertTrue(stevke.findAll().stream().filter(StevkaListka::isMrezaPrav).count() >= zapisanih - 2);
    }

    @Test
    void posebenIzidNeDaStevk() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(6)));
        VnosRezultata brezBoja = new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 1, null);
        assertEquals(0, ucenje.zabelezi(id, brezBoja, false, 5, "testni"));
    }

    @Test
    void ucenjeZacneSeleZDovoljStevkamiInZapiseRazlicico() throws IOException {
        assertNull(ucenje.uci(2, 1), "brez novih stevk ni ucenja");
        List<UcenjeStevk.Vzorec> mnist;
        try (InputStream v = UcenjeIzListkovTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            mnist = UcenjeStevk.beriIzbor(v);
        }
        List<StevkaListka> nove = new ArrayList<>();
        // brez listka se deli po id: vsaka peta je preizkusna, 300 -> 60 (najmanj 50)
        for (int i = 0; i < UcenjeIzListkov.NAJMANJ_NOVIH + 100; i++) {
            UcenjeStevk.Vzorec s = mnist.get(i);
            nove.add(new StevkaListka(null, s.slika(), s.oznaka(), true, LocalDateTime.now()));
        }
        stevke.saveAll(nove);

        MrezaStevkZapis zapis = ucenje.uci(2, 1);
        assertNotNull(zapis);
        assertEquals(nove.size(), zapis.getVzorcev());
        assertTrue(zapis.getNatancnost() > 0.9);
        if (zapis.isSprejeta()) {
            // sprejeta je samo razlicica, ki na preizkusnih stevkah ne bere slabse
            assertTrue(zapis.getNatancnost() >= zapis.getNatancnostPrej());
            assertEquals("ucena-" + zapis.getId(), vir.trenutna().oznaka());
            vir.nastavi(VirMrezeStevk.vgrajena().mreza(), "vgrajena");
        }
        assertNull(ucenje.uci(2, 1), "brez novih stevk po ucenju ni ponovnega ucenja");
    }

    @Test
    void preizkusneStevkeSeDelijoPoListku() {
        for (long idBranje = 1; idBranje <= 10; idBranje++) {
            boolean prva = UcenjeIzListkov.jePreizkusna(stevka(idBranje, 1));
            for (long id = 2; id <= 20; id++) {
                assertEquals(prva, UcenjeIzListkov.jePreizkusna(stevka(idBranje, id)),
                        "vse stevke istega listka so v istem delu");
            }
        }
        long preizkusnih = LongStream.rangeClosed(1, 10)
                .filter(b -> UcenjeIzListkov.jePreizkusna(stevka(b, 99))).count();
        assertEquals(2, preizkusnih, "petina listkov");
    }

    @Test
    void brezPreizkusnihStevkSeUcenjeOdlozi() throws IOException {
        // vse stevke z enega listka, ki ni preizkusni: nove mreze ni s cim primerjati
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(7)));
        while (Math.floorMod(id, 5L) == 0) {
            id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(8)));
        }
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < UcenjeIzListkov.NAJMANJ_NOVIH + 50; i++) {
            nove.add(new StevkaListka(id, new byte[784], i % 10, true, LocalDateTime.now()));
        }
        stevke.saveAll(nove);
        assertNull(ucenje.uci(2, 1));
        assertEquals(0, mreze.count(), "odlozeno ucenje ne zapise razlicice");
    }

    /* F2.5: popravljena celica (precrtano in prepisano) ne da stevk za ucenje - razrez
       v njej zajame tudi precrtano vrednost. */
    @Test
    void popravljenaCelicaNeGreVUcenje() {
        RazrezCelice.Stevka s = new RazrezCelice.Stevka(new float[784]);
        RazrezCelice.Vsebina dveStevki = new RazrezCelice.Vsebina(false,
                List.of(new RazrezCelice.Razlaga(List.of(s, s), 1)), 0.05, 2, 0.3);
        RazrezCelice.Vsebina popravljena = new RazrezCelice.Vsebina(false,
                List.of(new RazrezCelice.Razlaga(List.of(s, s), 1)), 0.05, 2, 0.8);
        RazrezCelice.Vsebina enaStevka = new RazrezCelice.Vsebina(false,
                List.of(new RazrezCelice.Razlaga(List.of(s), 1)), 0.03, 1, 0.1);
        LokalniBralnikListka.Izrez izrez = new LokalniBralnikListka.Izrez(List.of(
                new RazrezCelice.Vsebina[] {dveStevki, enaStevka},
                new RazrezCelice.Vsebina[] {popravljena, enaStevka}),
                new RazrezCelice.Vsebina[] {enaStevka, enaStevka});
        List<NizVnos> nizi = List.of(new NizVnos(11, 5), new NizVnos(11, 7));
        VnosRezultata izid = new VnosRezultata(IzidTekme.IGRANO, 2, 0, null, nizi);
        List<StevkaListka> zapis = UcenjeIzListkov.oznaci(1L, izrez, nizi, izid, false,
                MrezaStevk.nakljucna(new Random(1)));
        // 11 in 5 v prvem nizu, samo 7 v drugem (popravljena 11 izpade), rezultat 2 in 0
        assertTrue(popravljena.popravljena());
        assertEquals(6, zapis.size());
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
        try {
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
        } finally {
            vir.nastavi(VirMrezeStevk.vgrajena().mreza(), "vgrajena");
        }
        assertNull(vir.idTrenutne(), "vgrajena nima zapisa");
    }

    // ---------- Pomozno ----------

    /* Stevka z danim listkom in id (brez baze, za razdelitev). */
    private static StevkaListka stevka(long idBranje, long id) {
        StevkaListka s = new StevkaListka(idBranje, new byte[784], 1, true, LocalDateTime.now());
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private Long branje(byte[] foto) {
        Dogodek dogodek = pripraviDogodek(4);
        BranjeListka b = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now()));
        slike.save(new SlikaBranjaListka(b.getId(), foto, 2000, 1500));
        return b.getId();
    }

    /* Potrjen izid v straneh tekme; obrnjeno = prijava 1 je desni stolpec listka. */
    private static VnosRezultata vnos(int[][] izid, boolean obrnjeno) {
        List<NizVnos> nizi = new ArrayList<>();
        int a = 0;
        int b = 0;
        for (int[] n : izid) {
            nizi.add(obrnjeno ? new NizVnos(n[1], n[0]) : new NizVnos(n[0], n[1]));
            if (n[0] > n[1]) {
                a++;
            } else {
                b++;
            }
        }
        return new VnosRezultata(null, obrnjeno ? b : a, obrnjeno ? a : b, null, nizi);
    }
}
