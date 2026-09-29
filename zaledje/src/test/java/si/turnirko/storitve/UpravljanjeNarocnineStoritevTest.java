/* Stran "Narocnina" (UpravljanjeNarocnineStoritev): preklic ob koncu obdobja,
   obnova preklicane, preklop placevanja ob naslednji obnovi (igralec) in
   zamenjava organizatorskega paketa (nadgradnja takoj, znizanje ob obnovi).

   Stripe je nadomescen z laznim (StripeNarocnine): test preveri, KAJ storitev
   od njega zahteva in kaj zapise v vrstico, ne pravega omrezja. Isto nacelo kot
   PlacilaStoritevTest, ki dogodek podpise rocno. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import si.turnirko.dto.NarocninaDto;
import si.turnirko.dto.SpremembaPaketaDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UpravljanjeNarocnineStoritevTest {

    private static final String SKRIVNOST = "whsec_testna_skrivnost";
    private static final String STRIPE_ID = "sub_test_1";

    @Autowired UpravljanjeNarocnineStoritev storitev;
    @Autowired PlacilaStoritev placila;
    @Autowired UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired NarocninaRepozitorij narocninaRepozitorij;
    @MockitoBean StripeNarocnine stripe;

    private final TimeZone privzetiPas = TimeZone.getDefault();

    @BeforeEach
    void pripravi() {
        // Stripe vrne stanje, kakrsno ima; v testu je to kar zadnje zapisano
        when(stripe.sprostiUrnik(anyString())).thenReturn(stanje(CiklusPlacila.MESECNO, false, null));
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(true)))
                .thenReturn(stanje(CiklusPlacila.MESECNO, true, null));
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(false)))
                .thenReturn(stanje(CiklusPlacila.MESECNO, false, null));
    }

    @AfterEach
    void pocisti() {
        SecurityContextHolder.clearContext();
        TimeZone.setDefault(privzetiPas);
    }

    // ---------- Pregled ----------

    @Test
    void igralecBrezNarocnineDobiOdgovorBrezNje() {
        prijava(igralec("brez@test"));
        NarocninaDto dto = storitev.pregled();
        assertEquals(Paket.BREZPLACNO, dto.paket());
        assertFalse(dto.aktivna());
        assertFalse(dto.preklicana());
        assertNull(dto.obdobjeDo());
    }

    @Test
    void pregledVrneDatumeInCenoIzZapisa() {
        Uporabnik u = igralec("premium@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setZacetekOb(LocalDateTime.of(2025, 3, 12, 10, 0));
        n.setObdobjeOd(LocalDateTime.of(2026, 9, 12, 10, 0));
        n.setTrenutnoObdobjeDo(LocalDateTime.of(2026, 10, 12, 10, 0));
        narocninaRepozitorij.save(n);
        prijava(u);

        NarocninaDto dto = storitev.pregled();

        assertTrue(dto.aktivna());
        assertFalse(dto.preklicana());
        assertEquals(CiklusPlacila.MESECNO, dto.ciklus());
        assertEquals(4.99, dto.cena());
        assertEquals(LocalDate.of(2025, 3, 12), dto.narocenOd());
        assertEquals(LocalDate.of(2026, 9, 12), dto.obdobjeOd());
        assertEquals(LocalDate.of(2026, 10, 12), dto.obdobjeDo());
    }

    /* Vrstice izpred V35 nimajo zacetka obdobja: izpelje se iz konca in cikla. */
    @Test
    void zacetekObdobjaSeIzpeljeKerGaVrsticaNima() {
        Uporabnik u = igralec("staro@test");
        Narocnina n = premium(u, CiklusPlacila.LETNO, StatusNarocnine.AKTIVNA, true);
        n.setTrenutnoObdobjeDo(LocalDateTime.of(2027, 3, 12, 12, 0));
        narocninaRepozitorij.save(n);
        prijava(u);

        assertEquals(LocalDate.of(2026, 3, 12), storitev.pregled().obdobjeOd());
    }

    /* Konec obdobja, ki je v Sloveniji 12. 10. ob 00.30, je na strezniku v UTC se
       11. 10. Vmesnik steje dneve po koledarju, zato mora dobiti slovenski dan. */
    @Test
    void datumiGredoNavzvenPoSlovenskemCasu() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Uporabnik u = igralec("cas@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setTrenutnoObdobjeDo(LocalDateTime.of(2026, 10, 11, 22, 30)); // 12. 10. 00.30 po CEST
        narocninaRepozitorij.save(n);
        prijava(u);

        assertEquals(LocalDate.of(2026, 10, 12), storitev.pregled().obdobjeDo());
    }

    // ---------- Preklic ----------

    @Test
    void preklicOznaciNarocninoInPustiVeljavnoDoKonca() {
        Uporabnik u = igralec("preklic@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        prijava(u);

        NarocninaDto dto = storitev.prekliciOKoncuObdobja();

        assertTrue(dto.preklicana());
        assertTrue(dto.aktivna(), "preklicana narocnina velja do konca placanega obdobja");
        assertEquals(StatusNarocnine.PREKLICANA, narocninaRepozitorij.findByUporabnikId(u.getId())
                .orElseThrow().getStatus());
        verify(stripe).nastaviPreklicOKoncu(STRIPE_ID, true);
    }

    /* Narocnino z urnikom vodi urnik: preklop se pred preklicem umakne in
       zabelezena namera odpade (po preklicu ni obnove, ki bi jo spremenil). */
    @Test
    void preklicUmakneZabelezenPreklop() {
        Uporabnik u = igralec("preklic-preklop@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);
        prijava(u);

        storitev.prekliciOKoncuObdobja();

        verify(stripe).sprostiUrnik(STRIPE_ID);
        assertNull(narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getNaslednjiCiklus());
    }

    @Test
    void ponovniPreklicNeKlicaStripa() {
        Uporabnik u = igralec("ze-preklicana@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.PREKLICANA, true);
        prijava(u);

        assertTrue(storitev.prekliciOKoncuObdobja().preklicana());
        verifyNoInteractions(stripe);
    }

    @Test
    void zapadleNarocnineNiMogoceSpreminjati() {
        Uporabnik u = igralec("zapadla@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.ZAPADLA, true);
        prijava(u);

        assertThrows(DomenskaIzjema.class, () -> storitev.prekliciOKoncuObdobja());
        verify(stripe, never()).nastaviPreklicOKoncu(anyString(), eq(true));
    }

    // ---------- Obnova ----------

    @Test
    void obnovaUmakneKlicInNicNeStane() {
        Uporabnik u = igralec("obnova@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.PREKLICANA, true);
        prijava(u);

        NarocninaDto dto = storitev.obnovi(null);

        assertFalse(dto.preklicana());
        assertEquals(StatusNarocnine.AKTIVNA, dto.status());
        verify(stripe).nastaviPreklicOKoncu(STRIPE_ID, false);
        verify(stripe, never()).zabeleziPreklop(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    /* Ob obnovi uporabnik izbira placevanje: drugacen cikel se zabelezi kot preklop. */
    @Test
    void obnovaZDrugimCiklomZabeleziPreklop() {
        Uporabnik u = igralec("obnova-letno@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.PREKLICANA, true);
        prijava(u);
        when(stripe.zabeleziPreklop(STRIPE_ID, CiklusPlacila.LETNO, 53.49))
                .thenReturn(stanje(CiklusPlacila.MESECNO, false, "sub_sched_1"));

        NarocninaDto dto = storitev.obnovi(CiklusPlacila.LETNO);

        assertEquals(CiklusPlacila.MESECNO, dto.ciklus(), "cikel se spremeni sele ob obnovi obdobja");
        assertEquals(CiklusPlacila.LETNO, dto.naslednjiCiklus());
    }

    @Test
    void poteklaNarocninaSeNeDaObnoviti() {
        Uporabnik u = igralec("potekla@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.PREKLICANA, true);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().minusDays(1));
        narocninaRepozitorij.save(n);
        prijava(u);

        assertThrows(DomenskaIzjema.class, () -> storitev.obnovi(null));
        verifyNoInteractions(stripe);
    }

    // ---------- Preklop cikla ----------

    /* Cena preklopa je po pasu, s katerim je bila narocnina sklenjena. */
    @Test
    void preklopNaLetnoZaracunaCenoStarejsegaPasu() {
        Uporabnik u = igralec("preklop-starejsi@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        prijava(u);
        when(stripe.zabeleziPreklop(STRIPE_ID, CiklusPlacila.LETNO, 53.49))
                .thenReturn(stanje(CiklusPlacila.MESECNO, false, "sub_sched_1"));

        NarocninaDto dto = storitev.preklopi(CiklusPlacila.LETNO);

        assertEquals(CiklusPlacila.LETNO, dto.naslednjiCiklus());
        assertEquals(CiklusPlacila.MESECNO, dto.ciklus(), "do konca obdobja ostane tekoci cikel");
        verify(stripe).zabeleziPreklop(STRIPE_ID, CiklusPlacila.LETNO, 53.49);
    }

    @Test
    void preklopMlajsegaPasuJeCenejsi() {
        Uporabnik u = igralec("preklop-mlajsi@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, false);
        prijava(u);
        when(stripe.zabeleziPreklop(STRIPE_ID, CiklusPlacila.LETNO, 42.99))
                .thenReturn(stanje(CiklusPlacila.MESECNO, false, "sub_sched_1"));

        storitev.preklopi(CiklusPlacila.LETNO);

        verify(stripe).zabeleziPreklop(STRIPE_ID, CiklusPlacila.LETNO, 42.99);
    }

    @Test
    void preklopNaTekociCiklusUmakneZabelezenPreklop() {
        Uporabnik u = igralec("premislil@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);
        prijava(u);

        NarocninaDto dto = storitev.preklopi(CiklusPlacila.MESECNO);

        assertNull(dto.naslednjiCiklus());
        verify(stripe).sprostiUrnik(STRIPE_ID);
        verify(stripe, never()).zabeleziPreklop(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void ponovljenPreklopNeKlicaStripaZnova() {
        Uporabnik u = igralec("dvakrat@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);
        prijava(u);

        storitev.preklopi(CiklusPlacila.LETNO);

        verify(stripe, never()).zabeleziPreklop(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void preklicanaNarocninaSePrekloputiNeDa() {
        Uporabnik u = igralec("preklop-preklicana@test");
        premium(u, CiklusPlacila.MESECNO, StatusNarocnine.PREKLICANA, true);
        prijava(u);

        assertThrows(DomenskaIzjema.class, () -> storitev.preklopi(CiklusPlacila.LETNO));
    }

    /* Organizatorski paketi so samo letni - preklopa ni. */
    @Test
    void organizatorjevPaketSePrekloputiNeDa() {
        Uporabnik u = organizator("org@test");
        Narocnina n = new Narocnina(u, Paket.ORGANIZATOR_BASIC);
        n.setCiklus(CiklusPlacila.LETNO);
        n.setStatus(StatusNarocnine.AKTIVNA);
        n.setStripeNarocninaId(STRIPE_ID);
        narocninaRepozitorij.save(n);
        prijava(u);

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.preklopi(CiklusPlacila.MESECNO));
    }

    @Test
    void narocninaBrezStripaSeNeDaUpravljati() {
        Uporabnik u = organizator("pro@test");
        Narocnina n = new Narocnina(u, Paket.ORGANIZATOR_PRO);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        prijava(u);

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.prekliciOKoncuObdobja());
        verifyNoInteractions(stripe);
    }

    @Test
    void umikPreklopaSprostiUrnikInPocistiNamero() {
        Uporabnik u = igralec("umik@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);
        prijava(u);

        assertNull(storitev.razveljaviPreklop().naslednjiCiklus());
        verify(stripe).sprostiUrnik(STRIPE_ID);
    }

    // ---------- Organizator: zamenjava paketa ----------

    /* Nadgradnja velja takoj: Stripe zaracuna sorazmerno doplacilo, paket se
       spremeni v vrstici in odgovor pove, koliko je bilo zaracunano. */
    @Test
    void nadgradnjaVVisjiPaketVelaTakojInVrneDoplacilo() {
        Uporabnik u = organizator("nadgradnja@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        prijava(u);
        when(stripe.nadgradiPaket(STRIPE_ID, Paket.ORGANIZATOR_PRO, 249.99))
                .thenReturn(new StripeNarocnine.Nadgradnja(stanjeLetno(249.99, false, null), 3.29));

        SpremembaPaketaDto odgovor = storitev.zamenjajPaket(Paket.ORGANIZATOR_PRO);

        assertEquals(Paket.ORGANIZATOR_PRO, odgovor.narocnina().paket());
        assertEquals(249.99, odgovor.narocnina().cena());
        assertEquals(3.29, odgovor.doplacilo());
        assertNull(odgovor.narocnina().naslednjiPaket());
        assertEquals(Paket.ORGANIZATOR_PRO,
                narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getPaket());
        verify(stripe, never()).zabeleziPrehod(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    /* Zabelezeno znizanje nadgradnja preglasi (Stripe urnik sprosti sam). */
    @Test
    void nadgradnjaPreglasiZabelezenoZnizanje() {
        Uporabnik u = organizator("nadgradnja-znizanje@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.save(n);
        prijava(u);
        when(stripe.nadgradiPaket(STRIPE_ID, Paket.ORGANIZATOR_PRO, 249.99))
                .thenReturn(new StripeNarocnine.Nadgradnja(stanjeLetno(249.99, false, null), 3.29));

        assertNull(storitev.zamenjajPaket(Paket.ORGANIZATOR_PRO).narocnina().naslednjiPaket());
    }

    /* Znizanje danes ne stane nic in ne spremeni paketa: obdobje je placano po
       visji ceni. Zabelezi se samo namera, ki jo Stripe izvede ob obnovi. */
    @Test
    void znizanjeVelaSeleObObnoviInDanesNeStaneNic() {
        Uporabnik u = organizator("znizanje@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        prijava(u);
        when(stripe.zabeleziPrehod(STRIPE_ID, Paket.ORGANIZATOR_BASIC, 89.99))
                .thenReturn(stanjeLetno(169.99, false, "sub_sched_1"));

        SpremembaPaketaDto odgovor = storitev.zamenjajPaket(Paket.ORGANIZATOR_BASIC);

        assertEquals(Paket.ORGANIZATOR_PLUS, odgovor.narocnina().paket(), "do obnove ostane visji paket");
        assertEquals(169.99, odgovor.narocnina().cena());
        assertEquals(Paket.ORGANIZATOR_BASIC, odgovor.narocnina().naslednjiPaket());
        assertNull(odgovor.doplacilo(), "znizanje danes ne zaracuna nicesar");
        verify(stripe, never()).nadgradiPaket(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void izbiraTekocegaPaketaUmakneZabelezenoZnizanje() {
        Uporabnik u = organizator("premislil-paket@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.save(n);
        prijava(u);

        SpremembaPaketaDto odgovor = storitev.zamenjajPaket(Paket.ORGANIZATOR_PLUS);

        assertNull(odgovor.narocnina().naslednjiPaket());
        verify(stripe).sprostiUrnik(STRIPE_ID);
        verify(stripe, never()).zabeleziPrehod(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
        verify(stripe, never()).nadgradiPaket(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void ponovljenoZnizanjeNeKlicaStripaZnova() {
        Uporabnik u = organizator("dvakrat-paket@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.save(n);
        prijava(u);

        storitev.zamenjajPaket(Paket.ORGANIZATOR_BASIC);

        verify(stripe, never()).zabeleziPrehod(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void umikZnizanjaSprostiUrnikInPocistiNamero() {
        Uporabnik u = organizator("umik-paket@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.save(n);
        prijava(u);

        assertNull(storitev.razveljaviPrehod().naslednjiPaket());
        verify(stripe).sprostiUrnik(STRIPE_ID);
    }

    @Test
    void preklicanoNarocninoJeTrebaNajprejObnoviti() {
        Uporabnik u = organizator("paket-preklicana@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.PREKLICANA, 169.99);
        prijava(u);

        assertThrows(DomenskaIzjema.class, () -> storitev.zamenjajPaket(Paket.ORGANIZATOR_PRO));
        verifyNoInteractions(stripe);
    }

    /* Premium igralca ima svoj potek (preklop cikla); paketa ne menja. */
    @Test
    void igralecNeSmeZamenjatiPaketa() {
        Uporabnik u = igralec("igralec-paket@test");
        premium(u, CiklusPlacila.LETNO, StatusNarocnine.AKTIVNA, true);
        prijava(u);

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.zamenjajPaket(Paket.ORGANIZATOR_PRO));
        verifyNoInteractions(stripe);
    }

    @Test
    void zamenjaSeLahkoSamoZOrganizatorskimPaketom() {
        Uporabnik u = organizator("napacen-paket@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        prijava(u);

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.zamenjajPaket(Paket.PREMIUM));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.zamenjajPaket(Paket.BREZPLACNO));
        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.zamenjajPaket(null));
        verifyNoInteractions(stripe);
    }

    @Test
    void brezplacnegaLetaProNiMogoceZamenjati() {
        Uporabnik u = organizator("pro-brez-stripa@test");
        Narocnina n = new Narocnina(u, Paket.ORGANIZATOR_PRO);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        prijava(u);

        assertThrows(NeveljavenVnosIzjema.class, () -> storitev.zamenjajPaket(Paket.ORGANIZATOR_BASIC));
        verifyNoInteractions(stripe);
    }

    @Test
    void preklicOrganizatorjaPocistiZnizanje() {
        Uporabnik u = organizator("preklic-paket@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        narocninaRepozitorij.save(n);
        prijava(u);

        NarocninaDto dto = storitev.prekliciOKoncuObdobja();

        assertTrue(dto.preklicana());
        assertNull(dto.naslednjiPaket(), "po preklicu ni obnove, ki bi jo znizanje spremenilo");
        verify(stripe).sprostiUrnik(STRIPE_ID);
    }

    /* Ob obnovi preklicane narocnine organizator izbira paket. Obdobje je ze
       placano, zato je tudi visji paket prehod ob obnovi in ne takojsnja
       nadgradnja. */
    @Test
    void obnovaPreklicaneZDrugimPaketomZabeleziPrehodObObnovi() {
        Uporabnik u = organizator("obnova-paket@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.PREKLICANA, 169.99);
        prijava(u);
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(false)))
                .thenReturn(stanjeLetno(169.99, false, null));
        when(stripe.zabeleziPrehod(STRIPE_ID, Paket.ORGANIZATOR_PRO, 249.99))
                .thenReturn(stanjeLetno(169.99, false, "sub_sched_1"));

        NarocninaDto dto = storitev.obnovi(null, Paket.ORGANIZATOR_PRO);

        assertFalse(dto.preklicana());
        assertEquals(Paket.ORGANIZATOR_PLUS, dto.paket(), "novi paket zacne z naslednjim obdobjem");
        assertEquals(Paket.ORGANIZATOR_PRO, dto.naslednjiPaket());
        verify(stripe, never()).nadgradiPaket(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void obnovaBrezSpremembePaketaNeKlicePrehoda() {
        Uporabnik u = organizator("obnova-isti-paket@test");
        organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.PREKLICANA, 169.99);
        prijava(u);
        when(stripe.nastaviPreklicOKoncu(anyString(), eq(false)))
                .thenReturn(stanjeLetno(169.99, false, null));

        NarocninaDto dto = storitev.obnovi(null, Paket.ORGANIZATOR_PLUS);

        assertNull(dto.naslednjiPaket());
        verify(stripe, never()).zabeleziPrehod(anyString(), org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    /* Brezplacno leto Pro obstojecih organizatorjev nima Stripa: vmesnik po tem
       polju skrije dejanja, ki bi zavrnila. */
    @Test
    void pregledPoveAliNarocninoVodiStripe() {
        Uporabnik s = organizator("s-stripom@test");
        organizatorska(s, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        prijava(s);
        assertTrue(storitev.pregled().upravljiva());

        Uporabnik brez = organizator("brez-stripa@test");
        Narocnina n = new Narocnina(brez, Paket.ORGANIZATOR_PRO);
        n.setStatus(StatusNarocnine.AKTIVNA);
        narocninaRepozitorij.save(n);
        prijava(brez);
        assertFalse(storitev.pregled().upravljiva());
    }

    // ---------- Webhook: isto stanje, druga pot ----------

    /* Ob izvedenem preklopu Stripe zamenja postavko urnika: cikel in cena se
       prepisejo, zabelezena namera odpade in obdobje se zamakne. */
    @Test
    void webhookOPreklopuPrepiseCikelInPocistiNamero() {
        Uporabnik u = igralec("webhook-preklop@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 5349, "sub_sched_1",
                1760263200L, 1791799200L);
        placila.obdelajDogodek(telo, podpisi(telo));

        Narocnina po = narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow();
        assertEquals(CiklusPlacila.LETNO, po.getCiklus());
        assertEquals(53.49, po.getCenaObSklenitvi());
        assertNull(po.getNaslednjiCiklus(), "preklop je izveden, namera odpade");
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(1760263200L), ZoneId.systemDefault()),
                po.getObdobjeOd());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(1791799200L), ZoneId.systemDefault()),
                po.getTrenutnoObdobjeDo());
    }

    /* Urnik po izvedenem preklopu ostane prikljucen se cel cikel (Stripe, testni
       nacin) in prikljucen urnik vodi narocnino - sprosti se takoj, ko je preklop
       izveden. */
    @Test
    void webhookOIzvedenemPreklopuSprostiUrnik() {
        Uporabnik u = igralec("webhook-sprosti@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 5349, "sub_sched_1",
                1760263200L, 1791799200L);
        placila.obdelajDogodek(telo, podpisi(telo));

        verify(stripe).sprostiUrnik(STRIPE_ID);
    }

    /* Neuspela sprostitev ne sme podreti webhooka: stanje je zapisano, urnik pa
       sprosti naslednji preklop ali preklic sam. */
    @Test
    void neuspelaSprostitevUrnikaNePodreWebhooka() {
        Uporabnik u = igralec("webhook-sprosti-napaka@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);
        when(stripe.sprostiUrnik(STRIPE_ID)).thenThrow(new DomenskaIzjema("Stripe ne odgovarja"));

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 5349, "sub_sched_1",
                1760263200L, 1791799200L);
        placila.obdelajDogodek(telo, podpisi(telo));

        assertEquals(CiklusPlacila.LETNO,
                narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getCiklus());
    }

    /* Dokler urnik obstaja in cikel se ni zamenjan, zabelezena namera ostane. */
    @Test
    void webhookMedCakanjemNaPreklopNamereNePocisti() {
        Uporabnik u = igralec("webhook-caka@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);

        String telo = dogodekPosodobitve(STRIPE_ID, false, "month", 499, "sub_sched_1",
                1757671200L, 1760263200L);
        placila.obdelajDogodek(telo, podpisi(telo));

        assertEquals(CiklusPlacila.LETNO,
                narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getNaslednjiCiklus());
        // urnik, ki ga je Stripe ravnokar ustvaril, se ne sme sprostiti: odnesel bi preklop
        verify(stripe, never()).sprostiUrnik(anyString());
    }

    /* Urnika ni vec (uporabnik je preklop umaknil): namera odpade tudi, ce je
       ostala zapisana, ker je klic do nas zamudil. */
    @Test
    void webhookBrezUrnikaPocistiNamero() {
        Uporabnik u = igralec("webhook-umik@test");
        Narocnina n = premium(u, CiklusPlacila.MESECNO, StatusNarocnine.AKTIVNA, true);
        n.setNaslednjiCiklus(CiklusPlacila.LETNO);
        narocninaRepozitorij.save(n);

        String telo = dogodekPosodobitve(STRIPE_ID, false, "month", 499, null,
                1757671200L, 1760263200L);
        placila.obdelajDogodek(telo, podpisi(telo));

        assertNull(narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getNaslednjiCiklus());
    }


    // ---------- Webhook: znizanje paketa ----------

    private static final long OBDOBJE_DO = 1791799200L;
    private static final long LETO = 365L * 24 * 3600;

    private Narocnina znizanjeVCakanju(String prijavnoIme) {
        Uporabnik u = organizator(prijavnoIme);
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setTrenutnoObdobjeDo(LocalDateTime.ofInstant(Instant.ofEpochSecond(OBDOBJE_DO), ZoneId.systemDefault()));
        n.setNaslednjiPaket(Paket.ORGANIZATOR_BASIC);
        return narocninaRepozitorij.save(n);
    }

    /* Urnik ob obnovi zamenja postavko in Stripe isti dogodek poslje z novim
       obdobjem: znizanje je izvedeno, paket se zamenja in urnik sprosti. */
    @Test
    void webhookNaMejiObdobjaUveljaviZnizanjePaketa() {
        Narocnina n = znizanjeVCakanju("webhook-znizanje@test");

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 8999, "sub_sched_1",
                OBDOBJE_DO, OBDOBJE_DO + LETO);
        placila.obdelajDogodek(telo, podpisi(telo));

        Narocnina po = narocninaRepozitorij.findByUporabnikId(n.getUporabnik().getId()).orElseThrow();
        assertEquals(Paket.ORGANIZATOR_BASIC, po.getPaket());
        assertNull(po.getNaslednjiPaket(), "znizanje je izvedeno, namera odpade");
        assertEquals(89.99, po.getCenaObSklenitvi());
        verify(stripe).sprostiUrnik(STRIPE_ID);
    }

    /* Dokler obdobje traja, dogodek (npr. Stripov urnik, ki je ravnokar nastal)
       znizanja ne izvede in ne pobere zabelezene namere. */
    @Test
    void webhookMedObdobjemZnizanjaNeIzvede() {
        Narocnina n = znizanjeVCakanju("webhook-znizanje-caka@test");

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 16999, "sub_sched_1",
                OBDOBJE_DO - LETO, OBDOBJE_DO);
        placila.obdelajDogodek(telo, podpisi(telo));

        Narocnina po = narocninaRepozitorij.findByUporabnikId(n.getUporabnik().getId()).orElseThrow();
        assertEquals(Paket.ORGANIZATOR_PLUS, po.getPaket());
        assertEquals(Paket.ORGANIZATOR_BASIC, po.getNaslednjiPaket());
        verify(stripe, never()).sprostiUrnik(anyString());
    }

    /* Urnika ni vec (uporabnik je znizanje umaknil): namera odpade tudi, ce je
       ostala zapisana, ker je klic do nas zamudil. */
    @Test
    void webhookBrezUrnikaPocistiZnizanje() {
        Narocnina n = znizanjeVCakanju("webhook-znizanje-umik@test");

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 16999, null,
                OBDOBJE_DO - LETO, OBDOBJE_DO);
        placila.obdelajDogodek(telo, podpisi(telo));

        Narocnina po = narocninaRepozitorij.findByUporabnikId(n.getUporabnik().getId()).orElseThrow();
        assertEquals(Paket.ORGANIZATOR_PLUS, po.getPaket());
        assertNull(po.getNaslednjiPaket());
    }

    /* Navadna obnova brez zabelezenega znizanja paketa ne spremeni. */
    @Test
    void webhookOObnoviBrezZnizanjaPaketaNeSpremeni() {
        Uporabnik u = organizator("webhook-obnova@test");
        Narocnina n = organizatorska(u, Paket.ORGANIZATOR_PLUS, StatusNarocnine.AKTIVNA, 169.99);
        n.setTrenutnoObdobjeDo(LocalDateTime.ofInstant(Instant.ofEpochSecond(OBDOBJE_DO), ZoneId.systemDefault()));
        narocninaRepozitorij.save(n);

        String telo = dogodekPosodobitve(STRIPE_ID, false, "year", 16999, null,
                OBDOBJE_DO, OBDOBJE_DO + LETO);
        placila.obdelajDogodek(telo, podpisi(telo));

        assertEquals(Paket.ORGANIZATOR_PLUS,
                narocninaRepozitorij.findByUporabnikId(u.getId()).orElseThrow().getPaket());
    }

    // ---------- Pomozno ----------

    private static StripeNarocnine.Stanje stanje(CiklusPlacila ciklus, boolean preklic, String urnik) {
        return new StripeNarocnine.Stanje(
                LocalDateTime.of(2026, 9, 12, 10, 0),
                LocalDateTime.of(2026, 10, 12, 10, 0),
                ciklus, ciklus == CiklusPlacila.LETNO ? 53.49 : 4.99, preklic, urnik);
    }

    private static StripeNarocnine.Stanje stanjeLetno(double cena, boolean preklic, String urnik) {
        return new StripeNarocnine.Stanje(
                LocalDateTime.now().minusDays(350), LocalDateTime.now().plusDays(15),
                CiklusPlacila.LETNO, cena, preklic, urnik);
    }

    private Narocnina organizatorska(Uporabnik u, Paket paket, StatusNarocnine status, double cena) {
        Narocnina n = new Narocnina(u, paket);
        n.setCiklus(CiklusPlacila.LETNO);
        n.setStatus(status);
        n.setCenaObSklenitvi(cena);
        n.setStripeNarocninaId(STRIPE_ID);
        n.setObdobjeOd(LocalDateTime.now().minusDays(350));
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(15));
        return narocninaRepozitorij.save(n);
    }

    private Uporabnik igralec(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(StatusRacuna.CAKA);
        return uporabnikRepozitorij.save(u);
    }

    private Uporabnik organizator(String prijavnoIme) {
        Uporabnik u = new Uporabnik(prijavnoIme, "{bcrypt}x", Vloga.ORGANIZATOR);
        u.setStatus(StatusRacuna.POTRJEN);
        return uporabnikRepozitorij.save(u);
    }

    private Narocnina premium(Uporabnik u, CiklusPlacila ciklus, StatusNarocnine status, boolean starejsiOd21) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setCiklus(ciklus);
        n.setStatus(status);
        n.setStarejsiOd21(starejsiOd21);
        n.setCenaObSklenitvi(ciklus == CiklusPlacila.LETNO ? 53.49 : 4.99);
        n.setStripeNarocninaId(STRIPE_ID);
        n.setTrenutnoObdobjeDo(LocalDateTime.now().plusDays(18));
        return narocninaRepozitorij.save(n);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getVloga().name()))));
    }

    private static String podpisi(String telo) {
        long trenutek = Instant.now().getEpochSecond();
        String podpisano = trenutek + "." + telo;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SKRIVNOST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String podpis = HexFormat.of().formatHex(mac.doFinal(podpisano.getBytes(StandardCharsets.UTF_8)));
            return "t=" + trenutek + ",v1=" + podpis;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /* customer.subscription.updated z eno postavko (oblika, ki jo poslje Stripe
       od "flexible billing mode": obdobje je na postavki, ne na narocnini). */
    private static String dogodekPosodobitve(String idNarocnine, boolean preklicOKoncu, String interval,
            int centov, String urnik, long obdobjeOd, long obdobjeDo) {
        return """
                {
                  "id": "evt_upd",
                  "object": "event",
                  "type": "customer.subscription.updated",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "subscription",
                      "cancel_at_period_end": %s,
                      "schedule": %s,
                      "items": {
                        "object": "list",
                        "data": [
                          {
                            "id": "si_1",
                            "object": "subscription_item",
                            "current_period_start": %d,
                            "current_period_end": %d,
                            "price": {
                              "id": "price_1",
                              "object": "price",
                              "unit_amount": %d,
                              "currency": "eur",
                              "recurring": { "interval": "%s", "interval_count": 1 }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(idNarocnine, preklicOKoncu, urnik == null ? "null" : "\"" + urnik + "\"",
                obdobjeOd, obdobjeDo, centov, interval);
    }
}
