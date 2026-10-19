/* Prestavitev srecanja lige (V47): organizator srecanje premakne v drugo kolo
   ali ga pusti brez termina, dokler novi dan ni znan.

   Testi varujejo, kar razpored in igralci berejo: prestavljeno srecanje je
   SAMO v kolu, v katerem se igra (kolo srecanja je kolo igranja), kolo iz
   zreba si zapomni, da se da vrniti in da ekipa tam ni "prosta"; srecanje
   brez termina ne sodi v noben dan, tudi ne v dan svojega kola; premaknjeno je
   lahko tudi ze odigrano srecanje (ekipi sta se za termine zamenjali in sta
   srecanje poznega kola odigrali ze zdaj); ekipa pa ne igra dveh srecanj
   hkrati. Cista logika poteka lige je v PotekLigeTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.TerminiVnos;
import si.turnirko.dto.TerminiVnos.TerminKola;
import si.turnirko.dto.TerminiVnos.TerminSrecanja;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

class PrestavitevSrecanjaTest extends IntegracijskiTest {

    private static final LocalDateTime PRVI_VECER = LocalDateTime.of(2026, 10, 6, 18, 30);

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @PersistenceContext private EntityManager seja;

    /* Moder primer: srecanje 5. kola se je odigralo ze v 2. kolu. V razporedu
       stoji v 2. kolu in v 5. ne vec; kolo iz zreba ostane zapisano. Ura mora
       prezivet zapis (glej uraTerminaPrezivizapisVBazo). */
    @Test
    void srecanjeSePrestaviVDrugoKoloInStojiSamoTam() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        LocalDateTime vDrugemKolu = PRVI_VECER.plusDays(7).with(LocalTime.of(20, 0));

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), vDrugemKolu, 2, false, "Zamenjava termina"))));
        seja.flush();
        seja.clear();

        SrecanjeDto prestavljeno = srecanje(liga, iz5.id());
        assertEquals(2, prestavljeno.kolo(), "kolo srecanja je kolo, v katerem se igra");
        assertEquals(5, prestavljeno.izvirnoKolo());
        assertEquals(vDrugemKolu, prestavljeno.predvidenZacetek());
        assertFalse(prestavljeno.cakaNaTermin());
        assertEquals("Zamenjava termina", prestavljeno.razlogPrestavitve());
        assertEquals(3, srecanjaKola(liga, 2).size());
        assertEquals(1, srecanjaKola(liga, 5).size(), "v 5. kolu je eno srecanje manj");
        assertEquals(2, srecanjaKola(liga, 4).size(), "druga kola so nedotaknjena");
    }

    /* Prestavitev, po kateri ekipa igra dve srecanji ob isti uri, bi ji dala
       dve srecanji hkrati. V 2. kolu vsaka od stirih ekip ze igra ob 18.30. */
    @Test
    void prestavitevNaUroKjerEkipaZeIgraSeZavrne() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), PRVI_VECER.plusDays(7), 2, false, null)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    @Test
    void prestavitevVNeobstojeceKoloSeZavrne() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto prvo = srecanjeStoritev.zaLigo(liga).get(0);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(prvo.id(), PRVI_VECER.plusDays(70), 99, false, null)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Prestavitev nazaj v kolo iz zreba srecanje spet naredi navadno: izvirnega
       kola ni vec in razlog, ki ga ni cemu pojasnjevati, izgine. */
    @Test
    void vrnitevVKoloIzZrebaOcistiPrestavitev() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), PRVI_VECER.plusDays(7).with(LocalTime.of(20, 0)), 2, false, "Bolezen"))));

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), iz5.predvidenZacetek(), 5, false, null))));
        seja.flush();
        seja.clear();

        SrecanjeDto vrnjeno = srecanje(liga, iz5.id());
        assertEquals(5, vrnjeno.kolo());
        assertNull(vrnjeno.izvirnoKolo(), "srecanje je spet tam, kamor sodi");
        assertNull(vrnjeno.razlogPrestavitve());
        assertEquals(iz5.predvidenZacetek(), vrnjeno.predvidenZacetek());
    }

    /* Vec premikov zapored ne izgubi kola iz zreba. */
    @Test
    void izvirnoKoloPrezivi_veckratnoPrestavljanje() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), PRVI_VECER.plusDays(7).with(LocalTime.of(20, 0)), 2, false, null))));
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), PRVI_VECER.plusDays(21).with(LocalTime.of(20, 0)), 4, false, null))));

        SrecanjeDto zdaj = srecanje(liga, iz5.id());
        assertEquals(4, zdaj.kolo());
        assertEquals(5, zdaj.izvirnoKolo(), "izvirno kolo je kolo iz zreba, ne prejsnje");
    }

    /* Srecanje brez novega termina: kolo ostane izvirno, zacetka ni in ga ne
       dobi niti, ce organizator hkrati spremeni termin tega kola. */
    @Test
    void cakajoceSrecanjeNimaTerminaInGaTerminKolaNeDobi() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz3 = srecanjaKola(liga, 3).get(0);
        LocalDateTime novoKolo = PRVI_VECER.plusDays(15);

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(
                List.of(new TerminKola(3, novoKolo)),
                List.of(new TerminSrecanja(iz3.id(), null, null, true, "Dvorana zasedena"))));
        seja.flush();
        seja.clear();

        SrecanjeDto caka = srecanje(liga, iz3.id());
        assertTrue(caka.cakaNaTermin());
        assertNull(caka.predvidenZacetek());
        assertEquals(3, caka.kolo());
        assertEquals(3, caka.izvirnoKolo());
        assertEquals("Dvorana zasedena", caka.razlogPrestavitve());
        SrecanjeDto drugo = srecanjaKola(liga, 3).stream().filter(s -> !s.id().equals(iz3.id())).findFirst().orElseThrow();
        assertEquals(novoKolo, drugo.predvidenZacetek(), "ostalo srecanje kola dobi novi termin");
    }

    /* Cakajoce srecanje dobi dan z vnosom kola in zacetka; vnos samo z
       zacetkom ga vrne v kolo, v katerem je. */
    @Test
    void cakajoceSrecanjeDobiTermin() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz3 = srecanjaKola(liga, 3).get(0);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz3.id(), null, null, true, null))));

        LocalDateTime pozneje = PRVI_VECER.plusDays(28).with(LocalTime.of(20, 0));
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz3.id(), pozneje, 5, false, null))));

        SrecanjeDto zdaj = srecanje(liga, iz3.id());
        assertFalse(zdaj.cakaNaTermin());
        assertEquals(5, zdaj.kolo());
        assertEquals(3, zdaj.izvirnoKolo());
        assertEquals(pozneje, zdaj.predvidenZacetek());

        // samo z zacetkom: cakajoce se vrne v svoje kolo
        SrecanjeDto iz2 = srecanjaKola(liga, 2).get(0);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz2.id(), null, null, true, null))));
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz2.id(), iz2.predvidenZacetek()))));
        SrecanjeDto vrnjeno = srecanje(liga, iz2.id());
        assertFalse(vrnjeno.cakaNaTermin());
        assertEquals(2, vrnjeno.kolo());
        assertEquals(iz2.predvidenZacetek(), vrnjeno.predvidenZacetek());
    }

    /* Kolo, iz katerega so se vsa srecanja izselila, je se vedno kolo lige:
       vanj se da vrniti, razpored pa ga ne kaze. */
    @Test
    void izpraznjenoKoloJeSeVednoKoloLige() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        List<SrecanjeDto> kolo6 = srecanjaKola(liga, 6);
        List<TerminSrecanja> izselitev = new ArrayList<>();
        for (SrecanjeDto s : kolo6) {
            izselitev.add(new TerminSrecanja(s.id(), null, null, true, null));
        }
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), izselitev));
        assertTrue(srecanjeStoritev.zaLigo(liga).stream()
                .filter(s -> !s.cakaNaTermin()).noneMatch(s -> s.kolo() == 6), "6. kolo je prazno");

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(kolo6.get(0).id(), kolo6.get(0).predvidenZacetek(), 6, false, null))));

        assertFalse(srecanje(liga, kolo6.get(0).id()).cakaNaTermin());
        assertNull(srecanje(liga, kolo6.get(0).id()).izvirnoKolo());
    }

    /* Premakniti je mogoce tudi ze odigrano srecanje: izid in stanje ostaneta,
       zamenja se samo kolo in termin. */
    @Test
    void odigranoSrecanjeSePrestaviIzidOstane() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        Srecanje entiteta = srecanjeRepozitorij.findById(iz5.id()).orElseThrow();
        entiteta.setStatus(StatusSrecanja.KONCANO);
        entiteta.setDobljeneDomaci(5);
        entiteta.setDobljeneGost(3);
        srecanjeRepozitorij.saveAndFlush(entiteta);

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), PRVI_VECER.plusDays(7).with(LocalTime.of(20, 0)), 2, false, null))));
        seja.flush();
        seja.clear();

        SrecanjeDto prestavljeno = srecanje(liga, iz5.id());
        assertEquals(StatusSrecanja.KONCANO, prestavljeno.status());
        assertEquals(5, prestavljeno.dobljeneDomaci());
        assertEquals(3, prestavljeno.dobljeneGost());
        assertEquals(2, prestavljeno.kolo());
    }

    /* Liga brez ure (kolo je samo dan): kolo se prestavi na drug dan, premaknjeno
       srecanje, ki ga vnos ne nasteje, gre z njim; tisto z izrecnim zacetkom
       obdrzi svoj dan. (Pri ligi z uro bi premaknjeno srecanje na uro kola
       trcilo ob srecanjem obeh svojih ekip, zato ga vmesnik vedno poda z
       izrecnim zacetkom.) */
    @Test
    void terminKolaVelaZaPrestavljenoBrezIzrecnegaZacetka() {
        Long liga = ustvariLigo(LocalDateTime.of(2026, 10, 6, 0, 0));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        SrecanjeDto iz4 = srecanjaKola(liga, 4).get(0);
        LocalDateTime drugoKolo = LocalDateTime.of(2026, 10, 13, 0, 0);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), drugoKolo, 2, false, null),
                new TerminSrecanja(iz4.id(), drugoKolo, 2, false, null))));

        LocalDateTime novoKolo = LocalDateTime.of(2026, 10, 14, 0, 0);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(
                List.of(new TerminKola(2, novoKolo)),
                List.of(new TerminSrecanja(iz4.id(), novoKolo.plusDays(2)))));

        assertEquals(novoKolo, srecanje(liga, iz5.id()).predvidenZacetek(),
                "srecanje brez izrecnega zacetka gre s kolom");
        assertEquals(novoKolo.plusDays(2), srecanje(liga, iz4.id()).predvidenZacetek(),
                "izrecni zacetek prevlada");
        assertEquals(2, srecanje(liga, iz4.id()).kolo());
        assertEquals(4, srecanje(liga, iz4.id()).izvirnoKolo(), "popravek zacetka prestavitve ne brise");
    }

    /* Razlog: prazen niz ga zbrise, "ni podan" ga pusti (vmesnik ob popravku ure
       razloga ne nosi). */
    @Test
    void razlogSeObrezeZbriseInPustiBrezVnosa() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        LocalDateTime zvecer = PRVI_VECER.plusDays(7).with(LocalTime.of(20, 0));
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), zvecer, 2, false, "  Dvorana zasedena  "))));
        assertEquals("Dvorana zasedena", srecanje(liga, iz5.id()).razlogPrestavitve());

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), zvecer.plusMinutes(15), 2, false, null))));
        assertEquals("Dvorana zasedena", srecanje(liga, iz5.id()).razlogPrestavitve(),
                "popravek ure razloga ne izbrise");

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), zvecer.plusMinutes(15), 2, false, "   "))));
        assertNull(srecanje(liga, iz5.id()).razlogPrestavitve());
    }

    /* Odigrano srecanje ima dan: pustiti ga brez termina bi izid skrilo iz
       razporeda. Zavrne se; premakne se lahko le v kolo. */
    @Test
    void odigranoSrecanjeNeMoreCakatiNaTermin() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto iz5 = srecanjaKola(liga, 5).get(0);
        Srecanje entiteta = srecanjeRepozitorij.findById(iz5.id()).orElseThrow();
        entiteta.setStatus(StatusSrecanja.KONCANO);
        srecanjeRepozitorij.saveAndFlush(entiteta);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(iz5.id(), null, null, true, null)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Razlog nima smisla pri srecanju, ki ni prestavljeno. */
    @Test
    void razlogNeSluziNeprestavljenemuSrecanju() {
        Long liga = ustvariLigo(PRVI_VECER);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto prvo = srecanjaKola(liga, 1).get(0);

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminSrecanja(prvo.id(), prvo.predvidenZacetek(), null, null, "Pomota"))));

        assertNull(srecanje(liga, prvo.id()).razlogPrestavitve());
    }

    /* Potek lige se bere iz istih srecanj: kolo, iz katerega sta se obe
       srecanji izselili brez termina, je s tem odsotno, ostala pa se stejejo.
       Datumi so v preteklosti, zato so vsa kola odigrana. */
    @Test
    void potekLigeNeStejeCakajocihSrecanj() {
        Long liga = ustvariLigo(LocalDateTime.of(2020, 3, 3, 18, 30));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        LigaDto pred = ligaStoritev.najdi(liga);
        assertEquals(6, pred.steviloKol());
        assertEquals(6, pred.odigranihKol());

        List<TerminSrecanja> izselitev = new ArrayList<>();
        for (SrecanjeDto s : srecanjaKola(liga, 6)) {
            izselitev.add(new TerminSrecanja(s.id(), null, null, true, null));
        }
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), izselitev));

        LigaDto po = ligaStoritev.najdi(liga);
        assertEquals(5, po.steviloKol(), "6. kolo brez srecanj ni kolo razporeda");
        assertEquals(5, po.odigranihKol());
        assertNull(po.naslednje(), "srecanje brez termina ni naslednje");
    }

    // ---------- pomozne metode ----------

    private List<SrecanjeDto> srecanjaKola(Long liga, int kolo) {
        return srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == kolo).toList();
    }

    private SrecanjeDto srecanje(Long liga, Long id) {
        return srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private void dodajEkipe(Long idLiga, String... imena) {
        for (String ime : imena) {
            ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime));
        }
    }

    /* Liga kroznega sistema, dvokrozna: pri stirih ekipah 6 kol po 2 srecanji. */
    private Long ustvariLigo(LocalDateTime prvoKolo) {
        LigaVnos v = new LigaVnos("Prestavitve", "2026/27", SpolKategorija.MESANO,
                FormatSrecanja.SAVINJA, 5, null, true, 2, 1, 0, true, false,
                RavenTekmovanja.REKREATIVNO, false, null, prvoKolo, 7, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }
}
