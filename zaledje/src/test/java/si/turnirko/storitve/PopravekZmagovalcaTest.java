/* Zamenjava zmagovalca turnirske tekme (TekmaStoritev.popraviRezultat,
   PoslediceIzidaStoritev).

   Kar mora test cuvati:
   1. popravek zadnje tekme veje zamenja zmagovalca, koncna mesta in rating, dogodek
      pa ostane zakljucen - to je primer, zaradi katerega je popravek nastal,
   2. kjer je po tekmi kaj odigrano, brez izbire organizatorja ne gre NIC,
   3. izbris izidov izprazni celo vejo in pusti dogodek odprt za nov vnos,
   4. zamenjava igralcev pusti izide na svoji strani tekme,
   5. obracun tekme, ki je ostala brez izida, izgine iz dnevnika - preracun brise
      samo zapise tekem, ki jih odigra znova,
   6. rating po popravku je ves cas enak preracunu od zacetka. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PopravekPredogledDto;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPopravka;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.Zreb;
import si.turnirko.repozitoriji.ZrebRepozitorij;

class PopravekZmagovalcaTest extends IntegracijskiTest {

    private static final VnosRezultata PRVI_3_0 = new VnosRezultata(null, 3, 0, null, null);
    private static final VnosRezultata DRUGI_3_0 = new VnosRezultata(null, 0, 3, null, null);

    @Autowired private RatingStoritev ratingStoritev;
    @Autowired private SvRegijaStoritev svRegija;
    @Autowired private IzlocilniZrebStoritev izlocilniZreb;
    @Autowired private PreracunRatingaStoritev preracunRatinga;
    @Autowired private ZrebRepozitorij zrebRepozitorij;
    @PersistenceContext private EntityManager seja;

    @BeforeEach
    void ponovljivZreb() {
        zrebStoritev.nastaviNakljucje(new Random(11));
    }

    // ---------- Mreza: zadnja tekma (primer lastnika) ----------

    @Test
    void popravekFinalaZamenjaZmagovalcaMestaInRating() {
        Dogodek dogodek = pripraviDogodek(2);
        // postavitev in ne rocni zapis: preracun jo prebere iz dnevnika in obnovi
        for (Prijava p : prijavePoVrsti(dogodek.getId())) {
            ratingStoritev.nastaviZacetniRating(p.getIgralec(), 1500);
        }
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma finale = tekmeDogodka(dogodek.getId()).get(0);
        Prijava prvi = finale.getPrijava1();
        Prijava drugi = finale.getPrijava2();
        tekmaStoritev.vnesiRezultat(finale.getId(), PRVI_3_0);
        int ratingZmagovalca = rating(prvi);
        int ratingPorazenca = rating(drugi);
        assertTrue(ratingZmagovalca > 1500);
        assertEquals(1, prijava(prvi).getKoncnoMesto());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());

        // odlocitve ni: za finalom ni nicesar
        assertTrue(predogled(finale, DRUGI_3_0).izbire().isEmpty());
        tekmaStoritev.popraviRezultat(finale.getId(), DRUGI_3_0);
        izprazniSejo();

        Tekma po = tekmaRepozitorij.findById(finale.getId()).orElseThrow();
        assertEquals(drugi.getId(), po.getZmagovalec().getId());
        assertEquals(1, prijava(drugi).getKoncnoMesto());
        assertEquals(2, prijava(prvi).getKoncnoMesto());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus(),
                "za finalom ni nicesar, zato dogodek ostane zakljucen");
        // izhodisce je obema enako, zato si spremembi natanko zamenjata
        assertEquals(ratingZmagovalca, rating(drugi), "novi zmagovalec dobi, kar je dobil prejsnji");
        assertEquals(ratingPorazenca, rating(prvi), "nekdanji zmagovalec izgubi, kar je izgubil prejsnji");
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekme(List.of(finale.getId()), RatingStanje.SISTEM_TURNIRKO).size());
        ratingEnakPreracunu();
    }

    /* SV regija, zadnji par veje: sprememba velja tudi za mesta, ki niso prva. */
    @Test
    void popravekZadnjeTekmeVejeZrebaZamenjaSamoTistiMesti() {
        Dogodek dogodek = svDogodekSSkupinami();
        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());

        Tekma zaSedmo = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getMestoZmagovalca() != null && t.getMestoZmagovalca() == 7)
                .findFirst().orElseThrow();
        Map<Long, Integer> mestaPrej = mesta(dogodek);
        Long zmagovalec = zaSedmo.getZmagovalec().getId();
        Long porazenec = zaSedmo.porazenec().getId();
        assertEquals(7, mestaPrej.get(zmagovalec));
        assertEquals(8, mestaPrej.get(porazenec));

        assertTrue(predogled(zaSedmo, DRUGI_3_0).izbire().isEmpty());
        tekmaStoritev.popraviRezultat(zaSedmo.getId(), DRUGI_3_0);
        izprazniSejo();

        Map<Long, Integer> pricakovana = new HashMap<>(mestaPrej);
        pricakovana.put(zmagovalec, 8);
        pricakovana.put(porazenec, 7);
        assertEquals(pricakovana, mesta(dogodek), "drugi igralci obdrzijo svoja mesta");
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
    }

    // ---------- Mreza: naslednje tekme ----------

    /* Polfinale, finale se ni vneseno: igralec se v finalu samo zamenja. */
    @Test
    void naslednjaTekmaBrezIzidaDobiNovegaIgralca() {
        Dogodek dogodek = pripraviJakostniDogodek(4, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma polfinale = polfinale(dogodek, 0);
        tekmaStoritev.vnesiRezultat(polfinale.getId(), PRVI_3_0);
        Prijava novZmagovalec = polfinale.getPrijava2();

        PopravekPredogledDto predogled = predogled(polfinale, DRUGI_3_0);
        assertTrue(predogled.zamenjanZmagovalec());
        assertTrue(predogled.izbire().isEmpty());
        assertEquals(1, predogled.odvisnihBrezIzida());

        tekmaStoritev.popraviRezultat(polfinale.getId(), DRUGI_3_0);
        izprazniSejo();

        Tekma finale = finale(dogodek);
        assertEquals(novZmagovalec.getId(), udelezenecIzPolfinala(finale, polfinale.getId()).getId());
    }

    @Test
    void brezIzbireNeSpremeniNicesarKoJeNaslednjaTekmaOdigrana() {
        Dogodek dogodek = odigranaMreza4();
        Tekma polfinale = polfinale(dogodek, 0);
        Long zmagovalecPrej = polfinale.getZmagovalec().getId();

        assertEquals(List.of(NacinPopravka.IZBRISI_IZIDE, NacinPopravka.ZAMENJAJ_IGRALCA),
                predogled(polfinale, DRUGI_3_0).izbire());
        assertEquals(1, predogled(polfinale, DRUGI_3_0).odvisneZIzidom().size());

        assertThrows(DomenskaIzjema.class,
                () -> tekmaStoritev.popraviRezultat(polfinale.getId(), DRUGI_3_0));
        // napacna vrsta izbire ne velja
        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.popraviRezultat(
                polfinale.getId(), DRUGI_3_0, NacinPopravka.PODRI_NASLEDNJI_DEL));

        izprazniSejo();
        assertEquals(zmagovalecPrej,
                tekmaRepozitorij.findById(polfinale.getId()).orElseThrow().getZmagovalec().getId());
        assertEquals(StatusTekme.KONCANA, finale(dogodek).getStatus());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
    }

    @Test
    void izbrisIzidovIzprazniVejoInOdpreDogodek() {
        Dogodek dogodek = odigranaMreza4();
        Turnir turnir = dogodek(dogodek).getTurnir();
        turnirjiStoritev.zakljuci(turnir.getId());
        Tekma polfinale = polfinale(dogodek, 0);
        Prijava novZmagovalec = polfinale.getPrijava2();
        Long idFinala = finale(dogodek).getId();

        tekmaStoritev.popraviRezultat(polfinale.getId(), DRUGI_3_0, NacinPopravka.IZBRISI_IZIDE);
        izprazniSejo();

        Tekma finale = finale(dogodek);
        assertEquals(StatusTekme.PRIPRAVLJENA, finale.getStatus());
        assertNull(finale.getZmagovalec());
        assertNull(finale.getIzidTip());
        assertEquals(0, finale.getDobljeniNizi1());
        assertEquals(0, finale.getDobljeniNizi2());
        assertEquals(novZmagovalec.getId(), udelezenecIzPolfinala(finale, polfinale.getId()).getId());
        assertTrue(nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(idFinala).isEmpty(),
                "tocke izbrisanega izida gredo z njim");
        assertTrue(ratingZgodovinaRepozitorij
                        .spremembeZaTekme(List.of(idFinala), RatingStanje.SISTEM_TURNIRKO).isEmpty(),
                "tekma brez izida nima obracuna");

        assertEquals(StatusTekmovanja.V_TEKU, dogodek(dogodek).getStatus());
        assertEquals(StatusTekmovanja.V_TEKU, turnirRepozitorij.findById(turnir.getId()).orElseThrow().getStatus());
        assertTrue(prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .allMatch(p -> p.getKoncnoMesto() == null), "mesta nastanejo ob novem zakljucku");
        ratingEnakPreracunu();

        // manjkajoci izid se vnese kot prej in dogodek se zakljuci znova
        tekmaStoritev.vnesiRezultat(idFinala, PRVI_3_0);
        izprazniSejo();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
        assertEquals(1, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getKoncnoMesto())).count());
    }

    /* Tudi tekma za naslednjo: izprazni se cela veja, igralec iz nje ni vec znan. */
    @Test
    void izbrisIzidovSegaDoKoncaVeje() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());

        Tekma prvoKolo = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == 1 && t.getPozicija() == 1)
                .findFirst().orElseThrow();
        assertEquals(2, predogled(prvoKolo, DRUGI_3_0).odvisneZIzidom().size(), "polfinale in finale");

        tekmaStoritev.popraviRezultat(prvoKolo.getId(), DRUGI_3_0, NacinPopravka.IZBRISI_IZIDE);
        izprazniSejo();

        Tekma finale = finale(dogodek);
        Tekma polfinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == 2 && t.getPozicija() == 1)
                .findFirst().orElseThrow();
        assertEquals(StatusTekme.PRIPRAVLJENA, polfinale.getStatus());
        assertEquals(StatusTekme.CAKA, finale.getStatus(), "zmagovalec polfinala ni vec znan");
        assertNull(finale.getZmagovalec());
        assertEquals(1, java.util.stream.Stream.of(finale.getPrijava1(), finale.getPrijava2())
                .filter(java.util.Objects::nonNull).count(), "drugi finalist ostane");
        ratingEnakPreracunu();
    }

    @Test
    void zamenjavaIgralcaPustiIzideNaSvojiStrani() {
        Dogodek dogodek = odigranaMreza4();
        Tekma polfinale = polfinale(dogodek, 0);
        Prijava stari = polfinale.getZmagovalec();
        Prijava novi = polfinale.porazenec();
        Tekma finale = finale(dogodek);
        assertEquals(stari.getId(), finale.getZmagovalec().getId(), "izhodisce: stari je dobil tudi finale");
        int nizi1 = finale.getDobljeniNizi1();
        int nizi2 = finale.getDobljeniNizi2();
        int steviloTock = nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(finale.getId()).size();

        tekmaStoritev.popraviRezultat(polfinale.getId(), DRUGI_3_0, NacinPopravka.ZAMENJAJ_IGRALCA);
        izprazniSejo();

        Tekma po = finale(dogodek);
        assertEquals(StatusTekme.KONCANA, po.getStatus());
        assertEquals(nizi1, po.getDobljeniNizi1());
        assertEquals(nizi2, po.getDobljeniNizi2());
        assertEquals(steviloTock, nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(finale.getId()).size());
        assertEquals(novi.getId(), udelezenecIzPolfinala(po, polfinale.getId()).getId());
        assertEquals(novi.getId(), po.getZmagovalec().getId(), "zmaga strani, ki je zmagala");

        // dogodek ostane zakljucen, nekdanji finalist pa ne obdrzi finalnega mesta
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
        assertNull(prijava(stari).getKoncnoMesto());
        assertNotNull(prijava(novi).getKoncnoMesto());
        ratingEnakPreracunu();
    }

    /* Tekma za 3. mesto stoji na porazencih polfinalov, finale na zmagovalcih: nov
       porazenec polfinala gre tja, kjer je bil prejsnji. */
    @Test
    void zamenjavaVeljaTudiZaTekmoZaTretjeMesto() {
        Dogodek dogodek = pripraviJakostniDogodek(4, SistemTekmovanja.IZLOCILNI);
        dogodek.setTekmaZaTretjeMesto(true);
        dogodekRepozitorij.save(dogodek);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());
        izprazniSejo();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());

        Tekma polfinale = polfinale(dogodek, 0);
        Long stari = polfinale.getZmagovalec().getId();
        Long novi = polfinale.porazenec().getId();
        Map<Long, Integer> mestaPrej = mesta(dogodek);
        assertEquals(2, predogled(polfinale, DRUGI_3_0).odvisneZIzidom().size(), "finale in tekma za 3. mesto");

        tekmaStoritev.popraviRezultat(polfinale.getId(), DRUGI_3_0, NacinPopravka.ZAMENJAJ_IGRALCA);
        izprazniSejo();

        Map<Long, Integer> pricakovana = new HashMap<>(mestaPrej);
        pricakovana.put(novi, mestaPrej.get(stari));
        pricakovana.put(stari, mestaPrej.get(novi));
        assertEquals(pricakovana, mesta(dogodek));
    }

    /* SV regija: zamenjani igralec gre tudi po poti porazenih. */
    @Test
    void zamenjavaVZrebuVeljaTudiZaPotPorazencev() {
        Dogodek dogodek = svDogodekSSkupinami();
        odigrajVse(dogodek.getId());
        Tekma cetrtfinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getIdZreb() != null && t.getKolo() == 1)
                .min(Comparator.comparingLong(Tekma::getId)).orElseThrow();
        Long stari = cetrtfinale.getZmagovalec().getId();
        Long novi = cetrtfinale.porazenec().getId();
        Map<Long, Integer> mestaPrej = mesta(dogodek);

        assertFalse(predogled(cetrtfinale, DRUGI_3_0).odvisneZIzidom().isEmpty());
        tekmaStoritev.popraviRezultat(cetrtfinale.getId(), DRUGI_3_0, NacinPopravka.ZAMENJAJ_IGRALCA);
        izprazniSejo();

        Map<Long, Integer> pricakovana = new HashMap<>(mestaPrej);
        pricakovana.put(novi, mestaPrej.get(stari));
        pricakovana.put(stari, mestaPrej.get(novi));
        assertEquals(pricakovana, mesta(dogodek), "igralca si zamenjata koncni mesti, drugi ne");
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
        ratingEnakPreracunu();
    }

    @Test
    void izbrisVZrebuOdpreDogodekInOhraniMestaBrezTekem() {
        Dogodek dogodek = svDogodekSSkupinami();
        odigrajVse(dogodek.getId());
        Tekma cetrtfinale = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getIdZreb() != null && t.getKolo() == 1)
                .min(Comparator.comparingLong(Tekma::getId)).orElseThrow();

        tekmaStoritev.popraviRezultat(cetrtfinale.getId(), DRUGI_3_0, NacinPopravka.IZBRISI_IZIDE);
        izprazniSejo();

        assertEquals(StatusTekmovanja.V_TEKU, dogodek(dogodek).getStatus());
        // nov vnos pride do konca in vsak igralec ima spet svoje mesto
        odigrajVse(dogodek.getId());
        izprazniSejo();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
        List<Integer> mesta = mesta(dogodek).values().stream().sorted().toList();
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList(), mesta);
        ratingEnakPreracunu();
    }

    // ---------- Skupina ----------

    /* A premaga vse, B C in D, C D. Obrnjen dvoboj A-D ne spremeni vrstnega reda:
       A in B izenacena, A je premagal B; C in D izenacena, C je premagal D. */
    @Test
    void obrnjenaTekmaBrezSpremembeVrstnegaRedaNeZahtevaOdlocitve() {
        Dogodek dogodek = skupinaInIzlocilni();
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, "A");
        Tekma aD = dvoboj(dogodek, vrstni.get(0), vrstni.get(3));

        VnosRezultata obrnjen = zmagaDrugega(aD, vrstni.get(3));
        assertTrue(predogled(aD, obrnjen).zamenjanZmagovalec());
        assertTrue(predogled(aD, obrnjen).izbire().isEmpty());

        tekmaStoritev.popraviRezultat(aD.getId(), obrnjen);
        izprazniSejo();

        assertEquals(vrstni.stream().map(Prijava::getId).toList(),
                vrstniRedSkupine(dogodek, "A").stream().map(Prijava::getId).toList());
        assertFalse(izlocilneTekme(dogodek).isEmpty(), "izlocilni del ostane");
    }

    @Test
    void spremenjenVrstniRedZahtevaIzbiroInPredogledPoveKaj() {
        Dogodek dogodek = skupinaInIzlocilni();
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, "A");
        Tekma ab = dvoboj(dogodek, vrstni.get(0), vrstni.get(1));
        VnosRezultata obrnjen = zmagaDrugega(ab, vrstni.get(1));

        PopravekPredogledDto predogled = predogled(ab, obrnjen);
        assertEquals(List.of(NacinPopravka.PODRI_NASLEDNJI_DEL, NacinPopravka.OHRANI_NASLEDNJI_DEL),
                predogled.izbire());
        assertEquals(PopravekPredogledDto.VrstaDela.IZLOCILNI, predogled.naslednjiDel().vrsta());
        assertEquals(vrstni.get(0).prikazanoIme(), predogled.naslednjiDel().vrstniRedPrej().get(0));
        assertEquals(vrstni.get(1).prikazanoIme(), predogled.naslednjiDel().vrstniRedPotem().get(0));
        assertEquals(0, predogled.naslednjiDel().vnesenihTekem());

        assertThrows(DomenskaIzjema.class, () -> tekmaStoritev.popraviRezultat(ab.getId(), obrnjen));
        izprazniSejo();
        assertEquals(vrstni.get(0).getId(),
                tekmaRepozitorij.findById(ab.getId()).orElseThrow().getZmagovalec().getId());
    }

    @Test
    void ohraniNaslednjiDelPopraviSamoTabelo() {
        Dogodek dogodek = skupinaInIzlocilni();
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, "A");
        Tekma ab = dvoboj(dogodek, vrstni.get(0), vrstni.get(1));
        List<Long> izlocilnePrej = izlocilneTekme(dogodek).stream().map(Tekma::getId).toList();

        tekmaStoritev.popraviRezultat(ab.getId(), zmagaDrugega(ab, vrstni.get(1)),
                NacinPopravka.OHRANI_NASLEDNJI_DEL);
        izprazniSejo();

        assertEquals(vrstni.get(1).getId(), vrstniRedSkupine(dogodek, "A").get(0).getId());
        assertEquals(izlocilnePrej, izlocilneTekme(dogodek).stream().map(Tekma::getId).toList());
    }

    @Test
    void podriIzlocilniDelSOdigranimiTekmamiInGaZnovaZrebaj() {
        Dogodek dogodek = skupinaInIzlocilni();
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, "A");
        Tekma ab = dvoboj(dogodek, vrstni.get(0), vrstni.get(1));
        odigrajVse(dogodek.getId());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
        List<Long> izlocilne = izlocilneTekme(dogodek).stream().map(Tekma::getId).toList();
        VnosRezultata obrnjen = zmagaDrugega(ab, vrstni.get(1));
        assertEquals(izlocilne.size(), predogled(ab, obrnjen).naslednjiDel().vnesenihTekem());

        tekmaStoritev.popraviRezultat(ab.getId(), obrnjen, NacinPopravka.PODRI_NASLEDNJI_DEL);
        izprazniSejo();

        assertTrue(izlocilneTekme(dogodek).isEmpty());
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekme(izlocilne, RatingStanje.SISTEM_TURNIRKO).isEmpty());
        assertEquals(StatusTekmovanja.V_TEKU, dogodek(dogodek).getStatus());
        assertTrue(prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .allMatch(p -> p.getKoncnoMesto() == null));
        ratingEnakPreracunu();

        izlocilniZreb.izvediZreb(dogodek.getId());
        odigrajVse(dogodek.getId());
        izprazniSejo();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
    }

    @Test
    void podriZrebeSvRegijeJihVrneOrganizatorju() {
        Dogodek dogodek = svDogodekSSkupinami();
        odigrajVse(dogodek.getId());
        Skupina a = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).get(0);
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, a.getOznaka());
        Tekma ab = dvoboj(dogodek, vrstni.get(0), vrstni.get(1));
        VnosRezultata obrnjen = zmagaDrugega(ab, vrstni.get(1));

        PopravekPredogledDto predogled = predogled(ab, obrnjen);
        assertEquals(PopravekPredogledDto.VrstaDela.ZREBI, predogled.naslednjiDel().vrsta());
        assertEquals(a.getNivo(), predogled.naslednjiDel().nivo());

        tekmaStoritev.popraviRezultat(ab.getId(), obrnjen, NacinPopravka.PODRI_NASLEDNJI_DEL);
        izprazniSejo();

        List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(dogodek.getId(), a.getNivo());
        assertTrue(zrebi.stream().noneMatch(Zreb::isZgrajen));
        assertTrue(zrebi.stream().allMatch(z -> tekmaRepozitorij.findByIdZreb(z.getId()).isEmpty()));
        assertEquals(StatusTekmovanja.V_TEKU, dogodek(dogodek).getStatus());
        assertEquals(vrstni.get(1).getId(), vrstniRedSkupine(dogodek, a.getOznaka()).get(0).getId());
        ratingEnakPreracunu();

        // organizator zrebe naredi znova in turnir pride do konca
        zrebi.forEach(z -> svRegija.znovaZrebaj(z.getId()));
        odigrajVse(dogodek.getId());
        izprazniSejo();
        assertEquals(StatusTekmovanja.ZAKLJUCEN, dogodek(dogodek).getStatus());
    }

    /* Finalne skupine: preneseni izid mora slediti izvorni tekmi, sicer finalna tabela
       steje drug izid kot predtekmovalna. */
    @Test
    void prenesenaTekmaSlediPopravkuIzvorne() {
        Dogodek dogodek = skupineZaMesta();
        Tekma prenesena = tekmeDogodka(dogodek.getId()).stream()
                .filter(Tekma::jePrenesena).findFirst().orElseThrow();
        Tekma izvorna = tekmaRepozitorij.findById(prenesena.getIdPrenesena()).orElseThrow();
        Long novi = izvorna.porazenec().getId();
        VnosRezultata obrnjen = zmagaDrugega(izvorna, izvorna.porazenec());

        // par ostane v istem pasu ali pa organizator izbere, da finalne ostanejo
        NacinPopravka nacin = predogled(izvorna, obrnjen).izbire().isEmpty()
                ? null : NacinPopravka.OHRANI_NASLEDNJI_DEL;
        tekmaStoritev.popraviRezultat(izvorna.getId(), obrnjen, nacin);
        izprazniSejo();

        Tekma po = tekmaRepozitorij.findById(prenesena.getId()).orElseThrow();
        assertEquals(novi, po.getZmagovalec().getId());
    }

    @Test
    void podriFinalneSkupineNastanejoZnovaIzNovegaVrstnegaReda() {
        Dogodek dogodek = skupineZaMesta();
        List<Prijava> vrstni = vrstniRedSkupine(dogodek, "A");
        // drugi in tretji v skupini: zamenjava preide mejo pasu (1.-2. proti 3.-4.)
        Tekma tekma = dvoboj(dogodek, vrstni.get(1), vrstni.get(2));
        VnosRezultata obrnjen = zmagaDrugega(tekma, vrstni.get(2));
        PopravekPredogledDto predogled = predogled(tekma, obrnjen);
        assertEquals(PopravekPredogledDto.VrstaDela.FINALNE_SKUPINE, predogled.naslednjiDel().vrsta());

        tekmaStoritev.popraviRezultat(tekma.getId(), obrnjen, NacinPopravka.PODRI_NASLEDNJI_DEL);
        izprazniSejo();

        Long novDrugi = vrstni.get(2).getId();
        Skupina zgornja = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).stream()
                .filter(s -> s.getStopnja() > 1 && Integer.valueOf(1).equals(s.getPrvoMesto()))
                .findFirst().orElseThrow();
        assertTrue(tekmeDogodka(dogodek.getId()).stream()
                        .filter(t -> zgornja.getId().equals(t.getIdSkupina()))
                        .anyMatch(t -> novDrugi.equals(t.getPrijava1().getId())
                                || novDrugi.equals(t.getPrijava2().getId())),
                "tretji je po popravku drugi in igra za zgornja mesta");
    }

    // ---------- Drugo ----------

    /* Preracun brise samo zapise tekem, ki jih odigra znova: tekma, ki je postala
       brez boja, je prej obdrzala stari obracun. */
    @Test
    void tekmaKiPostaneBrezBojaIzgubiObracun() {
        Dogodek dogodek = pripraviJakostniDogodek(2, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma finale = tekmeDogodka(dogodek.getId()).get(0);
        tekmaStoritev.vnesiRezultat(finale.getId(), PRVI_3_0);

        tekmaStoritev.popraviRezultat(finale.getId(),
                new VnosRezultata(IzidTekme.BREZ_BOJA, null, null, 2, null));
        izprazniSejo();

        assertEquals(IzidTekme.BREZ_BOJA, tekmaRepozitorij.findById(finale.getId()).orElseThrow().getIzidTip());
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekme(List.of(finale.getId()), RatingStanje.SISTEM_TURNIRKO).isEmpty());
        ratingEnakPreracunu();
    }

    @Test
    void predogledNeSpremeniNicesar() {
        Dogodek dogodek = odigranaMreza4();
        Tekma polfinale = polfinale(dogodek, 0);
        Long zmagovalec = polfinale.getZmagovalec().getId();
        int zapisov = ratingZgodovinaRepozitorij.findAll().size();

        predogled(polfinale, DRUGI_3_0);
        izprazniSejo();

        assertEquals(zmagovalec, tekmaRepozitorij.findById(polfinale.getId()).orElseThrow().getZmagovalec().getId());
        assertEquals(StatusTekme.KONCANA, finale(dogodek).getStatus());
        assertEquals(zapisov, ratingZgodovinaRepozitorij.findAll().size());
    }

    @Test
    void predogledZavrneNapacenVnos() {
        Dogodek dogodek = odigranaMreza4();
        Tekma polfinale = polfinale(dogodek, 0);
        assertThrows(NeveljavenVnosIzjema.class, () -> predogled(polfinale, new VnosRezultata(null, 2, 1, null, null)));
    }

    @Test
    void dvojiceBrezRatinga() {
        Dogodek dogodek = pripraviDogodekDvojic(4);
        poveziVsePare(dogodek.getId());
        zrebStoritev.izvediZreb(dogodek.getId());
        Tekma finale = tekmeDogodka(dogodek.getId()).get(0);
        tekmaStoritev.vnesiRezultat(finale.getId(), PRVI_3_0);
        Prijava novi = finale.getPrijava2();

        assertFalse(predogled(finale, DRUGI_3_0).stejeVRating());
        tekmaStoritev.popraviRezultat(finale.getId(), DRUGI_3_0);
        izprazniSejo();

        assertEquals(novi.getId(), tekmaRepozitorij.findById(finale.getId()).orElseThrow().getZmagovalec().getId());
        assertEquals(1, prijava(novi).getKoncnoMesto());
    }

    // ---------- Pomozno ----------

    private PopravekPredogledDto predogled(Tekma tekma, VnosRezultata vnos) {
        return tekmaStoritev.predogledPopravka(tekma.getId(), vnos);
    }

    private void izprazniSejo() {
        seja.flush();
        seja.clear();
    }

    private Dogodek dogodek(Dogodek dogodek) {
        return dogodekRepozitorij.najdiSTurnirjem(dogodek.getId()).orElseThrow();
    }

    private Prijava prijava(Prijava prijava) {
        return prijavaRepozitorij.findById(prijava.getId()).orElseThrow();
    }

    private int rating(Prijava prijava) {
        return ratingStanjeRepozitorij
                .findByIgralecIdAndSistem(prijava(prijava).getIgralec().getId(), RatingStanje.SISTEM_TURNIRKO)
                .orElseThrow().getVrednost();
    }

    private Map<Long, Integer> mesta(Dogodek dogodek) {
        Map<Long, Integer> mesta = new HashMap<>();
        for (Prijava p : prijavaRepozitorij.najdiZaDogodek(dogodek.getId())) {
            mesta.put(p.getId(), p.getKoncnoMesto());
        }
        return mesta;
    }

    /* Mreza stirih, vse odigrano; finale ima vpisane tocke. */
    private Dogodek odigranaMreza4() {
        Dogodek dogodek = pripraviJakostniDogodek(4, SistemTekmovanja.IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        tekmaStoritev.vnesiRezultat(polfinale(dogodek, 0).getId(), PRVI_3_0);
        tekmaStoritev.vnesiRezultat(polfinale(dogodek, 1).getId(), PRVI_3_0);
        tekmaStoritev.vnesiRezultat(finale(dogodek).getId(), new VnosRezultata(null, 3, 1, null,
                List.of(new NizVnos(11, 5), new NizVnos(9, 11), new NizVnos(11, 7), new NizVnos(11, 9))));
        izprazniSejo();
        return dogodek;
    }

    private Tekma polfinale(Dogodek dogodek, int indeks) {
        int zadnje = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI).mapToInt(Tekma::getKolo).max().orElseThrow();
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == zadnje - 1)
                .sorted(Comparator.comparingInt(Tekma::getPozicija))
                .toList().get(indeks);
    }

    private Tekma finale(Dogodek dogodek) {
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI)
                .max(Comparator.comparingInt(Tekma::getKolo)).orElseThrow();
    }

    private static Prijava udelezenecIzPolfinala(Tekma finale, Long idPolfinala) {
        return idPolfinala.equals(finale.getIdIzvorTekma1()) ? finale.getPrijava1() : finale.getPrijava2();
    }

    /* Vse pripravljene tekme, dokler jih je; zgradi zrebe, ko cakajo. Zmaga prvi. */
    private void odigrajVse(Long idDogodka) {
        for (int krog = 0; krog < 300; krog++) {
            List<Tekma> pripravljene = tekmeDogodka(idDogodka).stream()
                    .filter(t -> t.getStatus() == StatusTekme.PRIPRAVLJENA).toList();
            if (pripravljene.isEmpty()) {
                boolean zgrajen = false;
                for (Zreb zreb : zrebRepozitorij.findByDogodekIdOrderByNivoAscIndeksAsc(idDogodka)) {
                    if (!zreb.isZgrajen()) {
                        svRegija.znovaZrebaj(zreb.getId());
                        zgrajen = true;
                    }
                }
                if (zgrajen) {
                    continue;
                }
                return;
            }
            for (Tekma t : pripravljene) {
                tekmaStoritev.vnesiRezultat(t.getId(), PRVI_3_0);
            }
        }
        throw new AssertionError("turnir se ne konca");
    }

    private Dogodek svDogodekSSkupinami() {
        Dogodek dogodek = pripraviJakostniDogodek(16, SistemTekmovanja.SV_REGIJA);
        zrebStoritev.izvediZreb(dogodek.getId());
        return dogodek;
    }

    /* Skupine in izlocilni del: skupine odigrane po ratingu (mocnejsi zmaga), mreza
       zgrajena, nobena tekma mreze se ni odigrana. Skupina A ima vrstni red, kot ga
       pricakujejo testi: prvi premaga vse, drugi tretjega in cetrtega, tretji cetrtega. */
    private Dogodek skupinaInIzlocilni() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SKUPINE_IZLOCILNI);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupinePoJakosti(dogodek);
        izlocilniZreb.izvediZreb(dogodek.getId());
        izprazniSejo();
        return dogodek;
    }

    private Dogodek skupineZaMesta() {
        Dogodek dogodek = pripraviJakostniDogodek(8, SistemTekmovanja.SKUPINE_ZA_MESTA);
        zrebStoritev.izvediZreb(dogodek.getId());
        odigrajSkupinePoJakosti(dogodek);
        izprazniSejo();
        return dogodek;
    }

    /* Vsak dvoboj dobi igralec z boljsim (manjsim) jakostnim mestom. */
    private void odigrajSkupinePoJakosti(Dogodek dogodek) {
        for (Tekma t : tekmeDogodka(dogodek.getId())) {
            if (t.getFaza() != FazaTekme.SKUPINA || t.getStatus() != StatusTekme.PRIPRAVLJENA) {
                continue;
            }
            boolean prviMocnejsi = t.getPrijava1().getStNosilca() < t.getPrijava2().getStNosilca();
            tekmaStoritev.vnesiRezultat(t.getId(), prviMocnejsi ? PRVI_3_0 : DRUGI_3_0);
        }
    }

    private Skupina skupina(Dogodek dogodek, String oznaka) {
        return skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId()).stream()
                .filter(s -> oznaka.equals(s.getOznaka()) && s.getStopnja() == 1)
                .findFirst().orElseThrow();
    }

    private List<Prijava> vrstniRedSkupine(Dogodek dogodek, String oznaka) {
        Skupina skupina = skupina(dogodek, oznaka);
        List<Tekma> tekme = tekmeDogodka(dogodek.getId());
        List<Prijava> prijave = prijavaRepozitorij.najdiZaDogodek(dogodek.getId());
        List<Tekma> tekmeSkupine = tekme.stream()
                .filter(t -> skupina.getId().equals(t.getIdSkupina())).toList();
        return razvrstitevStoritev.lestvica(SkupineStoritev.clani(skupina, prijave, tekme), tekmeSkupine).stream()
                .map(v -> prijave.stream().filter(p -> p.getId().equals(v.idPrijave())).findFirst().orElseThrow())
                .toList();
    }

    private Tekma dvoboj(Dogodek dogodek, Prijava a, Prijava b) {
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && !t.jePrenesena())
                .filter(t -> (t.getPrijava1().getId().equals(a.getId()) && t.getPrijava2().getId().equals(b.getId()))
                        || (t.getPrijava1().getId().equals(b.getId()) && t.getPrijava2().getId().equals(a.getId())))
                .findFirst().orElseThrow();
    }

    /* Vnos, s katerim dano prijavo tekme zmaga 3 : 0. */
    private static VnosRezultata zmagaDrugega(Tekma tekma, Prijava novZmagovalec) {
        return novZmagovalec.getId().equals(tekma.getPrijava1().getId()) ? PRVI_3_0 : DRUGI_3_0;
    }

    private List<Tekma> izlocilneTekme(Dogodek dogodek) {
        return tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI || t.getFaza() == FazaTekme.TOLAZILNI)
                .toList();
    }

    /* Merilo iz CLAUDE.md: preracun od zacetka mora dati isto kot popravek. */
    private void ratingEnakPreracunu() {
        izprazniSejo();
        Map<Long, Integer> zdaj = ratingi();
        preracunRatinga.preracunajOd(null);
        izprazniSejo();
        assertEquals(zdaj, ratingi());
    }

    private Map<Long, Integer> ratingi() {
        Map<Long, Integer> ratingi = new HashMap<>();
        for (RatingStanje stanje : ratingStanjeRepozitorij.findBySistem(RatingStanje.SISTEM_TURNIRKO)) {
            ratingi.put(stanje.getIgralec().getId(), stanje.getVrednost());
        }
        return ratingi;
    }
}
