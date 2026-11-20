/* Zlati nabor (ZlatiNabor, MeritevNabora) na simuliranih fotografijah pravega
   listka (SimuliraneFotografijeTest: stevke MNIST na posnetku listka z imeni).

   Pravila, ki jih test varuje:
   - na shranjeni sliki so samo celice s tockami; vse ostalo (imena, QR, oznake)
     je belo (zasebnost: nabor se hrani brez roka);
   - bralnik nad slike nabora prebere natanko isto kot nad fotografijo, zato je
     meritev nabora meritev pravega bralnika;
   - potrjen listek se zapise enkrat, z izidom v stolpcih listka;
   - vzorec obdrzi listke z najmanjsimi kljuci;
   - izvoz ne vsebuje povezave na branje (tekmo, igralce);
   - meritev nabora sme samo admin in izmeri vsak listek. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.Temnost;
import si.turnirko.dto.MeritevBranjaDto;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.modeli.ZlatiListek;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.repozitoriji.ZlatiListekRepozitorij;

class ZlatiNaborTest extends IntegracijskiTest {

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};

    @Autowired ZlatiNabor nabor;
    @Autowired MeritevNabora meritevNabora;
    @Autowired LokalniBralnikListka bralnik;
    @Autowired VirMrezeStevk vir;
    @Autowired UcenjeIzListkov ucenje;
    @Autowired ZlatiListekRepozitorij zlati;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired SlikaBranjaListkaRepozitorij slike;
    @Autowired UporabnikRepozitorij uporabniki;

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void naSlikiSoSamoCelice() throws IOException {
        LokalniBralnikListka.Poravnan p = bralnik.poravnaj(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(11)));
        byte[] png = ZlatiNabor.slikaCelic(p);
        Temnost shranjena = ZlatiNabor.temnost(png);
        boolean[] vCelici = maskaCelic(p);

        int crniloZunajPrej = 0;
        int crniloZunajPotem = 0;
        int crniloVCelicah = 0;
        for (int i = 0; i < vCelici.length; i++) {
            if (!vCelici[i]) {
                crniloZunajPrej += p.slika().t[i] > 0.5f ? 1 : 0;
                crniloZunajPotem += shranjena.t[i] > 0f ? 1 : 0;
            } else {
                crniloVCelicah += shranjena.t[i] > 0.5f ? 1 : 0;
            }
        }
        assertTrue(crniloZunajPrej > 2000, "na poravnanem listku so imena, QR in oznake: " + crniloZunajPrej);
        assertEquals(0, crniloZunajPotem, "zunaj celic je vse belo");
        assertTrue(crniloVCelicah > 1000, "stevke ostanejo: " + crniloVCelicah);
        assertTrue(png.length < 40_000, "PNG " + png.length + " B");
    }

    @Test
    void bralnikNadNaboromBereEnakoKotNadFotografijo() throws IOException {
        MrezaStevk mreza = vir.trenutna().mreza();
        for (int seme = 20; seme < 26; seme++) {
            LokalniBralnikListka.Poravnan p = bralnik.poravnaj(
                    SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(seme)));
            PrebranListek neposredno = LokalniBralnikListka.beri(LokalniBralnikListka.izrezi(p), mreza);
            LokalniBralnikListka.Poravnan izNabora =
                    new LokalniBralnikListka.Poravnan(ZlatiNabor.temnost(ZlatiNabor.slikaCelic(p)), p.tabela());
            assertEquals(neposredno, LokalniBralnikListka.beri(LokalniBralnikListka.izrezi(izNabora), mreza),
                    "seme " + seme);
        }
    }

    @Test
    void potrditevZapiseListekEnkratVStolpcihListka() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(12)));
        // prijava 1 je desni stolpec listka: izid tekme je obrnjen glede na listek
        ucenje.zabelezi(id, MeritevBranjaTest.vnos(IZID, true), true, 5, "testni");
        ucenje.zabelezi(id, MeritevBranjaTest.vnos(IZID, true), true, 5, "testni");

        List<ZlatiListek> vsi = zlati.findAll();
        assertEquals(1, vsi.size(), "isti listek samo enkrat");
        ZlatiNabor.Nalozen n = ZlatiNabor.nalozi(vsi.getFirst());
        assertEquals(IZID.length, n.resnica().nizi().size());
        for (int i = 0; i < IZID.length; i++) {
            assertArrayEquals(IZID[i], n.resnica().nizi().get(i), "niz " + (i + 1) + " v stolpcih listka");
        }
        assertEquals(3, n.resnica().rezultatLevo());
        assertEquals(1, n.resnica().rezultatDesno());
        assertEquals(5, n.zapis().getSteviloNizov());
        assertEquals("testni", n.zapis().getModel());
        assertEquals(5, n.poravnan().tabela().nizi().size(), "listek za 5 nizov");
    }

    @Test
    void listekBrezTockPoNizihNeGreVNabor() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(13)));
        ucenje.zabelezi(id, new si.turnirko.dto.VnosRezultata(null, 3, 1, null, List.of()), false, 5, "testni");
        assertEquals(0, zlati.count());
    }

    @Test
    void vzorecObdrziNajmanjseKljuce() {
        for (double kljuc : new double[] {0.5, 0.1, 0.9, 0.3, 0.7}) {
            zlati.save(new ZlatiListek(null, new byte[] {1}, "{}", "{}", 5, null, kljuc, LocalDateTime.now()));
        }
        assertEquals(2, zlati.obdrziNajmanjse(3));
        List<Double> ostali = zlati.findAll().stream().map(ZlatiListek::getKljuc).sorted().toList();
        assertEquals(List.of(0.1, 0.3, 0.5), ostali);
        assertEquals(1, zlati.countByKljucLessThan(0.2));
    }

    @Test
    void izvozBrezPovezaveNaTekmo() throws IOException {
        Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(14)));
        ucenje.zabelezi(id, MeritevBranjaTest.vnos(IZID, false), false, 5, "testni");
        Long idZlatega = zlati.findAll().getFirst().getId();

        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        nabor.izvozi(izhod);
        List<String> imena = new ArrayList<>();
        String json = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(izhod.toByteArray()))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                imena.add(e.getName());
                byte[] vsebina = zip.readAllBytes();
                if (e.getName().endsWith(".json")) {
                    json = new String(vsebina, StandardCharsets.UTF_8);
                }
            }
        }
        assertEquals(List.of("opis.txt", "listki/" + idZlatega + ".png", "listki/" + idZlatega + ".json", "konec.txt"), imena);
        assertTrue(json.contains("\"resnica\"") && json.contains("\"celice\""), json);
        assertFalse(json.toLowerCase().contains("branje"), "brez id branja: " + json);
    }

    @Test
    void meritevNaboraIzmeriVsakListekInJeSamoZaAdmina() throws IOException {
        for (int seme = 30; seme < 32; seme++) {
            Long id = branje(SimuliraneFotografijeTest.fotografijaIzida(IZID, new Random(seme)));
            ucenje.zabelezi(id, MeritevBranjaTest.vnos(IZID, false), false, 5, "testni");
        }
        MeritevBranjaDto.Skupina s = meritevNabora.izmeri(vir.trenutna().mreza(), n -> {});
        assertEquals(2, s.izmerjenih());
        MeritevBranjaDto.Celice c = s.celice();
        int vseh = c.pravZanesljive() + c.pravNegotove() + c.narobeNegotove() + c.narobeZanesljive();
        // 4 nizi (8) in Rezultat, ce je prebran (2) na listek
        assertTrue(vseh >= 16 && vseh <= 20, "celic " + vseh);
        assertTrue(c.pravZanesljive() + c.pravNegotove() >= vseh - 3, c.toString());

        racun("organizator-nabor@test", Vloga.ORGANIZATOR);
        prijava("organizator-nabor@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> meritevNabora.stanje());
        assertThrows(PrepovedanoIzjema.class, () -> meritevNabora.zazeni());
        racun("admin-nabor@test", Vloga.ADMIN);
        prijava("admin-nabor@test", "ROLE_ADMIN");
        assertEquals(2, meritevNabora.stanje().listkov());
    }

    // ---------- Pomozno ----------

    private static boolean[] maskaCelic(LokalniBralnikListka.Poravnan p) {
        Temnost t = p.slika();
        boolean[] m = new boolean[t.sirina * t.visina];
        List<CeliceListka.Celica> celice = new ArrayList<>();
        for (CeliceListka.Vrstica v : p.tabela().nizi()) {
            celice.add(v.levo());
            celice.add(v.desno());
        }
        celice.add(p.tabela().rezultat().levo());
        celice.add(p.tabela().rezultat().desno());
        for (CeliceListka.Celica c : celice) {
            for (int y = c.y0(); y <= c.y1(); y++) {
                for (int x = c.x0(); x <= c.x1(); x++) {
                    m[y * t.sirina + x] = true;
                }
            }
        }
        return m;
    }

    private Long branje(byte[] foto) {
        Dogodek dogodek = pripraviDogodek(4);
        BranjeListka b = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now()));
        slike.save(new SlikaBranjaListka(b.getId(), foto, 2000, 1500));
        return b.getId();
    }

    private void racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
    }

    private static void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
