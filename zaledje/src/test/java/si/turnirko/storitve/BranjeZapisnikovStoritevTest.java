/* Branje ligaskih zapisnikov (V79): tisk za bralnik, branje v ozadju, samodejni
   zapis zanesljivega zapisnika, pregled in potrditev s popravkom, prepis ze
   vpisanega srecanja. Bralnik je podtaknjen (TestniBralnik vrne pripravljen
   prepis); pravi bralnik na sinteticni fotografiji preverja
   BranjeZapisnikaFotografijeTest, branje samo SimulacijaZapisnikaTest.

   Domenska pravila, ki jih test varuje:
   - natis zapise kader po vrsticah; isti natis se ne podvaja; tuj organizator
     zapisnika ne natisne; formati s tremi igralci niso podprti;
   - zanesljivo prebran zapisnik se zapise sam (ce je organizator to dovolil) po
     ISTI poti kot rocni vnos: postava iz prvih nastopov, menjave, izidi, rating;
   - negotov zapisnik caka organizatorja; njegova potrditev (s popravki) se zapise;
   - ze vpisano srecanje se samo ne prepise; prepis zahteva izrecno potrditev;
   - natis tujega tekmovanja in neveljavna koda koncata z napako. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

import si.turnirko.branje.GeometrijaZapisnika;
import si.turnirko.dto.BranjeZapisnikaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NatisZapisnikaDto;
import si.turnirko.dto.NatisZapisnikovVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.ZapisZapisnikaVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.BranjeZapisnika;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeZapisnikaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Import(BranjeZapisnikovStoritevTest.Nastavitev.class)
class BranjeZapisnikovStoritevTest extends IntegracijskiTest {

    @TestConfiguration
    static class Nastavitev {
        @Bean
        @Primary
        TestniBralnik testniBralnikZapisnika() {
            return new TestniBralnik();
        }
    }

    /* Vrne prepis, ki ga pripravi test, za natis s kodo (id, skrivnost). */
    static class TestniBralnik implements BralnikZapisnika {
        volatile long idNatisa;
        volatile String skrivnost;
        volatile Function<Integer, PrebranZapisnik> prepis;

        @Override
        public boolean jeNastavljen() {
            return true;
        }

        @Override
        public LokalniBralnikZapisnika.Branje preberi(byte[] jpeg,
                Function<KodaListka.KodaZapisnika, LokalniBralnikZapisnika.NatisZaBranje> natisPoKodi) {
            LokalniBralnikZapisnika.NatisZaBranje n = natisPoKodi.apply(
                    new KodaListka.KodaZapisnika(idNatisa, skrivnost, null));
            return new LokalniBralnikZapisnika.Branje(prepis.apply((int) n.id()), "testni", null, null);
        }
    }

    @Autowired BranjeZapisnikovStoritev storitev;
    @Autowired NatisZapisnikovStoritev natisi;
    @Autowired TestniBralnik bralnik;
    @Autowired SkrivnostListka skrivnost;
    @Autowired LigaStoritev ligaStoritev;
    @Autowired SrecanjeStoritev srecanjeStoritev;
    @Autowired RatingStoritev ratingStoritev;
    @Autowired BranjeZapisnikaRepozitorij branja;
    @Autowired UporabnikRepozitorij uporabniki;
    @Autowired si.turnirko.repozitoriji.LigaRepozitorij ligaRepozitorij;

    private Uporabnik organizator;

    @BeforeEach
    void pripravi() {
        storitev.nastaviSamoAdmin(false);
        organizator = racun("org-zapisnik-" + System.nanoTime() + "@test", Vloga.ORGANIZATOR);
        prijava(organizator);
    }

    @AfterEach
    void pocisti() {
        storitev.nastaviSamoAdmin(true);
        SecurityContextHolder.clearContext();
    }

    // ---------- pomozno ----------

    private record Liga(Long id, List<SrecanjeDto> srecanja, List<Igralec> kaderA, List<Igralec> kaderB, Long ekipaA) {
        List<Igralec> domaci(SrecanjeDto s) {
            return s.idEkipaDomaci().equals(ekipaA) ? kaderA : kaderB;
        }

        List<Igralec> gost(SrecanjeDto s) {
            return s.idEkipaDomaci().equals(ekipaA) ? kaderB : kaderA;
        }
    }

    private Liga liga(FormatSrecanja format, Integer prag, int vKadru) {
        String oznaka = Long.toString(System.nanoTime() % 100000);
        LigaVnos v = new LigaVnos("Liga zapisnik " + oznaka, "2026/27", SpolKategorija.MOSKI, format, 5, prag, false,
                2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null, null, null, null);
        // liga brez prijave (paket organizatorja tu ni predmet), nato jo dobi organizator
        var prijavljen = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();
        Long liga = ligaStoritev.ustvari(v).id();
        var zapis = ligaRepozitorij.findById(liga).orElseThrow();
        zapis.setUstvaril(organizator);
        ligaRepozitorij.save(zapis);
        SecurityContextHolder.getContext().setAuthentication(prijavljen);
        List<List<Igralec>> kadri = new ArrayList<>();
        List<Long> ekipe = new ArrayList<>();
        for (String ime : List.of("Klub A", "Klub B")) {
            Klub klub = klubRepozitorij.save(new Klub(ime + " " + oznaka, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            ekipe.add(ekipa.id());
            List<Igralec> kader = new ArrayList<>();
            for (int i = 1; i <= vKadru; i++) {
                Igralec ig = noviIgralec("Ig" + ime.charAt(5) + i, "Pri" + ime.charAt(5) + i + oznaka);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
                kader.add(ig);
            }
            kadri.add(kader);
        }
        ligaStoritev.generirajRazpored(liga);
        return new Liga(liga, srecanjeStoritev.zaLigo(liga), kadri.get(0), kadri.get(1), ekipe.get(0));
    }

    private Uporabnik racun(String ime, Vloga vloga) {
        Uporabnik u = new Uporabnik(ime, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabniki.save(u);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                u.getUporabniskoIme(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }

    /* Tekma prepisa: vrstice kadra natisa za vsako stran in nizi (domaci, gost). */
    record T(List<Integer> d, List<Integer> g, int[][] nizi) {}

    static PrebranZapisnik prepis(int id, int vrsticD, int vrsticG, boolean negotovo, T... tekme) {
        List<PrebranZapisnik.Tekma> izid = new ArrayList<>();
        for (T t : tekme) {
            List<PrebranListek.Niz> nizi = new ArrayList<>();
            for (int s = 0; s < 5; s++) {
                if (t.nizi() != null && s < t.nizi().length) {
                    boolean neg = negotovo && s == 0;
                    nizi.add(new PrebranListek.Niz(t.nizi()[s][0], t.nizi()[s][1], neg, false));
                } else {
                    nizi.add(new PrebranListek.Niz(null, null, false, false));
                }
            }
            izid.add(new PrebranZapisnik.Tekma(krizci(t.d(), vrsticD), krizci(t.g(), vrsticG), nizi, null));
        }
        return new PrebranZapisnik(id, 10, 10, izid);
    }

    private static List<PrebranZapisnik.Krizec> krizci(List<Integer> izbrani, int vrstic) {
        List<PrebranZapisnik.Krizec> k = new ArrayList<>();
        for (int i = 0; i < vrstic; i++) {
            boolean je = izbrani.contains(i);
            k.add(new PrebranZapisnik.Krizec(i, je ? 0.25 : 0.0, je ? "KRIZEC" : "PRAZEN"));
        }
        return k;
    }

    static final int[][] ZMAGA = {{11, 5}, {11, 7}, {11, 9}};
    static final int[][] PORAZ = {{8, 11}, {11, 6}, {9, 11}, {7, 11}};

    private static byte[] fotografija() throws IOException {
        BufferedImage s = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(s, "jpg", o);
        return o.toByteArray();
    }

    private NatisZapisnikaDto natis(SrecanjeDto s, GeometrijaZapisnika.Oblika oblika) {
        return natisi.natisni(new NatisZapisnikovVnos(List.of(s.id()), oblika)).getFirst();
    }

    private void kodaZa(NatisZapisnikaDto n) {
        bralnik.idNatisa = n.idNatis();
        bralnik.skrivnost = n.koda().substring(n.koda().lastIndexOf(':') + 1);
    }

    /* Branje (in samodejni zapis) v niti testa: test tece v transakciji, ki se ne
       potrdi, zato izvajalec v ozadju zapisa ne vidi (kot pri testih listkov). */
    private BranjeZapisnika pocakaj(Long id) {
        storitev.obdelaj(id);
        return branja.findById(id).orElseThrow();
    }

    // ---------- natis ----------

    @Test
    void natisZapiseKaderInSeNePodvaja() {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 3);
        SrecanjeDto s = l.srecanja().getFirst();
        NatisZapisnikaDto prvi = natis(s, null);
        assertEquals(GeometrijaZapisnika.Oblika.VRSTICE, prvi.oblika());
        assertTrue(prvi.koda().matches("TZ:" + prvi.idNatis() + ":[A-Z2-7]{8}"));
        assertEquals(3, prvi.kaderDomaci().size());
        assertEquals(5, prvi.tekme().size());
        assertEquals(prvi.idNatis(), natis(s, null).idNatis());
        NatisZapisnikaDto kader = natis(s, GeometrijaZapisnika.Oblika.KADER);
        assertNotEquals(prvi.idNatis(), kader.idNatis());
        assertEquals(GeometrijaZapisnika.Oblika.KADER, kader.oblika());

        Uporabnik tuj = racun("tuj-zapisnik-" + System.nanoTime() + "@test", Vloga.ORGANIZATOR);
        prijava(tuj);
        assertThrows(PrepovedanoIzjema.class, () -> natis(s, null));
    }

    @Test
    void zapisnikZaBralnikJeSamoZaDvaIgralca() {
        Liga l = liga(FormatSrecanja.SNTL, null, 3);
        assertThrows(DomenskaIzjema.class, () -> natis(l.srecanja().getFirst(), null));
    }

    // ---------- samodejni zapis ----------

    @Test
    void zanesljivZapisnikSeZapiseSamZMenjavami() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 3);
        SrecanjeDto s = l.srecanja().getFirst();
        NatisZapisnikaDto n = natis(s, null);
        kodaZa(n);
        // Savinja: dvojice, A-X, B-Y, A-Y, B-X. Domaci: A = vrstica 0, B = 1; pri A-Y
        // igra vrstica 2 (menjava); dvojice igrata 0 in 2 (menjava para).
        bralnik.prepis = id -> prepis(id, 4, 4, false,
                new T(List.of(0, 2), List.of(0, 1), ZMAGA),
                new T(List.of(0), List.of(0), ZMAGA),
                new T(List.of(1), List.of(1), PORAZ),
                new T(List.of(2), List.of(1), ZMAGA),
                new T(List.of(1), List.of(0), PORAZ));
        BranjeZapisnikaDto sprejeto = storitev.sprejmi(fotografija(), null, true);
        BranjeZapisnika b = pocakaj(sprejeto.id());
        assertEquals(BranjeZapisnika.Stanje.ZAPISANO, b.getStanje(), "opomba: " + b.getOpomba());
        assertTrue(b.isZapisanoSamodejno());

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
        assertEquals(StatusSrecanja.KONCANO, p.srecanje().status());
        assertEquals(3, p.srecanje().dobljeneDomaci());
        assertEquals(2, p.srecanje().dobljeneGost());
        List<Igralec> dom = l.domaci(s);
        TekmaSrecanjaDto ay = p.tekme().get(3);
        assertEquals(dom.get(2).getId(), ay.idDomaci());
        assertTrue(ay.menjavaDomaci());
        TekmaSrecanjaDto dvojice = p.tekme().getFirst();
        assertTrue(dvojice.menjavaDomaci());
        assertFalse(p.tekme().get(1).menjavaDomaci());
        assertEquals(3, p.tekme().get(1).nizi().size());
    }

    @Test
    void negotovZapisnikCakaOrganizatorjaInPotrditevSPopravkomZapise() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 3);
        SrecanjeDto s = l.srecanja().getFirst();
        NatisZapisnikaDto n = natis(s, GeometrijaZapisnika.Oblika.KADER);
        kodaZa(n);
        bralnik.prepis = id -> prepis(id, 4, 4, true,
                new T(List.of(0, 1), List.of(0, 1), ZMAGA),
                new T(List.of(0), List.of(0), ZMAGA),
                new T(List.of(1), List.of(1), ZMAGA),
                new T(List.of(0), List.of(1), PORAZ),
                new T(List.of(1), List.of(0), PORAZ));
        BranjeZapisnikaDto sprejeto = storitev.sprejmi(fotografija(), null, true);
        BranjeZapisnika b = pocakaj(sprejeto.id());
        assertEquals(BranjeZapisnika.Stanje.PREBRANO, b.getStanje());
        assertFalse(b.getZanesljivo());
        BranjeZapisnikaDto podrobno = storitev.podrobno(b.getId());
        assertTrue(podrobno.predlog().negotovih() > 0);
        assertEquals(3, podrobno.podrobnosti().kaderDomaci().size());

        // organizator popravi prvi niz dvojic (11:5 -> 11:3) in potrdi
        List<Igralec> dom = l.domaci(s);
        List<Igralec> gos = l.gost(s);
        List<ZapisZapisnikaVnos.Tekma> tekme = new ArrayList<>();
        tekme.add(new ZapisZapisnikaVnos.Tekma(List.of(dom.get(0).getId(), dom.get(1).getId()),
                List.of(gos.get(0).getId(), gos.get(1).getId()),
                List.of(new NizVnos(11, 3), new NizVnos(11, 7), new NizVnos(11, 9)), null, null));
        tekme.add(tekma(dom.get(0), gos.get(0), ZMAGA));
        tekme.add(tekma(dom.get(1), gos.get(1), ZMAGA));
        tekme.add(tekma(dom.get(0), gos.get(1), PORAZ));
        tekme.add(tekma(dom.get(1), gos.get(0), PORAZ));
        BranjeZapisnikaDto potrjeno = storitev.potrdi(b.getId(), new ZapisZapisnikaVnos(tekme, false, 4000L));
        assertEquals(BranjeZapisnika.Stanje.ZAPISANO, potrjeno.stanje());
        assertFalse(potrjeno.zapisanoSamodejno());
        // popravek zapisanega zacne iz potrjenega zapisa (s popravljenim nizom), ne iz branja
        assertEquals(3, potrjeno.podrobnosti().potrjeno().tekme().getFirst().nizi().getFirst().tocke2());
        assertTrue(branja.findById(b.getId()).orElseThrow().getPopravljeno());
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
        assertEquals(StatusSrecanja.KONCANO, p.srecanje().status());
        assertEquals(3, p.tekme().getFirst().nizi().getFirst().tocke2());
    }

    private static ZapisZapisnikaVnos.Tekma tekma(Igralec d, Igralec g, int[][] nizi) {
        List<NizVnos> n = new ArrayList<>();
        for (int[] x : nizi) {
            n.add(new NizVnos(x[0], x[1]));
        }
        return new ZapisZapisnikaVnos.Tekma(List.of(d.getId()), List.of(g.getId()), n, null, null);
    }

    @Test
    void brezSamodejnegaZapisaZanesljivZapisnikCaka() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 2);
        SrecanjeDto s = l.srecanja().getFirst();
        kodaZa(natis(s, null));
        bralnik.prepis = id -> prepis(id, 3, 3, false,
                new T(List.of(0, 1), List.of(0, 1), ZMAGA), new T(List.of(0), List.of(0), ZMAGA),
                new T(List.of(1), List.of(1), ZMAGA), new T(List.of(0), List.of(1), ZMAGA),
                new T(List.of(1), List.of(0), ZMAGA));
        BranjeZapisnika b = pocakaj(storitev.sprejmi(fotografija(), null, false).id());
        assertEquals(BranjeZapisnika.Stanje.PREBRANO, b.getStanje());
        assertTrue(b.getZanesljivo());
        assertEquals(StatusSrecanja.RAZPORED, srecanjeStoritev.podrobno(s.id()).srecanje().status());
    }

    @Test
    void zeVpisanoSrecanjeSeNePrepiseSamo() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, 3, 2);
        SrecanjeDto s = l.srecanja().getFirst();
        kodaZa(natis(s, null));
        // prag 3: domaci zmagajo dvojice, A-X in B-Y; A-Y in B-X ostaneta prazni
        bralnik.prepis = id -> prepis(id, 3, 3, false,
                new T(List.of(0, 1), List.of(0, 1), ZMAGA), new T(List.of(0), List.of(0), ZMAGA),
                new T(List.of(1), List.of(1), ZMAGA), new T(List.of(), List.of(), null),
                new T(List.of(), List.of(), null));
        BranjeZapisnika prvo = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        assertEquals(BranjeZapisnika.Stanje.ZAPISANO, prvo.getStanje(), "opomba: " + prvo.getOpomba());
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
        assertEquals(3, p.srecanje().dobljeneDomaci());
        assertEquals(StatusTekmeSrecanja.NEODIGRANA, p.tekme().get(4).status());

        // druga fotografija istega zapisnika: srecanje je vpisano, samo se ne prepise
        BranjeZapisnika drugo = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        assertEquals(BranjeZapisnika.Stanje.PREBRANO, drugo.getStanje());
        assertNotNull(drugo.getOpomba());
        assertThrows(DomenskaIzjema.class, () -> storitev.potrdi(drugo.getId(),
                BranjeZapisnikovStoritev.vnosIzPredloga(branja.najdiPodrobno(drugo.getId()).orElseThrow())));
        ZapisZapisnikaVnos predlog = BranjeZapisnikovStoritev.vnosIzPredloga(
                branja.najdiPodrobno(drugo.getId()).orElseThrow());
        BranjeZapisnikaDto prepisano = storitev.potrdi(drugo.getId(),
                new ZapisZapisnikaVnos(predlog.tekme(), true, null));
        assertEquals(BranjeZapisnika.Stanje.ZAPISANO, prepisano.stanje());
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(s.id()).srecanje().status());
    }

    @Test
    void natisTujegaTekmovanjaInNeveljavnaKodaKoncataZNapako() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 2);
        NatisZapisnikaDto n = natis(l.srecanja().getFirst(), null);
        bralnik.prepis = id -> prepis(id, 3, 3, false);
        // napacna skrivnost
        bralnik.idNatisa = n.idNatis();
        bralnik.skrivnost = "AAAAAAAA";
        BranjeZapisnika b = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        assertEquals(BranjeZapisnika.Stanje.NAPAKA, b.getStanje());
        assertTrue(b.getNapaka().contains("ni veljavna"));
        // pravilna koda, a nalaga tuj organizator
        kodaZa(n);
        prijava(racun("tuj2-zapisnik-" + System.nanoTime() + "@test", Vloga.ORGANIZATOR));
        BranjeZapisnika tuje = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        assertEquals(BranjeZapisnika.Stanje.NAPAKA, tuje.getStanje());
        assertTrue(tuje.getNapaka().contains("ne urejaš"), tuje.getNapaka());
    }

    @Test
    void novejsaFotografijaIstegaZapisnikaZapreStarejso() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 2);
        SrecanjeDto s = l.srecanja().getFirst();
        kodaZa(natis(s, null));
        bralnik.prepis = id -> prepis(id, 3, 3, true,
                new T(List.of(0, 1), List.of(0, 1), ZMAGA), new T(List.of(0), List.of(0), ZMAGA),
                new T(List.of(1), List.of(1), ZMAGA), new T(List.of(0), List.of(1), ZMAGA),
                new T(List.of(1), List.of(0), ZMAGA));
        BranjeZapisnika prvo = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        BranjeZapisnika drugo = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        assertEquals(BranjeZapisnika.Stanje.PREBRANO, drugo.getStanje());
        assertEquals(BranjeZapisnika.Stanje.ZAVRZENO, branja.findById(prvo.getId()).orElseThrow().getStanje());
        assertEquals(2, storitev.seznam().stream().filter(x -> x.srecanje() != null
                && x.srecanje().id().equals(s.id())).count());
    }

    @Test
    void kljucPosiljanjaNeUstvariDvojnika() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 2);
        kodaZa(natis(l.srecanja().getFirst(), null));
        bralnik.prepis = id -> prepis(id, 3, 3, true);
        BranjeZapisnikaDto a = storitev.sprejmi(fotografija(), "kljuc-zapisnika-1", true);
        BranjeZapisnikaDto b = storitev.sprejmi(fotografija(), "kljuc-zapisnika-1", true);
        assertEquals(a.id(), b.id());
        pocakaj(a.id());
    }

    @Test
    void organizatorVidiSamoSvojaBranja() throws IOException {
        Liga l = liga(FormatSrecanja.SAVINJA, null, 2);
        kodaZa(natis(l.srecanja().getFirst(), null));
        bralnik.prepis = id -> prepis(id, 3, 3, true);
        BranjeZapisnika b = pocakaj(storitev.sprejmi(fotografija(), null, true).id());
        prijava(racun("tuj3-zapisnik-" + System.nanoTime() + "@test", Vloga.ORGANIZATOR));
        assertThrows(si.turnirko.izjeme.NiNajdenoIzjema.class, () -> storitev.podrobno(b.getId()));
        assertTrue(storitev.seznam().isEmpty());
    }
}
