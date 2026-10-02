/* Popravek ze shranjenega rezultata - turnirske tekme in posamicne tekme
   ligaskega srecanja.

   Zakaj to obstaja: rezultat se prepisuje s papirja in papir se bere z napako,
   del napak pa se pokaze sele cez nekaj dni. Dokler popravka ni bilo, je
   napacna stevilka ostala v bazi za vedno - varovalka existsByTekmaId je drugi
   obracun iste tekme zavrnila, zato se rating ni popravil niti takrat, kadar bi
   rezultat kdo prepisal na roko.

   Kar mora test cuvati:
   1. popravek ne sme spremeniti ZMAGOVALCA, kadar je po njem tekmovanje ze
      teklo (mreza turnirja, prag zmag v srecanju) - v ligi, ki odigra vse
      tekme srecanja, pa ga sme, ker potek ostane isti,
   2. popravek MORA premakniti rating - sicer je popravljen samo izpis,
   3. ligaska tekma ima ob obracunu in ob preracunu isti cas. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;

class PopravekRezultataTest extends IntegracijskiTest {

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private PreracunRatingaStoritev preracunRatinga;
    @Autowired private KlubRepozitorij klubRepozitorijLige;
    @Autowired private NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    @PersistenceContext private EntityManager seja;

    @BeforeEach
    void deterministicniZreb() {
        zrebStoritev.nastaviNakljucje(new Random(42));
    }

    // ---------- Turnirska tekma ----------

    /* Edina tekma dvojice prijavljenih, oba s postavljenim ratingom 1500.
       Postavitev (in ne rocni zapis v rating_stanje) zato, ker jo ponovni
       preracun prebere iz dnevnika in jo zna obnoviti - rocno vpisano stanje
       brez zapisa bi se ob preracunu izgubilo. */
    private Tekma pripraviEnoTekmo() {
        Dogodek dogodek = pripraviDogodek(2);
        for (Prijava p : prijavePoVrsti(dogodek.getId())) {
            ratingStoritev.nastaviZacetniRating(p.getIgralec(), 1500);
        }
        zrebStoritev.izvediZreb(dogodek.getId());
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                .findFirst().orElseThrow();
    }

    private int rating(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    /* Popravek izida v nizih ob istem zmagovalcu rating pusti, kjer je:
       zmaga je zmaga (3 : 0 in 3 : 2 prineseta isto). Preracun pa vseeno
       tece - in ne sme podvojiti dnevnika. */
    @Test
    void popravekTurnirskeTekmeSpremeniIzidRatingPaOstane() {
        Tekma tekma = pripraviEnoTekmo();
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();
        Igralec porazenec = tekma.getPrijava2().getIgralec();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        int poGladkiZmagi = rating(zmagovalec);
        assertTrue(poGladkiZmagi > 1500, "zmaga mora rating dvigniti");

        // isti zmagovalec, tesnejsi izid
        tekmaStoritev.popraviRezultat(tekma.getId(), new VnosRezultata(null, 3, 2, null, null));

        Tekma popravljena = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(3, popravljena.getDobljeniNizi1());
        assertEquals(2, popravljena.getDobljeniNizi2());
        assertEquals(poGladkiZmagi, rating(zmagovalec),
                "tesnejsa zmaga prinese isto kot 3:0 - zmaga je zmaga");
        assertEquals(1500 - (rating(zmagovalec) - 1500), rating(porazenec),
                "pri enakem K je vsota sprememb nic");

        // dnevnik ostane po dva zapisa na tekmo - preracun jih ne sme podvojiti
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekme(List.of(tekma.getId()), RatingStanje.SISTEM_TURNIRKO).size());
    }

    @Test
    void popravekNeSmeSpremenitiZmagovalca() {
        Tekma tekma = pripraviEnoTekmo();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        Long zmagovalec = tekma.getZmagovalec().getId();

        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.popraviRezultat(
                tekma.getId(), new VnosRezultata(null, 0, 3, null, null)));

        Tekma po = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(zmagovalec, po.getZmagovalec().getId(), "zavrnjen popravek ne sme nicesar spremeniti");
        assertEquals(3, po.getDobljeniNizi1());
        assertEquals(0, po.getDobljeniNizi2());
    }

    @Test
    void popravekNekoncaneTekmeJeZavrnjen() {
        Tekma tekma = pripraviEnoTekmo();
        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.popraviRezultat(
                tekma.getId(), new VnosRezultata(null, 3, 1, null, null)));
    }

    @Test
    void vnosNaZeKoncanoTekmoOstajaZavrnjen() {
        Tekma tekma = pripraviEnoTekmo();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));

        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.vnesiRezultat(
                tekma.getId(), new VnosRezultata(null, 3, 1, null, null)));
    }

    /* Tocke po nizih se ob popravku zapisejo ZNOVA: stare morajo iti, sicer bi
       se zaporedne stevilke podvojile (UNIQUE (id_tekma, zaporedna_st)). */
    @Test
    void popravekZapiseTockeNizovZnovaInJihNePodvoji() {
        Tekma tekma = pripraviEnoTekmo();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null,
                List.of(new NizVnos(11, 5), new NizVnos(11, 7), new NizVnos(11, 9))));
        assertEquals(3, nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekma.getId()).size());

        tekmaStoritev.popraviRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null,
                List.of(new NizVnos(11, 5), new NizVnos(9, 11), new NizVnos(11, 7),
                        new NizVnos(12, 10))));

        List<String> tocke = nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekma.getId())
                .stream().map(n -> n.getTocke1() + ":" + n.getTocke2()).toList();
        assertEquals(List.of("11:5", "9:11", "11:7", "12:10"), tocke);
    }

    // ---------- Ligaska tekma ----------

    /* Srecanje lige dveh ekip s postavo; `prag` je zmag za srecanje (null =
       odigrajo se vse tekme), `zacetek` termin prvega kola (null = brez
       terminov). Igralci imajo postavljen rating 1500, da prvi dan ne tece
       uvrstitev novinca in so spremembe navadni koraki. */
    private record Zapisnik(Long idSrecanja, List<TekmaSrecanjaDto> tekme) {
        Long tekma(int i) {
            return tekme.get(i).id();
        }
    }

    private Zapisnik pripraviZapisnik(FormatSrecanja format, Integer prag, LocalDateTime zacetek) {
        LigaVnos v = new LigaVnos("Test liga", "2025/26", SpolKategorija.MOSKI,
                format, 5, prag, false, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, zacetek, zacetek == null ? null : 7);
        Long liga = ligaStoritev.ustvari(v).id();
        for (String ime : List.of("Klub A", "Klub B")) {
            Klub klub = klubRepozitorijLige.save(new Klub(ime, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            for (int i = 1; i <= format.getStIgralcev(); i++) {
                Igralec ig = noviIgralec("Ig" + ime.replace(" ", "") + i, "Pri" + ime.charAt(5) + i);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
            }
        }
        ligaStoritev.generirajRazpored(liga);
        Long srecanje = srecanjeStoritev.zaLigo(liga).get(0).id();

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), i < 2));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), i < 2));
        }
        srecanjeStoritev.nastaviPostavo(srecanje, new PostavaVnos(mesta));
        return new Zapisnik(srecanje, srecanjeStoritev.podrobno(srecanje).tekme());
    }

    private Zapisnik pripraviZapisnik() {
        return pripraviZapisnik(FormatSrecanja.SNTL, null, null);
    }

    private void vnesi(Zapisnik z, int tekma, int domaci, int gost) {
        srecanjeStoritev.vnesiRezultat(z.tekma(tekma),
                new VnosRezultataSrecanja(null, domaci, gost, null, null));
    }

    private SrecanjePodrobnoDto podrobno(Zapisnik z) {
        return srecanjeStoritev.podrobno(z.idSrecanja());
    }

    @Test
    void popravekLigaskeTekmeZamenjaIzidInTocke() {
        // tekme.get(0) so dvojice, get(1) je prva posamicna (A-X)
        TekmaSrecanjaDto aX = pripraviZapisnik().tekme().get(1);
        srecanjeStoritev.vnesiRezultat(aX.id(), new VnosRezultataSrecanja(null, 3, 0, null,
                List.of(new NizVnos(11, 5), new NizVnos(11, 7), new NizVnos(11, 9))));

        TekmaSrecanjaDto po = srecanjeStoritev.popraviRezultat(aX.id(),
                new VnosRezultataSrecanja(null, 3, 1, null,
                        List.of(new NizVnos(11, 5), new NizVnos(9, 11), new NizVnos(11, 7),
                                new NizVnos(11, 9))));

        assertEquals(3, po.dobljeniNiziDomaci());
        assertEquals(1, po.dobljeniNiziGost());
        assertEquals(StranEkipe.DOMACI, po.zmagovalecStran());
        assertEquals(4, nizSrecanjaRepozitorij.findByTekmaIdOrderByZaporednaStAsc(aX.id()).size());
        // obracun ostane en sam par zapisov, tudi po preracunu
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(aX.id()), RatingStanje.SISTEM_TURNIRKO).size());
    }

    /* Savinja liga odigra vse tekme srecanja, zato zamenjana imena v zapisniku
       ne spremenijo poteka - popravek sme obrniti zmagovalca. Obrne se tudi
       izid srecanja (3:2 -> 2:3) in rating: zdaj je tocke dobil gost. */
    @Test
    void vLigiBrezPragaPopravekObrneZmagovalcaInIzidSrecanja() {
        Zapisnik z = pripraviZapisnik(FormatSrecanja.SAVINJA, null, null);
        // dvojice, A-X, B-Y, A-Y, B-X: domaci prve tri, gostje zadnji dve
        vnesi(z, 0, 3, 1);
        vnesi(z, 1, 3, 0);
        vnesi(z, 2, 3, 2);
        vnesi(z, 3, 1, 3);
        vnesi(z, 4, 0, 3);
        assertEquals(3, podrobno(z).srecanje().dobljeneDomaci());

        TekmaSrecanjaDto po = srecanjeStoritev.popraviRezultat(z.tekma(1),
                new VnosRezultataSrecanja(null, 0, 3, null,
                        List.of(new NizVnos(5, 11), new NizVnos(7, 11), new NizVnos(9, 11))));

        assertEquals(StranEkipe.GOST, po.zmagovalecStran());
        SrecanjePodrobnoDto s = podrobno(z);
        assertEquals(2, s.srecanje().dobljeneDomaci());
        assertEquals(3, s.srecanje().dobljeneGost());
        assertEquals(StatusSrecanja.KONCANO, s.srecanje().status());
        assertTrue(po.spremembaRatingaGost() > 0, "novi zmagovalec mora rating pridobiti");
        assertTrue(po.spremembaRatingaDomaci() < 0, "novi porazenec mora rating izgubiti");
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(z.tekma(1)), RatingStanje.SISTEM_TURNIRKO).size());
    }

    /* S pragom (prvi do 3 zmag) je srecanje 3:0 koncano po tretji tekmi.
       Obrnjena druga tekma bi pomenila 2:1 - srecanje ne bi bilo odloceno,
       zadnji dve tekmi pa sta neodigrani. Tak popravek se zavrne in ne spremeni
       nicesar. */
    @Test
    void sPragomPopravekNeSmeSpremenitiPotekaSrecanja() {
        Zapisnik z = pripraviZapisnik(FormatSrecanja.SAVINJA, 3, null);
        vnesi(z, 0, 3, 0);
        vnesi(z, 1, 3, 0);
        vnesi(z, 2, 3, 0);
        assertEquals(StatusTekmeSrecanja.NEODIGRANA, podrobno(z).tekme().get(3).status());

        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.popraviRezultat(z.tekma(1),
                new VnosRezultataSrecanja(null, 0, 3, null, null)));

        SrecanjePodrobnoDto s = podrobno(z);
        assertEquals(StranEkipe.DOMACI, s.tekme().get(1).zmagovalecStran());
        assertEquals(3, s.srecanje().dobljeneDomaci());
        assertEquals(0, s.srecanje().dobljeneGost());
    }

    /* Obrnjena ODLOCILNA tekma pusti potek, kakrsen je: srecanje se se vedno
       konca s peto tekmo, le da ga dobi druga ekipa. */
    @Test
    void sPragomSmePopravekObrnitiOdlocilnoTekmo() {
        Zapisnik z = pripraviZapisnik(FormatSrecanja.SAVINJA, 3, null);
        vnesi(z, 0, 3, 0);
        vnesi(z, 1, 3, 1);
        vnesi(z, 2, 1, 3);
        vnesi(z, 3, 0, 3);
        vnesi(z, 4, 3, 2);
        assertEquals(3, podrobno(z).srecanje().dobljeneDomaci());

        srecanjeStoritev.popraviRezultat(z.tekma(4), new VnosRezultataSrecanja(null, 2, 3, null, null));

        SrecanjePodrobnoDto s = podrobno(z);
        assertEquals(2, s.srecanje().dobljeneDomaci());
        assertEquals(3, s.srecanje().dobljeneGost());
        assertEquals(StatusSrecanja.KONCANO, s.srecanje().status());
    }

    @Test
    void popravekLigaskeTekmeBrezRezultataJeZavrnjen() {
        TekmaSrecanjaDto aX = pripraviZapisnik().tekme().get(1);
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.popraviRezultat(aX.id(),
                new VnosRezultataSrecanja(null, 3, 1, null, null)));
    }

    // ---------- Cas ligaske tekme ----------

    /* Srecanje, odigrano pred svojim terminom (ekipi sta se zamenjali za
       vecer), velja ob vpisu prvega izida in ne dva meseca v prihodnosti. In:
       ponovni preracun mora tekmi postaviti ISTI cas in isti rating kot obracun
       ob vnosu - prej je obracun bral termin, preracun pa dan zakljucka, zato
       je vsak popravek premesal tekme tistega vecera. */
    @Test
    void srecanjePredTerminomVeljaObVpisuInPreracunObdrziCasInRating() {
        LocalDateTime termin = LocalDate.now().plusDays(60).atTime(19, 45);
        Zapisnik z = pripraviZapisnik(FormatSrecanja.SAVINJA, null, termin);
        vnesi(z, 1, 3, 1);
        vnesi(z, 2, 1, 3);
        seja.flush();
        seja.clear();

        Map<String, String> obVnosu = zapisiTekem(z);
        assertEquals(4, obVnosu.size());
        LocalDate danes = LocalDate.now(ZoneId.of("Europe/Ljubljana"));
        for (RatingZgodovina r : zapisi(z)) {
            assertEquals(danes, r.getVeljaOb().toLocalDate(),
                    "tekma, vpisana pred terminom, velja na dan vpisa");
        }

        preracunRatinga.preracunajOd(null);
        seja.flush();
        seja.clear();

        assertEquals(obVnosu, zapisiTekem(z), "preracun mora dati isti cas in isto stevilko");
    }

    private List<RatingZgodovina> zapisi(Zapisnik z) {
        List<Long> tekme = z.tekme().stream().map(TekmaSrecanjaDto::id).toList();
        return ratingZgodovinaRepozitorij.findAll().stream()
                .filter(r -> r.getTekmaSrecanja() != null
                        && tekme.contains(r.getTekmaSrecanja().getId()))
                .toList();
    }

    /* tekma/igralec -> cas in nova vrednost; po tem se primerjata obracun in preracun */
    private Map<String, String> zapisiTekem(Zapisnik z) {
        Map<String, String> m = new HashMap<>();
        for (RatingZgodovina r : zapisi(z)) {
            m.put(r.getTekmaSrecanja().getId() + "/" + r.getIgralec().getId(),
                    r.getVeljaOb() + " " + r.getNovaVrednost());
        }
        return m;
    }
}
