/* Rocni zreb lige: organizator razpored VPISE, namesto da bi ga sestavil zreb.

   Testi varujejo tri stvari - da vpisani razpored nastane kot vsak drug (ista
   srecanja, termini iz semena, liga v teku), da streznik zavrne tisto, kar bi
   razpored pokvarilo (ekipa dvakrat v kolu, sama proti sebi, tuja ekipa, vrzel
   med koli), in da NE zavrne nepopolnega razporeda - rocno vodene lige ga
   imajo, popolnost je stvar opozorila v vmesniku in ne sheme. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.ParRazporedaDto;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.RocniRazporedVnos;
import si.turnirko.dto.RocniRazporedVnos.ParVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;

class LigaRocniZrebTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;

    /* Prepis papirnatega zreba: razpored je tak, kot ga je vpisal organizator,
       in ne tak, kot bi ga sestavil zreb. */
    @Test
    void vpisaniRazporedObveljaTakKotJeVpisan() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<SrecanjeDto> srecanja = ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(3)),
                new ParVnos(1, ekipe.get(1), ekipe.get(2)),
                new ParVnos(2, ekipe.get(2), ekipe.get(0)),
                new ParVnos(2, ekipe.get(3), ekipe.get(1)),
                new ParVnos(3, ekipe.get(0), ekipe.get(1)),
                new ParVnos(3, ekipe.get(2), ekipe.get(3)))));

        assertEquals(6, srecanja.size());
        assertEquals(List.of("Ek A-Ek D", "Ek B-Ek C"), pariVKolu(srecanja, 1));
        assertEquals(List.of("Ek C-Ek A", "Ek D-Ek B"), pariVKolu(srecanja, 2));
        assertEquals(List.of("Ek A-Ek B", "Ek C-Ek D"), pariVKolu(srecanja, 3));
        assertEquals(srecanja, srecanjeStoritev.zaLigo(liga),
                "vpisan razpored je za ostalo aplikacijo navaden razpored");
    }

    /* Vpis se konca enako kot zreb: liga tece, zastavica pa pove, od kod
       razpored je - to gre na javno stran lige. */
    @Test
    void vpisZazeneLigoInJoOznaciKotRocnoZrebano() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");

        ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1)))));

        assertEquals(StatusTekmovanja.V_TEKU, ligaStoritev.najdi(liga).status());
        assertTrue(ligaStoritev.najdi(liga).rocniZreb());
    }

    /* Termini so last kola in ne nacina zreba: seme lige (prvo kolo + razmik)
       napolni tudi vpisani razpored, sicer bi bil koledar prazen prav pri
       ligi, ki datume ze ima na papirju. */
    @Test
    void vpisaniRazporedDobiTerminaIzSemena() {
        Long liga = ustvariLigo(LocalDateTime.of(2026, 10, 4, 18, 0), 7);
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<SrecanjeDto> srecanja = ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1)),
                new ParVnos(1, ekipe.get(2), ekipe.get(3)),
                new ParVnos(2, ekipe.get(0), ekipe.get(2)),
                new ParVnos(2, ekipe.get(1), ekipe.get(3)))));

        assertEquals(LocalDateTime.of(2026, 10, 4, 18, 0), terminKola(srecanja, 1));
        assertEquals(LocalDateTime.of(2026, 10, 11, 18, 0), terminKola(srecanja, 2));
    }

    /* Kolo je en igralni dan - ekipa v njem odigra eno srecanje. Dve bi
       razdrli lestvico in termin kola. */
    @Test
    void ekipaNeSmeImetiDvehSrecanjVIstemKolu() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1)),
                        new ParVnos(1, ekipe.get(0), ekipe.get(2))))));
    }

    @Test
    void ekipaNeSmeIgratiSamaSSabo() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(0))))));
    }

    /* Ekipa iz druge lige bi bila srecanje, ki ga ni mogoce odigrati. */
    @Test
    void ekipaMoraBitiPrijavljenaVTemLigo() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");
        Long druga = ustvariLigo();
        Long tuja = dodajEkipe(druga, "Tuja X", "Tuja Y").get(0);

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), tuja)))));
    }

    /* Prazno kolo med polnimi je napaka pri prepisu; razpored bi ga izpisal
       kot prazno stran. */
    @Test
    void kolaMorajoTeciOdEnegaNaprejBrezVrzeli() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1)),
                        new ParVnos(3, ekipe.get(1), ekipe.get(0))))),
                "manjka 2. kolo");
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(2, ekipe.get(0), ekipe.get(1))))),
                "razpored se ne zacne s prvim kolom");
    }

    /* Popolnost razporeda je stvar tekmovanja in ne sheme: liga, ki se je
       vodila na roke, ima lahko par, ki se ne sreca, in par, ki se srecata
       trikrat. Streznik tega ne zavrne - v vmesniku je to opozorilo. */
    @Test
    void nepopolnegaRazporedaStreznikNeZavrne() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<SrecanjeDto> srecanja = ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1)),
                new ParVnos(2, ekipe.get(1), ekipe.get(0)))));

        assertEquals(2, srecanja.size(), "ekipi C in D v razporedu sploh nista");
    }

    /* Razpored nastane enkrat: druga pot do njega mora najti vrata zaprta,
       sicer bi liga dobila dva razporeda en cez drugega. */
    @Test
    void razporedaNiMogoceVpisatiDvakrat() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");
        RocniRazporedVnos vnos = new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1))));
        ligaStoritev.rocniRazpored(liga, vnos);

        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.rocniRazpored(liga, vnos));
        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.generirajRazpored(liga));
    }

    /* Predlog je isti racun kot zreb, a brez zapisa: vmesnik iz njega sestavi
       prazno mrezo (koliko kol, koliko srecanj v kolu) in jo na zahtevo
       napolni. Liga mora po njem ostati v pripravi. */
    @Test
    void predlogNicesarNeZapise() {
        Long liga = ustvariLigo();
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<ParRazporedaDto> predlog = ligaStoritev.predlogRazporeda(liga);

        assertEquals(12, predlog.size(), "stiri ekipe dvokrozno: 12 srecanj");
        assertEquals(6, predlog.stream().mapToInt(ParRazporedaDto::kolo).max().orElse(0),
                "6 kol po 2 srecanji - iz tega vmesnik sestavi prazno mrezo");
        assertEquals(StatusTekmovanja.PRIPRAVA, ligaStoritev.najdi(liga).status());
        assertTrue(srecanjeStoritev.zaLigo(liga).isEmpty(), "predlog ni razpored");
    }

    /* Prepis sedemdesetih srecanj s papirja vsebuje tipkarske napake, zato
       mora biti popravljiv brez brisanja lige (ekipe in kader ostanejo). */
    @Test
    void razveljavitevVrneLigoVPripravoInPobriseSrecanja() {
        Long liga = ustvariLigo();
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");
        ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1)))));

        ligaStoritev.razveljaviRazpored(liga);

        assertEquals(StatusTekmovanja.PRIPRAVA, ligaStoritev.najdi(liga).status());
        assertFalse(ligaStoritev.najdi(liga).rocniZreb(), "razveljavljen vpis ni vec rocni zreb");
        assertTrue(srecanjeStoritev.zaLigo(liga).isEmpty());
        assertEquals(2, ligaStoritev.ekipe(liga).size(), "ekipe in kader ostanejo");
        // popravljen razpored se vpise na novo
        ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(1), ekipe.get(0)))));
        assertEquals(List.of("Ek B-Ek A"), pariVKolu(srecanjeStoritev.zaLigo(liga), 1));
    }

    /* Meja je prvo srecanje, ki se je zacelo: postava in generirane tekme so
       ze zapis o igri in jih izbris razporeda ne sme tiho odnesti. */
    @Test
    void razveljavitevZacetegaSrecanjaNiMogoca() {
        Long liga = ustvariLigo();
        List<Long> ekipe = List.of(dodajEkipoSKadrom(liga, "Ek A"), dodajEkipoSKadrom(liga, "Ek B"));
        ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(1)))));
        zacniSrecanje(srecanjeStoritev.zaLigo(liga).get(0).id());

        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.razveljaviRazpored(liga));
    }

    /* Razveljavitev ni izjema rocnega vpisa - zreb, ki je stekel prezgodaj,
       je ista tezava in ima isto resitev. */
    @Test
    void razveljavitevVeljaTudiZaGeneriranRazpored() {
        Long liga = ustvariLigo();
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);

        ligaStoritev.razveljaviRazpored(liga);

        assertEquals(StatusTekmovanja.PRIPRAVA, ligaStoritev.najdi(liga).status());
        assertTrue(srecanjeStoritev.zaLigo(liga).isEmpty());
    }

    // ---------- pomozne metode ----------

    /* Postava naredi iz srecanja igro: nastanejo tekme in srecanje zapusti
       stanje RAZPORED. Mesta in kader prebere iz podrobnega pogleda, da test
       ne podvaja pravil formata. */
    private void zacniSrecanje(Long idSrecanje) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), i < 2));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), i < 2));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
    }

    /* Pari kola kot "domaci-gost", urejeni po imenu - razpored jih vrne po id
       srecanja in tega vrstnega reda test ne preverja. */
    private List<String> pariVKolu(List<SrecanjeDto> srecanja, int kolo) {
        return srecanja.stream()
                .filter(s -> s.kolo() == kolo)
                .map(s -> s.domaci() + "-" + s.gost())
                .sorted()
                .toList();
    }

    private LocalDateTime terminKola(List<SrecanjeDto> srecanja, int kolo) {
        return srecanja.stream()
                .filter(s -> s.kolo() == kolo)
                .map(SrecanjeDto::predvidenZacetek)
                .findFirst()
                .orElse(null);
    }

    /* Prosta ekipa s kadrom treh igralcev - dovolj za postavo vsakega formata. */
    private Long dodajEkipoSKadrom(Long idLiga, String ime) {
        Long idEkipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime)).id();
        for (int i = 1; i <= 3; i++) {
            Igralec igralec = noviIgralec("Ig" + ime.replace(" ", "") + i, "Pri" + i);
            ligaStoritev.dodajVKader(idEkipa, new KaderVnos(igralec.getId(), i));
        }
        return idEkipa;
    }

    private List<Long> dodajEkipe(Long idLiga, String... imena) {
        List<Long> idji = new ArrayList<>();
        for (String ime : imena) {
            idji.add(ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime)).id());
        }
        return idji;
    }

    private Long ustvariLigo() {
        return ustvariLigo(null, null);
    }

    private Long ustvariLigo(LocalDateTime prvoKolo, Integer razmik) {
        LigaVnos v = new LigaVnos("Rocna liga", "2025/26", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, true, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, prvoKolo, razmik);
        return ligaStoritev.ustvari(v).id();
    }
}
