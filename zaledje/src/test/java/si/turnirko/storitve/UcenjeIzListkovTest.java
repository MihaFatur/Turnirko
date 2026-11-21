/* Zbiranje stevk iz potrjenih listkov (UcenjeIzListkov); ucenje samo varuje
   UcenjeMrezeTest.

   Domenska pravila, ki jih ta test varuje:
   - ob potrditvi se iz fotografije zapisejo stevke s potrjenimi vrednostmi
     (celice nizov in rezultata, brez imen); celica, ki je razrez ne razdeli na
     pravo stevilo stevk (sklenjena »11«), se preskoci - napacna oznaka bi ucenje
     pokvarila;
   - posebni izidi (brez boja) ne dajo stevk;
   - pri listku brez kode (neznana smer stolpcev) se izbere smer, v kateri se
     mreza strinja s potrjenim izidom;
   - celica, ki jo je organizator popravil, je mocna oznaka; vsaka stevka ve svojo
     celico listka (F6.2);
   - popravek izida tekme zapise stevke njenih listkov znova z novim izidom in
     popravi resnico v zlatem naboru; popravek, ki spremeni vec kot dve celici,
     stevke in listek v naboru samo izbrise (F6.1). */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.branje.MrezaStevk;
import si.turnirko.branje.RazrezCelice;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PredlogIzidaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.BranjeListka;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.PrepoznavaTekme;
import si.turnirko.modeli.SlikaBranjaListka;
import si.turnirko.modeli.StanjeBranja;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StevkaListka;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.modeli.ZlatiListek;
import si.turnirko.repozitoriji.BranjeListkaRepozitorij;
import si.turnirko.repozitoriji.SlikaBranjaListkaRepozitorij;
import si.turnirko.repozitoriji.StevkaListkaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;
import si.turnirko.repozitoriji.ZlatiListekRepozitorij;

class UcenjeIzListkovTest extends IntegracijskiTest {

    @Autowired UcenjeIzListkov ucenje;
    @Autowired BranjeListkaRepozitorij branja;
    @Autowired SlikaBranjaListkaRepozitorij slike;
    @Autowired StevkaListkaRepozitorij stevke;
    @Autowired ZlatiListekRepozitorij zlati;
    @Autowired UporabnikRepozitorij uporabniki;

    private static final int[][] IZID = {{11, 7}, {9, 11}, {11, 5}, {11, 8}};
    // 11 7 | 9 11 | 11 5 | 11 8 = 12 stevk v nizih, rezultat 3 | 1 = 2
    private static final int STEVK = 14;
    // sklenjeno napisanih dvomestnih vrednosti razrez vcasih ne razdeli
    private static final int NAJMANJ = 10;

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

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
        // vsaka stevka ve svojo celico listka; brez popravkov ni mocnih oznak
        assertTrue(vse.stream().allMatch(s -> s.getCelica() != null));
        assertTrue(vse.stream().noneMatch(StevkaListka::isPopravljena));
        assertEquals(0, ucenje.zabelezi(id, vnos(IZID, false), false, 5, "testni"), "listek samo enkrat");
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

    /* F6.2: celice, ki jih je organizator popravil, so mocne oznake - v stolpcih
       listka, tudi ko je listek obrnjen glede na tekmo. */
    @Test
    void popravljeneCeliceSoMocneOznake() {
        RazrezCelice.Stevka s = new RazrezCelice.Stevka(new float[784]);
        RazrezCelice.Vsebina ena = new RazrezCelice.Vsebina(false,
                List.of(new RazrezCelice.Razlaga(List.of(s), 1)), 0.03, 1, 0.1);
        RazrezCelice.Vsebina dve = new RazrezCelice.Vsebina(false,
                List.of(new RazrezCelice.Razlaga(List.of(s, s), 1)), 0.05, 2, 0.3);
        LokalniBralnikListka.Izrez izrez = new LokalniBralnikListka.Izrez(List.<RazrezCelice.Vsebina[]>of(
                new RazrezCelice.Vsebina[] {ena, dve}),
                new RazrezCelice.Vsebina[] {ena, ena});
        // tekma: prijava 1 je 11, prijava 2 je 7; listek je obrnjen (levo prijava 2)
        List<NizVnos> nizi = List.of(new NizVnos(11, 7));
        VnosRezultata izid = new VnosRezultata(IzidTekme.IGRANO, 1, 0, null, nizi);
        // bralnik je prebral 11:1 - organizator je popravil tocke prijave 2
        PredlogIzidaDto predlog = new PredlogIzidaDto(IzidTekme.IGRANO, 1, 0, 1,
                List.of(new PredlogIzidaDto.NizPredloga(11, 1, false, true, false)), List.of(), false);
        PopravljeneCelice popravljene = PopravljeneCelice.izPredloga(predlog, izid);
        assertEquals(1, popravljene.stevilo());
        assertTrue(popravljene.niz(0, 2));

        List<StevkaListka> zapis = UcenjeIzListkov.oznaci(1L, izrez, nizi, izid, true,
                MrezaStevk.nakljucna(new Random(1)), popravljene, Set.of());
        // 7 levo (celica 0), 11 desno (celica 1), rezultat 0 | 1 (celici 100, 101)
        assertEquals(5, zapis.size());
        List<StevkaListka> mocne = zapis.stream().filter(StevkaListka::isPopravljena).toList();
        assertEquals(1, mocne.size(), "mocna je samo popravljena celica");
        assertEquals(7, mocne.getFirst().getVrednost());
        assertEquals((short) UcenjeIzListkov.celicaListka(0, 0), mocne.getFirst().getCelica());
        assertTrue(zapis.stream().anyMatch(x -> x.getCelica() == UcenjeIzListkov.CELICA_REZULTATA + 1));
        // brez predloga (branje ni uspelo) ni mogoce vedeti, kaj je bilo popravljeno
        assertEquals(0, PopravljeneCelice.izPredloga(null, izid).stevilo());
    }

