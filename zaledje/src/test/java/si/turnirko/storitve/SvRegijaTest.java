/* Testi sistema SV_REGIJA: razrez po nivojih, skupine, glavni in tolazilni
   zreb, igra za VSA mesta, prosta mesta, neodvisni nivoji, rocni vpis skupin in
   razporeditve v zrebu ter razveljavitev. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.SvPredlogDto;
import si.turnirko.dto.SvVnos;
import si.turnirko.dto.SvZrebPredlogDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.Zreb;
import si.turnirko.repozitoriji.ZrebRepozitorij;
import si.turnirko.storitve.SvRegijaStoritev.NivoRazrez;

class SvRegijaTest extends IntegracijskiTest {

    @Autowired private SvRegijaStoritev svRegija;
    @Autowired private ZrebRepozitorij zrebRepozitorij;

    @BeforeEach
    void ponovljivNakljucniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(7));
    }

    // ------------------------------------------------------------------
    // Razrez (cista logika)
    // ------------------------------------------------------------------

    private static List<Integer> velikosti(List<NivoRazrez> razrez) {
        return razrez.stream().map(NivoRazrez::velikost).toList();
    }

    @Test
    void polniNivojiPoSestnajstZadnjiDobiOstanek() {
        assertEquals(List.of(16, 16, 16), velikosti(SvRegijaStoritev.razrez(48, 4, 4, null, null)));
        assertEquals(List.of(16, 16, 8), velikosti(SvRegijaStoritev.razrez(40, 4, 4, null, null)));
        assertEquals(List.of(16, 16, 16, 16), velikosti(SvRegijaStoritev.razrez(64, 4, 4, null, null)));
        assertEquals(List.of(12), velikosti(SvRegijaStoritev.razrez(12, 4, 4, null, null)));
    }

    @Test
    void ostanekManjsiOdSkupineSePridruziPrejsnjemuNivoju() {
        // 16 + 1 ni nivo, ampak 17; 50 = 16 + 16 + 18
        assertEquals(List.of(16, 17), velikosti(SvRegijaStoritev.razrez(33, 4, 4, null, null)));
        assertEquals(List.of(16, 16, 18), velikosti(SvRegijaStoritev.razrez(50, 4, 4, null, null)));
    }

    @Test
    void organizatorIzberePoljubnoStevilNivojev() {
        // 4 nivoji: polni, dokler zadosca, sicer zadnji ostanek
        assertEquals(List.of(16, 16, 16, 12), velikosti(SvRegijaStoritev.razrez(60, 4, 4, 4, null)));
        // premalo za 4 polne: enakomerno (40 = 4 x 10)
        assertEquals(List.of(10, 10, 10, 10), velikosti(SvRegijaStoritev.razrez(40, 4, 4, 4, null)));
        // premalo za toliko nivojev
        assertThrows(DomenskaIzjema.class, () -> SvRegijaStoritev.razrez(5, 4, 4, 4, null));
    }

    @Test
    void rocneVelikostiNivojevMorajoSestatiVStevilo() {
        assertEquals(List.of(20, 12, 8),
                velikosti(SvRegijaStoritev.razrez(40, 4, 4, null, List.of(20, 12, 8))));
        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> SvRegijaStoritev.razrez(40, 4, 4, null, List.of(20, 12, 7)));
        assertTrue(napaka.getMessage().contains("39"), napaka.getMessage());
    }

    @Test
    void skupineInZrebiPolnegaNivoja() {
        NivoRazrez poln = SvRegijaStoritev.razrez(16, 4, 4, null, null).get(0);
        assertEquals(List.of(4, 4, 4, 4), poln.velikostiSkupin());
        // prva dva ranga v glavni zreb, druga dva v tolazilni
        assertEquals(List.of(8, 8), poln.velikostiZrebov());
        assertEquals(1, poln.odMesta());
        assertEquals(16, poln.doMesta());
    }

    @Test
    void nepopolniNivojDobiSkupineSVsajTremiIgralci() {
        assertEquals(List.of(4, 4, 3), SvRegijaStoritev.velikostiSkupin(11, 4));
        assertEquals(List.of(4, 3, 3), SvRegijaStoritev.velikostiSkupin(10, 4));
        assertEquals(List.of(3, 3, 3), SvRegijaStoritev.velikostiSkupin(9, 4));
        assertEquals(List.of(4, 4, 4, 3, 3), SvRegijaStoritev.velikostiSkupin(18, 4));
        // pet igralcev se ne da razdeliti v skupini po vsaj tri: ena skupina
        assertEquals(List.of(5), SvRegijaStoritev.velikostiSkupin(5, 4));
    }

    @Test
    void velikostiZrebovSestejejoVStevilIgralcev() {
        List<Integer> skupine = SvRegijaStoritev.velikostiSkupin(17, 4); // 4,4,3,3,3
        List<Integer> zrebi = SvRegijaStoritev.velikostiZrebov(skupine);
        assertEquals(List.of(10, 7), zrebi);
        assertEquals(17, zrebi.stream().mapToInt(Integer::intValue).sum());
        // nivo z eno skupino zrebov nima
        assertEquals(List.of(), SvRegijaStoritev.velikostiZrebov(List.of(5)));
    }

    // ------------------------------------------------------------------
    // Zreb skupin
    // ------------------------------------------------------------------

    @Test
    void zrebRazdeliIgralceVNivojeSkupinePoJakosti() {
        Dogodek dogodek = pripraviJakostniDogodek(32, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        assertEquals(List.of("1A", "1B", "1C", "1D", "2A", "2B", "2C", "2D"),
                skupine.stream().map(Skupina::getOznaka).toList());
        assertEquals(List.of(1, 1, 1, 1, 2, 2, 2, 2), skupine.stream().map(Skupina::getNivo).toList());

        // najmocnejsih 16 je v prvem nivoju
        Map<Long, Integer> nivoPoSkupini = new HashMap<>();
        skupine.forEach(s -> nivoPoSkupini.put(s.getId(), s.getNivo()));
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            int pricakovan = p.getStNosilca() <= 16 ? 1 : 2;
            assertEquals(pricakovan, nivoPoSkupini.get(p.getIdSkupina()),
                    "nosilec " + p.getStNosilca() + " v napacnem nivoju");
        }
        // vsaka skupina "vsak z vsakim": 6 tekem x 8 skupin
        assertEquals(48, tekmeDogodka(dogodek.getId()).size());
        // in zreba vsakega nivoja sta ustvarjena, a se brez tekem
        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId());
        assertEquals(4, zrebi.size());
        assertTrue(zrebi.stream().noneMatch(Zreb::isZgrajen));
        assertEquals(List.of(1, 9, 17, 25), zrebi.stream().map(Zreb::getPrvoMesto).toList());
    }

    // ------------------------------------------------------------------
    // Zreb za vsa mesta
    // ------------------------------------------------------------------

    @Test
    void poOdigranihSkupinahNastaneGlavniInTolazilniZrebZaVsaMesta() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);

        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId());
        assertTrue(zrebi.stream().allMatch(Zreb::isZgrajen));

        // mreza z 8 igralci za vsa mesta = 12 tekem: 4 + 2 + 1 + 1 + 2 + 1 + 1
        for (Zreb zreb : zrebi) {
            List<Tekma> tekme = tekmaRepozitorij.findByIdZreb(zreb.getId());
            assertEquals(12, tekme.size(), zreb.ime());
            assertEquals(zreb.faza(), tekme.get(0).getFaza());

            Map<String, Long> poRazponu = tekme.stream().collect(Collectors.groupingBy(
                    t -> t.getRazponOd() + "-" + t.getRazponDo(), Collectors.counting()));
            int od = zreb.getPrvoMesto();
            assertEquals(Map.of(
                            od + "-" + (od + 7), 4L,       // cetrtfinale
                            od + "-" + (od + 3), 2L,       // polfinale
                            (od + 4) + "-" + (od + 7), 2L, // polfinale za 5.-8.
                            od + "-" + (od + 1), 1L,       // finale
                            (od + 2) + "-" + (od + 3), 1L, // za 3. mesto
                            (od + 4) + "-" + (od + 5), 1L, // za 5. mesto
                            (od + 6) + "-" + (od + 7), 1L),// za 7. mesto
                    poRazponu);
        }
        assertEquals(24 + 24, tekmeDogodka(dogodek.getId()).size());
    }

    @Test
    void porazencaCetrtfinalaIzIstePolovicePlapataMedSeboj() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);

        Zreb glavni = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1).get(0);
        List<Tekma> tekme = tekmaRepozitorij.findByIdZreb(glavni.getId());
        List<Tekma> cetrtfinale = tekme.stream().filter(t -> t.getKolo() == 1)
                .sorted(Comparator.comparingInt(Tekma::getPozicija)).toList();
        Tekma zaPeto = tekme.stream().filter(t -> t.getRazponOd() == 5 && t.getRazponDo() == 8)
                .min(Comparator.comparingInt(Tekma::getPozicija)).orElseThrow();

        // zgornja polovica mreze: porazenca 1. in 2. cetrtfinala se srecata za 5.-8. mesto
        assertEquals(cetrtfinale.get(0).getId(), zaPeto.getIdIzvorTekma1());
        assertEquals(cetrtfinale.get(1).getId(), zaPeto.getIdIzvorTekma2());
        assertEquals(VlogaIzvora.PORAZENEC, zaPeto.getVlogaIzvora1());
        assertEquals(VlogaIzvora.PORAZENEC, zaPeto.getVlogaIzvora2());
    }

    @Test
    void vPrvemKoluGlavnegaZrebaIgralcaIzIsteSkupineNistaSkupaj() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);

        for (Zreb zreb : zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId())) {
            for (Tekma t : tekmaRepozitorij.findByIdZreb(zreb.getId())) {
                if (t.getKolo() == 1) {
                    assertNotEquals(t.getPrijava1().getIdSkupina(), t.getPrijava2().getIdSkupina(),
                            zreb.ime() + ": igralca iz iste skupine se ne srecata v 1. kolu");
                }
            }
        }
    }

    @Test
    void celotenDogodekSeZakljuciInVsakIgralecDobiSvojeMesto() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        Dogodek svez = dogodekRepozitorij.findById(dogodek.getId()).orElseThrow();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, svez.getStatus());
        assertEquals(poln(16), koncnaMesta(dogodek.getId()),
                "mesta 1..16, vsako natanko enkrat");
    }

    @Test
    void zmagovalecGlavnegaZrebaJePrviZmagovalecTolazilnegaPaDeveti() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId());
        for (Zreb zreb : zrebi) {
            Tekma finale = tekmaRepozitorij.findByIdZreb(zreb.getId()).stream()
                    .filter(t -> t.getRazponDo() - t.getRazponOd() == 1
                            && t.getRazponOd() == zreb.getPrvoMesto())
                    .findFirst().orElseThrow();
            Tekma sveza = tekmaRepozitorij.najdiZVsem(finale.getId()).orElseThrow();
            assertEquals(zreb.getPrvoMesto(), sveza.getZmagovalec().getKoncnoMesto());
            assertEquals(zreb.getPrvoMesto() + 1, sveza.porazenec().getKoncnoMesto());
        }
    }

    // ------------------------------------------------------------------
    // Nepopolni zrebi, prosta mesta
    // ------------------------------------------------------------------

    @Test
    void nivojZEnajstIgralciIgraZProstimiPrehodiInVsaMestaOstanejoStrnjena() {
        // 11 igralcev: skupine 4 + 4 + 3, glavni zreb 6 (2 prosta), tolazilni 5 (3 prosta)
        Dogodek dogodek = pripraviJakostniDogodek(11, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId());
        assertEquals(List.of(6, 5), zrebi.stream().map(Zreb::getStUdelezencev).toList());

        odigrajVse(dogodek.getId());

        Dogodek svez = dogodekRepozitorij.findById(dogodek.getId()).orElseThrow();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, svez.getStatus());
        assertEquals(poln(11), koncnaMesta(dogodek.getId()));

        // prosti prehod ne ustvari tekme: v glavnem zrebu s 6 igralci je 7 tekem
        // (2 + 2 + 1 + 1 + 1), ne 12
        assertEquals(7, tekmaRepozitorij.findByIdZreb(zrebi.get(0).getId()).size());
        assertTrue(tekmeDogodka(dogodek.getId()).stream()
                .noneMatch(t -> t.getFaza() != FazaTekme.SKUPINA
                        && (t.getPrijava1() == null || t.getPrijava2() == null)),
                "odigrana tekma ima vedno oba igralca");
    }

    @Test
    void nivojZEnoSamoSkupinoNimaZrebaInMestaDolociSkupina() {
        Dogodek dogodek = pripraviJakostniDogodek(4, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        assertTrue(zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId()).isEmpty());

        odigrajVse(dogodek.getId());

        Dogodek svez = dogodekRepozitorij.findById(dogodek.getId()).orElseThrow();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, svez.getStatus());
        assertEquals(poln(4), koncnaMesta(dogodek.getId()));
    }

    // ------------------------------------------------------------------
    // Neodvisni nivoji
    // ------------------------------------------------------------------

    @Test
    void zrebNivojaNeCakaNaDrugeNivojeDogodekPaSeZakljuciSeleKoJeKoncanVsak() {
        Dogodek dogodek = pripraviJakostniDogodek(32, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());

        odigrajSkupine(dogodek.getId(), 1);

        List<Zreb> prvi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1);
        List<Zreb> drugi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 2);
        assertTrue(prvi.stream().allMatch(Zreb::isZgrajen), "zreb 1. nivoja nastane takoj");
        assertTrue(drugi.stream().noneMatch(Zreb::isZgrajen), "2. nivo se ni odigral skupin");

        // cel prvi nivo do konca: dogodek se NE zakljuci, drugi nivo se igra
        odigrajNivo(dogodek.getId(), 1);
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());

        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(poln(32), koncnaMesta(dogodek.getId()));

        // nivoji ostanejo vsak v svojem razponu mest
        Map<Long, Integer> nivoPoSkupini = new HashMap<>();
        skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId())
                .forEach(s -> nivoPoSkupini.put(s.getId(), s.getNivo()));
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            int nivo = nivoPoSkupini.get(p.getIdSkupina());
            assertEquals(nivo == 1, p.getKoncnoMesto() <= 16, "mesto " + p.getKoncnoMesto());
        }
    }

    // ------------------------------------------------------------------
    // Nastavitve
    // ------------------------------------------------------------------

    @Test
    void nastavitveSpremenijoRazrezPredZrebom() {
        Dogodek dogodek = pripraviJakostniDogodek(24, SistemTekmovanja.SV_REGIJA);

        svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(null, List.of(10, 8, 6), 4, 4));
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        Map<Integer, Long> poNivoju = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .collect(Collectors.groupingBy(
                        p -> skupine.stream().filter(s -> s.getId().equals(p.getIdSkupina()))
                                .findFirst().orElseThrow().getNivo(),
                        Collectors.counting()));
        assertEquals(Map.of(1, 10L, 2, 8L, 3, 6L), poNivoju);
    }

    @Test
    void osemdesetIgralcevVTrehAliStirihNivojihSeOdigraDoKonca() {
        for (int nivojev : new int[] {3, 4}) {
            Dogodek dogodek = pripraviJakostniDogodek(80, SistemTekmovanja.SV_REGIJA);
            svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(nivojev, null, null, null));
            zrebStoritev.izvediZreb(dogodek.getId());

            // polni nivoji po 16, zadnji dobi ostanek (3 nivoji: 16+16+48, 4 nivoji: 16+16+16+32)
            List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
            Map<Long, Integer> nivoPoSkupini = new HashMap<>();
            skupine.forEach(s -> nivoPoSkupini.put(s.getId(), s.getNivo()));
            Map<Integer, Long> poNivoju = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                    .collect(Collectors.groupingBy(p -> nivoPoSkupini.get(p.getIdSkupina()),
                            Collectors.counting()));
            Map<Integer, Long> pricakovano = nivojev == 3
                    ? Map.of(1, 16L, 2, 16L, 3, 48L)
                    : Map.of(1, 16L, 2, 16L, 3, 16L, 4, 32L);
            assertEquals(pricakovano, poNivoju, nivojev + " nivoji");

            odigrajVse(dogodek.getId());

            assertEquals(StatusTekmovanja.ZAKLJUCEN,
                    dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
            assertEquals(poln(80), koncnaMesta(dogodek.getId()), nivojev + " nivoji: mesta 1..80");
        }
    }

    @Test
    void enRangVZrebuDaZrebePoRangih() {
        List<Integer> osemSkupin = SvRegijaStoritev.velikostiSkupin(31, 4); // 4 x7, 3
        assertEquals(List.of(16, 15), SvRegijaStoritev.velikostiZrebov(osemSkupin, 2));
        // vsak rang svoj zreb: 8 + 8 + 8 + 7
        assertEquals(List.of(8, 8, 8, 7), SvRegijaStoritev.velikostiZrebov(osemSkupin, 1));
        // nastavitev velja po nivojih: prvi nivo privzeto, drugi po rangih
        List<NivoRazrez> razrez = SvRegijaStoritev.razrez(47, 4, 4, null, List.of(16, 31), List.of(2, 1));
        assertEquals(List.of(8, 8), razrez.get(0).velikostiZrebov());
        assertEquals(List.of(8, 8, 8, 7), razrez.get(1).velikostiZrebov());
    }

    @Test
    void zrebiPoRangihSeOdigrajoDoKonca() {
        Dogodek dogodek = pripraviJakostniDogodek(31, SistemTekmovanja.SV_REGIJA);
        svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(1, null, null, null, List.of(1)));
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1);
        assertEquals(List.of(1, 9, 17, 25), zrebi.stream().map(Zreb::getPrvoMesto).toList());
        assertEquals(List.of(8, 8, 8, 7), zrebi.stream().map(Zreb::getStUdelezencev).toList());

        odigrajVse(dogodek.getId());

        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(poln(31), koncnaMesta(dogodek.getId()));
    }

    @Test
    void rangovVZrebuSta1Ali2() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        assertThrows(NeveljavenVnosIzjema.class, () -> svRegija.nastavi(dogodek.getId(),
                new SvVnos.Nastavitve(null, null, null, null, List.of(3))));
        assertThrows(NeveljavenVnosIzjema.class, () -> svRegija.nastavi(dogodek.getId(),
                new SvVnos.Nastavitve(null, null, null, null, List.of(0))));
        svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(null, null, null, null, List.of(2, 1)));
        assertEquals(List.of(2, 1),
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().rangovVZrebSeznam());
    }

    @Test
    void nastavitevNeGredoPoZrebu() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        assertThrows(DomenskaIzjema.class,
                () -> svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(2, null, null, null)));
    }

    @Test
    void neujemajoceRocneVelikostiZaustavijoZreb() {
        Dogodek dogodek = pripraviJakostniDogodek(24, SistemTekmovanja.SV_REGIJA);
        svRegija.nastavi(dogodek.getId(), new SvVnos.Nastavitve(null, List.of(10, 8, 5), null, null));
        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> zrebStoritev.izvediZreb(dogodek.getId()));
        assertTrue(napaka.getMessage().contains("23"), napaka.getMessage());
    }

    // ------------------------------------------------------------------
    // Rocni vpis in popravki
    // ------------------------------------------------------------------

    @Test
    void predlogSkupinNiZapisanVBazo() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        SvPredlogDto predlog = svRegija.predlogSkupin(dogodek.getId());

        assertEquals(1, predlog.nivoji().size());
        assertEquals(4, predlog.nivoji().get(0).skupine().size());
        assertEquals(16, predlog.nivoji().get(0).skupine().stream().mapToInt(s -> s.clani().size()).sum());
        assertTrue(tekmeDogodka(dogodek.getId()).isEmpty());
        assertTrue(skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).isEmpty());
    }

    @Test
    void rocniVpisSkupinPostaviIgralceTamKamorHoceOrganizator() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SV_REGIJA);
        List<Prijava> po = izborStoritev.vrstniRed(dogodek.getId());
        List<Long> id = po.stream().map(Prijava::getId).toList();

        // prvi nivo: najmocnejsi 1, 2, 7, 8 in 3, 4, 5, 6 - drugace, kot bi zreb
        zrebStoritev.izvediRocniZrebSv(dogodek.getId(), new SvVnos.Skupine(List.of(
                new SvVnos.Skupine.Nivo(List.of(
                        List.of(id.get(0), id.get(1), id.get(6), id.get(7)),
                        List.of(id.get(2), id.get(3), id.get(4), id.get(5)))))));

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        assertEquals(List.of("1A", "1B"), skupine.stream().map(Skupina::getOznaka).toList());
        Map<Long, Long> skupinaPoPrijavi = new HashMap<>();
        prijavaRepozitorij.najdiZaDogodek(dogodek.getId())
                .forEach(p -> skupinaPoPrijavi.put(p.getId(), p.getIdSkupina()));
        assertEquals(skupine.get(0).getId(), skupinaPoPrijavi.get(id.get(6)));
        assertEquals(skupine.get(1).getId(), skupinaPoPrijavi.get(id.get(2)));
        assertEquals(12, tekmeDogodka(dogodek.getId()).size());
        assertEquals(StatusTekmovanja.V_TEKU,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());

        odigrajVse(dogodek.getId());
        assertEquals(poln(8), koncnaMesta(dogodek.getId()));
    }

    @Test
    void rocniVpisZavrneManjkajocegaPodvojenegaInPremajhnoSkupino() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SV_REGIJA);
        List<Long> id = izborStoritev.vrstniRed(dogodek.getId()).stream().map(Prijava::getId).toList();

        // manjka eden
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSv(dogodek.getId(),
                new SvVnos.Skupine(List.of(new SvVnos.Skupine.Nivo(List.of(
                        id.subList(0, 4), id.subList(4, 7)))))));
        // isti igralec dvakrat
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSv(dogodek.getId(),
                new SvVnos.Skupine(List.of(new SvVnos.Skupine.Nivo(List.of(
                        id.subList(0, 4), List.of(id.get(3), id.get(4), id.get(5), id.get(6), id.get(7))))))));
        // skupina z enim igralcem
        assertThrows(NeveljavenVnosIzjema.class, () -> zrebStoritev.izvediRocniZrebSv(dogodek.getId(),
                new SvVnos.Skupine(List.of(new SvVnos.Skupine.Nivo(List.of(
                        id.subList(0, 7), List.of(id.get(7))))))));
        assertTrue(tekmeDogodka(dogodek.getId()).isEmpty(), "zavrnjen vpis ne pusti sledi");
    }

    @Test
    void razporeditevMestVZrebuSePredPrvoTekmoLahkoPopravi() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);

        Zreb glavni = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1).get(0);
        SvZrebPredlogDto predlog = svRegija.predlogZreba(glavni.getId());
        assertEquals(8, predlog.velikostMreze());

        // zamenjaj prvo in zadnje mesto
        List<Long> mesta = new ArrayList<>(predlog.mesta().stream()
                .map(c -> c == null ? null : c.idPrijave()).toList());
        Long prvi = mesta.get(0);
        mesta.set(0, mesta.get(7));
        mesta.set(7, prvi);
        svRegija.nastaviMesta(glavni.getId(), mesta);

        Zreb po = zrebRepozitorij.findById(glavni.getId()).orElseThrow();
        assertTrue(po.isRocni(), "razpored je vpisal organizator");
        List<Tekma> prvoKolo = tekmaRepozitorij.findByIdZreb(glavni.getId()).stream()
                .filter(t -> t.getKolo() == 1).sorted(Comparator.comparingInt(Tekma::getPozicija)).toList();
        assertEquals(mesta.get(0), prvoKolo.get(0).getPrijava1().getId());
        assertEquals(mesta.get(1), prvoKolo.get(0).getPrijava2().getId());
        assertEquals(mesta.get(6), prvoKolo.get(3).getPrijava1().getId());
        assertEquals(mesta.get(7), prvoKolo.get(3).getPrijava2().getId());
        assertEquals(12, tekmaRepozitorij.findByIdZreb(glavni.getId()).size(), "drevo je spet polno");

        // po prvi odigrani tekmi zreba ni vec mogoce spreminjati
        Tekma prva = prvoKolo.get(0);
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 1, null, null));
        assertThrows(DomenskaIzjema.class, () -> svRegija.nastaviMesta(glavni.getId(), mesta));
        assertThrows(DomenskaIzjema.class, () -> svRegija.znovaZrebaj(glavni.getId()));
    }

    @Test
    void razporeditevZUdelezencemIzDrugegaZrebaJeZavrnjena() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);

        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1);
        List<Long> glavni = svRegija.predlogZreba(zrebi.get(0).getId()).mesta().stream()
                .map(c -> c.idPrijave()).toList();
        List<Long> tolazilni = svRegija.predlogZreba(zrebi.get(1).getId()).mesta().stream()
                .map(c -> c.idPrijave()).toList();

        List<Long> mesta = new ArrayList<>(glavni);
        mesta.set(0, tolazilni.get(0));
        assertThrows(NeveljavenVnosIzjema.class, () -> svRegija.nastaviMesta(zrebi.get(0).getId(), mesta));
        // premajhna mreza
        assertThrows(NeveljavenVnosIzjema.class,
                () -> svRegija.nastaviMesta(zrebi.get(0).getId(), glavni.subList(0, 4)));
    }

    @Test
    void znovaIzzrebanZrebDaSpetPopolnoDrevo() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupine(dogodek.getId(), 1);
        Zreb tolazilni = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), 1).get(1);

        svRegija.znovaZrebaj(tolazilni.getId());

        assertEquals(12, tekmaRepozitorij.findByIdZreb(tolazilni.getId()).size());
        assertFalse(zrebRepozitorij.findById(tolazilni.getId()).orElseThrow().isRocni());
        odigrajVse(dogodek.getId());
        assertEquals(poln(16), koncnaMesta(dogodek.getId()));
    }

    // ------------------------------------------------------------------
    // Razveljavitev
    // ------------------------------------------------------------------

    @Test
    void razveljavitevVrneDogodekVPripravoBrezSkupinInTekem() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        assertFalse(tekmeDogodka(dogodek.getId()).isEmpty());

        svRegija.razveljaviZrebSkupin(dogodek.getId());

        assertEquals(StatusTekmovanja.PRIPRAVA,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertTrue(tekmeDogodka(dogodek.getId()).isEmpty());
        assertTrue(skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).isEmpty());
        assertTrue(zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(dogodek.getId()).isEmpty());
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNull(p.getIdSkupina());
        }

        // in zreb je spet mogoc
        zrebStoritev.izvediZreb(dogodek.getId());
        assertEquals(24, tekmeDogodka(dogodek.getId()).size());
    }

    @Test
    void razveljavitevPoZacetkuTekmeNiMogoca() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma prva = tekmeDogodka(dogodek.getId()).get(0);
        tekmaStoritev.vnesiRezultat(prva.getId(), new VnosRezultata(null, 3, 0, null, null));

        assertThrows(DomenskaIzjema.class, () -> svRegija.razveljaviZrebSkupin(dogodek.getId()));
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediRocniZrebSv(dogodek.getId(),
                new SvVnos.Skupine(List.of(new SvVnos.Skupine.Nivo(List.of(List.of(1L, 2L)))))));
    }

    @Test
    void rocniVpisPoZrebuPrepisePrejsnjeSkupine() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SV_REGIJA);
        List<Long> id = izborStoritev.vrstniRed(dogodek.getId()).stream().map(Prijava::getId).toList();
        zrebStoritev.izvediZreb(dogodek.getId());

        zrebStoritev.izvediRocniZrebSv(dogodek.getId(), new SvVnos.Skupine(List.of(
                new SvVnos.Skupine.Nivo(List.of(id.subList(0, 4), id.subList(4, 8))))));

        Map<Long, Long> skupinaPoPrijavi = new HashMap<>();
        prijavaRepozitorij.najdiZaDogodek(dogodek.getId())
                .forEach(p -> skupinaPoPrijavi.put(p.getId(), p.getIdSkupina()));
        assertEquals(skupinaPoPrijavi.get(id.get(0)), skupinaPoPrijavi.get(id.get(3)));
        assertNotEquals(skupinaPoPrijavi.get(id.get(0)), skupinaPoPrijavi.get(id.get(4)));
        assertEquals(12, tekmeDogodka(dogodek.getId()).size());
    }

    // ------------------------------------------------------------------
    // Odstop
    // ------------------------------------------------------------------

    @Test
    void odstopIgralcaMedSkupinamiNeZaustaviZrebov() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        Prijava odstopil = izborStoritev.vrstniRed(dogodek.getId()).get(15);

        tekmaStoritev.odstopiIgralca(odstopil.getId());
        odigrajVse(dogodek.getId());

        assertEquals(StatusTekmovanja.ZAKLJUCEN,
                dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(poln(16), koncnaMesta(dogodek.getId()));
    }

    // ------------------------------------------------------------------
    // Pomozno
    // ------------------------------------------------------------------

    /* Odigra vse pripravljene tekme, dokler jih je (po vsaki koncani skupini ali
       zrebu nastanejo nove). Zmaga vedno prvi na tekmi. */
    private void odigrajVse(Long idDogodka) {
        for (int krog = 0; krog < 300; krog++) {
            List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            if (pripravljene.isEmpty()) {
                return;
            }
            for (Tekma t : pripravljene) {
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
        throw new AssertionError("turnir se ne konca");
    }

    /* Odigra samo skupinske tekme danega nivoja. */
    private void odigrajSkupine(Long idDogodka, int nivo) {
        Set<Long> idjiSkupin = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka).stream()
                .filter(s -> s.getNivo() == nivo).map(Skupina::getId).collect(Collectors.toSet());
        for (Tekma t : tekmeDogodka(idDogodka)) {
            if (t.getFaza() == FazaTekme.SKUPINA && idjiSkupin.contains(t.getIdSkupina())
                    && t.getStatus() == StatusTekme.PRIPRAVLJENA) {
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
    }

    /* Odigra vse tekme danega nivoja (skupine in zrebe). */
    private void odigrajNivo(Long idDogodka, int nivo) {
        Set<Long> idjiSkupin = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka).stream()
                .filter(s -> s.getNivo() == nivo).map(Skupina::getId).collect(Collectors.toSet());
        Set<Long> idjiZrebov = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(idDogodka, nivo)
                .stream().map(Zreb::getId).collect(Collectors.toSet());
        for (int krog = 0; krog < 300; krog++) {
            List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .filter(t -> idjiSkupin.contains(t.getIdSkupina()) || idjiZrebov.contains(t.getIdZreb()))
                    .toList();
            if (pripravljene.isEmpty()) {
                return;
            }
            for (Tekma t : pripravljene) {
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }
    }

    private List<Integer> koncnaMesta(Long idDogodka) {
        List<Integer> mesta = new ArrayList<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(idDogodka)) {
            assertTrue(p.getKoncnoMesto() != null, "igralec brez koncnega mesta");
            mesta.add(p.getKoncnoMesto());
        }
        mesta.sort(Integer::compare);
        return mesta;
    }

    private static List<Integer> poln(int n) {
        List<Integer> mesta = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            mesta.add(i);
        }
        return mesta;
    }
}
