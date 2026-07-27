/* Testi formata TOP (sistem SKUPINE): izbor najboljsih N, zaporedna
   razporeditev po jakosti, krozni razpored znotraj skupine, odstop igralca
   in zakljucek brez izlocilnega dela. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;

class SkupinskiSistemTest extends IntegracijskiTest {

    // ------------------------------------------------------------------
    // Izbor in jakostni vrstni red
    // ------------------------------------------------------------------

    @Test
    void predlogUredilPoRatinguInIgralceBrezRatingaPostaviNaVrh() {
        Dogodek dogodek = pripraviSkupinskiDogodek(5, 1, 4);
        // prvi trije prijavljeni dobijo rating, zadnja dva ostaneta brez
        nastaviRatinge(dogodek, 1200, 1600, 1400);

        List<Prijava> vrstniRed = izborStoritev.vrstniRed(dogodek.getId());
        List<Integer> ratingi = vrstniRed.stream()
                .map(p -> izborStoritev.ratingi(vrstniRed).get(p.getIgralec().getId()))
                .toList();

        assertNull(ratingi.get(0), "igralec brez ratinga mora biti na vrhu");
        assertNull(ratingi.get(1), "oba brez ratinga sta na vrhu");
        assertEquals(List.of(1600, 1400, 1200), ratingi.subList(2, 5),
                "ostali po ratingu padajoce");
    }

    @Test
    void rocniVrstniRedPovoziPredlog() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        nastaviRatinge(dogodek, 1000, 1100, 1200, 1300);

        List<Prijava> predlog = izborStoritev.vrstniRed(dogodek.getId());
        // obrni vrstni red in ga shrani
        List<Long> obrnjeni = new ArrayList<>(predlog.stream().map(Prijava::getId).toList());
        java.util.Collections.reverse(obrnjeni);
        izborStoritev.shraniVrstniRed(dogodek.getId(), obrnjeni);

        List<Long> poShranjevanju = izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getId).toList();
        assertEquals(obrnjeni, poShranjevanju, "velja rocni vrstni red, ne predlog");
        assertEquals(List.of(1, 2, 3, 4), izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getStNosilca).toList(), "mesta so oststevilcena 1..N");
    }

    @Test
    void delniVrstniRedJeZavrnjen() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        List<Long> samoDva = izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getId).limit(2).toList();

        assertThrows(NeveljavenVnosIzjema.class,
                () -> izborStoritev.shraniVrstniRed(dogodek.getId(), samoDva),
                "delni seznam bi koga tiho pustil brez mesta");
    }

    @Test
    void vrstnegaRedaPoZrebuNiVecMogoceUrejati() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        List<Long> idji = izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getId).toList();
        zrebStoritev.izvediZreb(dogodek.getId());

        assertThrows(DomenskaIzjema.class,
                () -> izborStoritev.shraniVrstniRed(dogodek.getId(), idji));
    }

    // ------------------------------------------------------------------
    // Razporeditev v skupine
    // ------------------------------------------------------------------

    @Test
    void najboljsih24GreVTriSkupinePoOsemZaporedomaPoJakosti() {
        Dogodek dogodek = pripraviSkupinskiDogodek(24, 3, 8);
        // padajoci ratingi: prvi prijavljeni je najmocnejsi
        int[] ratingi = new int[24];
        for (int i = 0; i < 24; i++) {
            ratingi[i] = 2000 - i * 10;
        }
        nastaviRatinge(dogodek, ratingi);

        List<Long> pricakovaniVrstniRed = izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getId).toList();
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        assertEquals(List.of("A", "B", "C"), skupine.stream().map(Skupina::getOznaka).toList());

        // skupina A drzi mesta 1-8, B 9-16, C 17-24
        for (int i = 0; i < 3; i++) {
            List<Long> vSkupini = claniSkupine(dogodek, skupine.get(i).getId())
                    .stream().map(Prijava::getId).toList();
            assertEquals(new HashSet<>(pricakovaniVrstniRed.subList(i * 8, i * 8 + 8)),
                    new HashSet<>(vSkupini),
                    "skupina " + skupine.get(i).getOznaka() + " zajame mesta "
                            + (i * 8 + 1) + "-" + (i * 8 + 8));
        }
    }

    @Test
    void vsakZVsakimZnotrajSkupineInNobenaTekmaCezSkupine() {
        Dogodek dogodek = pripraviSkupinskiDogodek(12, 3, 4);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        // 3 skupine po 4 igralce -> vsaka 6 tekem
        assertEquals(18, tekme.size());
        assertTrue(tekme.stream().allMatch(t -> t.getFaza() == FazaTekme.SKUPINA),
                "izlocilnega dela pri tem sistemu ni");

        Set<String> pari = new HashSet<>();
        for (Tekma tekma : tekme) {
            assertEquals(tekma.getPrijava1().getIdSkupina(), tekma.getPrijava2().getIdSkupina(),
                    "tekma mora biti znotraj ene skupine");
            assertEquals(tekma.getIdSkupina(), tekma.getPrijava1().getIdSkupina());
            // vsak par natanko enkrat
            long a = Math.min(tekma.getPrijava1().getId(), tekma.getPrijava2().getId());
            long b = Math.max(tekma.getPrijava1().getId(), tekma.getPrijava2().getId());
            assertTrue(pari.add(a + "-" + b), "par " + a + "-" + b + " se ponovi");
        }
    }

    @Test
    void zadnjaSkupinaJeManjsaKoJePrijavPremalo() {
        Dogodek dogodek = pripraviSkupinskiDogodek(20, 3, 8);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        List<Integer> velikosti = skupine.stream()
                .map(s -> claniSkupine(dogodek, s.getId()).size()).toList();
        assertEquals(List.of(8, 8, 4), velikosti, "najmocnejsi skupini ostaneta polni");
    }

    @Test
    void neizbraniPostanejoRezerveInNeIgrajo() {
        Dogodek dogodek = pripraviSkupinskiDogodek(10, 2, 4);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Prijava> vse = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        List<Prijava> rezerve = vse.stream()
                .filter(p -> p.getStatus() == Prijava.StatusPrijave.REZERVA).toList();
        assertEquals(2, rezerve.size(), "10 prijav, 8 mest -> 2 rezervi");

        for (Prijava rezerva : rezerve) {
            assertNull(rezerva.getIdSkupina(), "rezerva ni v skupini");
            assertTrue(rezerva.getStNosilca() > 8, "rezerve so pod crto reza");
            boolean igra = tekmeDogodka(dogodek.getId()).stream()
                    .anyMatch(t -> jeUdelezenec(t, rezerva.getId()));
            assertFalse(igra, "rezerva ne sme imeti tekem");
        }
    }

    @Test
    void skupinaZEnimIgralcemJeZavrnjena() {
        // 17 igralcev pri skupinah po 8 bi dalo razrez 8 + 8 + 1
        Dogodek dogodek = pripraviSkupinskiDogodek(17, 3, 8);
        assertThrows(DomenskaIzjema.class, () -> zrebStoritev.izvediZreb(dogodek.getId()));
    }

    @Test
    void razrezSkupinPolniNajmocnejseSkupine() {
        assertEquals(List.of(8, 8, 4), ZrebStoritev.velikostiSkupin(20, 8));
        assertEquals(List.of(8, 8, 8), ZrebStoritev.velikostiSkupin(24, 8));
        assertEquals(List.of(5), ZrebStoritev.velikostiSkupin(5, 8));
        assertNull(ZrebStoritev.zadrzekRazreza(List.of(8, 8, 4)));
        assertNotNull(ZrebStoritev.zadrzekRazreza(ZrebStoritev.velikostiSkupin(17, 8)));
    }

    /* Razpored po jakosti: prvi nosilec zacne z najsibkejsim v skupini,
       dvoboj prvih dveh pa pade v zadnje kolo. */
    @Test
    void dvobojPrvihDvehNosilcevJeVZadnjemKolu() {
        Dogodek dogodek = pripraviSkupinskiDogodek(8, 1, 8);
        List<Long> poJakosti = izborStoritev.vrstniRed(dogodek.getId())
                .stream().map(Prijava::getId).toList();
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        int zadnjeKolo = tekme.stream().mapToInt(Tekma::getKolo).max().orElseThrow();
        assertEquals(7, zadnjeKolo, "8 igralcev -> 7 kol");

        Tekma vrh = najdiTekmo(tekme, poJakosti.get(0), poJakosti.get(1));
        assertEquals(zadnjeKolo, vrh.getKolo(), "1. proti 2. nosilcu v zadnjem kolu");

        Tekma prva = najdiTekmo(tekme, poJakosti.get(0), poJakosti.get(7));
        assertEquals(1, prva.getKolo(), "1. nosilec zacne z najsibkejsim");
    }

    // ------------------------------------------------------------------
    // Potek in zakljucek
    // ------------------------------------------------------------------

    @Test
    void koncanaSkupinaDobiMestaZakljucekPaJeBrezSkupneRazvrstitve() {
        Dogodek dogodek = pripraviSkupinskiDogodek(6, 2, 3);
        zrebStoritev.izvediZreb(dogodek.getId());

        for (Tekma tekma : tekmeDogodka(dogodek.getId())) {
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        }

        Dogodek svez = dogodekRepozitorij.findById(dogodek.getId()).orElseThrow();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, svez.getStatus());

        List<Prijava> igralci = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        for (Prijava prijava : igralci) {
            assertNotNull(prijava.getMestoVSkupini(), "vsak dobi mesto v svoji skupini");
            assertNull(prijava.getKoncnoMesto(),
                    "skupne razvrstitve cez skupine ni - skupine so rangi");
        }
        assertTrue(tekmeDogodka(dogodek.getId()).stream()
                        .noneMatch(t -> t.getFaza() == FazaTekme.GLAVNI),
                "izlocilni del se ne sme zgenerirati");
    }

    @Test
    void odstopOhraniOdigraneTekmePreostaleDobijoNasprotniki() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId()).stream()
                .sorted(Comparator.comparing(Tekma::getKolo).thenComparing(Tekma::getPozicija))
                .toList();
        // prva tekma se odigra normalno
        Tekma odigrana = tekme.get(0);
        Long idOdstopnika = odigrana.getPrijava1().getId();
        tekmaStoritev.vnesiRezultat(odigrana.getId(), new VnosRezultata(null, 3, 1, null, null));

        tekmaStoritev.odstopiIgralca(idOdstopnika);

        Prijava odstopnik = prijavaRepozitorij.findById(idOdstopnika).orElseThrow();
        assertEquals(Prijava.StatusPrijave.ODSTOPIL, odstopnik.getStatus());

        for (Tekma tekma : tekmeDogodka(dogodek.getId())) {
            if (!jeUdelezenec(tekma, idOdstopnika)) {
                continue;
            }
            assertEquals(StatusTekme.KONCANA, tekma.getStatus());
            if (tekma.getId().equals(odigrana.getId())) {
                assertEquals(IzidTekme.IGRANO, tekma.getIzidTip(), "odigrana tekma obvelja");
                assertEquals(idOdstopnika, tekma.getZmagovalec().getId());
            } else {
                assertEquals(IzidTekme.BREZ_BOJA, tekma.getIzidTip());
                assertFalse(tekma.getZmagovalec().getId().equals(idOdstopnika),
                        "preostale tekme dobi nasprotnik");
            }
        }
    }

    @Test
    void odstopNeObracunaRatingaZaNeodigraneTekme() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());

        Tekma prva = tekmeDogodka(dogodek.getId()).get(0);
        Long idOdstopnika = prva.getPrijava1().getId();
        Long idIgralca = prva.getPrijava2().getIgralec().getId();

        long zapisovPred = ratingZgodovinaRepozitorij.count();
        tekmaStoritev.odstopiIgralca(idOdstopnika);

        assertEquals(zapisovPred, ratingZgodovinaRepozitorij.count(),
                "tekme, ki niso bile odigrane, ne smejo premakniti ELO");
        assertNotNull(idIgralca);
    }

    /* Odstop ustvari do sedem tekem brez boja naenkrat. Ce bi se stele med
       odigrane, bi igralec, ki ni nastopil, na lestvici dobil sedem porazov
       za tekme, ki jih ni bilo - v skupini pa morajo normalno steti kot
       zmage nasprotnikov, ker odlocajo o uvrstitvi. */
    @Test
    void tekmeBrezBojaNeStejejoVStatistikoAmpakStejejoVLestvicoSkupine() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());

        Prijava odstopnik = prijavaRepozitorij.najdiZaDogodekSStatusom(
                dogodek.getId(), Prijava.StatusPrijave.PRIJAVLJEN).get(0);
        Long idIgralca = odstopnik.getIgralec().getId();
        tekmaStoritev.odstopiIgralca(odstopnik.getId());

        // v globalni statistiki ga ni, ker ni odigral nicesar
        boolean vStatistiki = statistikaStoritev.globalnaLestvica().stream()
                .anyMatch(v -> v.idIgralca().equals(idIgralca) && v.odigrane() > 0);
        assertFalse(vStatistiki, "tekme brez boja ne smejo steti med odigrane");

        // v skupini pa njegovi nasprotniki dobijo zmage
        List<Prijava> clani = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> p.getIdSkupina() != null).toList();
        List<si.turnirko.dto.VrsticaLestviceDto> lestvica =
                razvrstitevStoritev.lestvica(clani, tekmeDogodka(dogodek.getId()));
        assertEquals(3, lestvica.stream().mapToInt(v -> v.zmage()).sum(),
                "trije nasprotniki dobijo po eno zmago");
        assertEquals(0, lestvica.stream()
                        .filter(v -> v.idIgralca().equals(idIgralca))
                        .findFirst().orElseThrow().zmage(),
                "odstopnik v skupini nima zmag");
    }

    @Test
    void odstopPredZrebomNiMogoc() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        Long idPrijave = prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).get(0).getId();

        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.odstopiIgralca(idPrijave));
    }

    // ------------------------------------------------------------------

    private List<Prijava> claniSkupine(Dogodek dogodek, Long idSkupine) {
        return prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> idSkupine.equals(p.getIdSkupina()))
                .toList();
    }

    private Tekma najdiTekmo(List<Tekma> tekme, Long idPrve, Long idDruge) {
        return tekme.stream()
                .filter(t -> jeUdelezenec(t, idPrve) && jeUdelezenec(t, idDruge))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tekme med " + idPrve + " in " + idDruge + " ni"));
    }

    private boolean jeUdelezenec(Tekma tekma, Long idPrijave) {
        return (tekma.getPrijava1() != null && tekma.getPrijava1().getId().equals(idPrijave))
                || (tekma.getPrijava2() != null && tekma.getPrijava2().getId().equals(idPrijave));
    }
}
