/* Dodatni dostop organizatorjev do tekmovanj (DostopOrganizatorjevStoritev, V62).
   Testi drzijo merila, ki jih funkcija ne sme izgubiti:
   - dodeljen dostop odpre urejanje (LastnistvoStoritev) in odvzem ga zapre,
   - dodeljeno tekmovanje steje v mejo paketa SAMO, ce admin tako odloci,
   - lastnik lahko tekmovanje iz svoje meje izvzame, vrnitev zbrise vrstico,
   - meja loci turnirje in lige ter se sezonsko ponastavi,
   - dodeljuje in bere samo admin, dodeli se samo organizator. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.DostopTekmovanjaDto;
import si.turnirko.dto.DostopTekmovanjaDto.Organizator;
import si.turnirko.dto.DostopTekmovanjaDto.Vir;
import si.turnirko.dto.OrganizatorPregledDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.DostopOrganizatorjaRepozitorij;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class DostopOrganizatorjevTest extends IntegracijskiTest {

    private static final LocalDate DANES = LocalDate.now();

    @Autowired private DostopOrganizatorjevStoritev dostop;
    @Autowired private LastnistvoStoritev lastnistvo;
    @Autowired private NarocninaStoritev narocnina;
    @Autowired private OrganizatorPregledStoritev pregledStoritev;
    @Autowired private RacunDnevnikStoritev dnevnik;
    @Autowired private DostopOrganizatorjaRepozitorij dostopRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @PersistenceContext private EntityManager seja;

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Dostop ----------

    @Test
    void dodeljenDostopOdpreUrejanjeTurnirjaInOdvzemGaZapre() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");

        prijava("gost@test", "ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("admin@test", "ADMIN");
        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), false);
        prijava("gost@test", "ORGANIZATOR");
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("admin@test", "ADMIN");
        dostop.odstraniZaTurnir(turnir.getId(), gost.getId());
        prijava("gost@test", "ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriTurnirPoId(turnir.getId()));
    }

    @Test
    void dodeljenDostopOdpreUrejanjeLigeInVelja_SamoZaToLigo() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Liga prva = liga(lastnik, "Prva");
        Liga druga = liga(lastnik, "Druga");

        dostop.nastaviZaLigo(prva.getId(), gost.getId(), false);

        prijava("gost@test", "ORGANIZATOR");
        assertDoesNotThrow(() -> lastnistvo.preveriLigaPoId(prva.getId()));
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriLigaPoId(druga.getId()));
    }

    /* Dostop do turnirja ne odpre lige z istim id-jem in obratno: vrstica nosi
       natanko enega od obeh ciljev. */
    @Test
    void dostopDoTurnirjaNeOdpreLigeZIstimIdjem() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");
        Liga liga = liga(lastnik, "Liga");

        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), false);

        prijava("gost@test", "ORGANIZATOR");
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));
        assertThrows(PrepovedanoIzjema.class, () -> lastnistvo.preveriLigaPoId(liga.getId()));
    }

    // ---------- Meja paketa ----------

    @Test
    void dodeljenoKiSteje_polniMejoInZapreUstvarjanje() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC); // 2 turnirja
        turnir(gost, "Moj prvi");
        Turnir tuj = turnir(lastnik, "Tuj");
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(gost));

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), true);

        assertEquals(2, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));
        assertThrows(DomenskaIzjema.class, () -> narocnina.preveriOmejitevTurnirja(gost));
    }

    @Test
    void dodeljenoKiNeSteje_meje_ne_spremeni() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        turnir(gost, "Moj prvi");
        Turnir tuj = turnir(lastnik, "Tuj");

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), false);

        assertEquals(1, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(gost));
    }

    /* Preklop »šteje« na dodeljenem tekmovanju se pozna takoj, v obe smeri. */
    @Test
    void preklopStetjaNaDodeljenemSePozna() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_PLUS);
        Turnir tuj = turnir(lastnik, "Tuj");

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), true);
        assertEquals(1, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), false);
        assertEquals(0, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), true);
        assertEquals(1, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));
    }

    /* Turnirji in lige imajo vsak svojo mejo: dodeljen turnir ne polni lig. */
    @Test
    void dodeljenTurnirNePolniMejeLig() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC); // 1 liga
        Turnir tuj = turnir(lastnik, "Tuj");

        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), true);

        assertEquals(0, narocnina.steviloLigVSezoni(gost.getId(), DANES));
        assertDoesNotThrow(() -> narocnina.preveriOmejitevLige(gost));
    }

    @Test
    void dodeljenaLigaSteje() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC); // 1 liga
        Liga tuja = liga(lastnik, "Tuja");

        dostop.nastaviZaLigo(tuja.getId(), gost.getId(), true);

        assertEquals(1, narocnina.steviloLigVSezoni(gost.getId(), DANES));
        assertThrows(DomenskaIzjema.class, () -> narocnina.preveriOmejitevLige(gost));
    }

    /* Kvalifikacije so podaljsek lige, ki jo je racun ze stel. */
    @Test
    void dodeljenaLigaKvalifikacijNeSteje() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Liga visja = liga(lastnik, "Visja");
        Liga kval = liga(lastnik, "Kvalifikacije");
        kval.nastaviKvalifikacije(visja, liga(lastnik, "Nizja"));
        ligaRepozitorij.save(kval);

        dostop.nastaviZaLigo(kval.getId(), gost.getId(), true);

        assertEquals(0, narocnina.steviloLigVSezoni(gost.getId(), DANES));
    }

    /* Sezonski rez (1. julij) velja tudi za dodelitev: od kdaj steje, doloca
       cas dodelitve. */
    @Test
    void dodelitevIzPrejsnjeSezoneSeNeSteje() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir tuj = turnir(lastnik, "Tuj");
        dostop.nastaviZaTurnir(tuj.getId(), gost.getId(), true);
        assertEquals(1, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));

        seja.createNativeQuery("UPDATE dostop_organizatorja SET dodeljen_ob = ?1 WHERE id_racun = ?2")
                .setParameter(1, LocalDateTime.of(2020, 4, 1, 10, 0))
                .setParameter(2, gost.getId())
                .executeUpdate();
        seja.clear();

        assertEquals(0, narocnina.steviloTurnirjevVSezoni(gost.getId(), DANES));
    }

    // ---------- Lastnik ----------

    @Test
    void lastnikLahkoTekmovanjeIzvzameIzMejeInVrnitevZbriseVrstico() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_BASIC); // 2 turnirja
        Turnir prvi = turnir(lastnik, "Prvi");
        turnir(lastnik, "Drugi");
        assertThrows(DomenskaIzjema.class, () -> narocnina.preveriOmejitevTurnirja(lastnik));

        dostop.nastaviZaTurnir(prvi.getId(), lastnik.getId(), false);

        assertEquals(1, narocnina.steviloTurnirjevVSezoni(lastnik.getId(), DANES));
        assertDoesNotThrow(() -> narocnina.preveriOmejitevTurnirja(lastnik));

        dostop.nastaviZaTurnir(prvi.getId(), lastnik.getId(), true);

        assertEquals(2, narocnina.steviloTurnirjevVSezoni(lastnik.getId(), DANES));
        assertTrue(dostopRepozitorij.findByIdTurnirAndIdRacun(prvi.getId(), lastnik.getId()).isEmpty(),
                "privzetek lastnika se ne hrani");
    }

    /* Izvzetje lastnika dostopa ne spremeni: lastnistvo je vedno njegovo. */
    @Test
    void izvzetemuLastnikuDostopOstane() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Moj");
        dostop.nastaviZaTurnir(turnir.getId(), lastnik.getId(), false);

        prijava("lastnik@test", "ORGANIZATOR");
        assertDoesNotThrow(() -> lastnistvo.preveriTurnirPoId(turnir.getId()));

        prijava("admin@test", "ADMIN");
        DostopTekmovanjaDto d = dostop.zaTurnir(turnir.getId());
        Organizator o = d.organizatorji().get(0);
        assertEquals(Vir.LASTNIK, o.vir());
        assertFalse(o.steje());
    }

    @Test
    void odstranitevPrepisaLastnikuVrnePrivzetek() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Moj");
        dostop.nastaviZaTurnir(turnir.getId(), lastnik.getId(), false);

        DostopTekmovanjaDto d = dostop.odstraniZaTurnir(turnir.getId(), lastnik.getId());

        assertTrue(d.organizatorji().get(0).steje());
        assertEquals(1, narocnina.steviloTurnirjevVSezoni(lastnik.getId(), DANES));
    }

    // ---------- Pogoji ----------

    @Test
    void dodeliSeLahkoSamoOrganizatorju() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik igralec = new Uporabnik("igralec@test", "{bcrypt}x", Vloga.IGRALEC);
        igralec.setStatus(StatusRacuna.CAKA);
        igralec = uporabnikRepozitorij.save(igralec);
        Turnir turnir = turnir(lastnik, "Pokal");

        Long idIgralca = igralec.getId();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> dostop.nastaviZaTurnir(turnir.getId(), idIgralca, true));
    }

    @Test
    void nepotrjenOrganizatorDostopaNeDobi() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik cakajoc = new Uporabnik("cakajoc@test", "{bcrypt}x", Vloga.ORGANIZATOR);
        cakajoc.setStatus(StatusRacuna.CAKA);
        cakajoc = uporabnikRepozitorij.save(cakajoc);
        Turnir turnir = turnir(lastnik, "Pokal");

        Long id = cakajoc.getId();
        assertThrows(NeveljavenVnosIzjema.class, () -> dostop.nastaviZaTurnir(turnir.getId(), id, true));
    }

    @Test
    void odstranitevNeobstojecegaDostopaJeNiNajdeno() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");

        assertThrows(NiNajdenoIzjema.class, () -> dostop.odstraniZaTurnir(turnir.getId(), gost.getId()));
    }

    @Test
    void neobstojecTurnirInLigaStaNiNajdeno() {
        assertThrows(NiNajdenoIzjema.class, () -> dostop.zaTurnir(-1L));
        assertThrows(NiNajdenoIzjema.class, () -> dostop.zaLigo(-1L));
    }

    /* Veriga omeji pot na admina; to je druga obramba v storitvi. */
    @Test
    void urejaSamoAdmin() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");

        prijava("lastnik@test", "ORGANIZATOR");
        assertThrows(PrepovedanoIzjema.class, () -> dostop.zaTurnir(turnir.getId()));
        assertThrows(PrepovedanoIzjema.class,
                () -> dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), true));
        assertThrows(PrepovedanoIzjema.class,
                () -> dostop.odstraniZaTurnir(turnir.getId(), gost.getId()));
        assertTrue(dostopRepozitorij.findByIdTurnir(turnir.getId()).isEmpty());
    }

    // ---------- Izpis ----------

    @Test
    void izpisImaLastnikaPrvegaDodeljeneAbecedno_inKandidateBrezUdelezencev() {
        Klub klub = klubRepozitorij.save(new Klub("NTK Savinja", null));
        Uporabnik lastnik = organizator("lastnik@test", "Žan", "Zupan", klub, Paket.ORGANIZATOR_PRO);
        Uporabnik cerne = organizator("cerne@test", "Črt", "Černe", null, Paket.ORGANIZATOR_BASIC);
        Uporabnik bor = organizator("bor@test", "Bor", "Kos", null, Paket.ORGANIZATOR_PLUS);
        Uporabnik drug = organizator("drug@test", "Dan", "Pirc", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");
        turnir.setKlubLastnik(klub);
        turnirRepozitorij.save(turnir);

        dostop.nastaviZaTurnir(turnir.getId(), cerne.getId(), true);
        DostopTekmovanjaDto d = dostop.nastaviZaTurnir(turnir.getId(), bor.getId(), false);

        assertEquals("NTK Savinja", d.klubLastnik());
        assertEquals(List.of("Žan Zupan", "Bor Kos", "Črt Černe"),
                d.organizatorji().stream().map(Organizator::ime).toList());
        assertEquals(List.of(Vir.LASTNIK, Vir.DODELJEN, Vir.DODELJEN),
                d.organizatorji().stream().map(Organizator::vir).toList());
        assertEquals(List.of("Dan Pirc"), d.kandidati().stream().map(k -> k.ime()).toList());
        assertEquals(drug.getId(), d.kandidati().get(0).idRacun());
    }

    @Test
    void izpisKazePorabeZaVrstoTekmovanja() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        turnir(gost, "Moj");
        Liga tuja = liga(lastnik, "Tuja");

        DostopTekmovanjaDto d = dostop.nastaviZaLigo(tuja.getId(), gost.getId(), true);

        Organizator o = d.organizatorji().stream().filter(x -> x.idRacun().equals(gost.getId())).findFirst()
                .orElseThrow();
        assertEquals(Paket.ORGANIZATOR_BASIC, o.poraba().paket());
        assertEquals(1, o.poraba().uporabljeno(), "liga: ena dodeljena, turnir je druga meja");
        assertEquals(1, o.poraba().meja());
    }

    @Test
    void izpisBrezPaketaImaMejoNull() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik brez = new Uporabnik("brez@test", "{bcrypt}x", Vloga.ORGANIZATOR);
        brez.setStatus(StatusRacuna.POTRJEN);
        brez = uporabnikRepozitorij.save(brez);
        Turnir turnir = turnir(lastnik, "Pokal");

        DostopTekmovanjaDto d = dostop.nastaviZaTurnir(turnir.getId(), brez.getId(), true);

        Organizator o = d.organizatorji().get(1);
        assertNull(o.poraba().paket());
        assertNull(o.poraba().meja());
    }

    // ---------- Povezave z drugimi mesti ----------

    @Test
    void spremembaJeVDnevnikuRacunaOrganizatorja() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");

        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), true);
        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), false);
        dostop.odstraniZaTurnir(turnir.getId(), gost.getId());

        List<String> besedila = dnevnik.dnevnik(gost.getId()).stream().map(e -> e.tekst()).toList();
        assertEquals(3, besedila.size());
        assertTrue(besedila.stream().anyMatch(t -> t.contains("»Pokal« dodeljen, šteje")));
        assertTrue(besedila.stream().anyMatch(t -> t.contains("ne šteje v omejitev")));
        assertTrue(besedila.stream().anyMatch(t -> t.contains("odvzet")));
    }

    /* Lastniku se tekmovanje ne »dodeli«: dnevnik pove samo, da ne steje. */
    @Test
    void izvzetjeLastnikaVDnevnikuNiDodelitev() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Turnir turnir = turnir(lastnik, "Pokal");

        dostop.nastaviZaTurnir(turnir.getId(), lastnik.getId(), false);

        List<String> besedila = dnevnik.dnevnik(lastnik.getId()).stream().map(e -> e.tekst()).toList();
        assertEquals(List.of("Dostop turnirja »Pokal«: ne šteje v omejitev paketa"), besedila);
    }

    /* Ponovljen enak klic ni sprememba in ne zapisuje v dnevnik. */
    @Test
    void enakKlicNiSpremembe() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");

        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), true);
        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), true);

        assertEquals(1, dnevnik.dnevnik(gost.getId()).size());
        assertEquals(1, dostopRepozitorij.findByIdTurnir(turnir.getId()).size());
    }

    /* Pregled organizatorja steje iste dve poti kot meja: ustvarjeno in
       dodeljeno. Dodeljeno, ki ne steje, je na seznamu, v meji ga ni. */
    @Test
    void dodeljenaTekmovanjaSoVPregleduOrganizatorja() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_PLUS);
        turnir(gost, "Moj turnir");
        Turnir steje = turnir(lastnik, "Dodeljen in steje");
        Turnir nesteje = turnir(lastnik, "Dodeljen brez stetja");
        turnir(lastnik, "Nedodeljen");
        dostop.nastaviZaTurnir(steje.getId(), gost.getId(), true);
        dostop.nastaviZaTurnir(nesteje.getId(), gost.getId(), false);

        OrganizatorPregledDto p = pregledStoritev.pregled(
                uporabnikRepozitorij.findById(gost.getId()).orElseThrow(), DANES);

        List<String> imena = p.tekmovanja().stream().map(t -> t.ime()).sorted().toList();
        assertEquals(List.of("Dodeljen brez stetja", "Dodeljen in steje", "Moj turnir"), imena);
        assertEquals(2, p.turnirji().uporabljeno(), "moj + dodeljen, ki steje");
    }

    @Test
    void profilNosiDodeljenaTekmovanja() {
        Uporabnik lastnik = organizator("lastnik@test", "Ana", "Novak", null, Paket.ORGANIZATOR_PRO);
        Uporabnik gost = organizator("gost@test", "Bor", "Kos", null, Paket.ORGANIZATOR_BASIC);
        Turnir turnir = turnir(lastnik, "Pokal");
        Liga liga = liga(lastnik, "Liga");
        dostop.nastaviZaTurnir(turnir.getId(), gost.getId(), false);
        dostop.nastaviZaLigo(liga.getId(), gost.getId(), false);

        assertEquals(List.of(turnir.getId()), dostopRepozitorij.idjiTurnirjev(gost.getId()));
        assertEquals(List.of(liga.getId()), dostopRepozitorij.idjiLig(gost.getId()));
    }

    // ---------- Pomozno ----------

    private Uporabnik organizator(String prijavnoIme, String ime, String priimek, Klub klub, Paket paket) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        u.setPrijavljenoIme(ime);
        u.setPrijavljeniPriimek(priimek);
        u.setKlub(klub);
        u = uporabnikRepozitorij.save(u);
        Narocnina n = new Narocnina(u, paket);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        return u;
    }

    private Turnir turnir(Uporabnik lastnik, String ime) {
        Turnir t = new Turnir();
        t.setIme(ime);
        t.setUstvaril(lastnik);
        return turnirRepozitorij.save(t);
    }

    private Liga liga(Uporabnik lastnik, String ime) {
        Liga l = new Liga();
        l.setIme(ime);
        l.setSpolKategorija(SpolKategorija.MESANO);
        l.setUstvaril(lastnik);
        return ligaRepozitorij.save(l);
    }

    /* Prijava za preverbo lastnistva in vloge. Admin se za SecurityContext
       potrebuje tudi kot zapis v bazi: LastnistvoStoritev.trenutni() bere vlogo
       iz baze in ne iz pravic. */
    private void prijava(String prijavnoIme, String vloga) {
        if (uporabnikRepozitorij.findByUporabniskoIme(prijavnoIme).isEmpty()) {
            Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.valueOf(vloga));
            u.setStatus(StatusRacuna.POTRJEN);
            uporabnikRepozitorij.save(u);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(prijavnoIme, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + vloga))));
    }
}
