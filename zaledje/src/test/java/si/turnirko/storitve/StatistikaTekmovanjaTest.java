/* Zavihek "Zanimivosti" turnirja in lige.

   Testi varujejo pravila, ki jih je najlazje nehote razbiti:
     - dvojice ne vstopajo v vrstice o posamezniku (stejejo le v "V stevilkah"
       in v svojo vrstico),
     - vrstica, ki je podatki ne napolnijo, je odsotna in ne nicelna (liga brez
       vpisanih tock po nizih nima ne obrata ne najdaljsega niza),
     - pod pragom odigranih tekem zavihka sploh ni,
     - "srecanje na noz" je res tisto, ki ga je odlocila zadnja tekma, in ne
       vsako tesno srecanje.

   Turnirski del je krozni dogodek sestih igralcev (15 tekem), kjer izidi
   sledijo jakosti - z eno samo izjemo, ki je hkrati presenecenje, obrat,
   najdaljsi niz in najdaljsa tekma. Tako je vsaka trditev preverljiva na
   roke. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.StatistikaTekmovanjaDto;
import si.turnirko.dto.StatistikaTekmovanjaDto.Delavec;
import si.turnirko.dto.StatistikaTekmovanjaDto.Nosilec;
import si.turnirko.dto.StatistikaTekmovanjaDto.Vzpon;
import si.turnirko.dto.StatistikaTekmovanjaDto.Zid;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.KlubRepozitorij;

class StatistikaTekmovanjaTest extends IntegracijskiTest {

    @Autowired private StatistikaTekmovanjaStoritev statistikaTekmovanjaStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;

    /* Tocke edine tekme, ki jih ima - z vidika ZMAGOVALCA (najsibkejsega).
       Prva dva niza izgubljena, cetrti je najdaljsi na turnirju. */
    private static final List<int[]> TOCKE_PRESENECENJA = List.of(
            new int[] {8, 11}, new int[] {9, 11}, new int[] {11, 9},
            new int[] {15, 13}, new int[] {11, 6});

    /* Skupaj 104 tocke; najdaljsi niz jih ima 28. */
    private static final int TOCK_PRESENECENJA = 104;

    /* ================= Turnir ================= */

    @Test
    void turnirPovzameSvojeZgodbe() {
        Dogodek dogodek = kroznoSestih();
        Long idTurnirja = dogodek.getTurnir().getId();
        List<Igralec> poJakosti = poJakosti(dogodek);
        odigraj(dogodek, poJakosti);
        Igralec najmocnejsi = poJakosti.get(0);
        Igralec najsibkejsi = poJakosti.get(5);

        /* Odigrane tekme turnirja ne zakljucijo - to je organizatorjevo
           dejanje. Zavihek je zato viden ze med turnirjem in to pove. */
        assertTrue(statistikaTekmovanjaStoritev.zaTurnir(idTurnirja).vTeku(),
                "dokler turnir ni zakljucen, zavihek to pove");
        turnirjiStoritev.zakljuci(idTurnirja);

        StatistikaTekmovanjaDto s = statistikaTekmovanjaStoritev.zaTurnir(idTurnirja);

        assertTrue(s.dovoljPodatkov(), "15 tekem je cez prag");
        assertFalse(s.vTeku());

        // --- V stevilkah ---
        assertEquals(6, s.stevilke().igralcev());
        assertEquals(3, s.stevilke().klubov());
        assertEquals(15, s.stevilke().tekem());
        assertEquals(47, s.stevilke().nizov(), "14 tekem po 3 nize + ena po 5");
        assertEquals(TOCK_PRESENECENJA, s.stevilke().tock(),
                "tocke ima vpisane samo ena tekma; ostale v vsoto ne prispevajo");
        assertEquals(1, s.stevilke().dogodkov());
        assertNull(s.stevilke().ekip(), "turnir ekip nima");
        assertEquals(0, s.stevilke().tekemDvojic());

        // --- Presenecenje: najsibkejsi je premagal najmocnejsega ---
        assertNotNull(s.presenecenje());
        assertEquals(najsibkejsi.getId(), s.presenecenje().zmagovalec().idIgralec());
        assertEquals(najmocnejsi.getId(), s.presenecenje().porazenec().idIgralec());
        assertTrue(s.presenecenje().razlika() > 0, "poraženec je bil mocnejsi");
        assertEquals(s.presenecenje().ratingPorazenca() - s.presenecenje().ratingZmagovalca(),
                s.presenecenje().razlika());
        assertEquals("3 : 2", s.presenecenje().izid());

        // --- Obrat: edina tekma, dobljena po zaostanku 0 : 2 ---
        assertNotNull(s.obrat());
        assertEquals(1, s.obrat().koliko());
        assertEquals(najsibkejsi.getId(), s.obrat().zmagovalec().idIgralec());
        assertEquals("8:11, 9:11, 11:9, 15:13, 11:6", s.obrat().nizi());

        // --- Najdaljsi niz in najdaljsa tekma ---
        assertNotNull(s.najdaljsiNiz());
        assertEquals(15, s.najdaljsiNiz().tockePrvi());
        assertEquals(13, s.najdaljsiNiz().tockeDrugi());
        assertEquals(4, s.najdaljsiNiz().zaporedna());
        assertEquals(najsibkejsi.getId(), s.najdaljsiNiz().prvi().idIgralec(),
                "niz je dobil zmagovalec tekme");
        assertEquals(TOCK_PRESENECENJA, s.najdaljsaTekma().tock());
        assertEquals(5, s.najdaljsaTekma().nizov());

        // --- Zid: delez dobljenih nizov, ne njihova razlika ---
        assertEquals(3, s.zid().size());
        assertEquals(najmocnejsi.getId(), s.zid().get(0).oseba().idIgralec(),
                "14 : 3 je boljsi delez kot 12 : 3");
        assertEquals(14, s.zid().get(0).dobljeni());
        assertEquals(3, s.zid().get(0).prejeti());
        assertEquals(5, s.zid().get(0).odigrane());
        for (Zid z : s.zid()) {
            assertTrue(z.odigrane() >= 3, "pod pragom treh tekem vrstice ni");
        }

        // --- Delavci: vsi so odigrali pet tekem, loci jih stevilo zmag ---
        assertEquals(3, s.delavci().size());
        for (Delavec d : s.delavci()) {
            assertEquals(5, d.odigrane());
            assertEquals(0, d.dvojic(), "turnir dvojic nima");
        }
        assertEquals(najmocnejsi.getId(), s.delavci().get(0).oseba().idIgralec());
        assertEquals(4, s.delavci().get(0).zmage());

        // --- Klubi: dva igralca na klub ---
        assertEquals(3, s.klubi().size());
        assertEquals("Klub A", s.klubi().get(0).ime());
        assertEquals(8, s.klubi().get(0).zmage(), "4 + 4 zmagi najmocnejsih dveh");
        assertEquals(10, s.klubi().get(0).odigrane());
        assertEquals(2, s.klubi().get(0).igralcev());

        // --- Vzpon ELO: samo pridobitve, padavcev zavihek ne razglasa ---
        assertFalse(s.vzponi().isEmpty());
        for (Vzpon v : s.vzponi()) {
            assertTrue(v.pridobil() > 0, "v vrstici so samo pridobitve");
            assertEquals(trenutniRating(v.oseba().idIgralec()), v.koncni(),
                    "koncni rating vrstice je rating po zadnji tekmi tekmovanja");
        }
        for (int i = 1; i < s.vzponi().size(); i++) {
            assertTrue(s.vzponi().get(i - 1).pridobil() >= s.vzponi().get(i).pridobil());
        }

        // --- Prvi naslov: zmagovalec kroznega dela nima drugega naslova ---
        assertEquals(1, s.prviNaslovi().size());
        assertEquals(najmocnejsi.getId(), s.prviNaslovi().get(0).oseba().idIgralec());
        assertEquals("Clani posamicno", s.prviNaslovi().get(0).dogodek());

        // --- Ligaske postavke pri turnirju odpadejo ---
        assertNull(s.naNoz());
        assertTrue(s.gostje().isEmpty());
        assertTrue(s.nosilci().isEmpty());
        assertNull(s.dvojica(), "dogodka dvojic ni bilo");
    }

    /* Zmagovalec, ki naslov ze ima, ni "prvi naslov" - merilo je, da so vsi
       njegovi naslovi s TEGA turnirja. Drugi turnir ima ISTE igralce in isti
       jakostni vrstni red, zato zmaga isti clovek. */
    @Test
    void drugiNaslovNiPrvi() {
        Dogodek prvi = kroznoSestih();
        List<Igralec> poJakosti = poJakosti(prvi);
        odigraj(prvi, poJakosti);
        Igralec zmagovalec = poJakosti.get(0);

        Dogodek drugi = kroznoZIgralci(poJakosti);
        odigraj(drugi, poJakosti);

        StatistikaTekmovanjaDto s =
                statistikaTekmovanjaStoritev.zaTurnir(drugi.getTurnir().getId());

        assertTrue(s.prviNaslovi().stream()
                        .noneMatch(n -> n.oseba().idIgralec().equals(zmagovalec.getId())),
                "igralec je isti clovek in naslov ima ze s prvega turnirja");
        assertEquals(1, statistikaTekmovanjaStoritev.zaTurnir(prvi.getTurnir().getId())
                .prviNaslovi().size(), "na prvem turnirju je bil naslov prvi");
    }

    /* Pod pragom odigranih tekem zavihka ni - in ni prazen, ampak ga ni. */
    @Test
    void podPragomZavihkaNi() {
        Dogodek dogodek = pripraviDogodek(4);
        zrebStoritev.izvediZreb(dogodek.getId());
        for (Tekma t : tekmeDogodka(dogodek.getId())) {
            if (t.getPrijava1() != null && t.getPrijava2() != null) {
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(null, 3, 0, null, null));
            }
        }

        StatistikaTekmovanjaDto s =
                statistikaTekmovanjaStoritev.zaTurnir(dogodek.getTurnir().getId());

        assertFalse(s.dovoljPodatkov());
        assertNull(s.stevilke());
        assertTrue(s.zid().isEmpty());
        assertTrue(s.delavci().isEmpty());
    }

    /* Turnir, ki v ELO ne steje, dnevnika nima - vrstici o ratingu zato
       odpadeta, zavihek pa ostane. */
    @Test
    void brezEloOdpadetaVzponInPresenecenje() {
        Dogodek dogodek = kroznoSestih();
        dogodek.getTurnir().setStejeVElo(false);
        turnirRepozitorij.save(dogodek.getTurnir());
        odigraj(dogodek, poJakosti(dogodek));

        StatistikaTekmovanjaDto s =
                statistikaTekmovanjaStoritev.zaTurnir(dogodek.getTurnir().getId());

        assertTrue(s.dovoljPodatkov());
        assertFalse(s.stejeVElo());
        assertTrue(s.vzponi().isEmpty());
        assertNull(s.presenecenje());
        assertNotNull(s.zid(), "ostale postavke ratinga ne potrebujejo");
        assertEquals(15, s.stevilke().tekem());
    }

    /* ================= Liga ================= */

    @Test
    void ligaPovzameSvojeZgodbe() {
        Long idLige = ligaStirihEkip();
        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(idLige);
        assertEquals(6, srecanja.size(), "4 ekipe enokrozno -> 6 srecanj");

        /* Prvo srecanje odloci sele zadnja tekma (po stirih je 2 : 2), ostala
           so odlocena prej ali pa jih dobi gost. */
        odigrajSrecanje(srecanja.get(0).id(), true, false, true, false, true);
        odigrajSrecanje(srecanja.get(1).id(), false, false, false, true, true);
        odigrajSrecanje(srecanja.get(2).id(), false, false, false, true, true);
        for (int i = 3; i < srecanja.size(); i++) {
            odigrajSrecanje(srecanja.get(i).id(), true, true, true, false, false);
        }

        StatistikaTekmovanjaDto s = statistikaTekmovanjaStoritev.zaLigo(idLige);

        assertTrue(s.dovoljPodatkov());

        // --- V stevilkah: dvojice so stete posebej, ne v posamicne ---
        assertEquals(30, s.stevilke().tekem(), "6 srecanj po 5 tekem");
        assertEquals(6, s.stevilke().tekemDvojic(), "eno dvojice na srecanje");
        assertEquals(8, s.stevilke().igralcev());
        assertEquals(4, s.stevilke().ekip());
        assertNull(s.stevilke().dogodkov(), "liga dogodkov nima");
        assertNull(s.stevilke().tock(), "tock po nizih nihce ni vpisal");

        // --- Brez tock ni ne obrata ne najdaljsega niza; vrstic preprosto ni ---
        assertNull(s.obrat());
        assertNull(s.najdaljsiNiz());
        assertNull(s.najdaljsaTekma());

        // --- Dvojice ne vstopajo v posamicne vrstice ---
        for (Zid z : s.zid()) {
            assertEquals(6, z.odigrane(), "6 posamicnih na igralca, dvojice ne stejejo");
        }
        for (Delavec d : s.delavci()) {
            assertEquals(6, d.odigrane());
            assertEquals(3, d.dvojic(), "tri dvojice so stete posebej");
        }
        assertNotNull(s.dvojica(), "najuspesnejsi par ima svojo vrstico");
        assertTrue(s.dvojica().zmage() >= 1);

        // --- Srecanje na noz: samo prvo ---
        assertNotNull(s.naNoz());
        assertEquals(1, s.naNoz().koliko(), "ostala srecanja je odlocila ze predzadnja tekma");
        assertEquals(3, s.naNoz().dobljeneDomaci());
        assertEquals(2, s.naNoz().dobljeneGost());
        assertEquals(srecanja.get(0).id(), s.naNoz().idSrecanje());
        assertNotNull(s.naNoz().odlocil(), "odlocila je posamicna tekma, torej en clovek");

        // --- Gostje: dve zmagi v gosteh skupaj ---
        assertEquals(2, s.gostje().stream().mapToInt(g -> g.zmage()).sum());
        s.gostje().forEach(g -> assertTrue(g.zmage() > 0, "ekipe brez zmage v gosteh v vrstici ni"));

        // --- Nosilci: ena vrstica na ekipo ---
        assertEquals(4, s.nosilci().size());
        for (Nosilec n : s.nosilci()) {
            assertEquals(6, n.zmage() + n.porazi(), "vsak igralec odigra 6 posamicnih");
        }

        // --- Turnirske postavke pri ligi odpadejo ---
        assertTrue(s.prviNaslovi().isEmpty());
    }

    /* ================= Priprava ================= */

    /* Krozni dogodek sestih igralcev z znanim jakostnim vrstnim redom in tremi
       klubi po dva igralca. */
    private Dogodek kroznoSestih() {
        Dogodek dogodek = pripraviJakostniDogodek(6, SistemTekmovanja.KROZNI);
        /* Datum je pogoj, da je "prvi naslov" sploh vprasanje o casu. */
        dogodek.getTurnir().setDatumZacetka(LocalDate.of(2026, 3, 1));
        turnirRepozitorij.save(dogodek.getTurnir());
        nastaviKlube(dogodek, "Klub A", "Klub A", "Klub B", "Klub B", "Klub C", "Klub C");
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodek;
    }

    /* Jakostni vrstni red dogodka kot seznam igralcev (0 = najmocnejsi).
       Bere ga iz vrstnega reda prijav, ker pripraviJakostniDogodek ratinge
       razdeli prav po njem - in ne iz trenutnih ratingov, ki se med turnirjem
       premaknejo. */
    private List<Igralec> poJakosti(Dogodek dogodek) {
        return prijavePoVrsti(dogodek.getId()).stream().map(Prijava::getIgralec).toList();
    }

    /* Krozni dogodek na NOVEM turnirju z ze obstojecimi igralci - za preverbo,
       da drugi naslov istega cloveka ni prvi. */
    private Dogodek kroznoZIgralci(List<Igralec> igralci) {
        Turnir turnir = new Turnir();
        turnir.setIme("Drugi turnir");
        turnir.setDatumZacetka(LocalDate.of(2026, 4, 1));   // mesec za prvim
        turnirRepozitorij.save(turnir);

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Clani posamicno");
        dogodek.setSpolKategorija(SpolKategorija.MOSKI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(SistemTekmovanja.KROZNI);
        dogodekRepozitorij.save(dogodek);

        for (Igralec i : igralci) {
            prijavaRepozitorij.save(new Prijava(dogodek, i));
        }
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodek;
    }

    /* Odigra vseh 15 tekem: mocnejsi (nizji indeks v poJakosti) zmaga 3 : 0,
       edina izjema je dvoboj najmocnejsega z najsibkejsim - tam najsibkejsi
       zmaga 3 : 2 po zaostanku 0 : 2. */
    private void odigraj(Dogodek dogodek, List<Igralec> poJakosti) {
        Map<Long, Integer> mesta = new HashMap<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            mesta.put(p.getId(), poJakosti.indexOf(p.getIgralec()));
        }

        for (Tekma t : tekmeDogodka(dogodek.getId())) {
            int prvi = mesta.get(t.getPrijava1().getId());
            int drugi = mesta.get(t.getPrijava2().getId());
            boolean presenecenje = Math.min(prvi, drugi) == 0 && Math.max(prvi, drugi) == 5;
            if (!presenecenje) {
                boolean prviMocnejsi = prvi < drugi;
                tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(
                        null, prviMocnejsi ? 3 : 0, prviMocnejsi ? 0 : 3, null, null));
                continue;
            }
            boolean prviJeSibkejsi = prvi == 5;
            tekmaStoritev.vnesiRezultat(t.getId(), new VnosRezultata(
                    null, prviJeSibkejsi ? 3 : 2, prviJeSibkejsi ? 2 : 3, null,
                    tockeZaStran(prviJeSibkejsi)));
        }
    }

    /* Tocke presenecenja z vidika prijave 1: ce je tam zmagovalec, gredo
       naravnost, sicer obrnjeno. */
    private static List<NizVnos> tockeZaStran(boolean zmagovalecJePrvi) {
        List<NizVnos> nizi = new ArrayList<>();
        for (int[] niz : TOCKE_PRESENECENJA) {
            nizi.add(zmagovalecJePrvi ? new NizVnos(niz[0], niz[1]) : new NizVnos(niz[1], niz[0]));
        }
        return nizi;
    }

    private int trenutniRating(Long idIgralca) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(idIgralca, RatingStanje.SISTEM_KLUBSKI_ELO)
                .orElseThrow().getVrednost();
    }

    /* Liga stirih ekip po dva igralca, format Corbillon (A-X, B-Y, dvojice,
       A-Y, B-X), enokrozno in brez praga zmag - odigrajo se vse tekme. */
    private Long ligaStirihEkip() {
        Long idLige = ligaStoritev.ustvari(new LigaVnos(
                "Test liga", "2025/26", SpolKategorija.MOSKI, FormatSrecanja.CORBILLON, 5,
                null, false, 2, 1, 0, true, false, true, false, null, null, null)).id();
        for (int i = 1; i <= 4; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub " + i, null));
            var ekipa = ligaStoritev.dodajEkipo(idLige, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 2; j++) {
                Igralec ig = noviIgralec("Ig" + i + j, "Pri" + i + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), j));
            }
        }
        ligaStoritev.generirajRazpored(idLige);
        return idLige;
    }

    /* Odigra vseh pet tekem srecanja po danem vzorcu (true = zmaga domacih). */
    private void odigrajSrecanje(Long idSrecanje, boolean... domaciZmaga) {
        nastaviPostavo(idSrecanje);
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(idSrecanje).tekme();
        for (int i = 0; i < tekme.size(); i++) {
            boolean domaci = domaciZmaga[i];
            srecanjeStoritev.vnesiRezultat(tekme.get(i).id(), new VnosRezultataSrecanja(
                    null, domaci ? 3 : 1, domaci ? 1 : 3, null, null));
        }
    }

    private void nastaviPostavo(Long idSrecanje) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), true));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), true));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
    }
}