    @Test
    void razlikaPopravkaPoCelicah() {
        VnosRezultata a = vnos(IZID, false);
        int[][] drugi = {{11, 7}, {9, 11}, {11, 5}, {11, 9}};
        PopravljeneCelice r = PopravljeneCelice.izPopravka(a, vnos(drugi, false));
        assertEquals(1, r.stevilo());
        assertTrue(r.niz(3, 2));
        assertEquals(0, PopravljeneCelice.izPopravka(a, a).stevilo());
        // drugo stevilo nizov ali posebni izid: po celicah se ne da primerjati
        assertNull(PopravljeneCelice.izPopravka(a, vnos(new int[][] {{11, 7}, {11, 9}, {11, 5}}, false)));
        assertNull(PopravljeneCelice.izPopravka(a, new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 1, null)));
    }

    /* F6.1: organizator je ob potrditvi spregledal napako bralnika (7 prebrano kot 1)
       in izid pozneje popravil. Stevke listka se zapisejo znova s pravo vrednostjo,
       popravljena celica je mocna, resnica v zlatem naboru je popravljena. */
    @Test
    void popravekIzidaZapiseOznakeZnova() throws IOException {
        int[][] napacno = {{11, 1}, {9, 11}, {11, 5}, {11, 8}};
        Postavitev p = potrjenListek(napacno, new Random(14));
        assertTrue(stevke.findAll().stream().anyMatch(s -> s.getCelica() == 1 && s.getVrednost() == 1),
                "pred popravkom je oznaka napacna");

        tekmaStoritev.popraviRezultat(p.tekma().getId(), vnos(IZID, false));
        assertEquals(1, ucenje.popraviOznake(p.tekma().getId()));

        List<StevkaListka> vse = stevke.findAll();
        assertTrue(vse.size() >= NAJMANJ, "stevke so zapisane znova: " + vse.size());
        List<StevkaListka> celica = vse.stream().filter(s -> s.getCelica() == 1).toList();
        assertEquals(1, celica.size());
        assertEquals(7, celica.getFirst().getVrednost());
        assertTrue(celica.getFirst().isPopravljena(), "popravljena celica je mocna oznaka");
        assertEquals(1, vse.stream().filter(StevkaListka::isPopravljena).count());

        ZlatiListek z = zlati.findByIdBranje(p.idBranja()).orElseThrow();
        assertArrayEquals(new int[] {11, 7}, ZlatiNabor.nalozi(z).resnica().nizi().getFirst());
    }

    /* F6.1: popravek, ki listek postavi pod vprasaj (drugo stevilo nizov - listek je
       bil potrjen k napacni tekmi), stevke in listek v naboru samo izbrise. */
    @Test
    void popravekNaDrugListekIzbriseStevke() throws IOException {
        Postavitev p = potrjenListek(IZID, new Random(15));
        assertFalse(stevke.findAll().isEmpty());
        assertTrue(zlati.findByIdBranje(p.idBranja()).isPresent());

        tekmaStoritev.popraviRezultat(p.tekma().getId(),
                vnos(new int[][] {{11, 3}, {11, 4}, {11, 2}}, false));
        assertEquals(1, ucenje.popraviOznake(p.tekma().getId()));
        assertTrue(stevke.findAll().isEmpty(), "oznake niso vec zanesljive");
        assertTrue(zlati.findByIdBranje(p.idBranja()).isEmpty());
    }

    // ---------- Pomozno ----------

    private record Postavitev(Tekma tekma, Long idBranja) {}

    /* Koncana tekma z izidom `potrjeno` in potrjenim listkom s kodo (smer znana), na
       fotografiji katerega je IZID. Stevke in zlati nabor sta zapisana. */
    private Postavitev potrjenListek(int[][] potrjeno, Random seme) throws IOException {
        prijava("admin-ucenje@test");
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
        VnosRezultata izid = vnos(potrjeno, false);
        tekmaStoritev.vnesiRezultat(tekma.getId(), izid);

        BranjeListka b = new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now());
        b.setTekma(tekma);
        b.setPrepoznava(PrepoznavaTekme.KODA);
        b.setStanje(StanjeBranja.POTRJENO);
        b.setPotrjeno(JsonBranja.zapisi(izid));
        b = branja.save(b);
        slike.save(new SlikaBranjaListka(b.getId(), SimuliraneFotografijeTest.fotografijaIzida(IZID, seme),
                2000, 1500));
        assertTrue(ucenje.zabelezi(b.getId(), izid, false, 5, "testni") >= NAJMANJ);
        return new Postavitev(tekma, b.getId());
    }

    private Long branje(byte[] foto) {
        Dogodek dogodek = pripraviDogodek(4);
        BranjeListka b = branja.save(new BranjeListka(dogodek.getTurnir(), null, LocalDateTime.now()));
        slike.save(new SlikaBranjaListka(b.getId(), foto, 2000, 1500));
        return b.getId();
    }

    /* Potrjen izid v straneh tekme; obrnjeno = prijava 1 je desni stolpec listka. */
    static VnosRezultata vnos(int[][] izid, boolean obrnjeno) {
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

    private void prijava(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ADMIN);
        u.setStatus(StatusRacuna.POTRJEN);
        uporabniki.save(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(prijavnoIme,
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
