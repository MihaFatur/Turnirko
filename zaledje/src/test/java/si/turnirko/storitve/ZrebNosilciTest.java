/* Testi zreba z nosilci (NosilciStoritev):
   - razdelitev v skupine po jakostnih pasovih,
   - nosilska mesta v izlocilni mrezi (vrh, dno, cetrtine, osmine),
   - izlocilni del po skupinah (soigralca iz skupine sele v finalu,
     zmagovalec skupine v 1. kolu ne igra z zmagovalcem skupine),
   - klubska locitev kot mehko pravilo. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.VnosRezultata;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.Tekma;

class ZrebNosilciTest extends IntegracijskiTest {

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    // ------------------------------------------------------------------
    // Geometrija mreze
    // ------------------------------------------------------------------

    /* Prvi nosilec na vrhu, drugi na DNU (in ne na sredini). */
    @Test
    void nosilskaMestaMreze() {
        assertArrayEquals(new int[] {1, 2}, NosilciStoritev.seedVrstniRed(2));
        assertArrayEquals(new int[] {1, 4, 3, 2}, NosilciStoritev.seedVrstniRed(4));
        assertArrayEquals(new int[] {1, 8, 5, 4, 3, 6, 7, 2}, NosilciStoritev.seedVrstniRed(8));
        assertArrayEquals(new int[] {1, 16, 9, 8, 5, 12, 13, 4, 3, 14, 11, 6, 7, 10, 15, 2},
                NosilciStoritev.seedVrstniRed(16));
    }

    /* Pasovi se podvajajo, prva dva nosilca pa sta vsak svoj pas. */
    @Test
    void jakostniPasoviSePodvajajo() {
        assertEquals(List.of("0-1", "1-2", "2-4", "4-8", "8-11"),
                NosilciStoritev.pasovi(11).stream().map(p -> p[0] + "-" + p[1]).toList());
    }

    // ------------------------------------------------------------------
    // Razdelitev v skupine
    // ------------------------------------------------------------------

    @Test
    void prviPasNosilcevGreVSkupinePoVrsti() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<List<Prijava>> skupine = poSkupinah(dogodek);
        assertEquals(4, skupine.size(), "16 igralcev -> 4 skupine po 4");
        for (int i = 0; i < skupine.size(); i++) {
            assertEquals(i + 1, skupine.get(i).get(0).getStNosilca(),
                    "skupina " + (char) ('A' + i) + " ima za nosilca " + (i + 1) + ". igralca");
        }
    }

    @Test
    void vsakPasDaVsakiSkupiniPoEnegaIgralca() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        for (List<Prijava> skupina : poSkupinah(dogodek)) {
            assertEquals(List.of(0, 1, 2, 3), pasoviSkupine(skupina, 4),
                    "v skupini mora biti po en igralec iz vsakega jakostnega pasu");
        }
    }

    /* 14 igralcev v 4 skupinah: prvi trije pasovi so polni, zadnjega dobita
       dve skupini - in ti sta izzrebani, ne prvi po vrsti. */
    @Test
    void nepolnPasDobitaLeDveSkupini() {
        Dogodek dogodek = pripraviJakostniDogodek(14, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<List<Prijava>> skupine = poSkupinah(dogodek);
        assertEquals(List.of(3, 3, 4, 4),
                skupine.stream().map(List::size).sorted().toList());
        for (List<Prijava> skupina : skupine) {
            List<Integer> pasovi = pasoviSkupine(skupina, 4);
            assertEquals(List.of(0, 1, 2), pasovi.subList(0, 3),
                    "polni pasovi dajo vsaki skupini po enega igralca");
        }
    }

    /* Prvi pas ni zreb (1. nosilec vedno v A), vsi naslednji pa so. */
    @Test
    void prviPasJeStalenOstaliPasoviSeZrebajo() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SKUPINE_IZLOCILNI);
        List<Prijava> poJakosti = izborStoritev.vrstniRed(dogodek.getId());

        Set<String> razlicneRazdelitve = new HashSet<>();
        for (int ponovitev = 0; ponovitev < 20; ponovitev++) {
            List<List<Prijava>> skupine = nosilciStoritev.vSkupine(poJakosti, 4);

            List<String> drugiPas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                assertSame(poJakosti.get(i), skupine.get(i).get(0),
                        "prvi pas se ne zreba: " + (i + 1) + ". nosilec je nosilec svoje skupine");
                drugiPas.add(String.valueOf(poJakosti.indexOf(skupine.get(i).get(1))));
            }
            razlicneRazdelitve.add(String.join("-", drugiPas));
        }
        assertTrue(razlicneRazdelitve.size() > 1,
                "drugi pas se zreba - pri razlicnih semenih mora pasti drugace");
    }

    @Test
    void igralcaIstegaKlubaNistaVIstiSkupini() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SKUPINE_IZLOCILNI);
        nastaviKlube(dogodek,
                "Krim", "Olimpija", "Maribor", "Celje",
                "Krim", "Olimpija", "Maribor", "Celje",
                "Krim", "Olimpija", "Ptuj", "Kranj",
                "Krim", "Kranj", "Ptuj", "Celje");
        zrebStoritev.izvediZreb(dogodek.getId());

        for (List<Prijava> skupina : poSkupinah(dogodek)) {
            List<String> klubi = skupina.stream().map(p -> p.getKlubObPrijavi().getIme()).toList();
            assertEquals(klubi.size(), new HashSet<>(klubi).size(),
                    "v skupini se klub ponovi: " + klubi);
        }
    }

    /* Klubska locitev je mehko pravilo: ce je igralcev enega kluba prevec,
       zreb ne obstane, ampak popusti. */
    @Test
    void prevelikKlubZrebaNeUstavi() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SKUPINE_IZLOCILNI);
        nastaviKlube(dogodek, "Krim", "Krim", "Krim", "Krim", "Krim", "Krim", "Krim", "Krim",
                "Olimpija", "Olimpija", "Olimpija", "Olimpija",
                "Olimpija", "Olimpija", "Olimpija", "Olimpija");
        zrebStoritev.izvediZreb(dogodek.getId());

        List<List<Prijava>> skupine = poSkupinah(dogodek);
        assertEquals(16, skupine.stream().mapToInt(List::size).sum(), "vsi so razporejeni");
        for (List<Prijava> skupina : skupine) {
            assertEquals(List.of(0, 1, 2, 3), pasoviSkupine(skupina, 4),
                    "pasovna razdelitev obvelja tudi, ko klubov ni mogoce lociti");
        }
    }

    // ------------------------------------------------------------------
    // Cista izlocilna mreza
    // ------------------------------------------------------------------

    @Test
    void prviNosilecNaVrhDrugiNaDno() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        Prijava[] mreza = mrezaPrvegaKola(dogodek.getId());
        assertEquals(1, mreza[0].getStNosilca(), "1. nosilec na vrhu mreze");
        assertEquals(2, mreza[mreza.length - 1].getStNosilca(), "2. nosilec na dnu mreze");
    }

    @Test
    void pasoviDrzijoNosilceNarazen() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        Prijava[] mreza = mrezaPrvegaKola(dogodek.getId());
        preveriPas(mreza, nosilciDo(mreza, 2), 4, "prva dva nosilca se srecata sele v finalu");
        preveriPas(mreza, nosilciDo(mreza, 4), 3, "prvi stirje sele v polfinalu");
        preveriPas(mreza, nosilciDo(mreza, 8), 2, "prvih osem sele v cetrtfinalu");
    }

    /* Nosilci 3-4, 5-8 ... se ZREBAJO na svoja mesta - pri razlicnih semenih
       mora 3. nosilec pasti enkrat v zgornjo, enkrat v spodnjo polovico. */
    @Test
    void mestaZnotrajPasuSoIzzrebana() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.IZLOCILNI);
        List<Prijava> poJakosti = izborStoritev.vrstniRed(dogodek.getId());

        Set<Integer> mestaTretjega = new HashSet<>();
        for (int ponovitev = 0; ponovitev < 20; ponovitev++) {
            List<Prijava> nosilci = nosilciStoritev.vMrezo(poJakosti);
            assertSame(poJakosti.get(0), nosilci.get(0), "1. nosilec se ne zreba");
            assertSame(poJakosti.get(1), nosilci.get(1), "2. nosilec se ne zreba");
            mestaTretjega.add(nosilci.indexOf(poJakosti.get(2)));
        }
        assertEquals(Set.of(2, 3), mestaTretjega,
                "3. igralec po jakosti se zreba na nosilsko mesto 3 ali 4");
    }

    /* Prosta mesta pripadejo najvisjim nosilcem: pri 12 igralcih v mrezi 16
       so prosti prvi stirje. */
    @Test
    void prostaMestaDobijoNajvisjiNosilci() {
        Dogodek dogodek = pripraviJakostniDogodek(12, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> prosta = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getIzidTip() == IzidTekme.PROSTO).toList();
        assertEquals(4, prosta.size(), "16 - 12 = 4 prosta mesta");
        for (Tekma tekma : prosta) {
            assertTrue(tekma.getZmagovalec().getStNosilca() <= 4,
                    "prosto mesto dobi eden od prvih stirih nosilcev, ne "
                            + tekma.getZmagovalec().getStNosilca());
        }
    }

    @Test
    void igralcaIstegaKlubaSeVPrvemKoluNeSrecata() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.IZLOCILNI);
        /* Vsak klub ima po enega igralca v zgornji polovici jakosti in enega
           v pasu 9-16. Ta pas se zreba prav na mesta, ki v 1. kolu igrajo s
           prvimi osmimi, zato je klubska locitev dosegljiva samo z zrebom. */
        nastaviKlube(dogodek,
                "Krim", "Olimpija", "Maribor", "Celje",
                "Ptuj", "Kranj", "Velenje", "Koper",
                "Krim", "Olimpija", "Maribor", "Celje",
                "Ptuj", "Kranj", "Velenje", "Koper");
        zrebStoritev.izvediZreb(dogodek.getId());

        for (Tekma tekma : prvoKolo(dogodek.getId())) {
            assertFalse(istiKlub(tekma), "v 1. kolu igrata soklubista: " + opis(tekma));
        }
    }

    /* Par nima jakostnega mesta (rating para ne obstaja), zato se dvojice
       zrebajo povsem nakljucno - brez nosilcev. */
    @Test
    void dvojiceSeZrebajoBrezNosilcev() {
        Dogodek dogodek = pripraviDogodekDvojic(8);
        poveziVsePare(dogodek.getId());
        zrebStoritev.izvediZreb(dogodek.getId());

        for (Prijava prijava : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNull(prijava.getStNosilca(), "paru se jakostno mesto ne pripise");
        }
    }

    // ------------------------------------------------------------------
    // Izlocilni del po skupinah
    // ------------------------------------------------------------------

    /* 32 igralcev -> 8 skupin po 4; v skupinah zmaga mocnejsi, zato je
       zmagovalec skupine A 1. nosilec, zmagovalec B 2. in tako naprej. */
    @Test
    void izlocilniDelPoSkupinahSpostujeNosilskaMesta() {
        Dogodek dogodek = pripraviJakostniDogodek(32, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupineTakoDaZmagaMocnejsi(dogodek.getId());

        List<List<Prijava>> skupine = poSkupinah(dogodek);
        assertEquals(8, skupine.size());
        Prijava[] mreza = mrezaPrvegaKola(dogodek.getId());
        assertEquals(16, mreza.length, "16 kvalificiranih -> mreza 16, brez prostih mest");

        List<Prijava> zmagovalci = skupine.stream().map(s -> s.get(0)).toList();
        assertEquals(0, polozaj(mreza, zmagovalci.get(0)),
                "zmagovalec skupine A je 1. nosilec in gre na vrh mreze");
        assertEquals(15, polozaj(mreza, zmagovalci.get(1)),
                "zmagovalec skupine B je 2. nosilec in gre na dno mreze");

        preveriPas(mreza, zmagovalci.subList(0, 2), 4, "zmagovalca A in B sele v finalu");
        preveriPas(mreza, zmagovalci.subList(0, 4), 3, "prvi stirje nosilci sele v polfinalu");
        preveriPas(mreza, zmagovalci, 2, "vseh osem nosilcev sele v cetrtfinalu");
    }

    @Test
    void soigralcaIzSkupineSeSrecataSeleVFinalu() {
        Dogodek dogodek = pripraviJakostniDogodek(32, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupineTakoDaZmagaMocnejsi(dogodek.getId());

        Prijava[] mreza = mrezaPrvegaKola(dogodek.getId());
        for (List<Prijava> skupina : poSkupinah(dogodek)) {
            assertEquals(4, koloSrecanja(polozaj(mreza, skupina.get(0)),
                            polozaj(mreza, skupina.get(1))),
                    "zmagovalec in drugouvrsceni iste skupine se srecata sele v finalu");
        }
    }

    @Test
    void zmagovalecSkupineVPrvemKoluNeIgraZZmagovalcemSkupine() {
        Dogodek dogodek = pripraviJakostniDogodek(32, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupineTakoDaZmagaMocnejsi(dogodek.getId());

        Set<Long> zmagovalciSkupin = new HashSet<>(
                poSkupinah(dogodek).stream().map(s -> s.get(0).getId()).toList());
        for (Tekma tekma : prvoKolo(dogodek.getId())) {
            assertFalse(zmagovalciSkupin.contains(tekma.getPrijava1().getId())
                            && zmagovalciSkupin.contains(tekma.getPrijava2().getId()),
                    "dva zmagovalca skupin v 1. kolu: " + opis(tekma));
        }
    }

    // ------------------------------------------------------------------
    // Pomozne metode
    // ------------------------------------------------------------------

    /* Prijave po skupinah (A, B, C ...), znotraj skupine po jakosti. */
    private List<List<Prijava>> poSkupinah(Dogodek dogodek) {
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        return skupine.stream()
                .map(skupina -> prijave.stream()
                        .filter(p -> skupina.getId().equals(p.getIdSkupina()))
                        .sorted(Comparator.comparing(Prijava::getStNosilca))
                        .toList())
                .toList();
    }

    /* Jakostni pasovi (0-based) clanov skupine, urejeni narascajoce. */
    private static List<Integer> pasoviSkupine(List<Prijava> skupina, int stSkupin) {
        return skupina.stream().map(p -> (p.getStNosilca() - 1) / stSkupin).sorted().toList();
    }

    private List<Tekma> prvoKolo(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == 1)
                .sorted(Comparator.comparing(Tekma::getPozicija))
                .toList();
    }

    /* Polozaji igralcev v mrezi po vrsti od vrha navzdol; null = prosto mesto. */
    private Prijava[] mrezaPrvegaKola(Long idDogodka) {
        List<Tekma> prvoKolo = prvoKolo(idDogodka);
        Prijava[] mesta = new Prijava[prvoKolo.size() * 2];
        for (int i = 0; i < prvoKolo.size(); i++) {
            mesta[2 * i] = prvoKolo.get(i).getPrijava1();
            mesta[2 * i + 1] = prvoKolo.get(i).getPrijava2();
        }
        return mesta;
    }

    private static int polozaj(Prijava[] mreza, Prijava prijava) {
        for (int i = 0; i < mreza.length; i++) {
            if (mreza[i] != null && mreza[i].getId().equals(prijava.getId())) {
                return i;
            }
        }
        throw new AssertionError("prijave " + prijava.getId() + " ni v mrezi");
    }

    /* V katerem kolu bi se srecala igralca na danih polozajih mreze
       (1 = prvo kolo; pri mrezi 16 je 4 finale). */
    private static int koloSrecanja(int polozajA, int polozajB) {
        int kolo = 0;
        while (polozajA != polozajB) {
            polozajA /= 2;
            polozajB /= 2;
            kolo++;
        }
        return kolo;
    }

    /* Nobena dva iz danega pasu se ne smeta srecati pred danim kolom. */
    private static void preveriPas(Prijava[] mreza, List<Prijava> pas, int najprejKolo, String zakaj) {
        for (int i = 0; i < pas.size(); i++) {
            for (int j = i + 1; j < pas.size(); j++) {
                int kolo = koloSrecanja(polozaj(mreza, pas.get(i)), polozaj(mreza, pas.get(j)));
                assertTrue(kolo >= najprejKolo, zakaj + " - a se srecata v " + kolo + ". kolu");
            }
        }
    }

    private static List<Prijava> nosilciDo(Prijava[] mreza, int koliko) {
        List<Prijava> nosilci = new ArrayList<>();
        for (Prijava prijava : mreza) {
            if (prijava != null && prijava.getStNosilca() <= koliko) {
                nosilci.add(prijava);
            }
        }
        return nosilci;
    }

    /* Odigra skupinski del tako, da vsako tekmo dobi mocnejsi igralec -
       zmagovalec skupine je torej njen nosilec, drugi pa njen drugi pas. */
    private void odigrajSkupineTakoDaZmagaMocnejsi(Long idDogodka) {
        for (Tekma tekma : tekmeDogodka(idDogodka)) {
            if (tekma.getFaza() != FazaTekme.SKUPINA) {
                continue;
            }
            boolean prviMocnejsi = tekma.getPrijava1().getStNosilca()
                    < tekma.getPrijava2().getStNosilca();
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(
                    null, prviMocnejsi ? 3 : 0, prviMocnejsi ? 0 : 3, null, null));
        }
    }

    private static boolean istiKlub(Tekma tekma) {
        return tekma.getPrijava1() != null && tekma.getPrijava2() != null
                && tekma.getPrijava1().getKlubObPrijavi() != null
                && tekma.getPrijava1().getKlubObPrijavi().getId()
                        .equals(tekma.getPrijava2().getKlubObPrijavi().getId());
    }

    private static String opis(Tekma tekma) {
        return tekma.getPrijava1().prikazanoIme() + " - " + tekma.getPrijava2().prikazanoIme();
    }
}
