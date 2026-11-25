/* Vadnica stevk (nacrt F3) na simuliranih fotografijah pravega lista.

   Vir: posnetek lista, kot ga natisne vmesnik (resources/branje/vadnica-list.png,
   brezglavi Chrome, 10 px/mm), v okenca so napisane stevke iz preizkusnega MNIST
   (kot SimuliraneFotografijeTest pri listku). Test zato varuje tudi ujemanje
   postavitve v vmesniku (ListVadnice.tsx) in zaledju (GeometrijaVadnice).

   Pravila, ki jih test varuje:
   - list se prebere (koda, poravnava A4, okenca), vsaj 95 % okenc da stevke z
     oznako vrstice;
   - na shranjeni sliki so samo okenca (koda, besedilo in oznake so bele);
   - listek tekme in list vadnice se ne zamenjata (razumljivo sporocilo);
   - stevke gredo v ucenje sele po odobritvi; izloci se lahko samo stevke lista;
   - odobren list se ne prebere znova in ne odobri dvakrat;
   - en list je v celoti ucni ali v celoti preizkusni (en pisec);
   - vse je samo za admina. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.branje.GeometrijaListka;
import si.turnirko.branje.GeometrijaVadnice;
import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.Temnost;
import si.turnirko.branje.UcenjeStevk;
import si.turnirko.dto.OdobritevVadniceVnos;
import si.turnirko.dto.VadnicaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class VadnicaTest extends IntegracijskiTest {

    /* Fotografija lista A4 s telefonom: pokoncno platno, list zasede vecino slike. */
    private static final int SIRINA_FOTO = 2400;
    private static final int VISINA_FOTO = 3200;

    private static BufferedImage list;
    private static long idLista;
    private static List<UcenjeStevk.Vzorec> mnist;
    private static MrezaStevk mreza;

    @Autowired VadnicaStoritev storitev;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired UporabnikRepozitorij uporabniki;
    @Autowired LokalniBralnikListka bralnik;

    @BeforeAll
    static void pripravi() throws IOException {
        try (InputStream v = VadnicaTest.class.getResourceAsStream("/branje/vadnica-list.png")) {
            list = ImageIO.read(v);
        }
        try (InputStream v = VadnicaTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            mnist = UcenjeStevk.beriIzbor(v);
        }
        List<KodaListka.KodaVadnice> kode = KodaListka.najdiVadnico(list);
        assertEquals(1, kode.size(), "cist list ima eno kodo vadnice");
        idLista = kode.getFirst().idLista();
        mreza = VirMrezeStevk.vgrajena().mreza();
    }

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Branje lista ----------

    @Test
    void cistListNimaStevk() throws IOException {
        BranjeVadnice.Prebrana p = BranjeVadnice.preberi(foto(kopija(list), new Random(1)), mreza);
        assertEquals(idLista, p.idLista());
        assertEquals(GeometrijaVadnice.V1.razlicica(), p.razlicica());
        assertTrue(p.stevke().isEmpty(), "stevk: " + p.stevke().size());
        assertEquals(GeometrijaVadnice.V1.okenc(), p.praznih());
    }

    @Test
    void izpolnjenListDaStevkeZOznakoVrstice() throws IOException {
        for (long seme : new long[] {20112026L, 7L, 42L, 77L}) {
            Random r = new Random(seme);
            BufferedImage fotografija = foto(izpolnjen(r), r);
            assertOkencaNaMestu(fotografija, seme);
            BranjeVadnice.Prebrana p = BranjeVadnice.preberi(fotografija, mreza);
            assertEquals(idLista, p.idLista());
            int okencZStevkami = GeometrijaVadnice.V1.okenc() - p.praznih() - p.preskocenih();
            assertTrue(okencZStevkami >= 0.95 * GeometrijaVadnice.V1.okenc(),
                    "okenc s stevkami " + okencZStevkami + ", praznih " + p.praznih() + ", preskocenih " + p.preskocenih());
            long prav = 0;
            for (BranjeVadnice.Stevka s : p.stevke()) {
                int vrstica = s.okence() / GeometrijaVadnice.V1.stolpcev();
                String zapis = String.valueOf(GeometrijaVadnice.V1.vrednosti()[vrstica]);
                assertTrue(zapis.indexOf('0' + s.vrednost()) >= 0, "stevka " + s.vrednost() + " v vrstici " + zapis);
                prav += s.mrezaPrav() ? 1 : 0;
            }
            double delez = (double) prav / p.stevke().size();
            System.out.printf("Vadnica (seme %d): %d stevk, %d okenc, mreza prav %.3f%n",
                    seme, p.stevke().size(), okencZStevkami, delez);
            assertTrue(delez >= 0.85, "mreza prebere prav le " + delez);
            assertSamoOkenca(p.slika());
        }
    }

    /* Razlicica 2 (24. 11. 2026): okenca 17 x 9 mm kot celica listka, 16 vrstic po 8.
       Natisnjen list (vadnica-list-v2.png) se prebere po svoji postavitvi, oznaka
       stevke je vrstica te razlicice; listi razlicice 1 se se vedno berejo (zgoraj). */
    @Test
    void listDrugeRazliciceDaStevkeZOznakoVrstice() throws IOException {
        BufferedImage list2;
        try (InputStream v = VadnicaTest.class.getResourceAsStream("/branje/vadnica-list-v2.png")) {
            list2 = ImageIO.read(v);
        }
        GeometrijaVadnice.Okenca v2 = GeometrijaVadnice.V2;
        for (long seme : new long[] {24112026L, 5L}) {
            Random r = new Random(seme);
            BranjeVadnice.Prebrana p = BranjeVadnice.preberi(foto(izpolnjen(list2, v2, r), r), mreza);
            assertEquals(2, p.razlicica());
            int okencZStevkami = v2.okenc() - p.praznih() - p.preskocenih();
            assertTrue(okencZStevkami >= 0.95 * v2.okenc(),
                    "okenc s stevkami " + okencZStevkami + ", praznih " + p.praznih() + ", preskocenih " + p.preskocenih());
            long prav = 0;
            for (BranjeVadnice.Stevka s : p.stevke()) {
                String zapis = String.valueOf(v2.vrednost(s.okence()));
                assertTrue(zapis.indexOf('0' + s.vrednost()) >= 0, "stevka " + s.vrednost() + " v vrstici " + zapis);
                prav += s.mrezaPrav() ? 1 : 0;
            }
            double delez = (double) prav / p.stevke().size();
            System.out.printf("Vadnica v2 (seme %d): %d stevk, %d okenc, mreza prav %.3f%n",
                    seme, p.stevke().size(), okencZStevkami, delez);
            assertTrue(delez >= 0.85, "mreza prebere prav le " + delez);
        }
        // cist list druge razlicice: koda je razlicica 2, vsa okenca prazna
        BranjeVadnice.Prebrana cist = BranjeVadnice.preberi(foto(kopija(list2), new Random(1)), mreza);
        assertEquals(2, cist.razlicica());
        assertEquals(v2.okenc(), cist.praznih());
    }

    /* Zasuk do 20 st., mocnejsa perspektiva in zamegljena fotografija: list se
       se prebere in okenca ostanejo na mestu. */
    @Test
    void zahtevnaFotografijaSePrebere() throws IOException {
        for (long seme : new long[] {3L, 11L}) {
            Random r = new Random(seme);
            BufferedImage fotografija = ImageIO.read(new ByteArrayInputStream(SimuliraneFotografijeTest.fotografija(
                    izpolnjen(r), r, SimuliraneFotografijeTest.ZAHTEVNO, SIRINA_FOTO, VISINA_FOTO, 1300, 200)));
            assertOkencaNaMestu(fotografija, seme);
            BranjeVadnice.Prebrana p = BranjeVadnice.preberi(fotografija, mreza);
            int okencZStevkami = GeometrijaVadnice.V1.okenc() - p.praznih() - p.preskocenih();
            long prav = p.stevke().stream().filter(BranjeVadnice.Stevka::mrezaPrav).count();
            System.out.printf("Vadnica zahtevno (seme %d): %d stevk, %d okenc, mreza prav %.3f%n",
                    seme, p.stevke().size(), okencZStevkami, (double) prav / p.stevke().size());
            assertTrue(okencZStevkami >= 0.9 * GeometrijaVadnice.V1.okenc(), "okenc s stevkami " + okencZStevkami);
            assertTrue(prav >= 0.85 * p.stevke().size(), "mreza prav " + prav + " od " + p.stevke().size());
        }
    }

    /* Sliko, ki jo kolega poslje po aplikaciji za sporocila, ta pomanjsa (WhatsApp
       na ~1600 px daljse stranice) in stisne. Pri 1280 px se list
       prebere kot izvirnik; pri 1000 px je mreza slabsa, pri 800 px kode QR ni vec
       mogoce prebrati (vmesnik tedaj prosi za izvirnik: MAJHNA_SLIKA). */
    @Test
    void slikaIzAplikacijeZaSporocilaSePrebere() throws IOException {
        for (long seme : new long[] {3L, 11L}) {
            Random r = new Random(seme);
            BufferedImage slika = stisnjena(foto(izpolnjen(r), r), 1280);
            BranjeVadnice.Prebrana p = BranjeVadnice.preberi(slika, mreza);
            int okencZStevkami = GeometrijaVadnice.V1.okenc() - p.praznih() - p.preskocenih();
            long prav = p.stevke().stream().filter(BranjeVadnice.Stevka::mrezaPrav).count();
            assertTrue(okencZStevkami >= 0.95 * GeometrijaVadnice.V1.okenc(), "okenc s stevkami " + okencZStevkami);
            assertTrue(prav >= 0.9 * p.stevke().size(), "mreza prav " + prav + " od " + p.stevke().size());
        }
    }

    @Test
    void listekInVadnicaSeNeZamenjata() throws IOException {
        byte[] listek = SimuliraneFotografijeTest.fotografijaIzida(new int[][] {{11, 5}, {11, 7}, {11, 9}}, new Random(3));
        BralnikListka.NapakaBranjaListka napaka = assertThrows(BralnikListka.NapakaBranjaListka.class,
                () -> BranjeVadnice.preberi(ImageIO.read(new ByteArrayInputStream(listek)), mreza));
        assertTrue(napaka.getMessage().startsWith("To je listek tekme"), napaka.getMessage());

        byte[] vadnica = SimuliraneFotografijeTest.fotografija(kopija(list), new Random(4),
                SimuliraneFotografijeTest.OBICAJNO, SIRINA_FOTO, VISINA_FOTO, 1450, 250);
        napaka = assertThrows(BralnikListka.NapakaBranjaListka.class, () -> bralnik.preberi(vadnica));
        assertTrue(napaka.getMessage().startsWith("To je list vadnice"), napaka.getMessage());
    }

    // ---------- Storitev ----------

    @Test
    void stevkeGredoVUcenjeSeleOdobrene() throws IOException {
        prijava("admin-vadnica@test", Vloga.ADMIN);
        byte[] foto = jpeg(izpolnjen(new Random(5)), new Random(5));
        LocalDateTime prej = LocalDateTime.now().minusSeconds(1);

        VadnicaDto list1 = storitev.sprejmi(foto);
        assertEquals(idLista, list1.id());
        assertFalse(list1.odobrena());
        assertTrue(list1.stevk() > 150, "stevk " + list1.stevk());
        assertEquals(list1.stevk(), stevke.countByIdVadnica(idLista));
        assertEquals(0, stevke.countByOdobrenoTrueAndUstvarjenoObAfter(prej), "neodobrene ne stejejo za ucenje");
        assertTrue(stevke.najnovejse(PageRequest.of(0, 1000)).stream().noneMatch(s -> idLista == nic(s.getIdVadnica())));

        // nova fotografija istega lista zamenja prejsnjo (stevke se ne podvojijo)
        VadnicaDto list2 = storitev.sprejmi(foto);
        assertEquals(list2.stevk(), stevke.countByIdVadnica(idLista));

        VadnicaDto.Podrobno p = storitev.podrobno(idLista);
        assertEquals(list2.stevk(), p.stevke().size());
        assertEquals(784, java.util.Base64.getDecoder().decode(p.stevke().getFirst().slika()).length);
        assertNotNull(storitev.slika(idLista));

        // izlociti se da samo stevko tega lista: tuja (z listka) ostane
        StevkaListka tuja = stevke.save(new StevkaListka(null, new byte[784], 3, true, LocalDateTime.now()));
        Long izlocena = p.stevke().getFirst().id();
        VadnicaDto odobren = storitev.odobri(idLista, new OdobritevVadniceVnos(List.of(izlocena, tuja.getId())));
        assertTrue(odobren.odobrena());
        assertEquals(list2.stevk() - 1, odobren.stevk());
        assertTrue(stevke.existsById(tuja.getId()), "tuja stevka ni izbrisana");
        assertFalse(stevke.existsById(izlocena));
        assertEquals(list2.stevk() - 1 + 1, stevke.countByOdobrenoTrueAndUstvarjenoObAfter(prej),
                "odobrene stevke lista (in tuja) stejejo za ucenje");
        assertTrue(stevke.najnovejse(PageRequest.of(0, 1000)).stream().anyMatch(s -> idLista == nic(s.getIdVadnica())));
        assertEquals(list2.stevk() - 1, storitev.seznam().stevkVUcenju());

        assertThrows(DomenskaIzjema.class, () -> storitev.odobri(idLista, new OdobritevVadniceVnos(List.of())));
        assertThrows(DomenskaIzjema.class, () -> storitev.sprejmi(foto));

        storitev.izbrisi(idLista);
        assertEquals(0, stevke.countByIdVadnica(idLista));
        assertThrows(NiNajdenoIzjema.class, () -> storitev.podrobno(idLista));
    }

    @Test
    void izpraznjenListSeNeOdobriInPrazenSeNeShrani() throws IOException {
        prijava("admin-vadnica2@test", Vloga.ADMIN);
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.sprejmi(jpeg(kopija(list), new Random(6))));
        storitev.sprejmi(jpeg(izpolnjen(new Random(8)), new Random(8)));
        List<Long> vse = storitev.podrobno(idLista).stevke().stream().map(VadnicaDto.Stevka::id).toList();
        assertThrows(DomenskaIzjema.class, () -> storitev.odobri(idLista, new OdobritevVadniceVnos(vse)));
        assertFalse(storitev.podrobno(idLista).list().odobrena());
    }

    @Test
    void enListJeVesUcniAliVesPreizkusni() {
        LocalDateTime zdaj = LocalDateTime.now();
        for (long id : new long[] {1_000_000_005L, 1_000_000_007L}) {
            for (int okence = 0; okence < 20; okence++) {
                StevkaListka s = StevkaListka.zVadnice(id, okence, new byte[784], okence % 10, true, zdaj);
                assertEquals(id % 5 == 0, UcenjeMreze.jePreizkusna(s), "list " + id + ", okence " + okence);
            }
        }
    }

    @Test
    void vadnicaJeSamoZaAdmina() {
        prijava("organizator-vadnica@test", Vloga.ORGANIZATOR);
        assertThrows(PrepovedanoIzjema.class, () -> storitev.seznam());
        assertThrows(PrepovedanoIzjema.class, () -> storitev.sprejmi(new byte[] {1, 2, 3}));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.podrobno(idLista));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.slika(idLista));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.odobri(idLista, new OdobritevVadniceVnos(List.of())));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.izbrisi(idLista));
    }

    // ---------- Pomozno ----------

    private static long nic(Long v) {
        return v == null ? -1 : v;
    }

    /* Najdena okenca so tam, kjer so natisnjena: notranjost se zacne takoj za
       crto, tudi kadar se je stevka dotika (»1« v »10« tik ob levem robu). Prej je
       bralnik potezo ob robu vzel za crto in prvo stevko odrezal. */
    private static void assertOkencaNaMestu(BufferedImage fotografija, long seme) {
        KodaListka.KodaVadnice koda = KodaListka.najdiVadnico(fotografija).getFirst();
        Temnost poravnan = si.turnirko.branje.PoravnavaListka.poravnaj(fotografija, koda.tocke(),
                GeometrijaVadnice.POSTAVITEV).slika();
        List<si.turnirko.branje.CeliceListka.Celica> okenca = si.turnirko.branje.CeliceVadnice.najdi(poravnan, GeometrijaVadnice.V1);
        int odmik = si.turnirko.branje.RazrezCelice.ODMIK;
        for (int i = 0; i < okenca.size(); i++) {
            double[] mm = GeometrijaVadnice.V1.okence(i);
            var c = okenca.get(i);
            // razrez zacne pri x0 + ODMIK: za crto 0,45 mm, najvec ~1 mm od natisnjenega
            // zunanjega roba (poravnava se zmoti za nekaj px, zato tudi malo cez)
            int levo = c.x0() + odmik - (int) Math.round(mm[0] * 10);
            int zgoraj = c.y0() + odmik - (int) Math.round(mm[1] * 10);
            int desno = (int) Math.round(mm[2] * 10) - (c.x1() - odmik);
            int spodaj = (int) Math.round(mm[3] * 10) - (c.y1() - odmik);
            for (int rob : new int[] {levo, zgoraj, desno, spodaj}) {
                assertTrue(rob >= -3 && rob <= 10, "seme " + seme + ", okence " + i + ": robovi " + levo + " "
                        + zgoraj + " " + desno + " " + spodaj + " px od natisnjenega roba");
            }
        }
    }

    /* Na sliki lista so samo okenca: zunaj okenc (z robom 1,5 mm) ni crnila. */
    private static void assertSamoOkenca(byte[] png) {
        Temnost t = ZlatiNabor.temnost(png);
        boolean[] vOkencu = new boolean[t.sirina * t.visina];
        for (int i = 0; i < GeometrijaVadnice.V1.okenc(); i++) {
            double[] mm = GeometrijaVadnice.V1.okence(i);
            for (int y = (int) ((mm[1] - 1.5) * 10); y <= (mm[3] + 1.5) * 10; y++) {
                for (int x = (int) ((mm[0] - 1.5) * 10); x <= (mm[2] + 1.5) * 10; x++) {
                    vOkencu[y * t.sirina + x] = true;
                }
            }
        }
        int zunaj = 0;
        int znotraj = 0;
        for (int i = 0; i < vOkencu.length; i++) {
            if (t.t[i] > 0) {
                if (vOkencu[i]) {
                    znotraj++;
                } else {
                    zunaj++;
                }
            }
        }
        assertEquals(0, zunaj, "zunaj okenc je vse belo (koda, besedilo, oznake)");
        assertTrue(znotraj > 5000, "stevke ostanejo: " + znotraj);
    }

    /* List z MNIST stevkami v vseh okencih (kemicni svincnik). */
    private static BufferedImage izpolnjen(Random r) {
        return izpolnjen(list, GeometrijaVadnice.V1, r);
    }

    private static BufferedImage izpolnjen(BufferedImage osnova, GeometrijaVadnice.Okenca okenca, Random r) {
        BufferedImage slika = kopija(osnova);
        double px = GeometrijaListka.PX_NA_MM;     // vir je posnet v 10 px/mm
        for (int i = 0; i < okenca.okenc(); i++) {
            double[] mm = okenca.okence(i);
            String zapis = String.valueOf(okenca.vrednost(i));
            double w = (mm[2] - mm[0]) * px;
            double h = (mm[3] - mm[1]) * px;
            boolean dvomestno = zapis.length() == 2;
            double visina = h * (dvomestno ? 0.55 + r.nextDouble() * 0.15 : 0.6 + r.nextDouble() * 0.2);
            double x = mm[0] * px + w * (dvomestno ? 0.12 + r.nextDouble() * 0.1 : 0.3 + r.nextDouble() * 0.2);
            double y = mm[1] * px + (h - visina) / 2 + (r.nextDouble() - 0.5) * h * 0.1;
            for (char znak : zapis.toCharArray()) {
                int stevka = znak - '0';
                UcenjeStevk.Vzorec v;
                do {
                    v = mnist.get(r.nextInt(mnist.size()));
                } while (v.oznaka() != stevka);
                x += SimuliraneFotografijeTest.narisiStevko(slika, v.slika(), x, y, visina) + px * (0.3 + r.nextDouble() * 0.6);
            }
        }
        return slika;
    }

    private static byte[] jpeg(BufferedImage slika, Random r) throws IOException {
        return SimuliraneFotografijeTest.fotografija(slika, r, SimuliraneFotografijeTest.OBICAJNO,
                SIRINA_FOTO, VISINA_FOTO, 1450, 250);
    }

    private static BufferedImage foto(BufferedImage slika, Random r) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg(slika, r)));
    }

    /* Kot aplikacija za sporocila: pomanjsa s povprecenjem na »stranica« px daljse
       stranice in shrani kot JPEG 70 %. */
    private static BufferedImage stisnjena(BufferedImage izvirnik, int stranica) throws IOException {
        double k = (double) stranica / Math.max(izvirnik.getWidth(), izvirnik.getHeight());
        int w = (int) Math.round(izvirnik.getWidth() * k);
        int h = (int) Math.round(izvirnik.getHeight() * k);
        BufferedImage manjsa = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        manjsa.getGraphics().drawImage(izvirnik.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        ImageWriter pisec = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam nastavitve = pisec.getDefaultWriteParam();
        nastavitve.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        nastavitve.setCompressionQuality(0.7f);
        try (ImageOutputStream o = ImageIO.createImageOutputStream(izhod)) {
            pisec.setOutput(o);
            pisec.write(null, new IIOImage(manjsa, null, null), nastavitve);
        } finally {
            pisec.dispose();
        }
        return ImageIO.read(new ByteArrayInputStream(izhod.toByteArray()));
    }

    private static BufferedImage kopija(BufferedImage izvor) {
        BufferedImage k = new BufferedImage(izvor.getWidth(), izvor.getHeight(), BufferedImage.TYPE_INT_RGB);
        k.getGraphics().drawImage(izvor, 0, 0, null);
        return k;
    }

    private void prijava(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + vloga.name()))));
    }
}
