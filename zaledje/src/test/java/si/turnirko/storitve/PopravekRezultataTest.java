/* Popravek ze shranjenega rezultata - turnirske tekme in posamicne tekme
   ligaskega srecanja.

   Zakaj to obstaja: rezultat se prepisuje s papirja in papir se bere z napako,
   del napak pa se pokaze sele cez nekaj dni. Dokler popravka ni bilo, je
   napacna stevilka ostala v bazi za vedno - varovalka existsByTekmaId je drugi
   obracun iste tekme zavrnila, zato se rating ni popravil niti takrat, kadar bi
   rezultat kdo prepisal na roko.

   Dvoje, kar mora test cuvati:
   1. popravek ne sme spremeniti ZMAGOVALCA (po njem je tekmovanje ze teklo),
   2. popravek MORA premakniti rating - sicer je popravljen samo izpis. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;

class PopravekRezultataTest extends IntegracijskiTest {

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorijLige;
    @Autowired private NizSrecanjaRepozitorij nizSrecanjaRepozitorij;

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

    @Test
    void popravekTurnirskeTekmeSpremeniIzidInPremakneRating() {
        Tekma tekma = pripraviEnoTekmo();
        Igralec zmagovalec = tekma.getPrijava1().getIgralec();
        Igralec porazenec = tekma.getPrijava2().getIgralec();

        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 0, null, null));
        int poGladkiZmagi = rating(zmagovalec);
        assertTrue(poGladkiZmagi > 1500, "zmaga mora rating dvigniti");

        // isti zmagovalec, tesnejsi izid: margina je manjsa, zato manj tock
        tekmaStoritev.popraviRezultat(tekma.getId(), new VnosRezultata(null, 3, 2, null, null));

        Tekma popravljena = tekmaRepozitorij.findById(tekma.getId()).orElseThrow();
        assertEquals(3, popravljena.getDobljeniNizi1());
        assertEquals(2, popravljena.getDobljeniNizi2());
        assertTrue(rating(zmagovalec) < poGladkiZmagi,
                "tesnejsa zmaga mora prinesti manj tock kot 3:0");
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

    /* Liga dveh ekip po tri igralce, postava dolocena; vrne zapisnik. */
    private List<TekmaSrecanjaDto> pripraviZapisnik() {
        LigaVnos v = new LigaVnos("Test liga", "2025/26", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL, 5, null, false, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, null, null);
        Long liga = ligaStoritev.ustvari(v).id();
        for (String ime : List.of("Klub A", "Klub B")) {
            Klub klub = klubRepozitorijLige.save(new Klub(ime, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            for (int i = 1; i <= 3; i++) {
                Igralec ig = noviIgralec("Ig" + ime.replace(" ", "") + i, "Pri" + ime.charAt(5) + i);
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
        return srecanjeStoritev.podrobno(srecanje).tekme();
    }

    @Test
    void popravekLigaskeTekmeZamenjaIzidInTocke() {
        // tekme.get(0) so dvojice, get(1) je prva posamicna (A-X)
        TekmaSrecanjaDto aX = pripraviZapisnik().get(1);
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

    @Test
    void popravekLigaskeTekmeNeSmeObrnitiZmagovalca() {
        TekmaSrecanjaDto aX = pripraviZapisnik().get(1);
        srecanjeStoritev.vnesiRezultat(aX.id(),
                new VnosRezultataSrecanja(null, 3, 0, null, null));

        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.popraviRezultat(aX.id(),
                new VnosRezultataSrecanja(null, 0, 3, null, null)));
    }

    @Test
    void popravekLigaskeTekmeBrezRezultataJeZavrnjen() {
        TekmaSrecanjaDto aX = pripraviZapisnik().get(1);
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.popraviRezultat(aX.id(),
                new VnosRezultataSrecanja(null, 3, 1, null, null)));
    }
}
