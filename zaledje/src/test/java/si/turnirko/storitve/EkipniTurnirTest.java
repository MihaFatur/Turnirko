/* Ekipni turnir (V28): prijava je ekipa, ekipna tekma dobi izid iz svojega
   srecanja, turnir pa tece po isti kodi kot pri posameznikih (mreza,
   skupine, koncna mesta). Posamicne tekme srecanj stejejo v rating s tezo
   turnirja, preneseni izidi finalnih skupin pa ne stejejo dvakrat.

   Varuje tudi tekmo za 3. mesto (porazenca polfinalov), ki jo pozna izlocilna
   mreza posameznikov in ekip. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DogodekVnos;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TipTekmeSrecanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;

class EkipniTurnirTest extends IntegracijskiTest {

    @Autowired private EkipeDogodkaStoritev ekipeDogodka;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;

    // ---------------------------------------------------------------
    // Nastavitve in prijave
    // ---------------------------------------------------------------

    @Test
    void ekipniDogodekZahtevaFormatInDobiPrag() {
        Turnir turnir = turnir();
        assertThrows(NeveljavenVnosIzjema.class, () -> turnirjiStoritev.dodajDogodek(turnir.getId(),
                vnos(SistemTekmovanja.IZLOCILNI, null)));

        Dogodek dogodek = turnirjiStoritev.dodajDogodek(turnir.getId(),
                vnos(SistemTekmovanja.IZLOCILNI, FormatSrecanja.EKIPNI_DP));
        assertEquals(Disciplina.EKIPNO, dogodek.getDisciplina());
        assertEquals(3, dogodek.getZmagZaSrecanje(), "pet tekem: prvi do treh zmag");
    }

    @Test
    void naEkipniDogodekSeIgralciNePrijavljajo() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI, FormatSrecanja.POKAL_NTZS);
        Igralec igralec = noviIgralec("Ana", "Samotna");
        assertThrows(DomenskaIzjema.class,
                () -> turnirjiStoritev.prijaviIgralce(dogodek.getId(), List.of(igralec.getId())));
    }

    @Test
    void igralecNastopaZaEnoSamoEkipoDogodka() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI, FormatSrecanja.POKAL_NTZS);
        List<EkipaDto> ekipe = prijaviEkipe(dogodek, 2, 3);
        Long igralecPrve = ekipeDogodka.kader(ekipe.get(0).id()).get(0).idIgralec();
        assertThrows(DomenskaIzjema.class,
                () -> ekipeDogodka.dodajVKader(ekipe.get(1).id(), new KaderVnos(igralecPrve, null)));
    }

    @Test
    void zrebZahtevaDovoljVelikKader() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI, FormatSrecanja.POKAL_NTZS);
        prijaviEkipe(dogodek, 2, 2);
        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class,
                () -> zrebStoritev.izvediZreb(dogodek.getId()));
        assertTrue(napaka.getMessage().contains("Premajhen kader"));
    }

    // ---------------------------------------------------------------
    // Izlocilna mreza ekip
    // ---------------------------------------------------------------

    @Test
    void izlocilnaMrezaEkipTeceSkoziSrecanja() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI, FormatSrecanja.EKIPNI_DP);
        prijaviEkipe(dogodek, 4, 3);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(3, tekme.size(), "polfinala in finale");
        List<Tekma> polfinala = tekme.stream().filter(t -> t.getKolo() == 1).toList();
        for (Tekma t : polfinala) {
            assertTrue(srecanjeRepozitorij.najdiZaTekmo(t.getId()).isPresent(),
                    "tekma z obema ekipama ima srecanje");
        }
        Tekma finale = tekme.stream().filter(t -> t.getKolo() == 2).findFirst().orElseThrow();
        assertTrue(srecanjeRepozitorij.najdiZaTekmo(finale.getId()).isEmpty(), "finale se nima ekip");

        // neposreden vnos izida ekipne tekme ni mogoc - izid nastane iz srecanja
        assertThrows(NeveljavenVnosIzjema.class, () -> tekmaStoritev.vnesiRezultat(
                polfinala.get(0).getId(), new VnosRezultata(null, 3, 0, null, null)));

        odigrajEkipno(polfinala.get(0).getId(), true);
        Tekma koncana = tekmaRepozitorij.najdiZVsem(polfinala.get(0).getId()).orElseThrow();
        assertEquals(StatusTekme.KONCANA, koncana.getStatus());
        assertEquals(3, koncana.getDobljeniNizi1(), "izid ekipne tekme so dobljene posamicne tekme");
        assertEquals(koncana.getPrijava1().getId(), koncana.getZmagovalec().getId());

        odigrajEkipno(polfinala.get(1).getId(), false);
        Srecanje finaleSrecanje = srecanjeRepozitorij.najdiZaTekmo(finale.getId()).orElseThrow();
        assertEquals(StatusSrecanja.RAZPORED, finaleSrecanje.getStatus(), "finale dobi srecanje po polfinalih");

        odigrajEkipno(finale.getId(), true);
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        Tekma koncanFinale = tekmaRepozitorij.najdiZVsem(finale.getId()).orElseThrow();
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        Prijava prvak = prijave.stream().filter(p -> p.getId().equals(koncanFinale.getZmagovalec().getId()))
                .findFirst().orElseThrow();
        assertEquals(1, prvak.getKoncnoMesto());

        // v rating gredo posamicne tekme srecanj (dvojice ne), s tezo turnirja
        long posamicnih = 0;
        for (Tekma t : tekmeDogodka(dogodek.getId())) {
            Srecanje s = srecanjeRepozitorij.najdiZaTekmo(t.getId()).orElseThrow();
            for (var ts : tekmaSrecanjaRepozitorij.najdiZaSrecanje(s.getId())) {
                if (ts.getStatus() == StatusTekmeSrecanja.KONCANA) {
                    boolean obracunana = ratingZgodovinaRepozitorij.existsByTekmaSrecanjaId(ts.getId());
                    assertEquals(ts.getTip() == TipTekmeSrecanja.POSAMICNA, obracunana,
                            "posamicna tekma srecanja se obracuna, dvojice ne");
                    if (obracunana) {
                        posamicnih++;
                    }
                }
            }
        }
        assertEquals(6, posamicnih, "tri srecanja po dve odigrani posamicni tekmi (3 : 0)");
        assertEquals(1.0, ratingZgodovinaRepozitorij.findAll().stream()
                .filter(z -> z.getTekmaSrecanja() != null)
                .findFirst().orElseThrow().getTeza(), 1e-9, "teza uradnega turnirja");
    }

    /* Kontekst zapisnika pove turnir, dogodek in mesto tekme v mrezi. */
    @Test
    void zapisnikEkipneTekmePoveTurnirInFazo() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.IZLOCILNI, FormatSrecanja.POKAL_NTZS);
        prijaviEkipe(dogodek, 4, 3);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma polfinale = tekmeDogodka(dogodek.getId()).stream().filter(t -> t.getKolo() == 1)
                .findFirst().orElseThrow();
        Srecanje s = srecanjeRepozitorij.najdiZaTekmo(polfinale.getId()).orElseThrow();

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.getId());
        assertEquals("Ekipni turnir", p.kontekst().tekmovanje());
        assertEquals("U15 ekipno", p.kontekst().dogodek());
        assertEquals("polfinale", p.kontekst().opis());
        assertEquals(dogodek.getTurnir().getId(), p.srecanje().idTurnir());
        assertEquals(FormatSrecanja.POKAL_NTZS, p.format());
    }

    // ---------------------------------------------------------------
    // Skupine za mesta s prenesenim izidom
    // ---------------------------------------------------------------

    @Test
    void finalneSkupinePrenesejoMedsebojniIzid() {
        Dogodek dogodek = ekipniDogodek(SistemTekmovanja.SKUPINE_ZA_MESTA, FormatSrecanja.POKAL_NTZS);
        prijaviEkipe(dogodek, 8, 3);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Skupina> predtekmovalne = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        assertEquals(2, predtekmovalne.size());
        assertEquals(12, tekmeDogodka(dogodek.getId()).size(), "dve skupini po stiri ekipe");

        // v vsaki tekmi zmaga ekipa z manjsim jakostnim mestom
        odigrajVse(dogodek.getId());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        List<Skupina> finalne = skupine.stream().filter(s -> s.getStopnja() == 2)
                .sorted(Comparator.comparing(Skupina::getPrvoMesto)).toList();
        assertEquals(2, finalne.size());
        assertEquals("1.–4. mesto", finalne.get(0).getIme());
        assertEquals(5, finalne.get(1).getPrvoMesto());

        List<Tekma> vFinalnih = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> finalne.stream().anyMatch(s -> s.getId().equals(t.getIdSkupina())))
                .toList();
        assertEquals(12, vFinalnih.size(), "dve finalni skupini po sest dvobojev");
        List<Tekma> prenesene = vFinalnih.stream().filter(Tekma::jePrenesena).toList();
        assertEquals(4, prenesene.size(), "v vsaki finalni skupini dva dvoboja iz predtekmovanja");
        for (Tekma t : prenesene) {
            assertEquals(StatusTekme.KONCANA, t.getStatus());
            assertTrue(srecanjeRepozitorij.najdiZaTekmo(t.getId()).isEmpty(), "prenesen izid nima srecanja");
        }

        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus());
        Set<Integer> mesta = new HashSet<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            assertNotNull(p.getKoncnoMesto());
            mesta.add(p.getKoncnoMesto());
        }
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), mesta);

        // rating: vsaka odigrana posamicna tekma natanko en obracun (dva zapisa)
        long srecanj = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> srecanjeRepozitorij.najdiZaTekmo(t.getId()).isPresent()).count();
        long zapisov = ratingZgodovinaRepozitorij.findAll().stream()
                .filter(z -> z.getTekmaSrecanja() != null).count();
        assertEquals(srecanj * 3 * 2, zapisov, "srecanje 3 : 0 = tri posamicne, prenesene ne stejejo");
    }

    // ---------------------------------------------------------------
    // Tekma za 3. mesto (posamezniki)
    // ---------------------------------------------------------------

    @Test
    void tekmaZaTretjeMestoDolociTretjegaInCetrtega() {
        Dogodek dogodek = pripraviJakostniDogodek(4, SistemTekmovanja.IZLOCILNI);
        dogodek.setTekmaZaTretjeMesto(true);
        dogodekRepozitorij.save(dogodek);
        zrebStoritev.izvediZreb(dogodek.getId());

        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        assertEquals(4, tekme.size(), "polfinala, finale in tekma za 3. mesto");
        Tekma zaTretje = tekme.stream().filter(t -> t.getFaza() == FazaTekme.TOLAZILNI).findFirst().orElseThrow();
        assertEquals(StatusTekme.CAKA, zaTretje.getStatus());

        for (Tekma polfinale : tekme.stream().filter(t -> t.getKolo() == 1).toList()) {
            tekmaStoritev.vnesiRezultat(polfinale.getId(), new VnosRezultata(null, 3, 1, null, null));
        }
        zaTretje = tekmaRepozitorij.najdiZVsem(zaTretje.getId()).orElseThrow();
        assertEquals(StatusTekme.PRIPRAVLJENA, zaTretje.getStatus(), "porazenca polfinalov sta znana");

        Tekma finale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == 2).findFirst().orElseThrow();
        tekmaStoritev.vnesiRezultat(finale.getId(), new VnosRezultata(null, 3, 0, null, null));
        assertEquals(StatusTekmovanja.V_TEKU, dogodekRepozitorij.findById(dogodek.getId()).orElseThrow().getStatus(),
                "dogodek caka se na tekmo za 3. mesto");
        tekmaStoritev.vnesiRezultat(zaTretje.getId(), new VnosRezultata(null, 3, 2, null, null));

        Set<Integer> mesta = new HashSet<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            mesta.add(p.getKoncnoMesto());
        }
        assertEquals(Set.of(1, 2, 3, 4), mesta);
    }

    // ---------------------------------------------------------------
    // Pomozno
    // ---------------------------------------------------------------

    private Turnir turnir() {
        Turnir turnir = new Turnir();
        turnir.setIme("Ekipni turnir");
        turnir.setDatumZacetka(LocalDate.now());
        turnir.setRaven(RavenTekmovanja.URADNO);
        return turnirRepozitorij.save(turnir);
    }

    private static DogodekVnos vnos(SistemTekmovanja sistem, FormatSrecanja format) {
        return new DogodekVnos("U15 ekipno", SpolKategorija.MOSKI, "U15", 5, null, null,
                Disciplina.EKIPNO, sistem, null, null, format, null, null);
    }

    private Dogodek ekipniDogodek(SistemTekmovanja sistem, FormatSrecanja format) {
        return turnirjiStoritev.dodajDogodek(turnir().getId(), vnos(sistem, format));
    }

    /* Ekipe z imeni po vrsti ("Ekipa 01" ...) - brez ratinga je jakostni
       vrstni red abecedni, zato je ekipa z manjso stevilko mocnejsa. */
    private List<EkipaDto> prijaviEkipe(Dogodek dogodek, int stEkip, int stIgralcev) {
        List<EkipaDto> ekipe = new ArrayList<>();
        for (int i = 1; i <= stEkip; i++) {
            String ime = String.format("Ekipa %02d", i);
            Klub klub = klubRepozitorij.save(new Klub(ime, null));
            EkipaDto ekipa = ekipeDogodka.dodajEkipo(dogodek.getId(), new EkipaVnos(klub.getId(), 1, ime));
            for (int j = 1; j <= stIgralcev; j++) {
                Igralec ig = noviIgralec("Ig" + i + "x" + j, "Ekipni" + i + "x" + j);
                ekipeDogodka.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), j));
            }
            ekipe.add(ekipa);
        }
        return ekipe;
    }

    /* Odigra vse pripravljene ekipne tekme dogodka; zmaga ekipa z manjsim
       jakostnim mestom. Ponavlja, dokler nastajajo nove (finalne skupine). */
    private void odigrajVse(Long idDogodka) {
        boolean kajOdigrano = true;
        while (kajOdigrano) {
            kajOdigrano = false;
            for (Tekma t : tekmeDogodka(idDogodka)) {
                if (t.getStatus() == StatusTekme.KONCANA || t.getPrijava1() == null || t.getPrijava2() == null) {
                    continue;
                }
                boolean prvaMocnejsa = t.getPrijava1().getStNosilca() < t.getPrijava2().getStNosilca();
                odigrajEkipno(t.getId(), prvaMocnejsa);
                kajOdigrano = true;
            }
        }
    }

    /* Postava po vrsti kadra in posamicne tekme 3 : 0 za izbrano stran, dokler
       srecanje ni koncano. Domaca ekipa srecanja je ekipa prve prijave tekme. */
    private void odigrajEkipno(Long idTekme, boolean zmagaPrva) {
        Srecanje s = srecanjeRepozitorij.najdiZaTekmo(idTekme).orElseThrow();
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.getId());
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), i >= p.pozicijeDomaci().size() - p.stVDvojici()));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), i >= p.pozicijeGost().size() - p.stVDvojici()));
        }
        srecanjeStoritev.nastaviPostavo(s.getId(), new PostavaVnos(mesta));
        for (TekmaSrecanjaDto t : srecanjeStoritev.podrobno(s.getId()).tekme()) {
            if (srecanjeStoritev.podrobno(s.getId()).srecanje().status() == StatusSrecanja.KONCANO) {
                break;
            }
            srecanjeStoritev.vnesiRezultat(t.id(), new VnosRezultataSrecanja(
                    null, zmagaPrva ? 3 : 0, zmagaPrva ? 0 : 3, null, null));
        }
        assertFalse(srecanjeStoritev.podrobno(s.getId()).srecanje().status() != StatusSrecanja.KONCANO,
                "srecanje mora biti koncano");
    }

}
