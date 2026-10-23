/* Obvestilo po kolu (V50): ko je kolo lige odigrano in izidi vpisani, gre
   racunom igralcev lige in tistim, ki ligo spremljajo, ena e-posta - z izidi,
   lestvico, igralcu pa tudi z njegovimi tekmami. Vsako kolo gre ven najvec
   enkrat, ne med vnosom vecera in ne za staro kolo.

   Cas vnosa (odigran_ob) zapise vnos sam (zdaj po slovenskem casu), zato testi
   "zdaj" opravila podajo glede na ta trenutek. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.ObvestilaDto;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Narocnina;
import si.turnirko.modeli.ObvestiloKola;
import si.turnirko.modeli.Paket;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.SpremljanaLiga;
import si.turnirko.modeli.StatusNarocnine;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.posta.PomnilniskiPosiljatelj;
import si.turnirko.repozitoriji.NarocninaRepozitorij;
import si.turnirko.repozitoriji.ObvestiloKolaRepozitorij;
import si.turnirko.repozitoriji.SpremljanaLigaRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class ObvestilaPoKoluTest extends IntegracijskiTest {

    private static final ZoneId SLOVENIJA = ZoneId.of("Europe/Ljubljana");

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private ObvestilaPoKoluStoritev obvestila;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @Autowired private NarocninaRepozitorij narocninaRepozitorij;
    @Autowired private SpremljanaLigaRepozitorij spremljanaLigaRepozitorij;
    @Autowired private ObvestiloKolaRepozitorij obvestiloKolaRepozitorij;
    @Autowired private PomnilniskiPosiljatelj posta;

    @BeforeEach
    void pripravi() {
        posta.pocisti();
    }

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

    /* Glavna pot: igralec brez Premium dobi izid svoje ekipe v zadevi, svoje
       tekme, rating in vrstico o Premium; spremljevalec izide in lestvico brez
       osebnega dela. Glava odjave z enim klikom je v obeh. Drugic nic. */
    @Test
    void odigranoKoloGreIgralcuInSpremljevalcuEnkrat() {
        Liga l = pripraviLigo(2, true, LocalDateTime.now(SLOVENIJA).minusDays(1));
        Uporabnik igralec = racun("ana@test.si", l.kadri().get(0).get(0));
        Uporabnik spremlja = racun("gledalec@test.si", null);
        spremljanaLigaRepozitorij.save(new SpremljanaLiga(spremlja.getId(), l.id()));

        odigraj(l, srecanjaKola(l, 1).get(0), "3:1", "3:0", "3:2", "1:3", "0:3");
        assertEquals(2, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));

        PomnilniskiPosiljatelj.Sporocilo zanj = posta.zadnje("ana@test.si").orElseThrow();
        assertTrue(zanj.zadeva().startsWith("Savinja test, 1. kolo: "), zanj.zadeva());
        assertTrue(zanj.besedilo().contains("Tvoje tekme"), zanj.besedilo());
        assertTrue(zanj.besedilo().contains("Tvoj rating"), zanj.besedilo());
        assertTrue(zanj.besedilo().contains("S Premium"), "igralcu brez paketa vrstica o Premium");
        assertTrue(zanj.besedilo().contains("ker igraš v ligi Savinja test"));
        assertTrue(zanj.besedilo().contains("/lige/" + l.id() + "?ekipa="), "povezava na njegovo ekipo");
        assertNotNull(zanj.html());
        assertTrue(zanj.glave().get("List-Unsubscribe").contains("/api/v1/obvestila/odjava?z="));
        assertEquals("List-Unsubscribe=One-Click", zanj.glave().get("List-Unsubscribe-Post"));

        PomnilniskiPosiljatelj.Sporocilo gledalcu = posta.zadnje("gledalec@test.si").orElseThrow();
        assertEquals("Savinja test: izidi 1. kola", gledalcu.zadeva());
        assertFalse(gledalcu.besedilo().contains("Tvoje tekme"));
        assertFalse(gledalcu.besedilo().contains("S Premium"));
        assertTrue(gledalcu.besedilo().contains("ker spremljaš ligo"));

        posta.pocisti();
        assertEquals(0, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(5)), "kolo gre ven enkrat");
    }

    /* Liga z urami kola (V31): ekipa v kolu odigra dve srecanji. Igralec dobi
       tekme obeh, zadeva pa oba izida z vidika ekipe. */
    @Test
    void vecerZDvemaSrecanjemaDaObaIzida() {
        Liga l = pripraviLigo(4, false, LocalDateTime.now(SLOVENIJA).minusDays(1).withHour(18).withMinute(30),
                List.of(LocalTime.of(18, 30), LocalTime.of(19, 45)));
        racun("ana@test.si", l.kadri().get(0).get(0));
        List<SrecanjeDto> kolo1 = srecanjaKola(l, 1);
        assertEquals(4, kolo1.size());
        for (SrecanjeDto s : kolo1) {
            odigraj(l, s, "3:1", "3:0", "3:2", "1:3", "0:3");
        }
        assertEquals(1, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));

        PomnilniskiPosiljatelj.Sporocilo zanj = posta.zadnje("ana@test.si").orElseThrow();
        assertTrue(zanj.zadeva().matches("Savinja test, 1\\. kolo: Klub A[^·]* · \\d : \\d, \\d : \\d"), zanj.zadeva());
        long tekem = zanj.besedilo().lines().filter(v -> v.startsWith("  zmaga ") || v.startsWith("  poraz ")).count();
        assertEquals(6, tekem, "dvojice in dve posamicni tekmi v vsakem od obeh srecanj");
    }

    /* Med vnosom vecera (manj kot MIRUJE po zadnjem izidu) in za staro kolo
       (vec kot SVEZE) obvestila ni. */
    @Test
    void neMedVnosomInNeZaStaroKolo() {
        Liga l = pripraviLigo(2, true, LocalDateTime.now(SLOVENIJA).minusDays(1));
        racun("ana@test.si", l.kadri().get(0).get(0));
        odigraj(l, srecanjaKola(l, 1).get(0), "3:1", "3:0", "3:2", "1:3", "0:3");

        assertEquals(0, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusMinutes(30)));
        assertEquals(0, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusDays(8)));
        assertTrue(posta.vsa().isEmpty());
    }

    /* Kolo z nevpisanim srecanjem pocaka; ko je od dneva kola minilo
       DNI_ZA_DELNO dni in je vpisana vsaj polovica, gre z opombo. */
    @Test
    void delnoKoloPocakaNatoGreZOpombo() {
        Liga l = pripraviLigo(4, false, LocalDateTime.now(SLOVENIJA).minusDays(1));
        racun("ana@test.si", l.kadri().get(0).get(0));
        List<SrecanjeDto> kolo1 = srecanjaKola(l, 1);
        assertEquals(2, kolo1.size());
        odigraj(l, kolo1.get(0), "3:1", "3:0", "3:2", "1:3", "0:3");

        assertEquals(0, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));
        assertEquals(1, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusDays(3)));
        assertTrue(posta.zadnje("ana@test.si").orElseThrow().besedilo().contains("izid še ni vpisan"));
    }

    /* Kolo, ki se dopolni za novejsim (vpis za nazaj), ni vec novica. */
    @Test
    void starejseKoloZaNovejsimSePreskoci() {
        Liga l = pripraviLigo(2, true, LocalDateTime.now(SLOVENIJA).minusDays(10));
        racun("ana@test.si", l.kadri().get(0).get(0));

        odigraj(l, srecanjaKola(l, 2).get(0), "3:1", "3:0", "3:2", "1:3", "0:3");
        assertEquals(1, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));
        assertTrue(posta.zadnje("ana@test.si").orElseThrow().zadeva().contains("2. kolo"));

        posta.pocisti();
        odigraj(l, srecanjaKola(l, 1).get(0), "3:0", "3:0", "3:0", "3:0", "3:0");
        assertEquals(0, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));
        ObvestiloKola prvo = obvestiloKolaRepozitorij.findById(new ObvestiloKola.Kljuc(l.id(), 1)).orElseThrow();
        assertTrue(prvo.isPreskoceno());
    }

    /* Kdo ne dobi nic ali dobi brez vrstice o Premium: izklopljena obvestila,
       nepotrjen naslov, otrok (brez vrstice), placnik (brez vrstice), preizkus
       (datum konca). */
    @Test
    void prejemnikiInVrsticaOPremium() {
        Liga l = pripraviLigo(2, true, LocalDateTime.now(SLOVENIJA).minusDays(1));
        List<Igralec> a = l.kadri().get(0);
        List<Igralec> b = l.kadri().get(1);
        racun("izklop@test.si", a.get(0)).setObvestilaPoKolu(false);
        racun("nepotrjen@test.si", a.get(1)).setEmailPotrjenOb(null);
        Uporabnik otrok = racun("otrok@test.si", a.get(2));
        otrok.setEmailSkrbnika("stars@test.si");
        otrok.setSkrbnikPotrjenOb(LocalDateTime.now());
        narocnina(racun("placnik@test.si", b.get(0)), StatusNarocnine.AKTIVNA, null);
        narocnina(racun("preizkus@test.si", b.get(1)), StatusNarocnine.PREIZKUS,
                LocalDateTime.now().plusDays(20));

        odigraj(l, srecanjaKola(l, 1).get(0), "3:1", "3:0", "3:2", "1:3", "0:3");
        assertEquals(3, obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3)));

        assertTrue(posta.zadnje("izklop@test.si").isEmpty());
        assertTrue(posta.zadnje("nepotrjen@test.si").isEmpty());
        assertFalse(posta.zadnje("otrok@test.si").orElseThrow().besedilo().contains("Premium"));
        assertFalse(posta.zadnje("placnik@test.si").orElseThrow().besedilo().contains("Premium"));
        assertTrue(posta.zadnje("preizkus@test.si").orElseThrow().besedilo()
                .contains("Premium imaš brez plačila do"));
    }

    /* Odjava iz e-poste z zetonom (brez prijave) obvestila izklopi; neznan
       zeton je 404. Nastavitev racuna nato pove, da so izklopljena, in jih
       vklopi nazaj. */
    @Test
    void odjavaZZetonomInNastavitev() {
        Liga l = pripraviLigo(2, true, LocalDateTime.now(SLOVENIJA).minusDays(1));
        Uporabnik ana = racun("ana@test.si", l.kadri().get(0).get(0));
        odigraj(l, srecanjaKola(l, 1).get(0), "3:1", "3:0", "3:2", "1:3", "0:3");
        obvestila.poslji(LocalDateTime.now(SLOVENIJA).plusHours(3));
        String zeton = ana.getZetonOdjave();
        assertNotNull(zeton);
        assertTrue(posta.zadnje("ana@test.si").orElseThrow().besedilo().contains("z=" + zeton));

        assertThrows(NiNajdenoIzjema.class, () -> obvestila.odjava("ni-tak"));
        obvestila.odjava(zeton);
        assertFalse(ana.isObvestilaPoKolu());

        prijava(ana);
        ObvestilaDto stanje = obvestila.nastavitve();
        assertFalse(stanje.poKolu());
        assertEquals(List.of(l.id()), stanje.lige().stream().map(ObvestilaDto.Liga::id).toList());
        assertEquals("IGRA", stanje.lige().get(0).razlog());
        assertTrue(obvestila.nastavi(true).poKolu());
    }

    // ---------- Pomozno ----------

    private record Liga(Long id, List<Long> ekipe, List<List<Igralec>> kadri) {}

    /* Savinja liga z danim stevilom klubskih ekip, vsaka s tremi igralci
       (rating 1500, da prvi dan ne tece uvrstitev novinca). */
    private Liga pripraviLigo(int ekip, boolean dvokrozno, LocalDateTime zacetek) {
        return pripraviLigo(ekip, dvokrozno, zacetek, null);
    }

    private Liga pripraviLigo(int ekip, boolean dvokrozno, LocalDateTime zacetek, List<LocalTime> ure) {
        LigaVnos v = new LigaVnos("Savinja test", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, dvokrozno, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, zacetek, 7, null, null, ure);
        Long liga = ligaStoritev.ustvari(v).id();
        List<Long> ekipe = new ArrayList<>();
        List<List<Igralec>> kadri = new ArrayList<>();
        for (int e = 0; e < ekip; e++) {
            String ime = "Klub " + (char) ('A' + e);
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            ekipe.add(ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null)).id());
            List<Igralec> kader = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Igralec ig = noviIgralec("Ig" + (char) ('A' + e) + i, "Pri" + (char) ('A' + e) + i);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ligaStoritev.dodajVKader(ekipe.get(e), new KaderVnos(ig.getId(), i));
                kader.add(ig);
            }
            kadri.add(kader);
        }
        ligaStoritev.generirajRazpored(liga);
        return new Liga(liga, ekipe, kadri);
    }

    private List<SrecanjeDto> srecanjaKola(Liga l, int kolo) {
        return srecanjeStoritev.zaLigo(l.id()).stream().filter(s -> s.kolo() == kolo).toList();
    }

    /* Postava (prva dva iz kadra vsake ekipe) in vseh pet tekem srecanja. */
    private void odigraj(Liga l, SrecanjeDto s, String... izidi) {
        List<Igralec> domaci = l.kadri().get(l.ekipe().indexOf(s.idEkipaDomaci()));
        List<Igralec> gost = l.kadri().get(l.ekipe().indexOf(s.idEkipaGost()));
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
        List<Long> kaderDomaci = p.kaderDomaci().stream().map(KaderIgralecDto::idIgralec).toList();
        assertTrue(kaderDomaci.contains(domaci.get(0).getId()));
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    domaci.get(i).getId(), true));
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    gost.get(i).getId(), true));
        }
        srecanjeStoritev.nastaviPostavo(s.id(), new PostavaVnos(mesta));
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(s.id()).tekme();
        for (int i = 0; i < izidi.length; i++) {
            String[] n = izidi[i].split(":");
            srecanjeStoritev.vnesiRezultat(tekme.get(i).id(), new VnosRezultataSrecanja(
                    null, Integer.parseInt(n[0]), Integer.parseInt(n[1]), null, null));
        }
    }

    /* Potrjen racun s potrjenim naslovom; igralec je lahko prazen
       (spremljevalec brez zapisa). */
    private Uporabnik racun(String naslov, Igralec igralec) {
        Uporabnik u = new Uporabnik(naslov, "{bcrypt}x", Vloga.IGRALEC);
        u.setStatus(igralec == null ? StatusRacuna.CAKA : StatusRacuna.POTRJEN);
        u.setIgralec(igralec);
        u.setEmailPotrjenOb(LocalDateTime.now());
        return uporabnikRepozitorij.save(u);
    }

    private void narocnina(Uporabnik u, StatusNarocnine status, LocalDateTime do_) {
        Narocnina n = new Narocnina(u, Paket.PREMIUM);
        n.setStatus(status);
        n.setTrenutnoObdobjeDo(do_);
        narocninaRepozitorij.save(n);
    }

    private void prijava(Uporabnik u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getUporabniskoIme(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_IGRALEC"))));
    }
}
