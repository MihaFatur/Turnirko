/* Razveljavitev zapisnika ligaskega srecanja (SrecanjeStoritev.razveljaviZapisnik).

   Zakaj to obstaja: organizator Savinja lige je v postavo vpisal napacna
   igralca, srecanje vnesel do konca in ga ni mogel vec popraviti - postave po
   prvem izidu ni mogoce spremeniti, popravek pa zamenja le izid. Razveljavitev
   srecanje vrne v razpored, kot da ga nihce ni vpisal.

   Kar mora test cuvati:
   1. za razveljavljenim zapisnikom ne ostane nic: postava, tekme, tocke po
      nizih, obracun v dnevniku in cas prvega izida,
   2. po ponovnem vnosu "vse stima": rating je natanko tak, kot ga da preracun
      od zacetka - tudi kadar je srecanje vneseno za nazaj, za poznejsim
      srecanjem istih igralcev (preracunajCeVnesenoZaNazaj),
   3. razveljavitev se zavrne tam, kjer je po izidu srecanja ze teklo nekaj
      drugega (koncnica), in tujemu organizatorju. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.KoncnicaDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.PrepovedanoIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusRacuna;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Uporabnik;
import si.turnirko.modeli.Vloga;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.UporabnikRepozitorij;

class RazveljavitevZapisnikaTest extends IntegracijskiTest {

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KoncnicaStoritev koncnicaStoritev;
    @Autowired private PreracunRatingaStoritev preracunRatinga;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    @Autowired private UporabnikRepozitorij uporabnikRepozitorij;
    @PersistenceContext private EntityManager seja;

    @AfterEach
    void odjava() {
        SecurityContextHolder.clearContext();
    }

    /* Savinja liga dveh ekip s po tremi igralci v kadru (tretji je tisti, ki ga
       organizator po pomoti vpise). Igralci imajo postavljen rating 1500, da
       prvi dan ne tece uvrstitev novinca in so spremembe navadni koraki. */
    private record Liga(Long id, Long ekipaA, List<Igralec> kaderA, List<Igralec> kaderB) {
        /* Ekipi sta po zrebu doma ali v gosteh - kdo je kje, pove srecanje. */
        List<Igralec> domaci(SrecanjeDto s) {
            return s.idEkipaDomaci().equals(ekipaA) ? kaderA : kaderB;
        }
        List<Igralec> gost(SrecanjeDto s) {
            return s.idEkipaDomaci().equals(ekipaA) ? kaderB : kaderA;
        }
    }

    private Liga pripraviLigo(boolean dvokrozno, LocalDateTime zacetek, Integer koncnicaEkip) {
        LigaVnos v = new LigaVnos("Savinja test", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, dvokrozno, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, zacetek, zacetek == null ? null : 7,
                koncnicaEkip, koncnicaEkip == null ? null : 1, null);
        Long liga = ligaStoritev.ustvari(v).id();
        List<List<Igralec>> kadri = new ArrayList<>();
        List<Long> ekipe = new ArrayList<>();
        for (String ime : List.of("Klub A", "Klub B")) {
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            var ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
            ekipe.add(ekipa.id());
            List<Igralec> kader = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Igralec ig = noviIgralec("Ig" + ime.charAt(5) + i, "Pri" + ime.charAt(5) + i);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
                kader.add(ig);
            }
            kadri.add(kader);
        }
        ligaStoritev.generirajRazpored(liga);
        return new Liga(liga, ekipe.get(0), kadri.get(0), kadri.get(1));
    }

    private List<SrecanjeDto> srecanja(Liga l) {
        return srecanjeStoritev.zaLigo(l.id());
    }

    /* Postava srecanja: domaci A, B in gostje X, Y; oba para igrata dvojice. Kdo
       je doma, se prebere iz srecanja (v povratnem krogu sta ekipi obrnjeni). */
    private void postava(Long idSrecanje, Igralec a, Igralec b, Igralec x, Igralec y) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<Long> kaderDomaci = p.kaderDomaci().stream().map(KaderIgralecDto::idIgralec).toList();
        boolean obrnjeno = !kaderDomaci.contains(a.getId());
        Igralec[] dom = obrnjeno ? new Igralec[] {x, y} : new Igralec[] {a, b};
        Igralec[] gos = obrnjeno ? new Igralec[] {a, b} : new Igralec[] {x, y};
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    dom[i].getId(), true));
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    gos[i].getId(), true));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
    }

    /* Vnese vseh pet tekem (dvojice, A-X, B-Y, A-Y, B-X) z danimi izidi v nizih
       "domaci:gost", v danem vrstnem redu tekem. */
    private void vnesi(Long idSrecanje, int[] vrstniRed, String... izidi) {
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(idSrecanje).tekme();
        for (int i : vrstniRed) {
            String[] n = izidi[i].split(":");
            srecanjeStoritev.vnesiRezultat(tekme.get(i).id(), new VnosRezultataSrecanja(
                    null, Integer.parseInt(n[0]), Integer.parseInt(n[1]), null, null));
        }
    }

    private void vnesi(Long idSrecanje, String... izidi) {
        vnesi(idSrecanje, new int[] {0, 1, 2, 3, 4}, izidi);
    }

    private int rating(Igralec igralec) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(igralec.getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    /* Ratingi in vsi zapisi dnevnika igralcev lige - po njih se primerja stanje
       po vnosu s stanjem po preracunu od zacetka. */
    private Map<String, String> posnetek(Liga l) {
        seja.flush();
        seja.clear();
        Map<String, String> m = new HashMap<>();
        List<Igralec> vsi = new ArrayList<>(l.kaderA());
        vsi.addAll(l.kaderB());
        for (Igralec ig : vsi) {
            m.put("rating/" + ig.getId(), String.valueOf(rating(ig)));
        }
        for (RatingZgodovina r : ratingZgodovinaRepozitorij.findAll()) {
            if (r.getTekmaSrecanja() != null) {
                m.put(r.getTekmaSrecanja().getId() + "/" + r.getIgralec().getId(),
                        r.getVeljaOb() + " " + r.getSprememba() + " " + r.getNovaVrednost());
            }
        }
        return m;
    }

    @Test
    void razveljavitevPobriseZapisnikInVrneSrecanjeVRazpored() {
        Liga l = pripraviLigo(false, null, null);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long s = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        postava(s, d.get(0), d.get(1), g.get(0), g.get(1));
        List<Long> tekme = srecanjeStoritev.podrobno(s).tekme().stream().map(TekmaSrecanjaDto::id).toList();
        srecanjeStoritev.vnesiRezultat(tekme.get(1), new VnosRezultataSrecanja(null, 3, 0, null,
                List.of(new NizVnos(11, 5), new NizVnos(11, 7), new NizVnos(11, 9))));
        vnesi(s, new int[] {0, 2, 3, 4}, "3:1", null, "1:3", "3:2", "0:3");
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(s).srecanje().status());
        assertEquals(8, ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(tekme, RatingStanje.SISTEM_TURNIRKO).size());
        assertNotEquals(1500, rating(d.get(0)));

        SrecanjePodrobnoDto po = srecanjeStoritev.razveljaviZapisnik(s);

        assertEquals(StatusSrecanja.RAZPORED, po.srecanje().status());
        assertEquals(0, po.srecanje().dobljeneDomaci());
        assertEquals(0, po.srecanje().dobljeneGost());
        assertTrue(po.tekme().isEmpty(), "tekme gredo z zapisnikom");
        assertTrue(po.postave().isEmpty(), "postava gre z zapisnikom");
        assertTrue(nizSrecanjaRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekme.get(1)).isEmpty());
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(tekme, RatingStanje.SISTEM_TURNIRKO).isEmpty());
        for (Igralec ig : List.of(d.get(0), d.get(1), g.get(0), g.get(1))) {
            assertEquals(1500, rating(ig), "rating se vrne na stanje pred srecanjem");
        }
        seja.flush();
        seja.clear();
        assertNull(srecanjeRepozitorij.findById(s).orElseThrow().getOdigranOb(),
                "cas prvega izida gre z zapisnikom");

        // lestvica lige srecanja ne steje vec
        for (LestvicaEkipeDto e : ligaStoritev.lestvica(l.id())) {
            assertEquals(0, e.odigrane());
        }
    }

    /* Primer iz Savinja lige: v postavi je bil napacen igralec. Po razveljavitvi
       in ponovnem vnosu je napacni brez sledi, pravi ima tekme, rating pa je
       tak, kot ga da preracun od zacetka. */
    @Test
    void poRazveljavitviSeZapisnikVneseZnovaZDrugimIgralcem() {
        Liga l = pripraviLigo(false, null, null);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long s = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        Igralec pravi = d.get(1);
        Igralec napacni = d.get(2);
        postava(s, d.get(0), napacni, g.get(0), g.get(1));
        vnesi(s, "3:1", "3:0", "3:2", "1:3", "0:3");
        assertNotEquals(1500, rating(napacni));

        srecanjeStoritev.razveljaviZapisnik(s);
        postava(s, d.get(0), pravi, g.get(0), g.get(1));
        vnesi(s, "3:1", "3:0", "3:2", "1:3", "0:3");

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s);
        assertEquals(StatusSrecanja.KONCANO, p.srecanje().status());
        assertEquals(3, p.srecanje().dobljeneDomaci());
        KaderIgralecDto kaderNapacni = p.kaderDomaci().stream()
                .filter(k -> k.idIgralec().equals(napacni.getId())).findFirst().orElseThrow();
        assertEquals(0, kaderNapacni.zmage() + kaderNapacni.porazi(), "napacni ni odigral nicesar");
        assertEquals(1500, rating(napacni));
        assertTrue(ratingZgodovinaRepozitorij.findAll().stream()
                .noneMatch(r -> r.getIgralec().getId().equals(napacni.getId()) && r.getTekmaSrecanja() != null));
        assertNotEquals(1500, rating(pravi));

        Map<String, String> poVnosu = posnetek(l);
        preracunRatinga.preracunajOd(null);
        assertEquals(poVnosu, posnetek(l), "ponovni vnos mora dati isto kot preracun");
    }

    /* Razveljavljeno je srecanje 1. kola, igralci pa so medtem odigrali se 2.
       kolo. Ponovni vnos je torej vnos ZA NAZAJ: brez preracuna bi bil 1. krog
       obracunan proti stevilkam, ki ze vsebujejo 2. kolo, in rating bi se
       razsel s preracunom od zacetka. */
    @Test
    void ponovniVnosZaNazajDaIstiRatingKotPreracun() {
        LocalDateTime zacetek = LocalDate.now().minusDays(21).atTime(18, 30);
        Liga l = pripraviLigo(true, zacetek, null);
        List<SrecanjeDto> sr = srecanja(l);
        assertEquals(2, sr.size());
        List<Igralec> d = l.kaderA();
        List<Igralec> g = l.kaderB();
        Long prvo = sr.get(0).id();
        Long drugo = sr.get(1).id();
        Igralec a = d.get(0);
        Igralec b = d.get(1);
        Igralec x = g.get(0);
        Igralec y = g.get(1);

        postava(prvo, a, d.get(2), x, y); // napacen igralec
        vnesi(prvo, "3:0", "3:0", "3:0", "3:0", "3:0");
        postava(drugo, a, b, x, y);
        vnesi(drugo, "3:1", "3:1", "1:3", "3:1", "2:3");

        srecanjeStoritev.razveljaviZapisnik(prvo);
        postava(prvo, a, b, x, y);
        vnesi(prvo, "3:0", "0:3", "0:3", "0:3", "3:2");

        Map<String, String> poVnosu = posnetek(l);
        preracunRatinga.preracunajOd(null);
        assertEquals(poVnosu, posnetek(l), "vnos za nazaj mora dati isto kot preracun");
    }

    /* Vecer, vnesen od zadnje ure proti prvi (organizator je vzel zapisnike v
       obratnem vrstnem redu): isto pravilo - ko je srecanje vneseno do konca,
       se rating uredi, kot da bi bila vnesena po vrsti. */
    @Test
    void srecanjeVnesenoZaPoznejsimSeUrediSamo() {
        LocalDateTime zacetek = LocalDate.now().minusDays(21).atTime(18, 30);
        Liga l = pripraviLigo(true, zacetek, null);
        List<SrecanjeDto> sr = srecanja(l);
        List<Igralec> d = l.kaderA();
        List<Igralec> g = l.kaderB();
        Igralec a = d.get(0);
        Igralec b = d.get(1);
        Igralec x = g.get(0);
        Igralec y = g.get(1);

        postava(sr.get(1).id(), a, b, x, y);
        vnesi(sr.get(1).id(), "3:1", "3:1", "1:3", "3:1", "2:3");
        postava(sr.get(0).id(), a, b, x, y);
        // zadnja vnesena je tekma para - po preracunu ju odgovor vseeno nosi
        vnesi(sr.get(0).id(), new int[] {1, 2, 3, 4, 0}, "3:0", "0:3", "0:3", "0:3", "3:2");

        Map<String, String> poVnosu = posnetek(l);
        preracunRatinga.preracunajOd(null);
        assertEquals(poVnosu, posnetek(l));
    }

    @Test
    void srecanjeBrezZapisnikaNiMogoceRazveljaviti() {
        Liga l = pripraviLigo(false, null, null);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long s = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(s));
    }

    /* Postava je vpisana, izida pa se ni: tudi tak zapisnik se razveljavi. */
    @Test
    void zapisnikSamoSPostavoSeRazveljavi() {
        Liga l = pripraviLigo(false, null, null);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long s = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        postava(s, d.get(0), d.get(2), g.get(0), g.get(1));

        SrecanjePodrobnoDto po = srecanjeStoritev.razveljaviZapisnik(s);
        assertEquals(StatusSrecanja.RAZPORED, po.srecanje().status());
        assertTrue(po.postave().isEmpty());
    }

    /* Ko je iz koncne lestvice nastala koncnica, srecanja rednega dela ni mogoce
       razveljaviti; koncane tekme koncnice prav tako ne (serija je tekla), tisto
       v teku pa lahko. */
    @Test
    void poSestavljeniKoncniciSeRedniDelInKoncanaTekmaKoncniceNeRazveljavita() {
        Liga l = pripraviLigo(false, null, 2);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long redno = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        Igralec a = d.get(0);
        Igralec b = d.get(1);
        Igralec x = g.get(0);
        Igralec y = g.get(1);
        postava(redno, a, b, x, y);
        vnesi(redno, "3:0", "3:0", "3:0", "1:3", "3:2");

        KoncnicaDto koncnica = koncnicaStoritev.ustvari(l.id());
        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> srecanjeStoritev.razveljaviZapisnik(redno));
        assertTrue(napaka.getMessage().contains("koncnico"));
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(redno).srecanje().status());

        Long finale = koncnica.serije().get(0).tekme().get(0).id();
        postava(finale, a, b, x, y);
        vnesi(finale, new int[] {0}, "3:0");
        assertEquals(StatusSrecanja.RAZPORED,
                srecanjeStoritev.razveljaviZapisnik(finale).srecanje().status());

        postava(finale, a, b, x, y);
        vnesi(finale, "3:0", "3:0", "3:0", "1:3", "3:2");
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(finale));
    }

    /* Organizator, ki lige ni ustvaril (in ni iz kluba lastnika), zapisnika ne
       razveljavi - in srecanje ostane, kakrsno je. */
    @Test
    void tujOrganizatorNeRazveljavi() {
        Liga l = pripraviLigo(false, null, null);
        SrecanjeDto sr0 = srecanja(l).get(0);
        Long s = sr0.id();
        List<Igralec> d = l.domaci(sr0);
        List<Igralec> g = l.gost(sr0);
        postava(s, d.get(0), d.get(1), g.get(0), g.get(1));
        vnesi(s, "3:1", "3:0", "3:2", "1:3", "0:3");

        Uporabnik tuj = new Uporabnik("tuj@test", "{bcrypt}x", Vloga.ORGANIZATOR);
        tuj.setStatus(StatusRacuna.POTRJEN);
        uporabnikRepozitorij.save(tuj);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tuj@test", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATOR"))));

        assertThrows(PrepovedanoIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(s));
        SecurityContextHolder.clearContext();
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(s).srecanje().status());
    }
}
