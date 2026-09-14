/* Testi generatorja razporeda (krozni sistem) - cista logika brez baze. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RazporedStoritevTest {

    private final RazporedStoritev razpored = new RazporedStoritev();

    @Test
    void sodoSteviloEnokroznoVsakZVsakimEnkrat() {
        List<RazporedStoritev.Par> pari = razpored.razpored(4, false);
        assertEquals(6, pari.size(), "4 ekipe, enokrozno: 6 srecanj");
        assertEquals(3, steviloKol(pari), "3 kola");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 4, false);
    }

    @Test
    void sodoSteviloDvokroznoObaObracuna() {
        List<RazporedStoritev.Par> pari = razpored.razpored(4, true);
        assertEquals(12, pari.size(), "4 ekipe, dvokrozno: 12 srecanj");
        assertEquals(6, steviloKol(pari), "6 kol");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 4, true);
    }

    @Test
    void lihoSteviloVsakoKoloEnaEkipaPociva() {
        List<RazporedStoritev.Par> pari = razpored.razpored(5, false);
        assertEquals(10, pari.size(), "5 ekip, enokrozno: 10 srecanj");
        assertEquals(5, steviloKol(pari), "5 kol");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 5, false);
        // v vsakem kolu igrata najvec 2 para (ena ekipa pociva)
        for (int kolo = 1; kolo <= 5; kolo++) {
            final int k = kolo;
            long v = pari.stream().filter(p -> p.kolo() == k).count();
            assertEquals(2, v, "v kolu " + kolo + " naj bosta 2 srecanji");
        }
    }

    @Test
    void triEkipeDvokrozno() {
        List<RazporedStoritev.Par> pari = razpored.razpored(3, true);
        assertEquals(6, pari.size(), "3 ekipe, dvokrozno: 6 srecanj");
        preveriVsakParEnkrat(pari, 3, true);
    }

    @Test
    void premaloEkipPrazenRazpored() {
        assertTrue(razpored.razpored(1, true).isEmpty());
        assertTrue(razpored.razpored(0, false).isEmpty());
        assertTrue(razpored.razpored(1, true, true).isEmpty());
    }

    // ---------- enakomerna razvrstitev (razpored po parih) ----------

    /* Ne glede na velikost lige mora tudi razpored po parih ostati regularen
       krozni sistem: vsak z vsakim natanko enkrat in nihce dvakrat v istem
       kolu. Sodo in liho stevilo parov sta razlicni veji, zato so v seznamu
       obe (8 in 12 ekip = sodo parov, 6, 10 in 14 = liho) in liho stevilo ekip
       (navidezna ekipa). */
    @Test
    void poParihOstaneRegularenKrozniSistem() {
        for (int stEkip : new int[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16 }) {
            List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
            preveriBrezPonovitevVKolu(pari);
            preveriVsakParEnkrat(pari, stEkip, false);
        }
    }

    @Test
    void poParihDvokroznoObaObracuna() {
        List<RazporedStoritev.Par> pari = razpored.razpored(10, true, true);
        assertEquals(90, pari.size(), "10 ekip, dvokrozno: 90 srecanj");
        assertEquals(20, steviloKol(pari), "10 kol na cikel");
        preveriBrezPonovitevVKolu(pari);
        preveriVsakParEnkrat(pari, 10, true);
    }

    /* Pri sodem stevilu parov (8 ekip = 4 pari) noben par ne ostane brez
       nasprotnega para, zato se liga konca s kolom, v katerem vsaka ekipa
       igra samo s svojim parom - in kol je toliko kot pri navadnem sistemu. */
    @Test
    void poParihSodoParovZadnjeKoloSoDvobojiParov() {
        List<RazporedStoritev.Par> pari = razpored.razpored(8, false, true);
        assertEquals(7, steviloKol(pari), "8 ekip: 7 kol kot pri navadnem sistemu");

        List<RazporedStoritev.Par> zadnje = vKolu(pari, 7);
        assertEquals(4, zadnje.size(), "v zadnjem kolu igrajo vsi stirje pari");
        for (RazporedStoritev.Par p : zadnje) {
            assertEquals(4, Math.abs(p.domaci() - p.gost()),
                    "zadnje kolo je dvoboj para: i proti i+4");
        }
    }

    /* Bistvo enakomerne razvrstitve: par igra v vsakem krogu (dve koli) proti
       ISTEMU nasprotnemu paru, torej ena njegova ekipa proti zgornji in druga
       proti spodnji ekipi nasprotnika. Preverjeno na primeru iz zahteve -
       10 ekip, pari A-F, B-G, C-H, D-I, E-J. */
    @Test
    void poParihParVKroguIgraIsteNasprotnike() {
        int stEkip = 10;
        int parov = stEkip / 2;
        List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
        assertEquals(stEkip, steviloKol(pari),
                "pri lihem stevilu parov ima liga eno kolo vec (vsaka ekipa enkrat pociva)");

        for (int krog = 1; krog <= parov; krog++) {
            List<RazporedStoritev.Par> vKrogu = new ArrayList<>(vKolu(pari, 2 * krog - 1));
            vKrogu.addAll(vKolu(pari, 2 * krog));
            for (int p = 0; p < parov; p++) {
                Set<Integer> zgornja = nasprotniki(vKrogu, p);
                Set<Integer> spodnja = nasprotniki(vKrogu, p + parov);
                if (zgornja.contains(p + parov)) {
                    // krog, v katerem par odigra svoj notranji dvoboj
                    assertEquals(Set.of(p + parov), zgornja);
                    assertEquals(Set.of(p), spodnja);
                    continue;
                }
                assertEquals(zgornja, spodnja,
                        "obe ekipi para imata v krogu " + krog + " iste nasprotnike");
                assertEquals(2, zgornja.size(), "par v krogu igra z obema ekipama nasprotnega para");
                assertEquals(1, zgornja.stream().filter(n -> n < parov).count(),
                        "en nasprotnik je iz zgornje polovice");
                assertEquals(1, zgornja.stream().filter(n -> n >= parov).count(),
                        "en nasprotnik je iz spodnje polovice");
            }
        }
    }

    /* Gostitelj je posamezna ekipa in ne cel par: sicer bi ena ekipa gostila
       vse stiri dvoboje kroga in bi ji ob koncu sezone pripadlo bistveno vec
       (ali manj) domacih srecanj kot ostalim. Pri sodem stevilu ekip mora biti
       delitev natancna - polovica navzdol oz. navzgor. */
    @Test
    void poParihDomacaSrecanjaSoRazdeljena() {
        for (int stEkip : new int[] { 4, 6, 8, 10, 12, 14 }) {
            List<RazporedStoritev.Par> pari = razpored.razpored(stEkip, false, true);
            int srecanj = stEkip - 1;
            for (int ekipa = 0; ekipa < stEkip; ekipa++) {
                final int e = ekipa;
                long doma = pari.stream().filter(p -> p.domaci() == e).count();
                assertTrue(doma == srecanj / 2 || doma == (srecanj + 1) / 2,
                        "ekipa " + e + " pri " + stEkip + " ekipah igra " + doma
                                + " srecanj doma, pricakovano " + (srecanj / 2) + " ali "
                                + ((srecanj + 1) / 2));
            }
        }
    }

    // ---------- ure srecanj (kolo je vecer z vec srecanji) ----------

    private static final LocalTime OB_1830 = LocalTime.of(18, 30);
    private static final LocalTime OB_1945 = LocalTime.of(19, 45);

    /* Brez ur je razpored z mesti isti kot navadni: isti pari v istih kolih,
       mesto je le zaporedje v kolu. */
    @Test
    void brezUrRazporedOstaneNavaden() {
        List<RazporedStoritev.Par> navaden = razpored.razpored(6, true, false);
        List<RazporedStoritev.ParNaMestu> zMesti = razpored.razpored(6, true, false, null);

        assertEquals(navaden, zMesti.stream().map(RazporedStoritevTest::brezMesta).toList());
        assertEquals(List.of(0, 1, 2), zMesti.stream().filter(p -> p.kolo() == 1)
                .map(RazporedStoritev.ParNaMestu::mesto).toList());
    }

    /* Razdelitev po vecerih ne sme izgubiti ali podvojiti nobenega srecanja in
       ne sme dati kolu vec srecanj, kot je ur - ne glede na stevilo ekip,
       stevilo ur, krog in zreb po parih. */
    @Test
    void poUrahOstaneVsakZVsakimInKoloNimaVecSrecanjKotUr() {
        for (int stEkip = 2; stEkip <= 10; stEkip++) {
            for (int urVKolu = 1; urVKolu <= 5; urVKolu++) {
                final int stUr = urVKolu;
                List<LocalTime> ure = new ArrayList<>();
                for (int i = 0; i < stUr; i++) {
                    ure.add(LocalTime.of(17 + i, 0));
                }
                for (boolean dvokrozno : new boolean[] { false, true }) {
                    for (boolean poParih : new boolean[] { false, true }) {
                        List<RazporedStoritev.ParNaMestu> pari =
                                razpored.razpored(stEkip, dvokrozno, poParih, ure);
                        String primer = stEkip + " ekip, " + stUr + " ur";
                        preveriVsakParEnkrat(pari.stream().map(RazporedStoritevTest::brezMesta).toList(),
                                stEkip, dvokrozno);
                        int kol = pari.stream().mapToInt(RazporedStoritev.ParNaMestu::kolo).max().orElse(0);
                        for (int kolo = 1; kolo <= kol; kolo++) {
                            final int k = kolo;
                            List<Integer> mesta = pari.stream().filter(p -> p.kolo() == k)
                                    .map(RazporedStoritev.ParNaMestu::mesto).toList();
                            assertFalse(mesta.isEmpty(), primer + ": kolo " + kolo + " je prazno");
                            assertEquals(mesta.size(), new HashSet<>(mesta).size(),
                                    primer + ": dve srecanji na istem mestu");
                            assertTrue(mesta.stream().allMatch(m -> m >= 0 && m < stUr),
                                    primer + ": mesto brez ure");
                        }
                    }
                }
            }
        }
    }

    /* Primer iz zahteve: tri ekipe, dve srecanji na vecer (18.30 in 19.45).
       Vsak vecer je poln, ena ekipa igra dvakrat, a ne ob isti uri, in
       nobena ne igra dvakrat dva vecera zapored na racun druge. */
    @Test
    void triEkipeDveSrecanjiNaVecer() {
        List<RazporedStoritev.ParNaMestu> pari = razpored.razpored(3, true, false, List.of(OB_1830, OB_1945));

        assertEquals(3, pari.stream().mapToInt(RazporedStoritev.ParNaMestu::kolo).max().orElse(0),
                "6 srecanj po 2 na vecer = 3 kola");
        for (int kolo = 1; kolo <= 3; kolo++) {
            final int k = kolo;
            List<RazporedStoritev.ParNaMestu> vecer = pari.stream().filter(p -> p.kolo() == k).toList();
            assertEquals(2, vecer.size(), "vecer " + kolo + " je poln");
            assertEquals(List.of(0, 1), vecer.stream().map(RazporedStoritev.ParNaMestu::mesto).toList());
        }
        // vsaka ekipa igra dvakrat na natanko enem vecerov - obremenitev je enaka
        for (int ekipa = 0; ekipa < 3; ekipa++) {
            final int e = ekipa;
            long dvojnihVecerov = java.util.stream.IntStream.rangeClosed(1, 3)
                    .filter(k -> pari.stream().filter(p -> p.kolo() == k)
                            .filter(p -> p.domaci() == e || p.gost() == e).count() == 2)
                    .count();
            assertEquals(1, dvojnihVecerov, "ekipa " + ekipa + " igra dvakrat na enem vecerov");
        }
    }

    /* Ekipa sme v kolu igrati veckrat, a samo kadar drugace ne gre: ce je ur
       najvec za polovico ekip, gre razpored brez dvojnih nastopov (pri sodem
       stevilu ekip, kjer krozni sistem to omogoca). */
    @Test
    void poUrahEkipaIgraVeckratSamoKoDrugaceNeGre() {
        for (int stEkip : new int[] { 4, 6, 8, 10 }) {
            for (int stUr = 1; stUr <= stEkip / 2; stUr++) {
                List<LocalTime> ure = new ArrayList<>();
                for (int i = 0; i < stUr; i++) {
                    ure.add(LocalTime.of(17 + i, 0));
                }
                List<RazporedStoritev.ParNaMestu> pari = razpored.razpored(stEkip, true, false, ure);
                preveriBrezPonovitevVKolu(pari.stream().map(RazporedStoritevTest::brezMesta).toList());
            }
        }
    }

    /* Dve mizi hkrati: ekipa ne sme igrati dveh srecanj ob isti uri, tudi ce
       bi jo to potegnilo v isto kolo. */
    @Test
    void poUrahEkipaNeIgraDvakratObIstiUri() {
        List<LocalTime> dveMizi = List.of(LocalTime.of(18, 0), LocalTime.of(18, 0),
                LocalTime.of(19, 30), LocalTime.of(19, 30));
        for (int stEkip = 3; stEkip <= 9; stEkip++) {
            List<RazporedStoritev.ParNaMestu> pari = razpored.razpored(stEkip, true, false, dveMizi);
            Set<String> zasedeno = new HashSet<>();
            for (RazporedStoritev.ParNaMestu p : pari) {
                LocalTime ura = dveMizi.get(p.mesto());
                assertTrue(zasedeno.add(p.kolo() + "|" + ura + "|" + p.domaci()),
                        stEkip + " ekip: ekipa " + p.domaci() + " dvakrat ob " + ura + " v kolu " + p.kolo());
                assertTrue(zasedeno.add(p.kolo() + "|" + ura + "|" + p.gost()),
                        stEkip + " ekip: ekipa " + p.gost() + " dvakrat ob " + ura + " v kolu " + p.kolo());
            }
            preveriVsakParEnkrat(pari.stream().map(RazporedStoritevTest::brezMesta).toList(), stEkip, true);
        }
    }

    /* Povratni krog pride na vrsto sele, ko je razdeljen prvi: nobeno srecanje
       drugega kroga ne sme stati v kolu pred srecanjem prvega. */
    @Test
    void poUrahPrviKrogPredPovratnim() {
        for (int stEkip = 3; stEkip <= 8; stEkip++) {
            Set<String> prviKrog = new HashSet<>();
            for (RazporedStoritev.Par p : razpored.razpored(stEkip, false, false)) {
                prviKrog.add(p.domaci() + ">" + p.gost());
            }
            List<RazporedStoritev.ParNaMestu> pari = razpored.razpored(stEkip, true, false,
                    List.of(OB_1830, OB_1945, LocalTime.of(21, 0)));
            int zadnjeKoloPrvega = pari.stream().filter(p -> prviKrog.contains(p.domaci() + ">" + p.gost()))
                    .mapToInt(RazporedStoritev.ParNaMestu::kolo).max().orElse(0);
            int prvoKoloPovratnega = pari.stream().filter(p -> !prviKrog.contains(p.domaci() + ">" + p.gost()))
                    .mapToInt(RazporedStoritev.ParNaMestu::kolo).min().orElse(0);
            assertTrue(zadnjeKoloPrvega <= prvoKoloPovratnega,
                    stEkip + " ekip: povratno srecanje v " + prvoKoloPovratnega
                            + ". kolu, prvi krog pa traja do " + zadnjeKoloPrvega + ".");
        }
    }

    private static RazporedStoritev.Par brezMesta(RazporedStoritev.ParNaMestu p) {
        return new RazporedStoritev.Par(p.kolo(), p.domaci(), p.gost());
    }

    private List<RazporedStoritev.Par> vKolu(List<RazporedStoritev.Par> pari, int kolo) {
        return pari.stream().filter(p -> p.kolo() == kolo).toList();
    }

    private Set<Integer> nasprotniki(List<RazporedStoritev.Par> pari, int ekipa) {
        Set<Integer> nasprotniki = new HashSet<>();
        for (RazporedStoritev.Par p : pari) {
            if (p.domaci() == ekipa) nasprotniki.add(p.gost());
            if (p.gost() == ekipa) nasprotniki.add(p.domaci());
        }
        return nasprotniki;
    }

    // ---------- pomozne preverbe ----------

    private int steviloKol(List<RazporedStoritev.Par> pari) {
        return pari.stream().mapToInt(RazporedStoritev.Par::kolo).max().orElse(0);
    }

    private void preveriBrezPonovitevVKolu(List<RazporedStoritev.Par> pari) {
        int maxKolo = steviloKol(pari);
        for (int kolo = 1; kolo <= maxKolo; kolo++) {
            Set<Integer> nastopajoce = new HashSet<>();
            final int k = kolo;
            pari.stream().filter(p -> p.kolo() == k).forEach(p -> {
                assertNotEquals(p.domaci(), p.gost(), "ekipa ne igra sama s sabo");
                assertTrue(nastopajoce.add(p.domaci()), "ekipa " + p.domaci() + " dvakrat v kolu " + k);
                assertTrue(nastopajoce.add(p.gost()), "ekipa " + p.gost() + " dvakrat v kolu " + k);
            });
        }
    }

    /* Enokrozno: vsak neurejeni par natanko enkrat. Dvokrozno: vsak urejeni
       par (doma-gost) natanko enkrat. */
    private void preveriVsakParEnkrat(List<RazporedStoritev.Par> pari, int stEkip, boolean dvokrozno) {
        Set<String> videni = new HashSet<>();
        for (RazporedStoritev.Par p : pari) {
            String kljuc = dvokrozno
                    ? p.domaci() + ">" + p.gost()
                    : Math.min(p.domaci(), p.gost()) + "-" + Math.max(p.domaci(), p.gost());
            assertTrue(videni.add(kljuc), "par " + kljuc + " se ponovi");
        }
        int pricakovano = dvokrozno ? stEkip * (stEkip - 1) : stEkip * (stEkip - 1) / 2;
        assertEquals(pricakovano, videni.size());
        for (RazporedStoritev.Par p : pari) {
            assertTrue(p.domaci() >= 0 && p.domaci() < stEkip);
            assertTrue(p.gost() >= 0 && p.gost() < stEkip);
        }
        assertFalse(videni.isEmpty());
    }
}
