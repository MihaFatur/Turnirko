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
     in jo sprejme samo, ce na preizkusnih stevkah ne bere slabse. */
package si.turnirko.storitve;

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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;

class UcenjeIzListkovTest extends IntegracijskiTest {

    @Autowired UcenjeIzListkov ucenje;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired SlikaBranjaListkaRepozitorij slike;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired VirMrezeStevk vir;

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};
    // 11 7 | 9 11 | 11 5 | 11 8 = 12 stevk v nizih, rezultat 3 | 1 = 2
    private static final int STEVK = 14;
    // sklenjeno napisanih dvomestnih vrednosti razrez vcasih ne razdeli
    private static final int NAJMANJ = 10;

    @Test
    void potrjenListekDaStevke() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(4)));
        int zapisanih = ucenje.zabelezi(id, vnos(IZID, false), false);
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
        int zapisanih = ucenje.zabelezi(id, vnos(IZID, true), null);
        assertTrue(zapisanih >= NAJMANJ, "zapisanih " + zapisanih);
        assertTrue(stevke.findAll().stream().filter(StevkaListka::isMrezaPrav).count() >= zapisanih - 2);
    }

    @Test
    void posebenIzidNeDaStevk() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(6)));
        VnosRezultata brezBoja = new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 1, null);
        assertEquals(0, ucenje.zabelezi(id, brezBoja, false));
    }

    @Test
    void ucenjeZacneSeleZDovoljStevkamiInZapiseRazlicico() throws IOException {
        assertNull(ucenje.uci(2, 1), "brez novih stevk ni ucenja");
        List<UcenjeStevk.Vzorec> mnist;
        try (InputStream v = UcenjeIzListkovTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            mnist = UcenjeStevk.beriIzbor(v);
        }
        List<StevkaListka> nove = new ArrayList<>();
        for (int i = 0; i < UcenjeIzListkov.NAJMANJ_NOVIH + 50; i++) {
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

    // ---------- Pomozno ----------

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
