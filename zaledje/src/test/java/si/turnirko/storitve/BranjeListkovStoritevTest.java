/* Branje listkov s fotografije od nalaganja do potrditve (BranjeListkovStoritev).

   Bralnik je tu nadomestek (TestniBralnik) - klic API ni del testa; QR koda,
   prepoznava tekme, predlog in potrditev pa tecejo kot v produkciji.

   Domenska pravila, ki jih ta test varuje:
   - fotografija s QR kodo da predlog za pravo tekmo; potrditev vpise izid po
     poti rocnega vnosa in zabelezi, ali je bil predlog popravljen (merilo);
   - brez kode se tekma najde po imenih, tudi ce sta zamenjani strani;
   - dva listka na fotografiji ne izbereta tekme sama;
   - napaka bralnika ohrani tekmo po kodi, izid se vpise rocno;
   - ze vpisana tekma je opozorilo in je ni mogoce potrditi znova;
   - branje sme lastnik turnirja ali admin, in dokler je vklopljen preizkus
     (samo-admin), samo admin;
   - fotografija se izbrise 30 dni po turnirju, vrstica ostane;
   - branja, ki jih je prekinil ponovni zagon, postanejo napaka. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import si.turnirko.dto.BranjaTurnirjaDto;
import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Import(BranjeListkovStoritevTest.Nastavitev.class)
class BranjeListkovStoritevTest extends IntegracijskiTest {

    @TestConfiguration
    static class Nastavitev {
        @Bean
        @Primary
        TestniBralnik testniBralnik() {
            return new TestniBralnik();
        }
    }

    /* Vrne prepis, ki ga test pripravi, ali vrze napako. */
    static class TestniBralnik implements BralnikListka {
        PrebranListek naslednji;
        RuntimeException napaka;

        @Override
        public boolean jeNastavljen() {
            return true;
        }

        @Override
        public Branje preberi(byte[] jpeg) {
            if (napaka != null) {
                throw napaka;
            }
            return new Branje(naslednji, "testni");
        }
    }

    @Autowired BranjeListkovStoritev storitev;
    @Autowired TestniBralnik bralnik;
    @Autowired BranjeListkaRepozitorij branjaRepozitorij;
    @Autowired SlikaBranjaListkaRepozitorij slikeRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;

    private Dogodek dogodek;
    private Tekma tekma;

    @BeforeEach
    void pripravi() {
        bralnik.naslednji = null;
        bralnik.napaka = null;
        dogodek = pripraviDogodek(4);
        String[][] imena = {{"Ana", "Novak"}, {"Tim", "Kranjc"}, {"Luka", "Zupan"}, {"Nik", "Horvat"}};
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        for (int i = 0; i < prijave.size(); i++) {
            Igralec igralec = prijave.get(i).getIgralec();
            igralec.setIme(imena[i][0]);
            igralec.setPriimek(imena[i][1]);
            igralecRepozitorij.save(igralec);
        }
        zrebStoritev.izvediZreb(dogodek.getId());
        tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        racun("admin-branje@test", Vloga.ADMIN);
        prijava("admin-branje@test", "ROLE_ADMIN");
    }

    @AfterEach
    void pocisti() {
        storitev.nastaviSamoAdmin(true);
        SecurityContextHolder.clearContext();
    }

    @Test
    void fotografijaSKodoDaPredlogInPotrditevVpiseIzid() throws IOException {
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}, 3, 1);
        BranjeListkaDto sprejeto = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId()));
        assertEquals(StanjeBranja.V_VRSTI, sprejeto.stanje());

        storitev.obdelaj(sprejeto.id());
        BranjeListkaDto prebrano = eno();
        assertEquals(StanjeBranja.PREBRANO, prebrano.stanje());
        assertEquals(PrepoznavaTekme.KODA, prebrano.prepoznava());
        assertEquals(tekma.getId(), prebrano.tekma().tekma().id());
        PredlogIzidaDto predlog = prebrano.predlog();
        assertTrue(predlog.pripravljen(), predlog.opozorila().toString());
        assertEquals(3, predlog.dobljeniNizi1());

        BranjeListkaDto potrjeno = storitev.potrdi(sprejeto.id(), new PotrditevBranjaVnos(null, vnos(predlog)));
        assertEquals(StanjeBranja.POTRJENO, potrjeno.stanje());
        assertEquals(Boolean.FALSE, potrjeno.popravljeno());
        Tekma koncana = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(StatusTekme.KONCANA, koncana.getStatus());
        assertEquals(3, koncana.getDobljeniNizi1());
        assertEquals(1, koncana.getDobljeniNizi2());
        assertEquals(4, nizRepozitorij.findAll().stream()
                .filter(n -> n.getTekma().getId().equals(tekma.getId())).count());

        BranjaTurnirjaDto seznam = storitev.seznam(idTurnirja());
        assertEquals(1, seznam.potrjenih());
        assertEquals(1, seznam.brezPopravka());
    }

    @Test
    void popravljenPredlogSeZabelezi() throws IOException {
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);
        PredlogIzidaDto predlog = eno().predlog();

        VnosRezultata popravljen = new VnosRezultata(null, 3, 0, null,
                List.of(new NizVnos(11, 1), new NizVnos(11, 9), new NizVnos(11, 5)));
        assertEquals(Boolean.TRUE, storitev.potrdi(id, new PotrditevBranjaVnos(null, popravljen)).popravljeno());
        assertEquals(0, storitev.seznam(idTurnirja()).brezPopravka());
        assertNotNull(predlog);
    }

    @Test
    void brezKodeTekmoNajdejoImenaTudiObrnjeno() throws IOException {
        // sodnik je na prazen listek najprej napisal igralca 2
        bralnik.naslednji = prepis(priimek(tekma.getPrijava2()), priimek(tekma.getPrijava1()),
                new int[][] {{7, 11}, {11, 9}, {4, 11}, {8, 11}}, 1, 3);
        Long id = storitev.sprejmi(idTurnirja(), fotografija()).id();
        storitev.obdelaj(id);

        BranjeListkaDto b = eno();
        assertEquals(PrepoznavaTekme.IMENA, b.prepoznava());
        assertEquals(tekma.getId(), b.tekma().tekma().id());
        // levi stolpec je bil prijava 2: zmagovalec je prijava 1 s 3:1
        assertEquals(3, b.predlog().dobljeniNizi1());
        assertEquals(1, b.predlog().dobljeniNizi2());
        assertEquals(11, b.predlog().nizi().getFirst().tocke1());
    }

    @Test
    void neznanaImenaPonudijoKandidateInOrganizatorIzbere() throws IOException {
        bralnik.naslednji = prepis(priimek(tekma.getPrijava1()), "Nečitljivo",
                new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija()).id();
        storitev.obdelaj(id);

        BranjeListkaDto b = eno();
        assertNull(b.tekma());
        assertFalse(b.kandidati().isEmpty());
        BranjeListkaDto.KandidatListka kandidat = b.kandidati().getFirst();
        assertEquals(tekma.getId(), kandidat.tekma().tekma().id());

        BranjeListkaDto potrjeno = storitev.potrdi(id,
                new PotrditevBranjaVnos(kandidat.tekma().tekma().id(), vnos(kandidat.predlog())));
        assertEquals(PrepoznavaTekme.ROCNO, potrjeno.prepoznava());
        assertEquals(StatusTekme.KONCANA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());
    }

    @Test
    void dvaListkaNaFotografijiNeIzbereta() throws IOException {
        Tekma druga = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA && !t.getId().equals(tekma.getId()))
                .findFirst().orElseThrow();
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId(), "TK:" + druga.getId())).id();
        storitev.obdelaj(id);

        BranjeListkaDto b = eno();
        assertNull(b.tekma());
        assertTrue(b.napaka().contains("več listkov"));
    }

    @Test
    void napakaBralnikaOhraniTekmoPoKodi() throws IOException {
        bralnik.napaka = new BralnikListka.NapakaBranjaListka("Ni povezave s strežnikom za branje.");
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);

        BranjeListkaDto b = eno();
        assertEquals(StanjeBranja.NAPAKA, b.stanje());
        assertEquals("Ni povezave s strežnikom za branje.", b.napaka());
        assertEquals(tekma.getId(), b.tekma().tekma().id());
        assertNull(b.predlog());

        VnosRezultata rocno = new VnosRezultata(null, 3, 0, null, List.of());
        BranjeListkaDto potrjeno = storitev.potrdi(id, new PotrditevBranjaVnos(null, rocno));
        assertEquals(Boolean.TRUE, potrjeno.popravljeno(), "brez prepisa je vse vpisal organizator");
    }

    @Test
    void zeVpisanaTekmaJeOpozoriloInSeNePotrdiZnova() throws IOException {
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, List.of()));

        PredlogIzidaDto predlog = eno().predlog();
        assertFalse(predlog.pripravljen());
        assertTrue(predlog.opozorila().getFirst().contains("že vpisan"));
        assertThrows(DomenskaIzjema.class, () -> storitev.potrdi(id, new PotrditevBranjaVnos(null, vnos(predlog))));
    }

    @Test
    void imenaSeNeUjemajoSKodo() throws IOException {
        bralnik.naslednji = prepis("Peter Petek", "Jure Jurič", new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);

        PredlogIzidaDto predlog = eno().predlog();
        assertFalse(predlog.pripravljen());
        assertTrue(predlog.opozorila().stream().anyMatch(o -> o.contains("se ne ujemata s tekmo")));
    }

    @Test
    void dostopLastnikAdminInPreizkus() throws IOException {
        Uporabnik lastnik = racun("lastnik-branje@test", Vloga.ORGANIZATOR);
        racun("tujec-branje@test", Vloga.ORGANIZATOR);
        Turnir turnir = dogodek.getTurnir();
        turnir.setUstvaril(lastnik);
        turnirRepozitorij.save(turnir);
        byte[] slika = fotografija("TK:" + tekma.getId());

        prijava("lastnik-branje@test", "ROLE_ORGANIZATOR");
        assertFalse(storitev.stanje().omogoceno(), "v preizkusu samo admin");
        assertThrows(PrepovedanoIzjema.class, () -> storitev.sprejmi(idTurnirja(), slika));

        storitev.nastaviSamoAdmin(false);
        assertTrue(storitev.stanje().omogoceno());
        assertNotNull(storitev.sprejmi(idTurnirja(), slika));

        prijava("tujec-branje@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> storitev.seznam(idTurnirja()));

        SecurityContextHolder.clearContext();
        assertFalse(storitev.stanje().omogoceno());
    }

    @Test
    void zavrzenoInPotrjenoSeNeDaPonoviti() throws IOException {
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);
        assertEquals(StanjeBranja.V_VRSTI, storitev.znova(id).stanje(), "prebrano se sme prebrati znova");
        storitev.obdelaj(id);
        storitev.zavrzi(id);
        assertEquals(StanjeBranja.ZAVRZENO, eno().stanje());
        assertThrows(DomenskaIzjema.class, () -> storitev.znova(id));
        assertThrows(DomenskaIzjema.class, () -> storitev.zavrzi(id));
    }

    @Test
    void fotografijaSeIzbrise30DniPoTurnirju() {
        Turnir turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(LocalDate.now().minusDays(40));
        turnirRepozitorij.save(turnir);
        BranjeListka staro = shraniBranje(turnir, LocalDateTime.now().minusDays(40));
        BranjeListka novo = shraniBranje(turnir, LocalDateTime.now().minusDays(1));

        assertEquals(1, storitev.izbrisiStareSlike(LocalDate.now()));
        assertTrue(slikeRepozitorij.findById(staro.getId()).isEmpty());
        assertTrue(slikeRepozitorij.findById(novo.getId()).isPresent(), "listek vnesen pozneje ostane 30 dni");
        assertNotNull(branjaRepozitorij.findById(staro.getId()).orElseThrow().getSlikaIzbrisanaOb());
        assertEquals(0, storitev.izbrisiStareSlike(LocalDate.now()));
    }

    @Test
    void prekinjenaBranjaPostanejoNapaka() {
        BranjeListka b = shraniBranje(dogodek.getTurnir(), LocalDateTime.now());
        assertTrue(storitev.prekiniOstalaVVrsti() >= 1);
        BranjeListka po = branjaRepozitorij.findById(b.getId()).orElseThrow();
        assertEquals(StanjeBranja.NAPAKA, po.getStanje());
        assertTrue(po.getNapaka().contains("ponovni zagon"));
    }

    // ---------- Pomozno ----------

    private Long idTurnirja() {
        return dogodek.getTurnir().getId();
    }

    private BranjeListkaDto eno() {
        List<BranjeListkaDto> branja = storitev.seznam(idTurnirja()).branja();
        assertEquals(1, branja.size());
        return branja.getFirst();
    }

    private BranjeListka shraniBranje(Turnir turnir, LocalDateTime ob) {
        BranjeListka b = branjaRepozitorij.save(new BranjeListka(turnir, null, ob));
        slikeRepozitorij.save(new SlikaBranjaListka(b.getId(), new byte[] {1, 2, 3}, 10, 10));
        return b;
    }

    private static String ime(Prijava p) {
        return p.getIgralec().getIme() + " " + p.getIgralec().getPriimek();
    }

    private static String priimek(Prijava p) {
        return p.getIgralec().getPriimek();
    }

    private static PrebranListek prepis(String levo, String desno, int[][] tocke, int rezLevo, int rezDesno) {
        List<PrebranListek.Niz> nizi = new ArrayList<>();
        for (int[] niz : tocke) {
            nizi.add(new PrebranListek.Niz(niz[0], niz[1], false, false));
        }
        return new PrebranListek(true, null, levo, desno, false, null, null, nizi,
                rezLevo, rezDesno, false, "NI", null, null);
    }

    private static VnosRezultata vnos(PredlogIzidaDto p) {
        return new VnosRezultata(p.izidTip() == IzidTekme.IGRANO ? null : p.izidTip(),
                p.dobljeniNizi1(), p.dobljeniNizi2(), p.zmagovalecStran(),
                p.nizi().stream().map(n -> new NizVnos(n.tocke1(), n.tocke2())).toList());
    }

    /* JPEG 1600 x 1200 s QR kodami (ali brez), kot ga poslje telefon. */
    private static byte[] fotografija(String... kode) throws IOException {
        BufferedImage slika = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        try {
            g.setColor(new Color(0xF0, 0xEE, 0xE8));
            g.fillRect(0, 0, 1600, 1200);
            int x = 200;
            for (String koda : kode) {
                if (koda == null) {
                    continue;
                }
                g.drawImage(MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(koda,
                        BarcodeFormat.QR_CODE, 200, 200, Map.of(EncodeHintType.MARGIN, 1))), x, 300, null);
                x += 700;
            }
        } catch (com.google.zxing.WriterException e) {
            throw new IOException(e);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        ImageIO.write(slika, "jpg", izhod);
        return izhod.toByteArray();
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
