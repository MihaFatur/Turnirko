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
   - branja, ki jih je prekinil ponovni zagon, postanejo napaka;
   - dostop se preveri PRED dekodiranjem, prevelika fotografija se zavrne, cez
     mejo hkratnih dekodiranj zahteva dobi »zasedeno« in ne shrani nicesar;
   - med fotografijami istega listka ostane odprta ena (najnovejsa uspesno
     prebrana), potrditev zapre ostale. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.time.Duration;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import si.turnirko.dto.BranjaTurnirjaDto;
import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.CeliceListkaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.ZasedenoIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;
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
        Poravnava poravnava;

        @Override
        public boolean jeNastavljen() {
            return true;
        }

        @Override
        public Branje preberi(byte[] jpeg) {
            if (napaka != null) {
                throw napaka;
            }
            return new Branje(naslednji, "testni", poravnava);
        }
    }

    @Autowired BranjeListkovStoritev storitev;
    @Autowired TestniBralnik bralnik;
    @Autowired BranjeListkaRepozitorij branjaRepozitorij;
    @Autowired SlikaBranjaListkaRepozitorij slikeRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired MizeStoritev mize;

    private Dogodek dogodek;
    private Tekma tekma;

    @BeforeEach
    void pripravi() {
        bralnik.naslednji = null;
        bralnik.napaka = null;
        bralnik.poravnava = null;
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
        storitev.nastaviDelezKontrole(0);
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

    /* F4.1: poravnan listek in lega celic gresta v pregled; ponovno branje ju
       zamenja (brez poravnave izrezov ni), izbris fotografije vzame tudi izreze. */
    @Test
    void poravnanListekInLegaCelicZaIzrez() throws IOException {
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}, 3, 1);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        int[] vrstica = {220, 400, 560, 470, 630, 400, 970, 470};
        bralnik.poravnava = new BralnikListka.Poravnava(jpeg,
                new CeliceListkaDto(1050, 990, List.of(vrstica, vrstica, vrstica, vrstica, vrstica), vrstica));
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);

        BranjeListkaDto prebrano = eno();
        assertNotNull(prebrano.celice());
        assertEquals(1050, prebrano.celice().sirina());
        assertEquals(5, prebrano.celice().nizi().size());
        assertArrayEquals(vrstica, prebrano.celice().rezultat());
        assertFalse(prebrano.obrnjeno(), "po kodi listek ni obrnjen");
        assertArrayEquals(jpeg, storitev.poravnana(id));

        bralnik.poravnava = null;
        storitev.znova(id);
        storitev.obdelaj(id);
        assertNull(eno().celice());
        assertThrows(NiNajdenoIzjema.class, () -> storitev.poravnana(id));
    }

    /* F4.4: fotografija listka pri tekmi samo po potrditvi, samo lastniku turnirja
       in adminu, dokler fotografija obstaja. */
    @Test
    void listekTekmePoPotrditviSamoZaLastnika() throws IOException {
        Uporabnik lastnik = racun("lastnik-listek@test", Vloga.ORGANIZATOR);
        racun("tujec-listek@test", Vloga.ORGANIZATOR);
        Turnir turnir = dogodek.getTurnir();
        turnir.setUstvaril(lastnik);
        turnirRepozitorij.save(turnir);
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}, 3, 1);
        byte[] foto = fotografija("TK:" + tekma.getId());
        Long id = storitev.sprejmi(idTurnirja(), foto).id();
        storitev.obdelaj(id);
        assertThrows(NiNajdenoIzjema.class, () -> storitev.listekTekme(tekma.getId()), "pred potrditvijo ni listka");

        storitev.potrdi(id, new PotrditevBranjaVnos(null, vnos(eno().predlog())));
        byte[] shranjena = slikeRepozitorij.findById(id).orElseThrow().getVsebina();
        assertArrayEquals(shranjena, storitev.listekTekme(tekma.getId()));

        storitev.nastaviSamoAdmin(false);
        prijava("lastnik-listek@test", "ROLE_ORGANIZATOR");
        assertArrayEquals(shranjena, storitev.listekTekme(tekma.getId()));
        prijava("tujec-listek@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> storitev.listekTekme(tekma.getId()));
        assertThrows(PrepovedanoIzjema.class, () -> storitev.poravnana(id));

        prijava("admin-branje@test", "ROLE_ADMIN");
        slikeRepozitorij.deleteById(id);
        assertThrows(NiNajdenoIzjema.class, () -> storitev.listekTekme(tekma.getId()), "po 30 dneh ni fotografije");
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

    // ---------- Utrditev (F0, docs/NACRT-BRANJE-LISTKOV.md) ----------

    @Test
    void prevelikaFotografijaSeZavrnePredDekodiranjem() throws IOException {
        // 5000 x 4100 = 20,5 Mpx: pod splosno mejo slik (40 Mpx), nad mejo fotografije listka
        NeveljavenVnosIzjema napaka = assertThrows(NeveljavenVnosIzjema.class,
                () -> storitev.sprejmi(idTurnirja(), png(5000, 4100)));
        assertTrue(napaka.getMessage().contains("prevelika"), napaka.getMessage());
        assertTrue(branjaRepozitorij.findAll().isEmpty());
        assertEquals(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ, storitev.semaforDekodiranja().availablePermits(),
                "dovoljenje za dekodiranje se vrne tudi po napaki");
    }

    @Test
    void zasedenoDekodiranjeVrneZasedenoInNeShraniNicesar() throws Exception {
        byte[] slika = fotografija("TK:" + tekma.getId());
        storitev.nastaviCakanjeNaDekodiranje(Duration.ofMillis(50));
        storitev.semaforDekodiranja().acquire(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ);
        try {
            ZasedenoIzjema napaka = assertThrows(ZasedenoIzjema.class, () -> storitev.sprejmi(idTurnirja(), slika));
            assertEquals(BranjeListkovStoritev.PONOVI_CEZ_SEKUND, napaka.getCezSekund());
            assertTrue(branjaRepozitorij.findAll().isEmpty(), "zasedena zahteva ne shrani nicesar");
        } finally {
            storitev.semaforDekodiranja().release(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ);
            storitev.nastaviCakanjeNaDekodiranje(BranjeListkovStoritev.CAKANJE_NA_DEKODIRANJE);
        }
        assertNotNull(storitev.sprejmi(idTurnirja(), slika).id(), "ko je dovoljenje prosto, gre skozi");
        assertEquals(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ, storitev.semaforDekodiranja().availablePermits());
    }

    @Test
    void enUporabnikZasedeNajvecEnoDekodiranje() throws Exception {
        byte[] slika = fotografija("TK:" + tekma.getId());
        Long idPrvega = uporabnikRepozitorij.findByUporabniskoIme("admin-branje@test").orElseThrow().getId();
        Uporabnik drugi = racun("admin-drugi@test", Vloga.ADMIN);
        int n = 0;
        while (storitev.pasUporabnika(drugi.getId()) == storitev.pasUporabnika(idPrvega)) {
            drugi = racun("admin-drugi-" + (++n) + "@test", Vloga.ADMIN);
        }
        storitev.nastaviCakanjeNaDekodiranje(Duration.ofMillis(50));
        // prvi uporabnik ze dekodira (njegov pas je zaseden)
        storitev.pasUporabnika(idPrvega).acquire();
        try {
            assertThrows(ZasedenoIzjema.class, () -> storitev.sprejmi(idTurnirja(), slika));
            assertEquals(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ, storitev.semaforDekodiranja().availablePermits(),
                    "cakajoca zahteva istega uporabnika ne zasede skupnega dovoljenja");
            prijava(drugi.getUporabniskoIme(), "ROLE_ADMIN");
            assertNotNull(storitev.sprejmi(idTurnirja(), slika).id(), "drugi uporabnik ni blokiran");
        } finally {
            storitev.pasUporabnika(idPrvega).release();
            storitev.nastaviCakanjeNaDekodiranje(BranjeListkovStoritev.CAKANJE_NA_DEKODIRANJE);
        }
    }

    @Test
    void dvojnikiNeZaprejoBranjaBrezKode() throws IOException {
        // odprto branje iste tekme, ki ni bila ugotovljena po kodi (organizator jo se potrdi)
        BranjeListka poImenih = shraniBranje(dogodek.getTurnir(), LocalDateTime.now().minusMinutes(1));
        poImenih.setStanje(StanjeBranja.PREBRANO);
        poImenih.setTekma(tekma);
        poImenih.setPrepoznava(PrepoznavaTekme.IMENA);
        branjaRepozitorij.save(poImenih);

        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long poKodi = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(poKodi);

        assertEquals(StanjeBranja.PREBRANO, branjaRepozitorij.findById(poImenih.getId()).orElseThrow().getStanje());
        assertEquals(StanjeBranja.PREBRANO, branjaRepozitorij.findById(poKodi).orElseThrow().getStanje());
    }

    @Test
    void dostopSePreveriPredDekodiranjem() {
        racun("tujec-dekodiranje@test", Vloga.ORGANIZATOR);
        storitev.nastaviSamoAdmin(false);
        prijava("tujec-dekodiranje@test", "ROLE_ORGANIZATOR");
        // neveljavna slika: ce bi se dekodirala pred preverbo dostopa, bi bila napaka 400, ne 403
        assertThrows(PrepovedanoIzjema.class, () -> storitev.sprejmi(idTurnirja(), new byte[] {1, 2, 3}));
        assertEquals(BranjeListkovStoritev.HKRATNIH_DEKODIRANJ, storitev.semaforDekodiranja().availablePermits());
    }

    @Test
    void novejsaFotografijaIstegaListkaNadomestiStarejso() throws IOException {
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long prva = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(prva);
        PredlogIzidaDto predlog = branje(prva).predlog();
        Long druga = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(druga);

        BranjeListka starejsa = branjaRepozitorij.findById(prva).orElseThrow();
        assertEquals(StanjeBranja.ZAVRZENO, starejsa.getStanje());
        assertEquals(BranjeListkovStoritev.NADOMESCENO, starejsa.getNapaka());
        assertEquals(StanjeBranja.PREBRANO, branje(druga).stanje());
        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> storitev.potrdi(prva, new PotrditevBranjaVnos(null, vnos(predlog))));
        assertTrue(napaka.getMessage().contains("nadomestila"), napaka.getMessage());
    }

    @Test
    void neuspelaNovejsaFotografijaNeNadomestiPrebrane() throws IOException {
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long prva = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(prva);
        bralnik.napaka = new BralnikListka.NapakaBranjaListka("Fotografija je zamegljena.");
        Long druga = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(druga);

        assertEquals(StanjeBranja.PREBRANO, branje(prva).stanje(), "uspesno prebrana ostane");
        BranjeListka zaprta = branjaRepozitorij.findById(druga).orElseThrow();
        assertEquals(StanjeBranja.ZAVRZENO, zaprta.getStanje());
        assertEquals(BranjeListkovStoritev.VELJA_STAREJSA, zaprta.getNapaka(), "razlog pove, da velja starejsa");
    }

    @Test
    void obviseloBranjeSeSmePrebratiZnova() {
        BranjeListka sveze = shraniBranje(dogodek.getTurnir(), LocalDateTime.now());
        BranjeListka obviselo = shraniBranje(dogodek.getTurnir(), LocalDateTime.now().minusMinutes(5));
        assertFalse(branje(sveze.getId()).obviselo());
        assertTrue(branje(obviselo.getId()).obviselo());
        assertThrows(DomenskaIzjema.class, () -> storitev.znova(sveze.getId()), "branje, ki se se bere, ne");
        assertEquals(StanjeBranja.V_VRSTI, storitev.znova(obviselo.getId()).stanje());
    }

    @Test
    void potrditevZapreDrugeOdprteFotografijeIsteTekme() throws IOException {
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long prva = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(prva);
        // druga odprta fotografija iste tekme (npr. obe zakljuceni hkrati)
        BranjeListka druga = shraniBranje(dogodek.getTurnir(), LocalDateTime.now());
        druga.setStanje(StanjeBranja.NAPAKA);
        druga.setTekma(tekma);
        druga.setPrepoznava(PrepoznavaTekme.KODA);
        branjaRepozitorij.save(druga);

        storitev.potrdi(prva, new PotrditevBranjaVnos(null, vnos(branje(prva).predlog())));
        BranjeListka zaprta = branjaRepozitorij.findById(druga.getId()).orElseThrow();
        assertEquals(StanjeBranja.ZAVRZENO, zaprta.getStanje());
        assertEquals(BranjeListkovStoritev.POTRJENO_DRUGJE, zaprta.getNapaka());
    }

    // ---------- Pomozno ----------

    @Test
    void kontrolaNiZaEnDotikInPotrditevZapiseSledPregleda() throws IOException {
        // nastavitev nad 1 obvelja kot 1: vsak pripravljen predlog je kontrola
        storitev.nastaviDelezKontrole(7);
        bralnik.naslednji = prepis(ime(tekma.getPrijava1()), ime(tekma.getPrijava2()),
                new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}, 3, 1);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);

        BranjeListkaDto b = eno();
        assertTrue(b.kontrola());
        assertFalse(b.predlog().pripravljen(), "kontrola ni ponujena za en dotik");
        assertTrue(b.predlog().opozorila().contains(BranjeListkovStoritev.KONTROLA));

        // ponovno branje kontrole ne odstrani (organizator se ji ne more izogniti)
        storitev.nastaviDelezKontrole(0);
        storitev.znova(id);
        storitev.obdelaj(id);
        b = eno();
        assertTrue(b.kontrola());

        BranjeListkaDto potrjeno = storitev.potrdi(id,
                new PotrditevBranjaVnos(null, vnos(b.predlog()), 4200L, NacinPotrditve.OBRAZEC));
        assertEquals(Boolean.FALSE, potrjeno.popravljeno());
        BranjeListka zapis = branjaRepozitorij.findById(id).orElseThrow();
        assertTrue(zapis.isKontrola());
        assertEquals(Boolean.TRUE, zapis.getPripravljen(), "brez kontrole bi bil ponujen za en dotik");
        assertEquals(4200, zapis.getCasPregledaMs());
        assertEquals(NacinPotrditve.OBRAZEC, zapis.getNacinPotrditve());
    }

    @Test
    void nepripravljenPredlogNiKontrolaInNesmiselenCasSeNeZapise() throws IOException {
        storitev.nastaviDelezKontrole(1);
        // imeni se ne ujemata s tekmo: predlog ni pripravljen, kontrola bi bila brez pomena
        bralnik.naslednji = prepis("Peter Petek", "Jure Jurič", new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tekma.getId())).id();
        storitev.obdelaj(id);
        BranjeListkaDto b = eno();
        assertFalse(b.kontrola());

        storitev.potrdi(id, new PotrditevBranjaVnos(null, vnos(b.predlog()), 3_600_001L, NacinPotrditve.EN_DOTIK));
        BranjeListka zapis = branjaRepozitorij.findById(id).orElseThrow();
        assertNull(zapis.getCasPregledaMs(), "nad uro je pozabljeno okno");
        assertEquals(NacinPotrditve.EN_DOTIK, zapis.getNacinPotrditve());
        // z opozorilom o imenih predlog ni bil ponujen za en dotik
        assertEquals(Boolean.FALSE, zapis.getPripravljen());

        // druga tekma (prva ima izid ze vpisan, kar bi kontrolo izkljucilo samo)
        Tekma druga = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA && !t.getId().equals(tekma.getId()))
                .findFirst().orElseThrow();
        storitev.nastaviDelezKontrole(Double.NaN);
        bralnik.naslednji = prepis(null, null, new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);
        Long brezKontrole = storitev.sprejmi(idTurnirja(), fotografija("TK:" + druga.getId())).id();
        storitev.obdelaj(brezKontrole);
        BranjeListkaDto bk = branje(brezKontrole);
        assertTrue(bk.predlog().pripravljen(), bk.predlog().opozorila().toString());
        assertFalse(bk.kontrola(), "NaN je 0");
    }

    @Test
    void casPregledaZunajMejSeNeZapise() {
        assertNull(BranjeListkovStoritev.casPregleda(null));
        assertNull(BranjeListkovStoritev.casPregleda(-1L));
        assertEquals(0, BranjeListkovStoritev.casPregleda(0L));
        assertEquals(3_600_000, BranjeListkovStoritev.casPregleda(3_600_000L));
        assertNull(BranjeListkovStoritev.casPregleda(3_600_001L));
    }

    // ---------- Vrsta brez signala (F5.2, V73) ----------

    /* Telefon fotografijo poslje znova, ce se je odgovor izgubil: isti kljuc vrne
       ze ustvarjeno branje, nova vrstica in fotografija ne nastaneta. */
    @Test
    void istiKljucPosiljanjaVrneIstoBranje() throws IOException {
        byte[] slika = fotografija("TK:" + tekma.getId());
        long prej = branjaRepozitorij.count();
        Long prvo = storitev.sprejmi(idTurnirja(), slika, "7f3c2a10-5b1e-4c8e-9d2f-0a1b2c3d4e5f").id();
        Long ponovljeno = storitev.sprejmi(idTurnirja(), slika, "7f3c2a10-5b1e-4c8e-9d2f-0a1b2c3d4e5f").id();
        assertEquals(prvo, ponovljeno);
        assertEquals(prej + 1, branjaRepozitorij.count());
        Long drugo = storitev.sprejmi(idTurnirja(), slika, "0b9e8d7c-6a5f-4e3d-8c2b-1a0f9e8d7c6b").id();
        assertFalse(drugo.equals(prvo), "drug kljuc je nova fotografija");
        assertEquals(prej + 2, branjaRepozitorij.count());
        assertNotNull(storitev.sprejmi(idTurnirja(), slika, null).id(), "brez kljuca kot doslej");
        assertNotNull(storitev.sprejmi(idTurnirja(), slika, "  ").id(), "prazen kljuc kot brez kljuca");
        assertEquals(prej + 4, branjaRepozitorij.count());
    }

    /* Kljuc velja za uporabnika: drug organizator istega turnirja z enakim kljucem
       ne dobi tujega branja (V1). Isti kljuc za drug turnir je napaka odjemalca. */
    @Test
    void kljucPosiljanjaVeljaZaUporabnikaInTurnir() throws IOException {
        byte[] slika = fotografija("TK:" + tekma.getId());
        String kljuc = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
        Long admina = storitev.sprejmi(idTurnirja(), slika, kljuc).id();

        Uporabnik lastnik = racun("lastnik-kljuc@test", Vloga.ORGANIZATOR);
        Turnir turnir = dogodek.getTurnir();
        turnir.setUstvaril(lastnik);
        turnirRepozitorij.save(turnir);
        storitev.nastaviSamoAdmin(false);
        prijava("lastnik-kljuc@test", "ROLE_ORGANIZATOR");
        Long lastnika = storitev.sprejmi(idTurnirja(), slika, kljuc).id();
        assertFalse(lastnika.equals(admina), "tuj kljuc ne vrne branja drugega uporabnika");

        prijava("admin-branje@test", "ROLE_ADMIN");
        Long drugTurnir = pripraviDogodek(4).getTurnir().getId();
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.sprejmi(drugTurnir, slika, kljuc));
    }

    @Test
    void neveljavenKljucPosiljanjaSeZavrne() throws IOException {
        byte[] slika = fotografija("TK:" + tekma.getId());
        long prej = branjaRepozitorij.count();
        for (String kljuc : new String[] {"kratek", "a".repeat(65), "kljuc z presledki 123", "<script>12345</script>"}) {
            assertThrows(NeveljavenVnosIzjema.class, () -> storitev.sprejmi(idTurnirja(), slika, kljuc), kljuc);
        }
        assertEquals(prej, branjaRepozitorij.count());
    }

    @Test
    void kljuciPosiljanjaSePo24UrahPobrisejo() {
        BranjeListka staro = shraniBranje(dogodek.getTurnir(), LocalDateTime.now().minusHours(25));
        staro.setKljucPosiljanja("staro-kljuc-12345678");
        BranjeListka novo = shraniBranje(dogodek.getTurnir(), LocalDateTime.now().minusHours(23));
        novo.setKljucPosiljanja("novo-kljuc-12345678");
        branjaRepozitorij.saveAllAndFlush(List.of(staro, novo));

        assertEquals(1, storitev.pobrisiStareKljucePosiljanja());
        assertNull(branjaRepozitorij.findById(staro.getId()).orElseThrow().getKljucPosiljanja());
        assertEquals("novo-kljuc-12345678", branjaRepozitorij.findById(novo.getId()).orElseThrow().getKljucPosiljanja());
    }

    /* Zadnja varovalka pred dvojnikom pri dveh hkratnih zahtevah z istim kljucem:
       edinstveni indeks (id_nalozil, kljuc_posiljanja); sprejmi ujame zavrnitev in
       vrne ze zapisano branje. */
    @Test
    void edinstveniIndeksZavrneDvaEnakaKljuca() {
        Uporabnik admin = uporabnikRepozitorij.findByUporabniskoIme("admin-branje@test").orElseThrow();
        BranjeListka prvo = new BranjeListka(dogodek.getTurnir(), admin, LocalDateTime.now());
        prvo.setKljucPosiljanja("isti-kljuc-12345678");
        branjaRepozitorij.saveAndFlush(prvo);
        BranjeListka drugo = new BranjeListka(dogodek.getTurnir(), admin, LocalDateTime.now());
        drugo.setKljucPosiljanja("isti-kljuc-12345678");
        assertThrows(DataIntegrityViolationException.class, () -> branjaRepozitorij.saveAndFlush(drugo));
    }

    /* Skupne mize (V78, lastnik 25. 11. 2026): pult enega turnirja bere tudi listke
       turnirja, s katerim si deli mize; brez povezave koda drugega turnirja ne velja.
       Seznam obeh turnirjev kaze ista branja, potrditev vpise izid tekme drugega. */
    @Test
    void pultBereListkeTurnirjaSSkupnimiMizami() throws IOException {
        Dogodek drugi = pripraviDogodek(4);
        zrebStoritev.izvediZreb(drugi.getId());
        Tekma tuja = tekmeDogodka(drugi.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        bralnik.naslednji = prepis(ime(tuja.getPrijava1()), ime(tuja.getPrijava2()),
                new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0);

        Long brez = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tuja.getId())).id();
        storitev.obdelaj(brez);
        assertNull(branje(brez).tekma(), "brez povezave koda drugega turnirja ne velja");

        mize.povezi(drugi.getTurnir().getId(), new si.turnirko.dto.PovezavaMizVnos(idTurnirja()));
        Long id = storitev.sprejmi(idTurnirja(), fotografija("TK:" + tuja.getId())).id();
        storitev.obdelaj(id);
        BranjeListkaDto prebrano = branje(id);
        assertEquals(PrepoznavaTekme.KODA, prebrano.prepoznava());
        assertEquals(tuja.getId(), prebrano.tekma().tekma().id());
        assertEquals(drugi.getTurnir().getId(), prebrano.tekma().idTurnir());
        assertTrue(storitev.seznam(drugi.getTurnir().getId()).branja().stream().anyMatch(b -> b.id().equals(id)));

        storitev.potrdi(id, new PotrditevBranjaVnos(null, vnos(prebrano.predlog())));
        assertEquals(StatusTekme.KONCANA, tekmaRepozitorij.findById(tuja.getId()).orElseThrow().getStatus());
    }

    private BranjeListkaDto branje(Long id) {
        return storitev.seznam(idTurnirja()).branja().stream()
                .filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    /* PNG dane velikosti (dvobarvni, zato majhna datoteka). */
    private static byte[] png(int sirina, int visina) throws IOException {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        ImageIO.write(slika, "png", izhod);
        return izhod.toByteArray();
    }

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
        // kot pravi bralnik: vse natisnjene vrstice (tekme v testu so na 5 nizov), neodigrane prazne
        while (nizi.size() < 5) {
            nizi.add(new PrebranListek.Niz(null, null, false, false));
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
