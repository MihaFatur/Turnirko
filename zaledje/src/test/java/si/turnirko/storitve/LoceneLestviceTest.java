/* Testi locenih lestvic: moski / zenske in tekmovalci / rekreativci.

   Zakaj sploh: med spoloma ni niti ene obracunane tekme (0 od 91.741 uvozenih),
   zato sta skali neprimerljivi in skupno mesto ne pomeni nicesar. Rekreativci
   so loceni, ker ni posteno, da dober rekreativec prehiti nekoliko slabsega
   igralca, ki hodi na clanske turnirje NTZS.

   Stetje tekmovalnih tekem je ena poizvedba cez vse tri vire dnevnika
   (turnir, liga, ekipni turnir), ki mora brati samo pokrivni indeks - oboje
   varujejo testi spodaj. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;

class LoceneLestviceTest extends IntegracijskiTest {

    @Autowired private RekreativecStoritev rekreativecStoritev;
    @Autowired private StatistikaStoritev statistikaStoritev;
    @Autowired private ZrebStoritev zreb;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private EkipeDogodkaStoritev ekipeDogodka;
    @Autowired private DataSource podatkovniVir;

    private Optional<Tekma> pripravljena(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst();
    }

    /* Odigra CEL dogodek dane ravni; zmagovalec vsake tekme je prvi igralec,
       zato prvi prijavljeni zmaga mrezo in odigra log2(N) tekem. */
    private Dogodek odigrajCelDogodek(int steviloIgralcev, RavenTekmovanja raven) {
        Dogodek dogodek = pripraviDogodek(steviloIgralcev);
        Turnir turnir = dogodek.getTurnir();
        turnir.setRaven(raven);
        turnirRepozitorij.save(turnir);
        zreb.izvediZreb(dogodek.getId());
        for (Optional<Tekma> t = pripravljena(dogodek.getId());
                t.isPresent(); t = pripravljena(dogodek.getId())) {
            tekmaStoritev.vnesiRezultat(t.get().getId(), new VnosRezultata(null, 3, 0, null, null));
        }
        return dogodek;
    }

    /* Kolikokrat je igralec nastopil - po dnevniku ratinga. */
    private int nastopov(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(),
                        si.turnirko.modeli.RatingStanje.SISTEM_TURNIRKO)
                .map(si.turnirko.modeli.RatingStanje::getStTekem).orElse(0);
    }

    /* Zmagovalec mreze osmih na uradnem turnirju odigra tri tekme in s tem
       preneha biti rekreativec. */
    @Test
    void triTekmeNaUradnemTekmovanjuNaredijoTekmovalca() {
        Dogodek dogodek = odigrajCelDogodek(8, RavenTekmovanja.URADNO);
        Igralec prvak = tekmeDogodka(dogodek.getId()).stream()
                .max((a, b) -> Integer.compare(a.getKolo(), b.getKolo()))
                .orElseThrow().getZmagovalec().getIgralec();

        assertEquals(3, nastopov(prvak), "v mrezi osmih prvak odigra tri tekme");
        assertFalse(rekreativecStoritev.rekreativci(List.of(prvak.getId()))
                        .contains(prvak.getId()),
                "tri tekme na uradnem tekmovanju naredijo tekmovalca");
    }

    /* Kdor je odigral samo rekreativne tekme, ostane rekreativec - ne glede na
       to, koliko jih je. */
    @Test
    void samoRekreativneTekmeNeNaredijoTekmovalca() {
        Dogodek dogodek = odigrajCelDogodek(8, RavenTekmovanja.REKREATIVNO);
        Igralec prvak = tekmeDogodka(dogodek.getId()).stream()
                .max((a, b) -> Integer.compare(a.getKolo(), b.getKolo()))
                .orElseThrow().getZmagovalec().getIgralec();

        assertEquals(3, nastopov(prvak));
        assertTrue(rekreativecStoritev.rekreativci(List.of(prvak.getId()))
                        .contains(prvak.getId()),
                "rekreativne tekme v prag ne stejejo, pa jih je bilo tri");
    }

    /* Brez tekem je vsak rekreativec - prag se ne izpolni sam. */
    @Test
    void brezTekemJeVsakRekreativec() {
        Igralec igralec = noviIgralec("Brez", "Tekem");
        Set<Long> rekreativci = rekreativecStoritev.rekreativci(List.of(igralec.getId()));
        assertTrue(rekreativci.contains(igralec.getId()));
    }

    /* Lestvica nosi oznako rekreativca in loci spol - vmesnik iz tega sestavi
       dve loceni lestvici. */
    @Test
    void lestvicaNosiSpolInOznakoRekreativca() {
        Dogodek dogodek = odigrajCelDogodek(8, RavenTekmovanja.URADNO);
        Igralec prvak = tekmeDogodka(dogodek.getId()).stream()
                .max((a, b) -> Integer.compare(a.getKolo(), b.getKolo()))
                .orElseThrow().getZmagovalec().getIgralec();

        Igralec igralka = noviIgralec("Zenska", "Igralka", Spol.ZENSKI);
        igralka.setDatumRojstva(LocalDate.of(2000, 1, 1));
        igralecRepozitorij.save(igralka);

        List<LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica();

        LestvicaIgralcaDto vrsticaPrvaka = lestvica.stream()
                .filter(v -> v.idIgralca().equals(prvak.getId())).findFirst().orElseThrow();
        assertEquals(Spol.MOSKI, vrsticaPrvaka.spol());
        assertFalse(vrsticaPrvaka.rekreativec(), "prvak uradnega turnirja je tekmovalec");

        LestvicaIgralcaDto vrsticaIgralke = lestvica.stream()
                .filter(v -> v.idIgralca().equals(igralka.getId())).findFirst().orElseThrow();
        assertEquals(Spol.ZENSKI, vrsticaIgralke.spol());
        assertTrue(vrsticaIgralke.rekreativec(), "igralka brez tekem je rekreativka");
    }

    /* Tekme se stejejo iz vseh treh virov dnevnika: turnirske tekme, posamicne
       tekme lig in posamicne tekme ekipnih turnirjev (tam je raven last
       turnirja, srecanje pa lige nima). Igralec odigra v vsakem viru po eno
       tekmo - ce bi kateri vir izpadel ali stel dvakrat, bi prag treh tekem
       padel na napacnem koraku. */
    @Test
    void tekmovalneTekmeSeSestejejoIzVsehTrehVirov() {
        Dogodek dogodek = odigrajCelDogodek(2, RavenTekmovanja.URADNO);
        Igralec igralec = tekmeDogodka(dogodek.getId()).get(0).getPrijava1().getIgralec();
        assertTrue(jeRekreativec(igralec), "ena turnirska tekma je premalo");

        ligaSTekmo(igralec);
        assertTrue(jeRekreativec(igralec), "turnirska in ligaska tekma sta sele dve");

        ekipniTurnirSTekmo(igralec);
        assertEquals(3, nastopov(igralec), "v vsakem viru natanko ena tekma");
        assertFalse(jeRekreativec(igralec), "tretja tekma, na ekipnem turnirju, naredi tekmovalca");
    }

    /* Raven je last tekmovanja, zato poznejsa sprememba ravni velja takoj in
       brez preracuna - prav zato zastavica rekreativca ni shranjena. Velja za
       ligo in za ekipni turnir, ki raven bere po drugi poti (srecanje -> tekma
       -> turnir). */
    @Test
    void poznejsaSpremembaRavniVeljaBrezPreracuna() {
        Dogodek dogodek = odigrajCelDogodek(2, RavenTekmovanja.URADNO);
        Igralec igralec = tekmeDogodka(dogodek.getId()).get(0).getPrijava1().getIgralec();
        Long idLige = ligaSTekmo(igralec);
        Long idEkipnega = ekipniTurnirSTekmo(igralec);
        assertFalse(jeRekreativec(igralec));

        Liga liga = ligaRepozitorij.findById(idLige).orElseThrow();
        liga.setRaven(RavenTekmovanja.REKREATIVNO);
        ligaRepozitorij.saveAndFlush(liga);
        assertTrue(jeRekreativec(igralec), "rekreativna liga v prag ne steje vec");

        liga.setRaven(RavenTekmovanja.URADNO);
        ligaRepozitorij.saveAndFlush(liga);
        Turnir ekipni = turnirRepozitorij.findById(idEkipnega).orElseThrow();
        ekipni.setRaven(RavenTekmovanja.REKREATIVNO);
        turnirRepozitorij.saveAndFlush(ekipni);
        assertTrue(jeRekreativec(igralec), "rekreativni ekipni turnir v prag ne steje vec");
    }

    /* Stetje tece ob vsakem ogledu lestvice in profila cez ves dnevnik, zato
       sme iz dnevnika brati SAMO pokrivni indeks (V30). Brez njega je sqlite za
       vsak zapis prebral se celotno vrstico in lestvica je na polni bazi
       trajala sekunde - in noben funkcionalni test tega ne bi opazil.

       Test prestreze SQL, ki ga Hibernate zares poslje, in preveri njegov
       nacrt. Nacrt na prazni testni bazi je isti kot na polni: sqlite brez
       statistik (ANALYZE) ne izbira po velikosti tabele. */
    @Test
    void stetjeTekmovalnihTekemBereSamoPokrivniIndeks() {
        String sql = NacrtPoizvedbe.poslaniSql(
                () -> rekreativecStoritev.rekreativci(List.of(0L)), "rating_zgodovina");
        String dnevnik = NacrtPoizvedbe.vrsticaTabele(podatkovniVir, sql, "rating_zgodovina", "URADNO");
        assertTrue(dnevnik.contains("COVERING INDEX idx_rating_zgodovina_igralec_tekme"),
                "dnevnik se mora brati samo iz pokrivnega indeksa, nacrt: " + dnevnik);
    }

    // ---------- pomozno ----------

    private boolean jeRekreativec(Igralec igralec) {
        return rekreativecStoritev.rekreativci(List.of(igralec.getId())).contains(igralec.getId());
    }

    /* Uradna liga dveh ekip (SNTL), v kateri igralec odigra eno posamicno
       tekmo. Vrne id lige. */
    private Long ligaSTekmo(Igralec igralec) {
        Long liga = ligaStoritev.ustvari(new LigaVnos("Liga rekreativcev", "2025/26",
                SpolKategorija.MOSKI, FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, null, null)).id();
        EkipaDto domaci = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub("Liga L1").getId(), null, null));
        EkipaDto gostje = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub("Liga L2").getId(), null, null));
        ligaStoritev.dodajVKader(domaci.id(), new KaderVnos(igralec.getId(), 1));
        for (int i = 2; i <= 3; i++) {
            ligaStoritev.dodajVKader(domaci.id(), new KaderVnos(noviIgralec("LigaD" + i, "Kader").getId(), i));
        }
        for (int i = 1; i <= 3; i++) {
            ligaStoritev.dodajVKader(gostje.id(), new KaderVnos(noviIgralec("LigaG" + i, "Kader").getId(), i));
        }
        ligaStoritev.generirajRazpored(liga);
        odigrajPrvoPosamicno(srecanjeStoritev.zaLigo(liga).get(0).id());
        return liga;
    }

    /* Uradni ekipni turnir dveh ekip (EKIPNI_DP), v katerem igralec odigra
       eno posamicno tekmo srecanja. Vrne id turnirja. */
    private Long ekipniTurnirSTekmo(Igralec igralec) {
        Turnir turnir = new Turnir();
        turnir.setIme("Ekipni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        turnirRepozitorij.save(turnir);
        Dogodek dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(), new DogodekVnos(
                "Clani ekipno", SpolKategorija.MOSKI, null, 5, null, null, Disciplina.EKIPNO,
                SistemTekmovanja.IZLOCILNI, null, null, FormatSrecanja.EKIPNI_DP, null, null));
        EkipaDto prva = ekipeDogodka.dodajEkipo(dogodek.getId(),
                new EkipaVnos(klub("Ekipa E1").getId(), 1, "Ekipa E1"));
        EkipaDto druga = ekipeDogodka.dodajEkipo(dogodek.getId(),
                new EkipaVnos(klub("Ekipa E2").getId(), 1, "Ekipa E2"));
        ekipeDogodka.dodajVKader(prva.id(), new KaderVnos(igralec.getId(), 1));
        for (int i = 2; i <= 3; i++) {
            ekipeDogodka.dodajVKader(prva.id(), new KaderVnos(noviIgralec("EkipaP" + i, "Kader").getId(), i));
        }
        for (int i = 1; i <= 3; i++) {
            ekipeDogodka.dodajVKader(druga.id(), new KaderVnos(noviIgralec("EkipaD" + i, "Kader").getId(), i));
        }
        zreb.izvediZreb(dogodek.getId());
        Tekma tekma = tekmeDogodka(dogodek.getId()).get(0);
        odigrajPrvoPosamicno(srecanjeRepozitorij.najdiZaTekmo(tekma.getId()).orElseThrow().getId());
        return turnir.getId();
    }

    private Klub klub(String ime) {
        return klubRepozitorij.save(new Klub(ime, null));
    }

    /* Postava po vrstnem redu kadra (prvi v kadru dobi mesto A oz. X) in prva
       posamicna tekma, ki jo v obeh formatih igrata prva v kadru: A-X. */
    private void odigrajPrvoPosamicno(Long idSrecanje) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        postavi(mesta, StranEkipe.DOMACI, p.pozicijeDomaci(), p.kaderDomaci(), p.stVDvojici());
        postavi(mesta, StranEkipe.GOST, p.pozicijeGost(), p.kaderGost(), p.stVDvojici());
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
        TekmaSrecanjaDto prva = srecanjeStoritev.podrobno(idSrecanje).tekme().stream()
                .filter(t -> t.tip() == TipTekmeSrecanja.POSAMICNA)
                .findFirst().orElseThrow();
        assertEquals("A-X", prva.oznaka());
        srecanjeStoritev.vnesiRezultat(prva.id(), new VnosRezultataSrecanja(null, 3, 1, null, null));
    }

    /* Zadnji na strani gredo v dvojice - prvi v kadru ostane samo v posamicnih. */
    private static void postavi(List<PostavaVnos.MestoVnos> mesta, StranEkipe stran,
                                List<String> pozicije, List<KaderIgralecDto> kader, int vDvojici) {
        for (int i = 0; i < pozicije.size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(stran, pozicije.get(i), kader.get(i).idIgralec(),
                    i >= pozicije.size() - vDvojici));
        }
    }
}
