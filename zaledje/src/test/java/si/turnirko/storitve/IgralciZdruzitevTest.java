/* Cistka duplikatov: pari podobnih igralcev, »ni duplikat« in zdruzitev.

   Zdruzitev je edina operacija v aplikaciji, ki igralcu odnese zgodovino, zato
   test drzi stvari, ki se ne smejo zgoditi tiho:
   - vse, kar je kazalo na odvecnega, kaze na obdrzanega,
   - rating obdrzanega je po zdruzitvi natanko tisti, ki ga je imel odvecni
     (isti izidi, isti nasprotniki), nasprotniki pa ostanejo nedotaknjeni,
   - trk (oba v istem dogodku, oba z racunom, dva rocna ratinga) zdruzitev
     zavrne in ne spremeni nicesar. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.DvojnikIgralcaDto;
import si.turnirko.dto.ZdruzitevIgralcevDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.PrimerjavaDatuma;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.UjemanjeImena;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class IgralciZdruzitevTest extends IntegracijskiTest {

    @Autowired private DvojnikiIgralcevStoritev dvojniki;
    @Autowired private IgralciZdruzitevStoritev zdruzitev;
    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @PersistenceContext private EntityManager em;

    // ---------- pari ----------

    @Test
    void parIsteOsebeImaEnakDatumInJeNajprej() {
        Igralec a = igralec("Miha", "Zdruzitev", LocalDate.of(1990, 5, 5));
        Igralec b = igralec("Miha", "Zdruzitev", LocalDate.of(1990, 5, 5));
        Igralec drug = igralec("Mihael", "Zdruzitev", LocalDate.of(1971, 2, 2));

        List<DvojnikIgralcaDto> pari = pariZ(a, b, drug);

        // Miha / Mihael je krajsa oblika imena, torej tudi par - a najverjetnejsi je prvi
        assertEquals(3, pari.size());
        DvojnikIgralcaDto prvi = pari.get(0);
        assertEquals(UjemanjeImena.ISTO, prvi.ujemanje());
        assertEquals(PrimerjavaDatuma.ENAK, prvi.datum());
        assertEquals(java.util.Set.of(a.getId(), b.getId()),
                java.util.Set.of(prvi.levi().id(), prvi.desni().id()));
        assertTrue(pari.stream().skip(1).allMatch(p -> p.datum() == PrimerjavaDatuma.DRUG));
    }

    @Test
    void zamenjanoImeInPriimekJeObrnjeno() {
        Igralec a = igralec("Tadej", "Petkovsek", LocalDate.of(1980, 12, 27));
        Igralec b = igralec("Petkovsek", "Tadej", LocalDate.of(1980, 12, 27));

        DvojnikIgralcaDto par = pariZ(a, b).get(0);

        assertEquals(UjemanjeImena.OBRNJENO, par.ujemanje());
        assertEquals(PrimerjavaDatuma.ENAK, par.datum());
    }

    @Test
    void neznanDatumNiDokazZaIstoOsebo() {
        Igralec a = igralec("Tadej", "Neznan", LocalDate.of(1900, 1, 1));
        Igralec b = igralec("Tadej", "Neznan", LocalDate.of(1900, 1, 1));

        DvojnikIgralcaDto par = pariZ(a, b).get(0);

        assertEquals(PrimerjavaDatuma.DRUG, par.datum(),
                "dva neznana datuma (1. 1. 1900) nista »enak datum rojstva«");
    }

    @Test
    void tipkarskaNapakaJePodobnoImeInLevijeJeTistiZVecTekmami() {
        Igralec novejsi = igralec("Marjetka", "Napakic", LocalDate.of(1990, 1, 1));
        Igralec starejsi = igralec("Marjetko", "Napakic", LocalDate.of(1990, 1, 1));
        RatingStanje stanje = new RatingStanje(starejsi, RatingStanje.SISTEM_TURNIRKO, 1400);
        stanje.setStTekem(15);
        ratingStanjeRepozitorij.save(stanje);

        DvojnikIgralcaDto par = pariZ(novejsi, starejsi).get(0);

        assertEquals(UjemanjeImena.PODOBNO, par.ujemanje());
        assertEquals(starejsi.getId(), par.levi().id(), "levi je tisti z vec tekmami");
        assertEquals(novejsi.getId(), par.desni().id());
    }

    @Test
    void niDuplikatSkrijeParInOstaneSkrit() {
        Igralec a = igralec("Lara", "Skrita", LocalDate.of(2001, 3, 3));
        Igralec b = igralec("Lara", "Skrita", LocalDate.of(1985, 7, 7));
        assertEquals(1, pariZ(a, b).size());

        // vrstni red id-jev ni pomemben
        dvojniki.niDuplikat(b.getId(), a.getId());
        assertTrue(pariZ(a, b).isEmpty(), "zavrnjen par se ne pokaze vec");

        // ponovna odlocitev je neskodna
        dvojniki.niDuplikat(a.getId(), b.getId());
        assertTrue(pariZ(a, b).isEmpty());
    }

    @Test
    void niDuplikatSamegaSebeJeNapaka() {
        Igralec a = igralec("Sam", "Zase", LocalDate.of(2001, 3, 3));
        assertThrows(NeveljavenVnosIzjema.class, () -> dvojniki.niDuplikat(a.getId(), a.getId()));
    }

    @Test
    void arhiviranIgralecNiVecDelCistke() {
        Igralec a = igralec("Nina", "Arhivska", LocalDate.of(2001, 3, 3));
        Igralec b = igralec("Nina", "Arhivska", LocalDate.of(2001, 3, 3));
        b.setArhiviran(true);
        igralecRepozitorij.save(b);

        assertTrue(pariZ(a, b).isEmpty());
    }

    // ---------- zdruzitev ----------

    @Test
    void zdruzitevPreusmeriPrijavoPrenesePraznaPoljaInArhivira() {
        Dogodek dogodek = pripraviDogodek(2);
        Prijava prijava = prijavePoVrsti(dogodek.getId()).get(0);
        Igralec odvec = prijava.getIgralec();
        Klub klub = klubRepozitorij.save(new Klub("Zdruzitveni klub", null));
        odvec.setKlub(klub);
        odvec.setNtzsLicenca("99001");
        odvec.setEmail("odvec@primer.si");
        igralecRepozitorij.save(odvec);
        Igralec obdrzan = noviIgralec("Obdrzan", "Igralec");
        obdrzan.setDatumRojstva(LocalDate.of(1900, 1, 1));
        igralecRepozitorij.save(obdrzan);

        ZdruzitevIgralcevDto izid = zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertFalse(izid.preracunan(), "odvecni nima tekem, preracun ni potreben");
        assertEquals(obdrzan.getId(), prijavaRepozitorij.findById(prijava.getId()).orElseThrow()
                .getIgralec().getId(), "prijava kaze na obdrzanega");
        Igralec po = igralecRepozitorij.findById(obdrzan.getId()).orElseThrow();
        assertEquals("99001", po.getNtzsLicenca(), "prazna licenca se izpolni");
        assertEquals("odvec@primer.si", po.getEmail());
        assertEquals(klub.getId(), po.getKlub().getId());
        assertEquals(LocalDate.of(2000, 1, 1), po.getDatumRojstva(),
                "neznan datum (1. 1. 1900) se zamenja z znanim");
        Igralec arhiviran = igralecRepozitorij.findById(odvec.getId()).orElseThrow();
        assertTrue(arhiviran.isArhiviran());
        assertNull(arhiviran.getNtzsLicenca(), "unikatna polja odvecnega so sproscena");
        assertEquals(obdrzan.getId(), izid.igralec().id());
    }

    @Test
    void zdruzitevNeprepiseObdrzanegaPolja() {
        Igralec obdrzan = noviIgralec("Obdrzan", "Polje");
        obdrzan.setNtzsLicenca("11111");
        igralecRepozitorij.save(obdrzan);
        Igralec odvec = noviIgralec("Obdrzan", "Polje");
        odvec.setNtzsLicenca("22222");
        igralecRepozitorij.save(odvec);

        zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertEquals("11111", igralecRepozitorij.findById(obdrzan.getId()).orElseThrow().getNtzsLicenca());
    }

    @Test
    void zdruzitevZZaznamomNiDuplikatPobrisePar() {
        Igralec a = igralec("Pari", "Brisanje", LocalDate.of(1999, 1, 1));
        Igralec b = igralec("Pari", "Brisanje", LocalDate.of(1998, 1, 1));
        dvojniki.niDuplikat(a.getId(), b.getId());

        zdruzitev.zdruzi(a.getId(), b.getId());
        em.clear();

        Number ostanek = (Number) em.createNativeQuery(
                "SELECT count(*) FROM igralec_ni_duplikat WHERE id_igralec_a = ?1 OR id_igralec_b = ?1")
                .setParameter(1, b.getId()).getSingleResult();
        assertEquals(0, ostanek.intValue());
    }

    @Test
    void zdruzitevSTekmamiSestaviRatingObdrzanegaZnova() {
        Dogodek dogodek = odigranTurnir();
        Igralec odvec = prijavePoVrsti(dogodek.getId()).get(0).getIgralec();
        Map<Long, List<Integer>> pred = stanja();
        List<Integer> ratingOdvecnega = pred.get(odvec.getId());
        assertNotNull(ratingOdvecnega, "odvecni je igral na turnirju");

        // obdrzani je ista oseba (isti spol in datum), zato je izhodisce enako
        Igralec obdrzan = noviIgralec("Dvojnik", "Odvecnega");
        ZdruzitevIgralcevDto izid = zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertTrue(izid.preracunan(), "odvecni je imel tekme, rating se sestavi znova");
        Map<Long, List<Integer>> po = stanja();
        assertEquals(ratingOdvecnega, po.get(obdrzan.getId()),
                "obdrzani ima natanko rating, ki ga je imel odvecni");
        assertFalse(po.containsKey(odvec.getId()), "odvecni nima vec ratinga");
        for (Map.Entry<Long, List<Integer>> vnos : pred.entrySet()) {
            if (!vnos.getKey().equals(odvec.getId())) {
                assertEquals(vnos.getValue(), po.get(vnos.getKey()), "nasprotniki ostanejo, kot so bili");
            }
        }
        assertEquals(0, ratingZgodovinaRepozitorij.findAll().stream()
                .filter(z -> z.getIgralec().getId().equals(odvec.getId())).count());
    }

    @Test
    void obaVIstemDogodkuNistaDvojnika() {
        Dogodek dogodek = pripraviDogodek(2);
        List<Prijava> prijave = prijavePoVrsti(dogodek.getId());
        Long a = prijave.get(0).getIgralec().getId();
        Long b = prijave.get(1).getIgralec().getId();

        DomenskaIzjema izjema = assertThrows(DomenskaIzjema.class, () -> zdruzitev.zdruzi(a, b));

        assertTrue(izjema.getMessage().contains("istem dogodku"), izjema.getMessage());
        em.clear();
        assertFalse(igralecRepozitorij.findById(b).orElseThrow().isArhiviran(), "nic se ni spremenilo");
    }

    @Test
    void racunOdvecnegaSePrenese() {
        Igralec obdrzan = noviIgralec("Racun", "Prenos");
        Igralec odvec = noviIgralec("Racun", "Prenos");
        Uporabnik racun = new Uporabnik("odvec@primer.si", "zgostitev", Vloga.IGRALEC);
        racun.setStatus(StatusRacuna.POTRJEN);
        racun.setIgralec(odvec);
        uporabnikRepozitorij.save(racun);

        zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertEquals(obdrzan.getId(), uporabnikRepozitorij.findById(racun.getId()).orElseThrow()
                .getIgralec().getId(), "racun se po zdruzitvi poveze z obdrzanim");
    }

    @Test
    void obaZRacunomNistaDvojnika() {
        Igralec a = noviIgralec("Racun", "Oba");
        Igralec b = noviIgralec("Racun", "Oba");
        for (Igralec igralec : List.of(a, b)) {
            Uporabnik racun = new Uporabnik("racun" + igralec.getId() + "@primer.si", "zgostitev", Vloga.IGRALEC);
            racun.setStatus(StatusRacuna.POTRJEN);
            racun.setIgralec(igralec);
            uporabnikRepozitorij.save(racun);
        }

        DomenskaIzjema izjema = assertThrows(DomenskaIzjema.class, () -> zdruzitev.zdruzi(a.getId(), b.getId()));

        assertTrue(izjema.getMessage().contains("racun"), izjema.getMessage());
    }

    @Test
    void rocnaPostavitevOdvecnegaSePrenese() {
        Igralec obdrzan = noviIgralec("Postavitev", "Prenos");
        Igralec odvec = noviIgralec("Postavitev", "Prenos");
        ratingStoritev.nastaviZacetniRating(odvec, 1234);

        ZdruzitevIgralcevDto izid = zdruzitev.zdruzi(obdrzan.getId(), odvec.getId());
        em.clear();

        assertFalse(izid.preracunan(), "brez tekem preracun ni potreben");
        assertEquals(1234, izid.igralec().rating(), "rocno postavljen rating se prenese");
        RatingStanje stanje = ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(obdrzan.getId(), RatingStanje.SISTEM_TURNIRKO).orElseThrow();
        assertTrue(stanje.isPostavljen());
        assertTrue(ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(odvec.getId(), RatingStanje.SISTEM_TURNIRKO).isEmpty());
    }

    @Test
    void dvaRocnaRatingaSeNeMesata() {
        Igralec a = noviIgralec("Postavitev", "Dvojna");
        Igralec b = noviIgralec("Postavitev", "Dvojna");
        ratingStoritev.nastaviZacetniRating(a, 1300);
        ratingStoritev.nastaviZacetniRating(b, 1100);

        DomenskaIzjema izjema = assertThrows(DomenskaIzjema.class, () -> zdruzitev.zdruzi(a.getId(), b.getId()));

        assertTrue(izjema.getMessage().contains("rocno postavljen rating"), izjema.getMessage());
    }

    @Test
    void zdruzitevSSamimSebojJeNapaka() {
        Igralec a = noviIgralec("Sam", "Seboj");
        assertThrows(NeveljavenVnosIzjema.class, () -> zdruzitev.zdruzi(a.getId(), a.getId()));
    }

    // ---------- pomozno ----------

    private Igralec igralec(String ime, String priimek, LocalDate datum) {
        Igralec igralec = noviIgralec(ime, priimek, Spol.MOSKI);
        igralec.setDatumRojstva(datum);
        return igralecRepozitorij.save(igralec);
    }

    /* Pari, v katerih nastopa vsaj eden od danih igralcev (baza ima lahko
       drugih). */
    private List<DvojnikIgralcaDto> pariZ(Igralec... igralci) {
        List<Long> idji = java.util.Arrays.stream(igralci).map(Igralec::getId).toList();
        return dvojniki.pari().stream()
                .filter(p -> idji.contains(p.levi().id()) && idji.contains(p.desni().id()))
                .toList();
    }

    /* Odigra cel izlocilni turnir stirih igralcev (kot PreracunRatingaTest). */
    private Dogodek odigranTurnir() {
        Dogodek dogodek = pripraviDogodek(4);
        var turnir = dogodek.getTurnir();
        turnir.setDatumZacetka(LocalDate.now().minusMonths(1));
        turnirRepozitorij.save(turnir);

        zrebStoritev.izvediZreb(dogodek.getId());
        for (int i = 0; i < 3; i++) {
            Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA)
                    .findFirst().orElseThrow();
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, i % 3, null, null));
        }
        return dogodek;
    }

    private Map<Long, List<Integer>> stanja() {
        Map<Long, List<Integer>> po = new HashMap<>();
        for (RatingStanje s : ratingStanjeRepozitorij.findBySistem(RatingStanje.SISTEM_TURNIRKO)) {
            po.put(s.getIgralec().getId(), List.of(s.getVrednost(), s.getStTekem()));
        }
        return po;
    }
}
