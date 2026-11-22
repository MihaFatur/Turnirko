/* Sodnik slika listek s svojim telefonom (S2, SodniskaBranjaStoritev).

   Bralnik je nadomestek kot v BranjeListkovStoritevTest (isti kontekst Springa);
   QR koda s skrivnostjo, omejitve, zeton, potrditev in predpregled tecejo kot v
   produkciji.

   Domenska pravila, ki jih ta test varuje:
   - fotografija s pravo skrivnostjo da branje z enkratnim zetonom; sodnik vidi
     predlog in ga potrdi; pri VELJA_TAKOJ je izid vpisan po poti rocnega vnosa
     (nacin SODNIK, merilo »popravljeno« velja kot pri organizatorju);
   - pri PREDPREGLED izid pocaka: organizator vidi sodnikov izid kot predlog in ga
     potrdi z enim dotikom; sodnikov popravek prebranega je opozorilo;
   - brez skrivnosti (stari listek), z napacno skrivnostjo, pri izklopljenem
     stikalu ali koncani tekmi branje ne nastane;
   - zeton velja samo za svoje branje, je enkraten in ga ponovljeno posiljanje
     (isti kljuc) zamenja; tuj zeton ne razkrije, ali branje obstaja;
   - sodnik vpise samo odigrano tekmo s tockami (posebne izide vpise organizator);
   - omejitev po naslovu IP steje tudi zavrnjene poskuse; tekma ima mejo fotografij;
   - stikalo sme nastaviti samo, kdor sme brati listke turnirja;
   - pri VELJA_TAKOJ velja izid takoj samo, ce ga podpira fotografija (smeTakoj):
     sprememba zanesljivo prebrane stevilke ali prazen listek gre k organizatorju;
   - napacni zetoni so omejeni po naslovu, IPv6 se steje po omrezju /64;
   - ponovno branje sodniskega listka ohrani tekmo (zeton ostane uporaben). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import si.turnirko.dto.BranjeListkaDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PotrditevBranjaVnos;
import si.turnirko.dto.SodnikPoslanoDto;
import si.turnirko.dto.SodniskaPotrditevVnos;
import si.turnirko.dto.SodniskoBranjeDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.izjeme.PrevecZahtevIzjema;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPotrditve;
import si.turnirko.modeli.SodniskaPotrditev;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@Import(BranjeListkovStoritevTest.Nastavitev.class)
class SodniskaBranjaStoritevTest extends IntegracijskiTest {

    private static final String NASLOV = "192.0.2.10";

    @Autowired SodniskaBranjaStoritev sodnik;
    @Autowired BranjeListkovStoritev organizator;
    @Autowired BranjeListkovStoritevTest.TestniBralnik bralnik;
    @Autowired SkrivnostListka skrivnost;
    @Autowired BranjeListkaRepozitorij branjaRepozitorij;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired OmejevalnikPoskusov omejevalnik;

    private Dogodek dogodek;
    private Tekma tekma;

    @BeforeEach
    void pripravi() {
        omejevalnik.pocistiVse();
        bralnik.naslednji = null;
        bralnik.napaka = null;
        bralnik.poravnava = null;
        dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        stikalo(SodniskaPotrditev.VELJA_TAKOJ);
        // sodnik ni prijavljen
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void pocisti() {
        organizator.nastaviSamoAdmin(true);
        omejevalnik.pocistiVse();
        SecurityContextHolder.clearContext();
    }

    @Test
    void sodnikPotrdiInIzidVeljaTakoj() throws IOException {
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}, 3, 1, 7);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())),
                "kljuc-sodnika-1", NASLOV);
        assertTrue(poslano.zeton().length() >= 40);
        assertEquals(StanjeBranja.V_VRSTI, sodnik.stanje(poslano.idBranja(), poslano.zeton(), NASLOV).stanje());

        organizator.obdelaj(poslano.idBranja());
        SodniskoBranjeDto prebrano = sodnik.stanje(poslano.idBranja(), poslano.zeton(), NASLOV);
        assertEquals(StanjeBranja.PREBRANO, prebrano.stanje());
        assertEquals(tekma.getPrijava1().prikazanoIme(), prebrano.igralec1());
        assertEquals(3, prebrano.predlog().dobljeniNizi1());
        assertEquals(7, prebrano.prebranaMiza());
        assertEquals(SodniskaPotrditev.VELJA_TAKOJ, prebrano.velja());

        SodniskoBranjeDto potrjeno = sodnik.potrdi(poslano.idBranja(),
                new SodniskaPotrditevVnos(poslano.zeton(), vnos(3, 1, new int[][] {{11, 7}, {9, 11}, {11, 5}, {11, 8}}), 7), NASLOV);
        assertEquals(StanjeBranja.POTRJENO, potrjeno.stanje());
        assertTrue(potrjeno.sodnikPotrdil());
        Tekma koncana = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(StatusTekme.KONCANA, koncana.getStatus());
        assertEquals(3, koncana.getDobljeniNizi1());

        BranjeListka b = branjaRepozitorij.findById(poslano.idBranja()).orElseThrow();
        assertEquals(NacinPotrditve.SODNIK, b.getNacinPotrditve());
        assertEquals(Boolean.FALSE, b.getPopravljeno());
        assertNull(b.getNalozil());
        assertEquals(7, b.getMizaSodnika());
        // zeton je enkraten
        assertNull(b.getZetonSodnika());
        assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(poslano.idBranja(), poslano.zeton(), NASLOV));
    }

    @Test
    void predpregledPocakaOrganizatorja() throws IOException {
        stikalo(SodniskaPotrditev.PREDPREGLED);
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());

        SodniskoBranjeDto potrjeno = sodnik.potrdi(poslano.idBranja(),
                new SodniskaPotrditevVnos(poslano.zeton(), vnos(3, 0, new int[][] {{11, 7}, {11, 9}, {11, 5}}), null), NASLOV);
        assertEquals(StanjeBranja.PREBRANO, potrjeno.stanje());
        assertTrue(potrjeno.sodnikPotrdil());
        assertEquals(StatusTekme.PRIPRAVLJENA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());

        prijavaAdmina();
        BranjeListkaDto vVrsti = organizator.seznam(idTurnirja()).branja().getFirst();
        assertTrue(vVrsti.poslalSodnik());
        assertTrue(vVrsti.sodnikPotrdil());
        assertEquals(SodniskaPotrditev.PREDPREGLED, organizator.seznam(idTurnirja()).sodnikiListki());
        assertTrue(vVrsti.predlog().pripravljen(), vVrsti.predlog().opozorila().toString());
        assertEquals(3, vVrsti.predlog().dobljeniNizi1());

        organizator.potrdi(poslano.idBranja(), new PotrditevBranjaVnos(null,
                vnos(3, 0, new int[][] {{11, 7}, {11, 9}, {11, 5}}), null, NacinPotrditve.EN_DOTIK));
        assertEquals(StatusTekme.KONCANA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());
    }

    @Test
    void sodnikovPopravekJeOpozoriloOrganizatorju() throws IOException {
        stikalo(SodniskaPotrditev.PREDPREGLED);
        // bralnik je 1 prebral kot 7 v prvem nizu
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        sodnik.potrdi(poslano.idBranja(),
                new SodniskaPotrditevVnos(poslano.zeton(), vnos(3, 0, new int[][] {{11, 1}, {11, 9}, {11, 5}}), null), NASLOV);

        prijavaAdmina();
        BranjeListkaDto b = organizator.seznam(idTurnirja()).branja().getFirst();
        assertEquals(Boolean.TRUE, b.sodnikSpremenil());
        assertFalse(b.predlog().pripravljen());
        assertTrue(b.predlog().opozorila().contains(BranjeListkovStoritev.SODNIK_SPREMENIL));
        assertEquals(1, b.predlog().nizi().getFirst().tocke2());
    }

    @Test
    void brezPraveSkrivnostiBranjeNeNastane() throws IOException {
        long prej = branjaRepozitorij.count();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> sodnik.sprejmi(fotografija("TK:" + tekma.getId()), null, NASLOV));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> sodnik.sprejmi(fotografija("TK:" + tekma.getId() + ":AAAAAAAA"), null, NASLOV));
        // skrivnost druge tekme ne velja za to tekmo
        String tuja = skrivnost.za(tekma.getId() + 1);
        assertThrows(NeveljavenVnosIzjema.class,
                () -> sodnik.sprejmi(fotografija("TK:" + tekma.getId() + ":" + tuja), null, NASLOV));
        assertThrows(NeveljavenVnosIzjema.class, () -> sodnik.sprejmi(fotografija(), null, NASLOV));
        assertEquals(prej, branjaRepozitorij.count());
    }

    @Test
    void izklopljenoStikaloAliKoncanaTekma() throws IOException {
        stikalo(null);
        DomenskaIzjema izklopljeno = assertThrows(DomenskaIzjema.class,
                () -> sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV));
        assertEquals(SodniskaBranjaStoritev.IZKLOPLJENO, izklopljeno.getMessage());

        stikalo(SodniskaPotrditev.VELJA_TAKOJ);
        Tekma t = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        t.setStatus(StatusTekme.KONCANA);
        tekmaRepozitorij.save(t);
        assertThrows(DomenskaIzjema.class,
                () -> sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV));
    }

    @Test
    void zetonVeljaSamoZaSvojeBranje() throws IOException {
        SodnikPoslanoDto prvo = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        Tekma druga = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA && !t.getId().equals(tekma.getId()))
                .findFirst().orElseThrow();
        SodnikPoslanoDto drugo = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(druga.getId())), null, NASLOV);

        NiNajdenoIzjema tuj = assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(prvo.idBranja(), drugo.zeton(), NASLOV));
        NiNajdenoIzjema neobstojece = assertThrows(NiNajdenoIzjema.class,
                () -> sodnik.stanje(Long.MAX_VALUE, prvo.zeton(), NASLOV));
        // enak odgovor: ne izda, ali branje obstaja
        assertEquals(tuj.getMessage(), neobstojece.getMessage());
        assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(prvo.idBranja(), "x", NASLOV));

        // potekel zeton
        BranjeListka b = branjaRepozitorij.findById(prvo.idBranja()).orElseThrow();
        b.setZetonVeljaDo(LocalDateTime.now().minusSeconds(1));
        branjaRepozitorij.save(b);
        assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(prvo.idBranja(), prvo.zeton(), NASLOV));
    }

    @Test
    void istiKljucVrneIstoBranjeZNovimZetonom() throws IOException {
        byte[] foto = fotografija(skrivnost.vsebinaQr(tekma.getId()));
        SodnikPoslanoDto prvo = sodnik.sprejmi(foto, "kljuc-ponovitve-1", NASLOV);
        SodnikPoslanoDto ponovljeno = sodnik.sprejmi(foto, "kljuc-ponovitve-1", NASLOV);
        assertEquals(prvo.idBranja(), ponovljeno.idBranja());
        assertNotEquals(prvo.zeton(), ponovljeno.zeton());
        assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(prvo.idBranja(), prvo.zeton(), NASLOV));
        assertEquals(StanjeBranja.V_VRSTI, sodnik.stanje(prvo.idBranja(), ponovljeno.zeton(), NASLOV).stanje());
    }

    @Test
    void posebenIzidVpiseOrganizator() throws IOException {
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        VnosRezultata predaja = new VnosRezultata(IzidTekme.PREDAJA, 1, 0, 1, List.of(new NizVnos(11, 3)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> sodnik.potrdi(poslano.idBranja(), new SodniskaPotrditevVnos(poslano.zeton(), predaja, null), NASLOV));
        VnosRezultata brezTock = new VnosRezultata(null, 3, 0, null, List.of());
        assertThrows(NeveljavenVnosIzjema.class,
                () -> sodnik.potrdi(poslano.idBranja(), new SodniskaPotrditevVnos(poslano.zeton(), brezTock, null), NASLOV));
        // neveljaven niz zavrne ista preverba kot rocni vnos
        assertThrows(RuntimeException.class, () -> sodnik.potrdi(poslano.idBranja(),
                new SodniskaPotrditevVnos(poslano.zeton(), vnos(3, 0, new int[][] {{11, 10}, {11, 9}, {11, 5}}), null), NASLOV));
    }

    @Test
    void omejitevNaslovaStejeTudiZavrnjene() {
        byte[] smeti = {1, 2, 3};
        for (int i = 0; i < SodniskaBranjaStoritev.NAJVEC_Z_NASLOVA; i++) {
            assertThrows(NeveljavenVnosIzjema.class, () -> sodnik.sprejmi(smeti, null, NASLOV));
        }
        assertThrows(PrevecZahtevIzjema.class, () -> sodnik.sprejmi(smeti, null, NASLOV));
        // drug naslov ni prizadet
        assertThrows(NeveljavenVnosIzjema.class, () -> sodnik.sprejmi(smeti, null, "198.51.100.7"));
    }

    @Test
    void tekmaImaMejoFotografij() throws IOException {
        Turnir turnir = turnirRepozitorij.findById(idTurnirja()).orElseThrow();
        for (int i = 0; i < SodniskaBranjaStoritev.NAJVEC_NA_TEKMO; i++) {
            BranjeListka b = new BranjeListka(turnir, null, LocalDateTime.now());
            b.setPoslalSodnik(true);
            b.setTekma(tekma);
            b.setStanje(StanjeBranja.ZAVRZENO);
            branjaRepozitorij.save(b);
        }
        assertThrows(PrevecZahtevIzjema.class,
                () -> sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV));
    }

    @Test
    void stikaloSmeSamoKdorSmeBratiListke() {
        Uporabnik lastnik = racun("lastnik-sodniki@test", Vloga.ORGANIZATOR);
        racun("tujec-sodniki@test", Vloga.ORGANIZATOR);
        Turnir turnir = turnirRepozitorij.findById(idTurnirja()).orElseThrow();
        turnir.setUstvaril(lastnik);
        turnirRepozitorij.save(turnir);
        organizator.nastaviSamoAdmin(false);

        prijava("tujec-sodniki@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> organizator.nastaviSodnike(idTurnirja(), null));

        prijava("lastnik-sodniki@test", "ROLE_ORGANIZATOR");
        assertEquals(SodniskaPotrditev.PREDPREGLED,
                organizator.nastaviSodnike(idTurnirja(), SodniskaPotrditev.PREDPREGLED));
        assertNull(organizator.nastaviSodnike(idTurnirja(), null));
    }

    @Test
    void spremembaZanesljiveStevilkePocakaOrganizatorja() throws IOException {
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        // 11:7 je bralnik prebral zanesljivo, sodnik vpise 11:3
        SodniskoBranjeDto odgovor = sodnik.potrdi(poslano.idBranja(), new SodniskaPotrditevVnos(poslano.zeton(),
                vnos(3, 0, new int[][] {{11, 3}, {11, 9}, {11, 5}}), null), NASLOV);
        assertEquals(StanjeBranja.PREBRANO, odgovor.stanje());
        assertTrue(odgovor.sodnikPotrdil());
        assertEquals(StatusTekme.PRIPRAVLJENA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());
        prijavaAdmina();
        BranjeListkaDto b = organizator.seznam(idTurnirja()).branja().getFirst();
        assertTrue(b.predlog().opozorila().contains(BranjeListkovStoritev.SODNIK_SPREMENIL));
    }

    @Test
    void popravekNegotoveStevilkeVeljaTakoj() throws IOException {
        PrebranListek p = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        List<PrebranListek.Niz> nizi = new ArrayList<>(p.nizi());
        nizi.set(0, new PrebranListek.Niz(11, 7, false, true));
        bralnik.naslednji = new PrebranListek(true, null, null, null, false, null, null, nizi, 3, 0, false, "NI",
                null, null, null, false);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        SodniskoBranjeDto odgovor = sodnik.potrdi(poslano.idBranja(), new SodniskaPotrditevVnos(poslano.zeton(),
                vnos(3, 0, new int[][] {{11, 1}, {11, 9}, {11, 5}}), null), NASLOV);
        assertEquals(StanjeBranja.POTRJENO, odgovor.stanje());
        assertEquals(StatusTekme.KONCANA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());
    }

    /* Fotografija praznega listka (ali QR) s poljubnim izidom ne gre v mrezo. */
    @Test
    void praznListekNeVeljaTakoj() throws IOException {
        bralnik.naslednji = prepis(new int[][] {}, 0, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        SodniskoBranjeDto odgovor = sodnik.potrdi(poslano.idBranja(), new SodniskaPotrditevVnos(poslano.zeton(),
                vnos(3, 0, new int[][] {{11, 0}, {11, 0}, {11, 0}}), null), NASLOV);
        assertEquals(StanjeBranja.PREBRANO, odgovor.stanje());
        assertEquals(StatusTekme.PRIPRAVLJENA, tekmaRepozitorij.findById(tekma.getId()).orElseThrow().getStatus());
    }

    @Test
    void tockeNizaSoOmejene() throws IOException {
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        assertThrows(NeveljavenVnosIzjema.class, () -> sodnik.potrdi(poslano.idBranja(),
                new SodniskaPotrditevVnos(poslano.zeton(), vnos(3, 0, new int[][] {{2000, 1998}, {11, 9}, {11, 5}}), null),
                NASLOV));
    }

    @Test
    void napacniZetoniSoOmejeni() {
        for (int i = 0; i < SodniskaBranjaStoritev.NAJVEC_NAPAK_ZETONA; i++) {
            assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(1L, "napacen", NASLOV));
        }
        assertThrows(PrevecZahtevIzjema.class, () -> sodnik.stanje(1L, "napacen", NASLOV));
        assertThrows(NiNajdenoIzjema.class, () -> sodnik.stanje(1L, "napacen", "198.51.100.7"));
    }

    @Test
    void naslovIpv6SeStejePoOmrezju() {
        assertEquals(SodniskaBranjaStoritev.kljucNaslova("2001:db8:1:2:aaaa::1"),
                SodniskaBranjaStoritev.kljucNaslova("2001:db8:1:2:bbbb::2"));
        assertNotEquals(SodniskaBranjaStoritev.kljucNaslova("2001:db8:1:2::1"),
                SodniskaBranjaStoritev.kljucNaslova("2001:db8:1:3::1"));
        assertEquals("192.0.2.1", SodniskaBranjaStoritev.kljucNaslova("192.0.2.1"));
        assertEquals("?", SodniskaBranjaStoritev.kljucNaslova(null));
    }

    @Test
    void ponovnoBranjeOhraniTekmo() throws IOException {
        bralnik.naslednji = prepis(new int[][] {{11, 7}, {11, 9}, {11, 5}}, 3, 0, null);
        SodnikPoslanoDto poslano = sodnik.sprejmi(fotografija(skrivnost.vsebinaQr(tekma.getId())), null, NASLOV);
        organizator.obdelaj(poslano.idBranja());
        prijavaAdmina();
        organizator.znova(poslano.idBranja());
        SecurityContextHolder.clearContext();
        SodniskoBranjeDto vVrsti = sodnik.stanje(poslano.idBranja(), poslano.zeton(), NASLOV);
        assertEquals(StanjeBranja.V_VRSTI, vVrsti.stanje());
        assertEquals(tekma.getPrijava1().prikazanoIme(), vVrsti.igralec1());
    }

    // ---------- Pomozno ----------

    private Long idTurnirja() {
        return dogodek.getTurnir().getId();
    }

    private void stikalo(SodniskaPotrditev nacin) {
        Turnir turnir = turnirRepozitorij.findById(idTurnirja()).orElseThrow();
        turnir.setSodnikiListki(nacin);
        turnirRepozitorij.save(turnir);
    }

    private static PrebranListek prepis(int[][] tocke, int rezLevo, int rezDesno, Integer miza) {
        List<PrebranListek.Niz> nizi = new ArrayList<>();
        for (int[] niz : tocke) {
            nizi.add(new PrebranListek.Niz(niz[0], niz[1], false, false));
        }
        while (nizi.size() < 5) {
            nizi.add(new PrebranListek.Niz(null, null, false, false));
        }
        return new PrebranListek(true, null, null, null, false, null, null, nizi,
                rezLevo, rezDesno, false, "NI", null, null, miza, false);
    }

    private static VnosRezultata vnos(int n1, int n2, int[][] tocke) {
        List<NizVnos> nizi = new ArrayList<>();
        for (int[] niz : tocke) {
            nizi.add(new NizVnos(niz[0], niz[1]));
        }
        return new VnosRezultata(null, n1, n2, null, nizi);
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
                g.drawImage(MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(koda,
                        BarcodeFormat.QR_CODE, 200, 200, Map.of(EncodeHintType.MARGIN, 1))), x, 300, null);
                x += 500;
            }
        } catch (com.google.zxing.WriterException e) {
            throw new IOException(e);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream izhod = new ByteArrayOutputStream();
        ImageIO.write(slika, "jpeg", izhod);
        return izhod.toByteArray();
    }

    private Uporabnik racun(String prijavnoIme, Vloga vloga) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", vloga);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private void prijavaAdmina() {
        racun("admin-sodniki@test", Vloga.ADMIN);
        prijava("admin-sodniki@test", "ROLE_ADMIN");
    }

    private void prijava(String prijavnoIme, String vloga) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority(vloga))));
    }
}
