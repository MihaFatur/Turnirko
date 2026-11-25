/* Mize turnirja in klic tekem (M1; MizeStoritev, Tekma.setStatus).

   Domenska pravila, ki jih ta test varuje (docs/pravila/mize.md):
   - klic: pripravljena tekma gre na obstojeco, vklopljeno, prosto mizo in je V_IGRI
     s casom klica; druga miza samo za ekipno srecanje; brez nastavljenih miz ali
     izven kategorije v teku klica ni;
   - sodnik je igralec s prijavo na turnir in ne igra v tej tekmi;
   - preklic vrne tekmo med pripravljene in pobrise klic; ekipna tekma, katere
     srecanje ze ima postavo, ostane v igri in izgubi samo mizo;
   - vsak vpis izida mizo sprosti (cas konca), miza ostane zapisana na tekmi in
     porazenec je predlagan za sodnika naslednje tekme na njej;
   - stevila miz ni mogoce zmanjsati in mize ne izklopiti, dokler se na njej igra;
   - tuj organizator do miz in klicev nima dostopa;
   - nespremenljivka: vrnitev tekme pred igro pobrise klic, konec zapise cas konca;
   - miza z listka se zapise samo na nepoklicano tekmo in samo na prosto mizo. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.KlicTekmeVnos;
import si.turnirko.dto.MizeTurnirjaDto;
import si.turnirko.dto.NastavitveMizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.PovezavaMizVnos;
import si.turnirko.dto.RedKategorijVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VrstniRedTekemVnos;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class MizeStoritevTest extends IntegracijskiTest {

    @Autowired MizeStoritev mize;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired EkipeDogodkaStoritev ekipeDogodka;
    @Autowired SrecanjeStoritev srecanjeStoritev;
    @Autowired SrecanjeRepozitorij srecanjeRepozitorij;
    @PersistenceContext EntityManager seja;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- nastavitve in pregled ----------

    @Test
    void nastavitveInPregled() {
        Dogodek dogodek = izlocilni(4);
        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(3, List.of(3), true, 5));

        MizeTurnirjaDto p = mize.pregled(idTurnir);
        assertEquals(3, p.steviloMiz());
        assertEquals(List.of(3), p.izklopljeneMize());
        assertTrue(p.porazenecSodi());
        assertEquals(5, p.pocitekMin());
        assertTrue(p.dogodkiVTeku());
        assertEquals(3, p.mize().size());
        assertTrue(p.mize().get(2).izklopljena());
        // dva polfinala sta pripravljena, finale caka
        assertEquals(2, p.kandidati().size());
        assertEquals("polfinale", p.kandidati().get(0).tekma().del());
        assertFalse(p.mize().get(0).predlogi().isEmpty());
    }

    @Test
    void neveljavneNastavitve() {
        Long idTurnir = izlocilni(4).getTurnir().getId();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(100, List.of(), false, 3)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(4, List.of(5), false, 3)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(4, List.of(), false, 31)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(null, List.of(1), false, 3)));
        // delni vnos ne ponastavi nastavitev tiho
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(4, List.of(), null, 3)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastavi(idTurnir, new NastavitveMizVnos(4, List.of(), true, null)));
    }

    /* Izklopljena miza se z listka ne zapise (sicer bi listek z napacno mizo vplival
       na predlog sodnika te mize). */
    @Test
    void mizaZListkaNeNaIzklopljenoMizo() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(3, List.of(2), false, 3));
        Tekma a = pripravljene(dogodek).get(0);
        tekmaStoritev.vnesiRezultat(a.getId(), new VnosRezultata(null, 3, 0, null, null));
        mize.zapisiMizoZListka(a.getId(), 2);
        assertNull(sveza(a.getId()).getMiza());
    }

    // ---------- klic ----------

    @Test
    void klicInSprostitevMize() {
        Dogodek dogodek = izlocilni(4);
        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(2, List.of(), true, 3));
        List<Tekma> polfinala = pripravljene(dogodek);
        Tekma prvi = polfinala.get(0);

        mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, null));
        Tekma poklicana = sveza(prvi.getId());
        assertEquals(StatusTekme.V_IGRI, poklicana.getStatus());
        assertEquals(1, poklicana.getMiza());
        assertNotNull(poklicana.getPoklicanaOb());

        // na zasedeno mizo ne gre druga tekma
        assertThrows(DomenskaIzjema.class,
                () -> mize.poklici(polfinala.get(1).getId(), new KlicTekmeVnos(1, null, null)));
        MizeTurnirjaDto med = mize.pregled(idTurnir);
        assertEquals(prvi.getId(), med.mize().get(0).tekma().tekma().id());
        assertEquals(1, med.kandidati().size());

        // vpis izida mizo sprosti; miza ostane na tekmi, porazenec sodi naslednjo
        tekmaStoritev.vnesiRezultat(prvi.getId(), new VnosRezultata(null, 3, 1, null, null));
        Tekma koncana = sveza(prvi.getId());
        assertEquals(StatusTekme.KONCANA, koncana.getStatus());
        assertEquals(1, koncana.getMiza());
        assertNotNull(koncana.getKoncanaOb());

        MizeTurnirjaDto po = mize.pregled(idTurnir);
        assertNull(po.mize().get(0).tekma());
        assertNotNull(po.mize().get(0).prostaOd());
        Igralec porazenec = koncana.porazenec().getIgralec();
        assertEquals(porazenec.getId(), po.mize().get(0).cakajociSodnik().id());
    }

    @Test
    void premikInSodnik() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(3, List.of(), false, 3));
        List<Tekma> polfinala = pripravljene(dogodek);
        Tekma prvi = polfinala.get(0);
        Igralec izDrugega = polfinala.get(1).getPrijava1().getIgralec();

        mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, izDrugega.getId()));
        var klic = sveza(prvi.getId()).getPoklicanaOb();
        assertEquals(izDrugega.getId(), sveza(prvi.getId()).getSodnik().getId());

        // premik na drugo mizo ohrani cas klica, sodnik se lahko odstrani
        mize.poklici(prvi.getId(), new KlicTekmeVnos(3, null, null));
        Tekma premaknjena = sveza(prvi.getId());
        assertEquals(3, premaknjena.getMiza());
        assertNull(premaknjena.getSodnik());
        assertEquals(klic, premaknjena.getPoklicanaOb());
    }

    @Test
    void sodnikMoraBitiNaTurnirjuInNeIgra() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(2, List.of(), false, 3));
        Tekma prvi = pripravljene(dogodek).get(0);
        Igralec tujec = noviIgralec("Tuji", "Sodnik");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, tujec.getId())));
        Long igralecTekme = prvi.getPrijava2().getIgralec().getId();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, igralecTekme)));
        assertEquals(StatusTekme.PRIPRAVLJENA, sveza(prvi.getId()).getStatus());
    }

    @Test
    void mizaMoraObstajatiInBitiVklopljena() {
        Dogodek dogodek = izlocilni(4);
        Tekma prvi = pripravljene(dogodek).get(0);
        // brez nastavljenih miz klica ni
        assertThrows(DomenskaIzjema.class, () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, null)));

        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(3, List.of(2), false, 3));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(4, null, null)));
        assertThrows(DomenskaIzjema.class, () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(2, null, null)));
        // druga miza je samo za ekipno srecanje
        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, 3, null)));
    }

    @Test
    void cakajociInKoncanihTekemNiMogocePoklicati() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(2, List.of(), false, 3));
        Tekma finale = tekmaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.CAKA).findFirst().orElseThrow();
        assertThrows(DomenskaIzjema.class, () -> mize.poklici(finale.getId(), new KlicTekmeVnos(1, null, null)));

        Tekma prvi = pripravljene(dogodek).get(0);
        tekmaStoritev.vnesiRezultat(prvi.getId(), new VnosRezultata(null, 3, 0, null, null));
        assertThrows(DomenskaIzjema.class, () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, null)));
    }

    @Test
    void preklicVrneTekmoMedPripravljene() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(2, List.of(), false, 3));
        List<Tekma> polfinala = pripravljene(dogodek);
        Tekma prvi = polfinala.get(0);
        mize.poklici(prvi.getId(), new KlicTekmeVnos(2, null, polfinala.get(1).getPrijava1().getIgralec().getId()));

        mize.preklici(prvi.getId());
        Tekma po = sveza(prvi.getId());
        assertEquals(StatusTekme.PRIPRAVLJENA, po.getStatus());
        assertNull(po.getMiza());
        assertNull(po.getPoklicanaOb());
        assertNull(po.getSodnik());
        // nepoklicane tekme ni kaj preklicati
        assertThrows(DomenskaIzjema.class, () -> mize.preklici(prvi.getId()));
    }

    @Test
    void zasedeneMizeNiMogoceOdstraniti() {
        Dogodek dogodek = izlocilni(4);
        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(3, List.of(), false, 3));
        mize.poklici(pripravljene(dogodek).get(0).getId(), new KlicTekmeVnos(3, null, null));

        assertThrows(DomenskaIzjema.class, () -> mize.nastavi(idTurnir, new NastavitveMizVnos(2, List.of(), false, 3)));
        assertThrows(DomenskaIzjema.class, () -> mize.nastavi(idTurnir, new NastavitveMizVnos(3, List.of(3), false, 3)));
        assertThrows(DomenskaIzjema.class, () -> mize.nastavi(idTurnir, new NastavitveMizVnos(null, List.of(), false, 3)));
        // druge mize se lahko izklopijo
        mize.nastavi(idTurnir, new NastavitveMizVnos(3, List.of(1), false, 3));
    }

    // ---------- rocni vrstni red ----------

    @Test
    void rocniVrstniRedVrste() {
        Dogodek dogodek = izlocilni(4);
        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(1, List.of(), false, 3));
        List<Long> samodejno = mize.pregled(idTurnir).kandidati().stream()
                .map(k -> k.tekma().tekma().id()).toList();
        assertEquals(2, samodejno.size());
        assertFalse(mize.pregled(idTurnir).rocniVrstniRed());

        // obrnjen red; tuj id (ni tekma turnirja) se ne zapise
        List<Long> obrnjen = List.of(samodejno.get(1), samodejno.get(0), 999_999L);
        mize.nastaviVrstniRed(idTurnir, new VrstniRedTekemVnos(obrnjen));
        MizeTurnirjaDto p = mize.pregled(idTurnir);
        assertTrue(p.rocniVrstniRed());
        assertEquals(List.of(samodejno.get(1), samodejno.get(0)),
                p.kandidati().stream().map(k -> k.tekma().tekma().id()).toList());
        assertEquals(samodejno.get(1), p.mize().get(0).predlogi().get(0));
        assertEquals(List.of(samodejno.get(1), samodejno.get(0)), sveziTurnir(idTurnir).getVrstniRedTekem());

        // prazen seznam: spet samodejno
        mize.nastaviVrstniRed(idTurnir, new VrstniRedTekemVnos(List.of()));
        assertFalse(mize.pregled(idTurnir).rocniVrstniRed());
        assertEquals(samodejno, mize.pregled(idTurnir).kandidati().stream()
                .map(k -> k.tekma().tekma().id()).toList());
    }

    // ---------- dostop ----------

    @Test
    void tujOrganizatorNimaDostopa() {
        Dogodek dogodek = izlocilni(4);
        Turnir turnir = dogodek.getTurnir();
        Uporabnik lastnik = racun("lastnik-mize@test", Vloga.ORGANIZATOR);
        racun("tujec-mize@test", Vloga.ORGANIZATOR);
        turnir.setUstvaril(lastnik);
        turnirRepozitorij.save(turnir);
        Tekma prvi = pripravljene(dogodek).get(0);

        prijava("tujec-mize@test", "ROLE_ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> mize.pregled(turnir.getId()));
        assertThrows(PrepovedanoIzjema.class, () -> mize.osebe(turnir.getId()));
        assertThrows(PrepovedanoIzjema.class,
                () -> mize.nastavi(turnir.getId(), new NastavitveMizVnos(2, List.of(), false, 3)));
        assertThrows(PrepovedanoIzjema.class, () -> mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, null)));
        assertThrows(PrepovedanoIzjema.class, () -> mize.preklici(prvi.getId()));
        assertThrows(PrepovedanoIzjema.class,
                () -> mize.nastaviVrstniRed(turnir.getId(), new VrstniRedTekemVnos(List.of(prvi.getId()))));

        prijava("lastnik-mize@test", "ROLE_ORGANIZATOR");
        mize.nastavi(turnir.getId(), new NastavitveMizVnos(2, List.of(), false, 3));
        mize.poklici(prvi.getId(), new KlicTekmeVnos(1, null, null));
        assertEquals(4, mize.osebe(turnir.getId()).size());
    }

    // ---------- ekipno srecanje ----------

    @Test
    void ekipnoSrecanjeNaDvehMizahInPreklicPoPostavi() {
        Dogodek dogodek = ekipni();
        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(4, List.of(), false, 3));
        Tekma polfinale = pripravljene(dogodek).get(0);

        mize.poklici(polfinale.getId(), new KlicTekmeVnos(1, 2, null));
        Tekma poklicana = sveza(polfinale.getId());
        assertEquals(1, poklicana.getMiza());
        assertEquals(2, poklicana.getMiza2());
        MizeTurnirjaDto p = mize.pregled(idTurnir);
        assertEquals(polfinale.getId(), p.mize().get(1).tekma().tekma().id());
        assertTrue(p.mize().get(0).tekma().ekipno());
        assertNotNull(p.mize().get(0).tekma().idSrecanje());

        // postava je srecanje zacela: preklic odstrani samo mizi
        vpisiPostavo(srecanjeRepozitorij.najdiZaTekmo(polfinale.getId()).orElseThrow().getId());
        mize.preklici(polfinale.getId());
        Tekma brezMize = sveza(polfinale.getId());
        assertEquals(StatusTekme.V_IGRI, brezMize.getStatus());
        assertNull(brezMize.getMiza());
        assertNull(brezMize.getMiza2());

        // ekipna tekma v igri brez mize je kandidat in dobi mizo naknadno
        MizeTurnirjaDto znova = mize.pregled(idTurnir);
        assertTrue(znova.kandidati().stream().anyMatch(k -> k.tekma().tekma().id().equals(polfinale.getId())));
        mize.poklici(polfinale.getId(), new KlicTekmeVnos(4, null, null));
        assertEquals(4, sveza(polfinale.getId()).getMiza());
    }

    // ---------- nespremenljivka in miza z listka ----------

    @Test
    void vrnitevPredIgroPobriseKlicKonecZapiseCas() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(2, List.of(), false, 3));
        Tekma prvi = pripravljene(dogodek).get(0);
        mize.poklici(prvi.getId(), new KlicTekmeVnos(2, null, null));

        Tekma t = sveza(prvi.getId());
        t.setStatus(StatusTekme.KONCANA);
        assertNotNull(t.getKoncanaOb());
        assertEquals(2, t.getMiza());
        // koncana tekma, ki bi se znova igrala, ni na stari mizi
        t.setStatus(StatusTekme.V_IGRI);
        assertNull(t.getMiza());
        assertNull(t.getKoncanaOb());
        t.setStatus(StatusTekme.KONCANA);
        t.setStatus(StatusTekme.CAKA);
        assertNull(t.getKoncanaOb());
        assertNull(t.getMiza());
        assertNull(t.getPoklicanaOb());
    }

    @Test
    void mizaZListkaSamoNaNepoklicanoTekmoInProstoMizo() {
        Dogodek dogodek = izlocilni(4);
        mize.nastavi(dogodek.getTurnir().getId(), new NastavitveMizVnos(3, List.of(), false, 3));
        List<Tekma> polfinala = pripravljene(dogodek);
        Tekma a = polfinala.get(0);
        Tekma b = polfinala.get(1);
        mize.poklici(b.getId(), new KlicTekmeVnos(2, null, null));

        tekmaStoritev.vnesiRezultat(a.getId(), new VnosRezultata(null, 3, 0, null, null));
        // miza 2 je zasedena s tekmo b, miza 9 ne obstaja
        mize.zapisiMizoZListka(a.getId(), 2);
        mize.zapisiMizoZListka(a.getId(), 9);
        assertNull(sveza(a.getId()).getMiza());
        mize.zapisiMizoZListka(a.getId(), 1);
        assertEquals(1, sveza(a.getId()).getMiza());

        // poklicana tekma ohrani svojo mizo
        tekmaStoritev.vnesiRezultat(b.getId(), new VnosRezultata(null, 3, 0, null, null));
        mize.zapisiMizoZListka(b.getId(), 3);
        assertEquals(2, sveza(b.getId()).getMiza());
    }

    // ---------- predlog (M2) ----------

    /* Lastnik 23. 11. 2026: skupina po 3 pride v kolu med skupine po 4, ne na konec;
       v kolu gredo skupine po vrsti (oznaka skupine pride iz baze v predlog).
       11 igralcev: skupine po 4, 4 in 3, vse s 3 koli. */
    @Test
    void predlogPoKolihInSkupinahTudiSSkupinoPoTri() {
        Dogodek dogodek = pripraviDogodek(11, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Long> tekemVSkupini = tekmeDogodka(dogodek.getId()).stream()
                .collect(java.util.stream.Collectors.groupingBy(Tekma::getIdSkupina,
                        java.util.stream.Collectors.counting()))
                .values().stream().sorted().toList();
        assertEquals(List.of(3L, 6L, 6L), tekemVSkupini, "skupine po 3, 4 in 4");

        Long idTurnir = dogodek.getTurnir().getId();
        mize.nastavi(idTurnir, new NastavitveMizVnos(1, List.of(), false, 3));
        List<String> vrstniRed = mize.pregled(idTurnir).kandidati().stream()
                .map(k -> k.tekma().tekma().kolo() + ". kolo, " + k.tekma().del())
                .toList();

        assertEquals(15, vrstniRed.size());
        assertEquals(vrstniRed.stream().sorted().toList(), vrstniRed,
                "po kolih, v kolu po skupinah: " + vrstniRed);
        assertEquals(5, vrstniRed.stream().filter(s -> s.startsWith("1. kolo")).count());
    }

    // ---------- skupne mize (V78) ----------

    /* Lastnik 25. 11. 2026: dva turnirja v isti dvorani na istih mizah. Po povezavi
       kaze stran Mize obeh isto, nastavitve so glavnega, ista miza ne more dobiti
       tekme iz obeh turnirjev, porazenec/sodnik je lahko iz drugega turnirja. */
    @Test
    void povezanaTurnirjaImataSkupneMize() {
        Dogodek d1 = izlocilni(4);
        Dogodek d2 = izlocilni(4);
        Long t1 = d1.getTurnir().getId();
        Long t2 = d2.getTurnir().getId();
        mize.nastavi(t1, new NastavitveMizVnos(3, List.of(), true, 5));

        assertTrue(mize.zaPovezavo(t2).stream().anyMatch(t -> t.id().equals(t1)));
        mize.povezi(t2, new PovezavaMizVnos(t1));
        assertTrue(mize.zaPovezavo(t2).stream().noneMatch(t -> t.id().equals(t1)));

        MizeTurnirjaDto p2 = mize.pregled(t2);
        assertEquals(3, p2.steviloMiz());
        assertEquals(5, p2.pocitekMin());
        assertEquals(List.of(t1, t2), p2.skupne().turnirji().stream().map(MizeTurnirjaDto.TurnirMizDto::id).toList());
        assertEquals(List.of(d1.getId(), d2.getId()),
                p2.skupne().kategorije().stream().map(MizeTurnirjaDto.KategorijaMizDto::idDogodka).toList());
        assertEquals(4, p2.kandidati().size(), "polfinala obeh turnirjev");
        assertEquals(4, mize.pregled(t1).kandidati().size());

        Tekma a = pripravljene(d1).get(0);
        Tekma b = pripravljene(d2).get(0);
        mize.poklici(a.getId(), new KlicTekmeVnos(1, null, null));
        DomenskaIzjema zasedena = assertThrows(DomenskaIzjema.class,
                () -> mize.poklici(b.getId(), new KlicTekmeVnos(1, null, null)));
        assertTrue(zasedena.getMessage().contains("Na mizi 1 se že igra"), zasedena.getMessage());

        // sodnik tekme drugega turnirja je igralec prvega
        Long sodnik = pripravljene(d1).get(0).getPrijava1().getIgralec().getId();
        mize.poklici(b.getId(), new KlicTekmeVnos(2, null, sodnik));
        assertEquals(sodnik, sveza(b.getId()).getSodnik().getId());
        assertTrue(mize.osebe(t2).stream().anyMatch(o -> o.id().equals(sodnik)));

        // nastavitve s strani drugega turnirja zapise glavni
        mize.nastavi(t2, new NastavitveMizVnos(4, List.of(), false, 3));
        assertEquals(4, sveziTurnir(t1).getSteviloMiz());
        assertEquals(t1, sveziTurnir(t2).getIdSkupneMize());
    }

    /* Miza, na kateri se ze igra v obeh turnirjih, prepreci povezavo (dve tekmi na
       mizi 1); povezava je samo za turnirje istih dni. */
    @Test
    void povezavaZavrneIstoMizoVIgriInDrugDan() {
        Dogodek d1 = izlocilni(4);
        Dogodek d2 = izlocilni(4);
        Long t1 = d1.getTurnir().getId();
        Long t2 = d2.getTurnir().getId();
        mize.nastavi(t1, new NastavitveMizVnos(2, List.of(), false, 3));
        mize.nastavi(t2, new NastavitveMizVnos(2, List.of(), false, 3));
        mize.poklici(pripravljene(d1).get(0).getId(), new KlicTekmeVnos(1, null, null));
        mize.poklici(pripravljene(d2).get(0).getId(), new KlicTekmeVnos(1, null, null));

        DomenskaIzjema obe = assertThrows(DomenskaIzjema.class, () -> mize.povezi(t2, new PovezavaMizVnos(t1)));
        assertTrue(obe.getMessage().contains("v obeh turnirjih"), obe.getMessage());
        assertThrows(NeveljavenVnosIzjema.class, () -> mize.povezi(t1, new PovezavaMizVnos(t1)));

        Dogodek d3 = izlocilni(4);
        Turnir jutri = d3.getTurnir();
        jutri.setDatumZacetka(LocalDate.now().plusDays(1));
        turnirRepozitorij.save(jutri);
        assertThrows(DomenskaIzjema.class, () -> mize.povezi(jutri.getId(), new PovezavaMizVnos(t1)));
        assertTrue(mize.zaPovezavo(t1).stream().noneMatch(t -> t.id().equals(jutri.getId())));
    }

    /* Turnir, ki odide, dobi kopijo nastavitev; ce odide glavni, skupino prevzame
       naslednji (tretji turnir ostane povezan z drugim). */
    @Test
    void razdruzitevDaKopijoNastavitevInNovegaGlavnega() {
        Long t1 = izlocilni(4).getTurnir().getId();
        Long t2 = izlocilni(4).getTurnir().getId();
        Long t3 = izlocilni(4).getTurnir().getId();
        mize.nastavi(t1, new NastavitveMizVnos(6, List.of(2), true, 4));
        mize.povezi(t2, new PovezavaMizVnos(t1));
        mize.povezi(t3, new PovezavaMizVnos(t2));
        assertEquals(t1, sveziTurnir(t3).getIdSkupneMize(), "skupina je globoka en nivo");

        mize.razdruzi(t1);
        Turnir drugi = sveziTurnir(t2);
        assertNull(drugi.getIdSkupneMize());
        assertEquals(6, drugi.getSteviloMiz());
        assertEquals(List.of(2), List.copyOf(drugi.getIzklopljeneMize()));
        assertEquals(t2, sveziTurnir(t3).getIdSkupneMize());
        assertEquals(1, mize.pregled(t1).skupne().turnirji().size());
        assertEquals(2, mize.pregled(t3).skupne().turnirji().size());

        mize.razdruzi(t3);
        Turnir tretji = sveziTurnir(t3);
        assertNull(tretji.getIdSkupneMize());
        assertEquals(6, tretji.getSteviloMiz());
        assertThrows(DomenskaIzjema.class, () -> mize.razdruzi(t3));
    }

    /* Vrstni red kategorij (organizator, V78) odloci v kolu: kategorija drugega turnirja
       gre naprej. Samo kategorije turnirjev s temi mizami. */
    @Test
    void redKategorijVeljaZaPredlog() {
        Dogodek d1 = izlocilni(4);
        Dogodek d2 = izlocilni(4);
        Dogodek tuj = izlocilni(4);
        Long t1 = d1.getTurnir().getId();
        Long t2 = d2.getTurnir().getId();
        mize.nastavi(t1, new NastavitveMizVnos(2, List.of(), false, 3));
        mize.povezi(t2, new PovezavaMizVnos(t1));

        assertEquals(d1.getId(), mize.pregled(t1).kandidati().get(0).tekma().idDogodka());
        mize.nastaviRedKategorij(t2, new RedKategorijVnos(List.of(d2.getId(), d1.getId())));
        MizeTurnirjaDto p = mize.pregled(t1);
        assertTrue(p.skupne().redNastavljen());
        assertEquals(d2.getId(), p.skupne().kategorije().get(0).idDogodka());
        assertEquals(d2.getId(), p.kandidati().get(0).tekma().idDogodka());
        assertEquals(d2.getId(), p.kandidati().get(1).tekma().idDogodka());
        assertEquals(t2, p.kandidati().get(0).tekma().idTurnir());

        assertThrows(NeveljavenVnosIzjema.class,
                () -> mize.nastaviRedKategorij(t1, new RedKategorijVnos(List.of(tuj.getId()))));
        mize.nastaviRedKategorij(t1, new RedKategorijVnos(List.of()));
        assertFalse(mize.pregled(t1).skupne().redNastavljen());
    }

    /* Vedno isti racun (lastnik 25. 11. 2026): tujega turnirja ni med ponujenimi in
       povezava z njim je prepovedana. */
    @Test
    void tujegaTurnirjaNiMogocePovezati() {
        Turnir moj = izlocilni(4).getTurnir();
        Turnir tuj = izlocilni(4).getTurnir();
        Uporabnik jaz = racun("moj-skupne@test", Vloga.ORGANIZATOR);
        Uporabnik drug = racun("tuj-skupne@test", Vloga.ORGANIZATOR);
        moj.setUstvaril(jaz);
        tuj.setUstvaril(drug);
        turnirRepozitorij.save(moj);
        turnirRepozitorij.save(tuj);

        prijava("moj-skupne@test", "ROLE_ORGANIZATOR");
        assertTrue(mize.zaPovezavo(moj.getId()).stream().noneMatch(t -> t.id().equals(tuj.getId())));
        assertThrows(PrepovedanoIzjema.class, () -> mize.povezi(moj.getId(), new PovezavaMizVnos(tuj.getId())));
        assertThrows(PrepovedanoIzjema.class, () -> mize.povezi(tuj.getId(), new PovezavaMizVnos(moj.getId())));
    }

    // ---------- pomozno ----------

    private Dogodek izlocilni(int igralcev) {
        Dogodek dogodek = pripraviDogodek(igralcev);
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodek;
    }

    private List<Tekma> pripravljene(Dogodek dogodek) {
        return tekmaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .sorted(Comparator.comparing(Tekma::getPozicija))
                .toList();
    }

    private Tekma sveza(Long id) {
        seja.flush();
        seja.clear();
        return tekmaRepozitorij.najdiZVsem(id).orElseThrow();
    }

    private Turnir sveziTurnir(Long id) {
        seja.flush();
        seja.clear();
        return turnirRepozitorij.findById(id).orElseThrow();
    }

    private Dogodek ekipni() {
        Turnir turnir = new Turnir();
        turnir.setIme("Ekipni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnirRepozitorij.save(turnir);
        Dogodek dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos("U15 ekipno",
                SpolKategorija.MOSKI, "U15", 5, null, null, Disciplina.EKIPNO, SistemTekmovanja.IZLOCILNI,
                null, null, FormatSrecanja.POKAL_NTZS, null, null));
        List<EkipaDto> ekipe = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String ime = String.format("Ekipa %02d", i);
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            EkipaDto ekipa = ekipeDogodka.dodajEkipo(dogodek.getId(), new EkipaVnos(klub.getId(), 1, ime));
            for (int j = 1; j <= 3; j++) {
                Igralec ig = noviIgralec("Ig" + i + "x" + j, "Ekipni" + i + "x" + j);
                ekipeDogodka.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), j));
            }
            ekipe.add(ekipa);
        }
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodekRepozitorij.findById(dogodek.getId()).orElseThrow();
    }

    private void vpisiPostavo(Long idSrecanja) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanja);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), false));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), false));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanja, new PostavaVnos(mesta));
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
