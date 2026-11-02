/* Socasne zahteve na pravem PostgreSQL (prenova baze, faza 2;
   docs/PRENOVA-BAZE.md, razdelek 5.4).

   SQLite je dva hkratna pisca pretvoril v napako 500 in pravilo je ostalo
   celo. PostgreSQL ju spusti oba - brez zaklepov (Zaklepi) bi bilo pravilo,
   ki ga varuje storitev po vzorcu "preberi -> preveri -> zapisi", prekrseno
   brez napake. Vsak test zato sprozi dve (ali tri) zahteve, ki gredo skozi
   preverbo v isti milisekundi (CountDownLatch), in preveri izid v bazi.

   Razred NAMENOMA ni @Transactional: vsaka nit mora potrditi svojo
   transakcijo, sicer se ne bi videli. Testi zato za seboj pocistijo. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import si.turnirko.dto.RegistracijaVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.KodaPopusta;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.VrstaKodePopusta;
import si.turnirko.repozitoriji.DogodekRepozitorij;
import si.turnirko.repozitoriji.IgralecRepozitorij;
import si.turnirko.repozitoriji.KodaPopustaRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingStanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.TurnirRepozitorij;

@SpringBootTest
@ActiveProfiles("test")
class SocasnostTest {

    @Autowired TurnirRepozitorij turnirji;
    @Autowired DogodekRepozitorij dogodki;
    @Autowired PrijavaRepozitorij prijave;
    @Autowired IgralecRepozitorij igralci;
    @Autowired TekmaRepozitorij tekme;
    @Autowired RatingStanjeRepozitorij stanja;
    @Autowired KodaPopustaRepozitorij kode;
    @Autowired ZrebStoritev zreb;
    @Autowired TekmaStoritev tekmaStoritev;
    @Autowired RegistracijaStoritev registracija;
    @Autowired PreracunRatingaStoritev preracun;
    @Autowired Zaklepi zaklepi;
    @Autowired SpremljaniIgralciStoritev spremljani;
    @Autowired OmejevalnikPoskusov omejevalnik;
    @Autowired JdbcTemplate jdbc;

    private Long idTurnir;
    private Long idKoda;
    private final List<String> naslovi = new ArrayList<>();

    /* Dva vnosa iste tekme hkrati (dvojni klik, ponovljena zahteva po slabem
       Wi-Fi): obvelja en, drugi dobi jasno zavrnitev, obracun je en sam. */
    @Test
    void dvaVnosaIsteTekmeObracunataEnkrat() throws Exception {
        Dogodek dogodek = mreza(4);
        Tekma tekma = pripravljene(dogodek).get(0);

        List<Object> izidi = hkrati(
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null)),
                () -> tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null)));

        assertEquals(1, izidi.stream().filter(Tekma.class::isInstance).count(), "obvelja en vnos: " + izidi);
        assertInstanceOf(DomenskaIzjema.class,
                izidi.stream().filter(i -> !(i instanceof Tekma)).findFirst().orElseThrow(),
                "drugi vnos dobi pravilo (tekma je ze koncana), ne 500 ali 409");
        assertEquals(2, stevilo("SELECT count(*) FROM rating_zgodovina WHERE id_tekma = ?", tekma.getId()),
                "ena tekma, en obracun - po en zapis za vsakega igralca");
    }

    /* Sosednji tekmi mreze se koncata hkrati in obe vpiseta zmagovalca v
       isto naslednjo tekmo: oba morata napredovati, nobeden ne sme dobiti 409. */
    @Test
    void sosednjiTekmiNapredujetaObe() throws Exception {
        Dogodek dogodek = mreza(4);
        List<Tekma> polfinale = pripravljene(dogodek);
        assertEquals(2, polfinale.size());

        List<Object> izidi = hkrati(
                () -> tekmaStoritev.vnesiRezultat(polfinale.get(0).getId(), new VnosRezultata(null, 3, 0, null, null)),
                () -> tekmaStoritev.vnesiRezultat(polfinale.get(1).getId(), new VnosRezultata(null, 3, 0, null, null)));

        assertTrue(izidi.stream().allMatch(Tekma.class::isInstance), "oba vnosa uspeta: " + izidi);
        Tekma finale = tekme.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertNotNull(finale.getPrijava1(), "zmagovalec prvega polfinala je v finalu");
        assertNotNull(finale.getPrijava2(), "zmagovalec drugega polfinala je v finalu");
        assertEquals(StatusTekme.PRIPRAVLJENA, finale.getStatus());
    }

    /* Koda z najvec dvema uporabama in tri hkratne registracije: dve dobita
       ugodnost, tretja zavrnitev - nobena tretja tiho ne zdrsne skozi. */
    @Test
    void kodaZDvemaMestomaNeSprejmeTretjega() throws Exception {
        KodaPopusta koda = new KodaPopusta("SOCASNO2", VrstaKodePopusta.PREMIUM_MESECI, LocalDateTime.now());
        koda.setMeseci(2);
        koda.setNajvecUporab(2);
        idKoda = kode.save(koda).getId();
        omejevalnik.pocistiVse();

        List<Callable<Object>> zahteve = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String email = "socasno" + i + "@turnirko.test";
            naslovi.add(email);
            String ip = "10.0.0." + i;
            zahteve.add(() -> registracija.registriraj(new RegistracijaVnos("Ana", "Socasna", null, email,
                    "dolgo-geslo-123", false, LocalDate.of(1990, 1, 1), null, "SOCASNO2"), ip));
        }
        List<Object> izidi = hkrati(zahteve);

        assertEquals(1, izidi.stream().filter(NeveljavenVnosIzjema.class::isInstance).count(),
                "tretja registracija s polno kodo je zavrnjena: " + izidi);
        assertEquals(2, stevilo("SELECT count(*) FROM uporaba_kode_popusta WHERE id_koda = ?", idKoda));
    }

    /* Vnos rezultata med preracunom: zaklepa ratinga ne dobi, zato rezultat
       shrani BREZ obracuna (namesto da bi cakal minute ali padel) - preracun ga
       ob koncu doobracuna, tu ga doobracuna varovalka. Rating na koncu je
       natanko tisti, ki ga da preracun od zacetka. */
    @Test
    void vnosMedPreracunomSeObracunaPozneje() {
        Dogodek dogodek = mreza(4);
        Tekma tekma = pripravljene(dogodek).get(0);

        try (Zaklepi.ZaklepSeje preracunTece = zaklepi.zakleniPreracunNaSeji()) {
            tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null, 3, 1, null, null));
            assertEquals(0, stevilo("SELECT count(*) FROM rating_zgodovina WHERE id_tekma = ?", tekma.getId()),
                    "med preracunom se tekma ne obracuna");
        }

        preracun.varovalka();
        assertEquals(2, stevilo("SELECT count(*) FROM rating_zgodovina WHERE id_tekma = ?", tekma.getId()),
                "po preracunu je tekma obracunana");
        List<Integer> poVarovalki = ratingiDogodka(dogodek);

        preracun.preracunajOd(null);
        assertEquals(poVarovalki, ratingiDogodka(dogodek), "isti rating kot ob preracunu od zacetka");
    }

    // ---------- pomozno ----------

    /* Izlocilni dogodek uradnega turnirja z izzrebano mrezo. */
    private Dogodek mreza(int steviloIgralcev) {
        Turnir turnir = new Turnir();
        turnir.setIme("Socasni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        idTurnir = turnirji.save(turnir).getId();

        Dogodek dogodek = new Dogodek();
        dogodek.setTurnir(turnir);
        dogodek.setIme("Clani posamicno");
        dogodek.setSpolKategorija(SpolKategorija.MOSKI);
        dogodek.setPrivzetoSteviloNizov(5);
        dogodek.setSistemTekmovanja(SistemTekmovanja.IZLOCILNI);
        dogodki.save(dogodek);
        for (int i = 1; i <= steviloIgralcev; i++) {
            Igralec igralec = new Igralec();
            igralec.setIme("Igralec" + i);
            igralec.setPriimek("Socasni" + i);
            igralec.setSpol(Spol.MOSKI);
            igralec.setDatumRojstva(LocalDate.of(1990, 1, 1));
            prijave.save(new Prijava(dogodek, igralci.save(igralec)));
        }
        zreb.nastaviNakljucje(new Random(7));
        zreb.izvediZreb(dogodek.getId());
        return dogodek;
    }

    private List<Tekma> pripravljene(Dogodek dogodek) {
        return tekme.najdiZaDogodek(dogodek.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
    }

    private List<Integer> ratingiDogodka(Dogodek dogodek) {
        return prijave.najdiZaDogodek(dogodek.getId()).stream()
                .map(p -> stanja.findByIgralecIdAndSistem(p.getIgralec().getId(), RatingStanje.SISTEM_TURNIRKO)
                        .map(RatingStanje::getVrednost).orElse(null))
                .sorted(java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                .toList();
    }

    /* Brezplacen racun sme spremljati enega igralca (SpremljaniIgralciStoritev.
       NAJVEC_BREZPLACNO). Dva hkratna zahtevka istega racuna za RAZLICNA igralca
       sta oba prebrala stetje 0 in oba shranila - zaklep racuna (Zaklepi.Vrsta.RACUN)
       ju spravi v vrsto, drugi vidi prvega in je zavrnjen. */
    @Test
    void dvaHkratnaSpremljanjaIstegaRacunaSprejmetaEnega() throws Exception {
        String ime = "socasnost-navijac-" + System.nanoTime();
        Long idRacun = jdbc.queryForObject("INSERT INTO uporabnik (uporabnisko_ime, geslo_hash, vloga, status, ustvarjen_ob)"
                + " VALUES (?, 'x', 'NAVIJAC', 'POTRJEN', now()) RETURNING id", Long.class, ime);
        List<Long> idjiIgralcev = new ArrayList<>();
        for (String priimek : List.of("Prvi", "Drugi")) {
            idjiIgralcev.add(jdbc.queryForObject("INSERT INTO igralec (ime, priimek, spol, datum_rojstva, ustvarjen_ob)"
                    + " VALUES ('Socasni', ?, 'MOSKI', '2000-01-01', now()) RETURNING id", Long.class, priimek));
        }
        try {
            List<Object> izidi = hkrati(idjiIgralcev.stream().<Callable<Object>>map(idIgralca -> () -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        ime, null, List.of(new SimpleGrantedAuthority("ROLE_NAVIJAC"))));
                try {
                    return spremljani.spremljaj(idIgralca);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList());

            assertEquals(1, izidi.stream().filter(DomenskaIzjema.class::isInstance).count(),
                    "drugi igralec je zavrnjen, ker je meja ze dosezena: " + izidi);
            assertEquals(1, stevilo("SELECT count(*) FROM spremljan_igralec WHERE id_racun = ?", idRacun));
        } finally {
            jdbc.update("DELETE FROM spremljan_igralec WHERE id_racun = ?", idRacun);
            jdbc.update("DELETE FROM uporabnik WHERE id = ?", idRacun);
            for (Long id : idjiIgralcev) {
                jdbc.update("DELETE FROM igralec WHERE id = ?", id);
            }
        }
    }

    @SafeVarargs
    private List<Object> hkrati(Callable<Object>... zahteve) throws Exception {
        return hkrati(List.of(zahteve));
    }

    /* Zahteve sprozi hkrati (vse niti cakajo na isti znak) in vrne izid vsake:
       vrnjeno vrednost ali vrzeno izjemo. */
    private List<Object> hkrati(List<Callable<Object>> zahteve) throws Exception {
        ExecutorService niti = Executors.newFixedThreadPool(zahteve.size());
        try {
            CountDownLatch zacetek = new CountDownLatch(1);
            List<Future<Object>> prihodnji = new ArrayList<>();
            for (Callable<Object> zahteva : zahteve) {
                prihodnji.add(niti.submit(() -> {
                    zacetek.await();
                    try {
                        return zahteva.call();
                    } catch (RuntimeException izjema) {
                        return izjema;
                    }
                }));
            }
            zacetek.countDown();
            List<Object> izidi = new ArrayList<>();
            for (Future<Object> f : prihodnji) {
                izidi.add(f.get(30, TimeUnit.SECONDS));
            }
            return izidi;
        } finally {
            niti.shutdownNow();
        }
    }

    private long stevilo(String sql, Object... parametri) {
        Long n = jdbc.queryForObject(sql, Long.class, parametri);
        return n == null ? 0 : n;
    }

    @AfterEach
    void pocisti() {
        if (idTurnir != null) {
            String dogodkiTurnirja = "SELECT id FROM dogodek WHERE id_turnir = " + idTurnir;
            String tekmeTurnirja = "SELECT id FROM tekma WHERE id_dogodek IN (" + dogodkiTurnirja + ")";
            List<Long> idjiIgralcev = jdbc.queryForList(
                    "SELECT id_igralec FROM prijava WHERE id_dogodek IN (" + dogodkiTurnirja + ")", Long.class);
            jdbc.update("DELETE FROM rating_zgodovina WHERE id_tekma IN (" + tekmeTurnirja + ")");
            jdbc.update("DELETE FROM niz WHERE id_tekma IN (" + tekmeTurnirja + ")");
            jdbc.update("UPDATE tekma SET id_izvor_tekma_1 = NULL, id_izvor_tekma_2 = NULL"
                    + " WHERE id_dogodek IN (" + dogodkiTurnirja + ")");
            jdbc.update("DELETE FROM tekma WHERE id_dogodek IN (" + dogodkiTurnirja + ")");
            jdbc.update("DELETE FROM prijava WHERE id_dogodek IN (" + dogodkiTurnirja + ")");
            jdbc.update("DELETE FROM dogodek WHERE id_turnir = ?", idTurnir);
            jdbc.update("DELETE FROM turnir WHERE id = ?", idTurnir);
            for (Long id : idjiIgralcev) {
                jdbc.update("DELETE FROM rating_zgodovina WHERE id_igralec = ?", id);
                jdbc.update("DELETE FROM rating_stanje WHERE id_igralec = ?", id);
                jdbc.update("DELETE FROM igralec WHERE id = ?", id);
            }
            idTurnir = null;
        }
        for (String email : naslovi) {
            // potrditvene kode, dnevnik in poraba kode gredo z racunom (ON DELETE CASCADE)
            jdbc.update("DELETE FROM uporabnik WHERE uporabnisko_ime = ?", email);
        }
        naslovi.clear();
        if (idKoda != null) {
            jdbc.update("DELETE FROM koda_popusta WHERE id = ?", idKoda);
            idKoda = null;
        }
    }
}
