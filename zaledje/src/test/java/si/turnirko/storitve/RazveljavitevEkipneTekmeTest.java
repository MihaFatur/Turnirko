/* Razveljavitev zapisnika EKIPNE TEKME TURNIRJA (RazveljavitevEkipneTekmeStoritev
   prek SrecanjeStoritev.razveljaviZapisnik) - isto kot pri ligi, a izid
   srecanja je tu postal izid tekme v mrezi ali skupini in je sprozil turnir.

   Kar mora test cuvati:
   1. po razveljavitvi je turnir tak, kot da tekme ni bilo: tekma spet
      pripravljena, zmagovalec gre iz naslednje tekme (ta spet caka in nima
      srecanja), rating je pred srecanjem, po ponovnem vnosu "vse stima",
   2. dogodek, zakljucen s to tekmo, spet tece, koncna mesta gredo, zaprt
      turnir se odpre,
   3. skupinska tekma vrne mesta v skupini in pobere, kar je nastalo iz skupine
      (finalne skupine za mesta, izlocilni del), dokler se to ni zacelo,
   4. razveljavitev se zavrne, ko je po tekmi ze teklo naprej (zacet naslednji
      dvoboj, zacet izlocilni del), in nic ne spremeni; po razveljavitvi
      naslednjega dvoboja gre. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.RatingZgodovina;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

class RazveljavitevEkipneTekmeTest extends IntegracijskiTest {

    @Autowired private EkipeDogodkaStoritev ekipeDogodka;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private PreracunRatingaStoritev preracunRatinga;
    @Autowired private IzlocilniZrebStoritev izlocilniZreb;
    @PersistenceContext private EntityManager seja;

    // ---------------------------------------------------------------
    // Izlocilna mreza
    // ---------------------------------------------------------------

    /* Polfinale je bilo vpisano s pravo ekipo, a z napacnimi igralci. Po
       razveljavitvi ekipa iz finala odide, srecanje finala gre (nastane znova),
       rating je pred srecanjem; po ponovnem vnosu z drugim zmagovalcem je v
       finalu druga ekipa in rating tak, kot ga da preracun od zacetka. */
    @Test
    void razveljavitevPolfinalaVrneMrezoInPoVnosuStima() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI);
        prijaviEkipe(dogodek, 4, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        Tekma prvo = tekme.stream().filter(t -> t.getKolo() == 1).findFirst().orElseThrow();
        Tekma drugo = tekme.stream().filter(t -> t.getKolo() == 1 && !t.getId().equals(prvo.getId()))
                .findFirst().orElseThrow();
        Tekma finale = tekme.stream().filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        Map<String, String> pred = posnetekRatingov();

        odigraj(prvo.getId(), true, 0);
        Long zmagovalecPrvega = tekma(prvo.getId()).getZmagovalec().getId();
        assertEquals(1, udelezencevFinala(finale.getId()), "zmagovalec je napredoval");
        odigraj(drugo.getId(), true, 0);
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(finale.getId()).getStatus());
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(finale.getId()).isPresent());
        Long srecanjePrvega = srecanjeRepozitorij.najdiZaTekmo(prvo.getId()).orElseThrow().getId();
        List<Long> tekmeSrecanja = tekmeSrecanja(srecanjePrvega);
        assertFalse(ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(tekmeSrecanja, RatingStanje.SISTEM_TURNIRKO).isEmpty());

        SrecanjePodrobnoDto po = srecanjeStoritev.razveljaviZapisnik(srecanjePrvega);

        assertEquals(StatusSrecanja.RAZPORED, po.srecanje().status());
        assertTrue(po.tekme().isEmpty(), "tekme gredo z zapisnikom");
        assertTrue(po.postave().isEmpty(), "postava gre z zapisnikom");
        seja.flush();
        seja.clear();
        Tekma polfinale = tekma(prvo.getId());
        assertEquals(StatusTekme.PRIPRAVLJENA, polfinale.getStatus());
        assertNull(polfinale.getZmagovalec());
        assertNull(polfinale.getIzidTip());
        assertEquals(0, polfinale.getDobljeniNizi1() + polfinale.getDobljeniNizi2());
        Tekma po2 = tekma(finale.getId());
        assertEquals(StatusTekme.CAKA, po2.getStatus(), "finale spet caka");
        assertEquals(1, udelezencevFinala(finale.getId()), "ostane le zmagovalec drugega polfinala");
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(finale.getId()).isEmpty(),
                "finale nima srecanja, dokler ni znanih obeh ekip");
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(tekmeSrecanja, RatingStanje.SISTEM_TURNIRKO).isEmpty());

        // ponovni vnos z drugimi igralci in obrnjenim izidom
        odigraj(prvo.getId(), false, 1);
        Tekma ponovno = tekma(prvo.getId());
        assertNotEquals(zmagovalecPrvega, ponovno.getZmagovalec().getId(), "zmagala je druga ekipa");
        assertEquals(2, udelezencevFinala(finale.getId()));
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(finale.getId()).getStatus());
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(finale.getId()).isPresent());

        Map<String, String> poVnosu = posnetekRatingov();
        preracunRatinga.preracunajOd(null);
        assertEquals(poVnosu, posnetekRatingov(), "ponovni vnos mora dati isto kot preracun");
        assertNotEquals(pred, poVnosu);
    }

    /* Tekma za 3. mesto prejme porazenca polfinala: razveljavitev polfinala
       pobere tudi njega, ne le zmagovalca. */
    @Test
    void razveljavitevPolfinalaPoberePorazencaIzTekmeZaTretjeMesto() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI);
        dogodek.setTekmaZaTretjeMesto(true);
        dogodekRepozitorij.save(dogodek);
        prijaviEkipe(dogodek, 4, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> polfinala = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 1 && t.getFaza() == FazaTekme.GLAVNI).toList();
        Tekma zaTretje = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.TOLAZILNI).findFirst().orElseThrow();

        odigraj(polfinala.get(0).getId(), true, 0);
        odigraj(polfinala.get(1).getId(), true, 0);
        assertEquals(2, udelezencevFinala(zaTretje.getId()));
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(zaTretje.getId()).isPresent());

        srecanjeStoritev.razveljaviZapisnik(
                srecanjeRepozitorij.najdiZaTekmo(polfinala.get(0).getId()).orElseThrow().getId());

        seja.flush();
        seja.clear();
        assertEquals(1, udelezencevFinala(zaTretje.getId()), "porazenec prvega polfinala je odsel");
        assertEquals(StatusTekme.CAKA, tekma(zaTretje.getId()).getStatus());
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(zaTretje.getId()).isEmpty());
    }

    /* Finale je zadnja tekma: dogodek je bil zakljucen, turnir zaprt. Po
       razveljavitvi oboje spet tece, mest ni; po novem vnosu je prvak druga
       ekipa. */
    @Test
    void razveljavitevFinalaOdpreZakljucenDogodekInTurnir() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI);
        prijaviEkipe(dogodek, 2, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma finale = tekmeDogodka(dogodek.getId()).get(0);
        odigraj(finale.getId(), true, 0);
        Long idTurnirja = dogodek.getTurnir().getId();
        turnirjiStoritev.zakljuci(idTurnirja);
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, turnirRepozitorij.findById(idTurnirja).orElseThrow().getStatus());
        Long prvakPrej = tekma(finale.getId()).getZmagovalec().getId();
        assertEquals(1, prijavaRepozitorij.findById(prvakPrej).orElseThrow().getKoncnoMesto());

        srecanjeStoritev.razveljaviZapisnik(
                srecanjeRepozitorij.najdiZaTekmo(finale.getId()).orElseThrow().getId());

        seja.flush();
        seja.clear();
        assertEquals(StatusTekmovanja.V_TEKU, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(StatusTekmovanja.V_TEKU, turnirRepozitorij.findById(idTurnirja).orElseThrow().getStatus());
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNull(p.getKoncnoMesto(), "koncna mesta so nastala ob zakljucku");
        }
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(finale.getId()).getStatus());

        odigraj(finale.getId(), false, 0);
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        Long prvakPotem = tekma(finale.getId()).getZmagovalec().getId();
        assertNotEquals(prvakPrej, prvakPotem);
        assertEquals(1, prijavaRepozitorij.findById(prvakPotem).orElseThrow().getKoncnoMesto());
        assertEquals(2, prijavaRepozitorij.findById(prvakPrej).orElseThrow().getKoncnoMesto());
    }

    /* Srecanje v teku (postava in prvi izid): tekma je V_IGRI, posledic ni.
       Razveljavitev jo vrne med pripravljene. */
    @Test
    void razveljavitevSrecanjaVTekuVrneTekmoMedPripravljene() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI);
        prijaviEkipe(dogodek, 4, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma polfinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getKolo() == 1).findFirst().orElseThrow();
        Srecanje s = srecanjeRepozitorij.najdiZaTekmo(polfinale.getId()).orElseThrow();
        vpisiPostavo(s.getId(), 0);
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(s.getId()).tekme();
        srecanjeStoritev.vnesiRezultat(tekme.get(0).id(), new VnosRezultataSrecanja(null, 3, 0, null, null));
        assertEquals(StatusTekme.V_IGRI, tekma(polfinale.getId()).getStatus());

        SrecanjePodrobnoDto po = srecanjeStoritev.razveljaviZapisnik(s.getId());

        assertEquals(StatusSrecanja.RAZPORED, po.srecanje().status());
        assertTrue(po.tekme().isEmpty());
        seja.flush();
        seja.clear();
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(polfinale.getId()).getStatus());

        // srecanje brez zapisnika nima kaj razveljaviti
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(s.getId()));
    }

    // ---------------------------------------------------------------
    // Kjer je turnir ze tekel naprej
    // ---------------------------------------------------------------

    /* Finale je ze zacelo: polfinala ni mogoce razveljaviti in nic se ne
       spremeni. Ko je razveljavljeno finale, gre tudi polfinale. */
    @Test
    void polfinalaNiMogoceRazveljavitiDokJeFinaleZacelo() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI);
        prijaviEkipe(dogodek, 4, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        Tekma prvo = tekme.stream().filter(t -> t.getKolo() == 1).findFirst().orElseThrow();
        Tekma drugo = tekme.stream().filter(t -> t.getKolo() == 1 && !t.getId().equals(prvo.getId()))
                .findFirst().orElseThrow();
        Tekma finale = tekme.stream().filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        odigraj(prvo.getId(), true, 0);
        odigraj(drugo.getId(), true, 0);
        Long srecanjePrvega = srecanjeRepozitorij.najdiZaTekmo(prvo.getId()).orElseThrow().getId();
        Long srecanjeFinala = srecanjeRepozitorij.najdiZaTekmo(finale.getId()).orElseThrow().getId();
        vpisiPostavo(srecanjeFinala, 0);
        assertEquals(StatusTekme.V_IGRI, tekma(finale.getId()).getStatus());
        Map<String, String> pred = posnetekRatingov();

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> srecanjeStoritev.razveljaviZapisnik(srecanjePrvega));
        assertTrue(napaka.getMessage().contains("razveljavi njen zapisnik"), napaka.getMessage());

        seja.flush();
        seja.clear();
        assertEquals(StatusTekme.KONCANA, tekma(prvo.getId()).getStatus(), "nic se ni spremenilo");
        assertEquals(StatusSrecanja.KONCANO, srecanjeRepozitorij.findById(srecanjePrvega).orElseThrow().getStatus());
        assertEquals(2, udelezencevFinala(finale.getId()));
        assertEquals(pred, posnetekRatingov());

        srecanjeStoritev.razveljaviZapisnik(srecanjeFinala);
        srecanjeStoritev.razveljaviZapisnik(srecanjePrvega);
        seja.flush();
        seja.clear();
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(prvo.getId()).getStatus());
        assertEquals(StatusTekme.CAKA, tekma(finale.getId()).getStatus());
    }

    // ---------------------------------------------------------------
    // Skupine
    // ---------------------------------------------------------------

    /* Zadnja predtekmovalna tekma je sprozila finalne skupine za mesta. Po
       razveljavitvi finalnih skupin ni, skupina tekme nima mest, druga ima
       svoja; po ponovnem vnosu finalne skupine nastanejo znova. */
    @Test
    void razveljavitevSkupinskeTekmePoberFinalneSkupine() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.SKUPINE_ZA_MESTA);
        prijaviEkipe(dogodek, 8, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> predtekmovanje = tekmeDogodka(dogodek.getId());
        assertEquals(12, predtekmovanje.size());
        for (Tekma t : predtekmovanje) {
            odigraj(t.getId(), jePrvaMocnejsa(t), 0);
        }
        assertEquals(2, finalneSkupine(dogodek.getId()).size());
        assertEquals(24, tekmeDogodka(dogodek.getId()).size());

        Tekma izbrana = predtekmovanje.get(0);
        Long idSkupine = izbrana.getIdSkupina();
        Map<Long, Integer> mestaDrugeSkupine = mestaVSkupini(dogodek.getId(), idSkupine, false);
        assertTrue(mestaDrugeSkupine.values().stream().allMatch(m -> m != null));

        srecanjeStoritev.razveljaviZapisnik(
                srecanjeRepozitorij.najdiZaTekmo(izbrana.getId()).orElseThrow().getId());

        seja.flush();
        seja.clear();
        assertTrue(finalneSkupine(dogodek.getId()).isEmpty(), "finalne skupine so nastale iz izida skupin");
        assertEquals(12, tekmeDogodka(dogodek.getId()).size(), "ostane samo predtekmovanje");
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(izbrana.getId()).getStatus());
        assertTrue(mestaVSkupini(dogodek.getId(), idSkupine, true).values().stream().allMatch(m -> m == null),
                "skupina ni vec odigrana do konca");
        assertEquals(mestaDrugeSkupine, mestaVSkupini(dogodek.getId(), idSkupine, false),
                "druga skupina je se odigrana");
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNull(p.getKoncnoMesto());
        }

        odigraj(izbrana.getId(), jePrvaMocnejsa(izbrana), 0);
        assertEquals(2, finalneSkupine(dogodek.getId()).size(), "finalne skupine nastanejo znova");
        assertEquals(24, tekmeDogodka(dogodek.getId()).size());
    }

    /* Finalna skupina je ze zacela: skupinske tekme ni mogoce razveljaviti. */
    @Test
    void skupinskeTekmeNiMogoceRazveljavitiDokSoFinalneSkupineZacele() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.SKUPINE_ZA_MESTA);
        prijaviEkipe(dogodek, 8, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> predtekmovanje = tekmeDogodka(dogodek.getId());
        for (Tekma t : predtekmovanje) {
            odigraj(t.getId(), jePrvaMocnejsa(t), 0);
        }
        Tekma izFinalne = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> !t.jePrenesena() && t.getStatus() == StatusTekme.PRIPRAVLJENA
                        && finalneSkupine(dogodek.getId()).stream()
                                .anyMatch(s -> s.getId().equals(t.getIdSkupina())))
                .findFirst().orElseThrow();
        odigraj(izFinalne.getId(), true, 0);

        Long srecanjeSkupine = srecanjeRepozitorij.najdiZaTekmo(predtekmovanje.get(0).getId())
                .orElseThrow().getId();
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(srecanjeSkupine));
        assertEquals(2, finalneSkupine(dogodek.getId()).size(), "nic se ni spremenilo");

        srecanjeStoritev.razveljaviZapisnik(
                srecanjeRepozitorij.najdiZaTekmo(izFinalne.getId()).orElseThrow().getId());
        srecanjeStoritev.razveljaviZapisnik(srecanjeSkupine);
        seja.flush();
        seja.clear();
        assertTrue(finalneSkupine(dogodek.getId()).isEmpty());
    }

    /* Zadnja tekma v finalni skupini zakljuci dogodek; razveljavitev dogodek
       odpre in pobere mesta iz finalnih skupin (finalne skupine ostanejo).
       Ponovni vnos mesta vrne. */
    @Test
    void razveljavitevZadnjeTekmeFinalneSkupineOdpreDogodek() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.SKUPINE_ZA_MESTA);
        prijaviEkipe(dogodek, 8, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        Map<Long, Integer> mestaPrej = koncnaMesta(dogodek.getId());
        Tekma zadnja = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> !t.jePrenesena())
                .max(Comparator.comparingLong(Tekma::getId)).orElseThrow();
        Long srecanje = srecanjeRepozitorij.najdiZaTekmo(zadnja.getId()).orElseThrow().getId();

        srecanjeStoritev.razveljaviZapisnik(srecanje);

        seja.flush();
        seja.clear();
        assertEquals(StatusTekmovanja.V_TEKU, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(2, finalneSkupine(dogodek.getId()).size(), "finalne skupine ostanejo");
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNull(p.getKoncnoMesto(), "vsa mesta so nastala ob zakljucku");
        }
        odigraj(zadnja.getId(), jePrvaMocnejsa(tekma(zadnja.getId())), 0);
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        assertEquals(mestaPrej, koncnaMesta(dogodek.getId()));
    }

    /* Izlocilni del po skupinah nastane iz izidov skupin: razveljavitev
       skupinske tekme ga pobere (dokler ni zacet), nato se zrebanje ponovi. */
    @Test
    void razveljavitevSkupinskeTekmePoberIzlocilniDel() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.SKUPINE_IZLOCILNI);
        prijaviEkipe(dogodek, 8, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Tekma> skupinske = tekmeDogodka(dogodek.getId());
        for (Tekma t : skupinske) {
            odigraj(t.getId(), jePrvaMocnejsa(t), 0);
        }
        izlocilniZreb.izvediZreb(dogodek.getId());
        assertEquals(3, glavne(dogodek.getId()).size());

        Tekma izbrana = skupinske.get(0);
        srecanjeStoritev.razveljaviZapisnik(
                srecanjeRepozitorij.najdiZaTekmo(izbrana.getId()).orElseThrow().getId());

        seja.flush();
        seja.clear();
        assertTrue(glavne(dogodek.getId()).isEmpty(), "izlocilni del je nastal iz skupin");
        assertEquals(skupinske.size(), tekmeDogodka(dogodek.getId()).size());
        assertEquals(StatusTekme.PRIPRAVLJENA, tekma(izbrana.getId()).getStatus());

        odigraj(izbrana.getId(), jePrvaMocnejsa(izbrana), 0);
        izlocilniZreb.izvediZreb(dogodek.getId());
        assertEquals(3, glavne(dogodek.getId()).size());

        // izlocilni del je zacet: skupinske tekme ni vec mogoce razveljaviti
        Tekma polfinale = glavne(dogodek.getId()).stream().filter(t -> t.getKolo() == 1).findFirst().orElseThrow();
        vpisiPostavo(srecanjeRepozitorij.najdiZaTekmo(polfinale.getId()).orElseThrow().getId(), 0);
        Long srecanjeIzbrane = srecanjeRepozitorij.najdiZaTekmo(izbrana.getId()).orElseThrow().getId();
        assertThrows(DomenskaIzjema.class, () -> srecanjeStoritev.razveljaviZapisnik(srecanjeIzbrane));
        assertEquals(3, glavne(dogodek.getId()).size());
    }

    // ---------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------

    private Tekma tekma(Long id) {
        return tekmaRepozitorij.najdiZVsem(id).orElseThrow();
    }

    private List<Tekma> glavne(Long idDogodka) {
        return tekmeDogodka(idDogodka).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI || t.getFaza() == FazaTekme.TOLAZILNI).toList();
    }

    private List<Skupina> finalneSkupine(Long idDogodka) {
        return skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka).stream()
                .filter(s -> s.getStopnja() > 1).toList();
    }

    /* Koliko udelezencev ima tekma (0, 1 ali 2). */
    private int udelezencevFinala(Long idTekme) {
        Tekma t = tekma(idTekme);
        return (t.getPrijava1() != null ? 1 : 0) + (t.getPrijava2() != null ? 1 : 0);
    }

    /* Mesta v skupini: ali prijave skupine tekme (`tista`) ali vseh ostalih. */
    private Map<Long, Integer> mestaVSkupini(Long idDogodka, Long idSkupine, boolean tista) {
        List<Tekma> tekme = tekmeDogodka(idDogodka);
        Map<Long, Integer> mesta = new HashMap<>();
        Map<Long, Prijava> poId = new HashMap<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(idDogodka)) {
            poId.put(p.getId(), p);
        }
        for (Skupina s : skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)) {
            if (s.getStopnja() != 1 || s.getId().equals(idSkupine) != tista) {
                continue;
            }
            for (Prijava p : SkupineStoritev.clani(s, new ArrayList<>(poId.values()), tekme)) {
                mesta.put(p.getId(), p.getMestoVSkupini());
            }
        }
        return mesta;
    }

    private Map<Long, Integer> koncnaMesta(Long idDogodka) {
        Map<Long, Integer> mesta = new HashMap<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(idDogodka)) {
            mesta.put(p.getId(), p.getKoncnoMesto());
        }
        return mesta;
    }

    /* Rating vsakega igralca in vsi zapisi dnevnika srecanj - po njih se
       primerja stanje po vnosu s stanjem po preracunu od zacetka. */
    private Map<String, String> posnetekRatingov() {
        seja.flush();
        seja.clear();
        Map<String, String> m = new HashMap<>();
        ratingStanjeRepozitorij.findAll().forEach(
                r -> m.put("rating/" + r.getIgralec().getId(), String.valueOf(r.getVrednost())));
        for (RatingZgodovina r : ratingZgodovinaRepozitorij.findAll()) {
            if (r.getTekmaSrecanja() != null) {
                m.put(r.getTekmaSrecanja().getId() + "/" + r.getIgralec().getId(),
                        r.getVeljaOb() + " " + r.getSprememba() + " " + r.getNovaVrednost());
            }
        }
        return m;
    }

    private List<Long> tekmeSrecanja(Long idSrecanje) {
        return tekmaSrecanjaRepozitorij.najdiZaSrecanje(idSrecanje).stream()
                .map(si.turnirko.modeli.TekmaSrecanja::getId).toList();
    }

    private Turnir turnir() {
        Turnir turnir = new Turnir();
        turnir.setIme("Ekipni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        return turnirRepozitorij.save(turnir);
    }

    private Dogodek ekipniDogodek(SistemTekmovanja sistem) {
        return turnirjiStoritev.dodajDogodek(turnir().getId(), new DogodekVnos("U15 ekipno",
                SpolKategorija.MOSKI, "U15", 5, null, null, Disciplina.EKIPNO, sistem, null, null,
                FormatSrecanja.POKAL_NTZS, null, null));
    }

    /* Ekipe z imeni po vrsti ("Ekipa 01" ...): brez ratinga je jakostni vrstni
       red abecedni, zato je ekipa z manjso stevilko mocnejsa. Igralci imajo
       postavljen rating 1500, da ne tece uvrstitev novinca in so spremembe
       navadni koraki. */
    private List<EkipaDto> prijaviEkipe(Dogodek dogodek, int stEkip, int stIgralcev) {
        List<EkipaDto> ekipe = new ArrayList<>();
        for (int i = 1; i <= stEkip; i++) {
            String ime = String.format("Ekipa %02d", i);
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            EkipaDto ekipa = ekipeDogodka.dodajEkipo(dogodek.getId(), new EkipaVnos(klub.getId(), 1, ime));
            for (int j = 1; j <= stIgralcev; j++) {
                Igralec ig = noviIgralec("Ig" + i + "x" + j, "Ekipni" + i + "x" + j);
                ratingStoritev.nastaviZacetniRating(ig, 1500);
                ekipeDogodka.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), j));
            }
            ekipe.add(ekipa);
        }
        return ekipe;
    }

    private static boolean jePrvaMocnejsa(Tekma t) {
        return t.getPrijava1().getStNosilca() < t.getPrijava2().getStNosilca();
    }

    /* Odigra vse pripravljene ekipne tekme, dokler nastajajo nove. */
    private void odigrajVse(Long idDogodka) {
        boolean kajOdigrano = true;
        while (kajOdigrano) {
            kajOdigrano = false;
            for (Tekma t : tekmeDogodka(idDogodka)) {
                if (t.getStatus() == StatusTekme.KONCANA || t.getPrijava1() == null || t.getPrijava2() == null) {
                    continue;
                }
                odigraj(t.getId(), jePrvaMocnejsa(t), 0);
                kajOdigrano = true;
            }
        }
    }

    /* Postava iz kadra od `zamik`-tega igralca naprej (zamik 1 = drugi trije
       igralci kadra stirih), nato posamicne tekme 3 : 0 za izbrano stran. Domaca
       ekipa srecanja je ekipa prve prijave tekme. */
    private void odigraj(Long idTekme, boolean zmagaPrva, int zamik) {
        Long idSrecanja = srecanjeRepozitorij.najdiZaTekmo(idTekme).orElseThrow().getId();
        vpisiPostavo(idSrecanja, zamik);
        for (TekmaSrecanjaDto t : srecanjeStoritev.podrobno(idSrecanja).tekme()) {
            if (srecanjeStoritev.podrobno(idSrecanja).srecanje().status() == StatusSrecanja.KONCANO) {
                break;
            }
            srecanjeStoritev.vnesiRezultat(t.id(), new VnosRezultataSrecanja(
                    null, zmagaPrva ? 3 : 0, zmagaPrva ? 0 : 3, null, null));
        }
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(idSrecanja).srecanje().status(),
                "srecanje mora biti koncano");
        assertNotNull(tekma(idTekme).getZmagovalec());
    }

    private void vpisiPostavo(Long idSrecanja, int zamik) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanja);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i + zamik).idIgralec(), false));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i + zamik).idIgralec(), false));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanja, new PostavaVnos(mesta));
    }
}
