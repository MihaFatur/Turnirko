/* Sinhronizacija s Stupo na PRAVIH posnetkih dogodkov NTZS (okleščeni in
   stisnjeni v src/test/resources/stupa/{id}): vsak posnetek predstavlja eno
   obliko tekmovanja, ki jo vir pozna.

     245  1. OT U15 2026/27     posamicno, 16+7 skupin in mreza s prostimi prehodi
     228  22. DP U21            posamicno in dvojice
     105  1. SNTL moski 25/26   liga s koncnico (polfinale in finale na dve zmagi)
     222  I./II. SNTL           kvalifikacije - liga samo s serijo
     221  35. ekipni DP U15     ekipno, skupine za mesta s prenesenimi izidi
     175  ekipno kadeti 24/25   stiri stopnje skupin, odstopljena ekipa
     233  pokal NTZS 2026       mreza, zastarelo finale, ZA 1. in ZA 3. MESTO
     138  ekipno kadetinje      oznake igralcev po zaporedni tekmi (A1..A9)
     104  1. SNTL zenske 25/26  srecanja z zapisanimi samo zmagovalci podtekem
     100  1. SNTL moski 24/25   tockovanje z tocko za poraz
     168  3. SNTL moski 24/25   uradne tocke brez tockovanja, neodlocena 5 : 5
     186  U-13 ekipno Zalog     igralec z imenom iz ene besede
     188  U-13 ekipno N. mesto  oseba brez datuma rojstva

   Vsak uvoz mora preiti VSE obvezne preverbe uskladitve (tekme, zmagovalci,
   izidi, mreza, srecanja z igralci, tocke lestvice, serije koncnice), test pa
   se preveri, da model zapise tisto, kar dogodek pri viru je. */
package si.turnirko.uvoz.stupa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.PorociloUvozaDto;
import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Liga;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SerijaKoncnice;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.Spol;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.Srecanje;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.TekmaSrecanja;
import si.turnirko.modeli.Turnir;
import si.turnirko.modeli.VirTekmovanja;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.ZunanjaPovezava;
import si.turnirko.repozitoriji.LigaRepozitorij;
import si.turnirko.repozitoriji.PostavaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.SerijaKoncniceRepozitorij;
import si.turnirko.repozitoriji.SrecanjeRepozitorij;
import si.turnirko.repozitoriji.TekmaSrecanjaRepozitorij;
import si.turnirko.repozitoriji.ZunanjaPovezavaRepozitorij;
import si.turnirko.storitve.IntegracijskiTest;
import si.turnirko.storitve.LestvicaLigeStoritev;

class SinhronizacijaStupeTest extends IntegracijskiTest {

    @Autowired private UvozStupeStoritev uvoz;
    @Autowired private LigaRepozitorij ligaRepozitorij;
    @Autowired private SrecanjeRepozitorij srecanjeRepozitorij;
    @Autowired private TekmaSrecanjaRepozitorij tekmaSrecanjaRepozitorij;
    @Autowired private PostavaSrecanjaRepozitorij postavaRepozitorij;
    @Autowired private SerijaKoncniceRepozitorij serijaRepozitorij;
    @Autowired private ZunanjaPovezavaRepozitorij povezave;
    @Autowired private LestvicaLigeStoritev lestvice;

    @TempDir Path zacasna;

    // ---------------------------------------------------------------------
    // Turnirji posameznikov in dvojic
    // ---------------------------------------------------------------------

    @Test
    void turnirPosamicnoSkupineInMreza() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(245);
        Turnir turnir = turnirRepozitorij.findById(izvedba.sled().idTurnir).orElseThrow();
        assertEquals(VirTekmovanja.STUPA, turnir.getVir());
        assertEquals(RavenTekmovanja.URADNO, turnir.getRaven());
        assertEquals(LocalDate.of(2026, 9, 12), turnir.getDatumZacetka());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, turnir.getStatus());

        List<Dogodek> dogodki = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(turnir.getId());
        assertEquals(2, dogodki.size());
        dogodki.forEach(d -> assertEquals(SistemTekmovanja.SKUPINE_IZLOCILNI, d.getSistemTekmovanja()));
        Dogodek moski = dogodki.get(0);
        assertEquals(SpolKategorija.MOSKI, moski.getSpolKategorija());
        assertEquals(16, skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(moski.getId()).size());
        assertEquals(87 + 31, tekmaRepozitorij.najdiZaDogodek(moski.getId()).stream()
                .filter(t -> t.getStatus() == StatusTekme.KONCANA).count());
        // clanstvo v skupinah je zapisano (prejsnji uvoz ga ni pisal nikjer)
        assertTrue(prijavaRepozitorij.najdiZaDogodek(moski.getId()).stream().allMatch(p -> p.getIdSkupina() != null));

        // zenske: mreza 16 s prostima prehodoma prvih dveh nosilk
        List<Tekma> mrezaZensk = tekmaRepozitorij.najdiZaDogodek(dogodki.get(1).getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI).toList();
        assertEquals(15, mrezaZensk.size());
        assertEquals(2, mrezaZensk.stream().filter(t -> t.getIzidTip() == IzidTekme.PROSTO).count());
        Tekma finale = mrezaZensk.stream().filter(t -> t.getKolo() == 4).findFirst().orElseThrow();
        assertEquals("Klara Rahotin Pavič", finale.getZmagovalec().getIgralec().polnoIme());
        assertEquals(1, finale.getZmagovalec().getKoncnoMesto());
        assertTrue(nizRepozitorij.count() > 500, "tocke nizov so zapisane");
    }

    @Test
    void ponovniUvozObdrziIdjeInNePodvoji() {
        UvozStupeStoritev.Izvedba prvi = uvozi(245);
        List<Long> dogodki = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(prvi.sled().idTurnir).stream()
                .map(Dogodek::getId).toList();
        long igralcev = igralecRepozitorij.count();
        long tekem = tekmaRepozitorij.count();

        UvozStupeStoritev.Izvedba drugi = uvozi(245);
        assertEquals(prvi.sled().idTurnir, drugi.sled().idTurnir);
        assertEquals(dogodki, dogodekRepozitorij.findByTurnirIdOrderByIdAsc(drugi.sled().idTurnir).stream()
                .map(Dogodek::getId).toList());
        assertEquals(igralcev, igralecRepozitorij.count(), "igralci so povezani, ne ustvarjeni znova");
        assertEquals(tekem, tekmaRepozitorij.count());
        assertEquals(0, drugi.porocilo().stevec("novih igralcev"));
    }

    @Test
    void turnirDvojic() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(228);
        List<Dogodek> dvojice = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir).stream()
                .filter(d -> d.getDisciplina() == Disciplina.DVOJICE).toList();
        assertEquals(2, dvojice.size());
        for (Dogodek d : dvojice) {
            assertEquals(SistemTekmovanja.IZLOCILNI, d.getSistemTekmovanja());
            List<Prijava> pari = prijavaRepozitorij.najdiZaDogodek(d.getId()).stream()
                    .filter(p -> p.getStatus() == Prijava.StatusPrijave.PRIJAVLJEN).toList();
            assertFalse(pari.isEmpty());
            assertTrue(pari.stream().allMatch(p -> p.getIgralec2() != null), "prijava dvojic je par");
        }
        assertEquals(Set.of(SpolKategorija.MOSKI, SpolKategorija.ZENSKE),
                dvojice.stream().map(Dogodek::getSpolKategorija).collect(Collectors.toSet()));
    }

    /* Prijava, ki jo je organizator izkljucil (is_excluded) in ni igrala, ni
       udelezenec: brez skupine bi stala med prijavljenimi (2. OT U17 2026). */
    @Test
    void izkljucenaPrijavaNiUdelezenecTurnirja() throws IOException {
        UvozStupeStoritev.Izvedba izvedba = uvoz.izvedi(zIzkljucenoPrijavo(245), IdentitetaStupe.Nacin.SAMODEJNO, Map.of());
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        assertTrue(izvedba.porocilo().dovoljuje(), () -> opis(dto));
        assertEquals(1, izvedba.porocilo().stevec("izkljucenih prijav pri viru (izpuscene)"));
        for (Dogodek d : dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir)) {
            assertTrue(prijavaRepozitorij.najdiZaDogodek(d.getId()).stream().allMatch(p -> p.getIdSkupina() != null),
                    "vsak udelezenec je v skupini");
        }
    }

    /* Izkljucena ekipa lige (2. SNTL M 2025/26: podvojena prijava kluba) ne
       sme na lestvico - stala bi tam kot "NTK Vesna (2)" z 0 srecanji. */
    @Test
    void izkljucenaEkipaNiNaLestviciLige() throws IOException {
        UvozStupeStoritev.Izvedba izvedba = uvoz.izvedi(zIzkljucenoPrijavo(104), IdentitetaStupe.Nacin.SAMODEJNO, Map.of());
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        assertTrue(izvedba.porocilo().dovoljuje(), () -> opis(dto));
        assertEquals(1, izvedba.porocilo().stevec("izkljucenih ekip pri viru (izpuscene)"));
        List<LestvicaEkipeDto> lestvica = lestvice.lestvica(izvedba.sled().idLiga);
        assertTrue(lestvica.stream().allMatch(v -> v.odigrane() > 0), () -> "ekipa brez srecanj: " + lestvica);
    }

    /* Turnir, ki se se ni zacel: vir ima razpis s kategorijami, stopenj (zrebov)
       pa se ne. Zapise se turnir v pripravi brez dogodkov, porocilo pa to pove
       z opozorilom - sicer bi admin videl "pripravljeno za uvoz" in mislil, da
       uvaza rezultate. */
    @Test
    void turnirBrezTekmovanjJeVPripraviZOpozorilom() throws IOException {
        posnetek(245);
        Path mapa = zacasna.resolve("245");
        Files.writeString(mapa.resolve("stopnje.json"), "{\"data\": []}");
        PosnetekDogodka p = PosnetekDogodka.beri(mapa, null, null, LocalDate.of(2026, 9, 10));

        UvozStupeStoritev.Izvedba izvedba = uvoz.izvedi(p, IdentitetaStupe.Nacin.STROGO, Map.of());
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        assertTrue(izvedba.porocilo().dovoljuje(), () -> opis(dto));
        Turnir turnir = turnirRepozitorij.findById(izvedba.sled().idTurnir).orElseThrow();
        assertEquals(StatusTekmovanja.PRIPRAVA, turnir.getStatus());
        assertTrue(dogodekRepozitorij.findByTurnirIdOrderByIdAsc(turnir.getId()).isEmpty());
        assertTrue(dto.opozorila().stream().anyMatch(u -> u.vrsta().startsWith("vir se nima nobenega tekmovanja")),
                () -> opis(dto));
    }

    // ---------------------------------------------------------------------
    // Lige
    // ---------------------------------------------------------------------

    /* Prvi dve osebi se v posnetku 1. SNTL pojavita brez osebnih podatkov (pri
       viru pod drugim user_role_id) - v registru ju najde po imenu. */
    @Test
    void ligaSKoncnico() {
        Igralec brin = noviIgralec("Brin", "Vovk Petrovski", LocalDate.of(2006, 6, 16), Spol.MOSKI);
        noviIgralec("Domen", "Hohnjec", LocalDate.of(2005, 7, 28), Spol.MOSKI);
        UvozStupeStoritev.Izvedba izvedba = uvozi(105);
        assertEquals(brin.getId(), povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA,
                ZunanjaPovezava.Vrsta.IGRALEC, "19780").orElseThrow().getIdLokalni(),
                "oseba brez osebnih podatkov je povezana po imenu");
        Liga liga = ligaRepozitorij.findById(izvedba.sled().idLiga).orElseThrow();
        assertEquals(VirTekmovanja.STUPA, liga.getVir());
        assertEquals(FormatSrecanja.SNTL_PRVA, liga.getFormatSrecanja());
        assertEquals(4, liga.getKoncnicaEkip());
        assertEquals(2, liga.getKoncnicaZmag());
        assertEquals(StatusTekmovanja.ZAKLJUCEN, liga.getStatus());

        List<Srecanje> srecanja = srecanjeRepozitorij.najdiZaLigo(liga.getId());
        assertEquals(90, srecanja.stream().filter(s -> s.getSerija() == null).count(), "redni del");
        assertEquals(6, srecanja.stream().filter(s -> s.getSerija() != null).count(), "koncnica brez nepotrebnih tekem");

        List<SerijaKoncnice> serije = serijaRepozitorij.najdiZaLigo(liga.getId());
        assertEquals(3, serije.size());
        SerijaKoncnice finale = serije.stream().filter(s -> s.getKrog() == 2).findFirst().orElseThrow();
        assertEquals("NTK Savinja Plasard", finale.getZmagovalec().prikazanoIme());
        assertEquals(2, finale.getZmage1() + finale.getZmage2());
        SerijaKoncnice polfinale = serije.stream().filter(s -> s.getKrog() == 1 && s.getPar() == 1).findFirst().orElseThrow();
        assertEquals(1, polfinale.getMesto1(), "prva stran serije je prvi po rednem delu");

        // lestvica rednega dela steje samo redni del: 36 tock prvaka
        LestvicaEkipeDto prvi = lestvice.lestvica(liga.getId()).get(0);
        assertEquals("NTK Savinja Plasard", prvi.ekipa());
        assertEquals(36, prvi.tocke());
    }

    @Test
    void kvalifikacijeMedLigama() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(222);
        Liga liga = ligaRepozitorij.findById(izvedba.sled().idLiga).orElseThrow();
        assertEquals(2, liga.getKoncnicaEkip());
        List<SerijaKoncnice> serije = serijaRepozitorij.najdiZaLigo(liga.getId());
        assertEquals(1, serije.size());
        assertEquals("NTS Mengeš", serije.get(0).getZmagovalec().prikazanoIme());
        assertTrue(srecanjeRepozitorij.najdiZaLigo(liga.getId()).stream().allMatch(s -> s.getSerija() != null));
    }

    // ---------------------------------------------------------------------
    // Ekipni turnirji
    // ---------------------------------------------------------------------

    @Test
    void ekipniDpSkupineZaMesta() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(221);
        List<Dogodek> dogodki = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir);
        Dogodek moski = dogodki.get(0);
        assertEquals(Disciplina.EKIPNO, moski.getDisciplina());
        assertEquals(SistemTekmovanja.SKUPINE_ZA_MESTA, moski.getSistemTekmovanja());
        assertEquals(FormatSrecanja.EKIPNI_DP, moski.getFormatSrecanja());
        assertEquals(3, moski.getZmagZaSrecanje());

        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(moski.getId());
        assertEquals(Set.of(1, 5), skupine.stream().filter(s -> s.getPrvoMesto() != null)
                .map(Skupina::getPrvoMesto).collect(Collectors.toSet()));

        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(moski.getId());
        List<Tekma> prenesene = tekme.stream().filter(Tekma::jePrenesena).toList();
        assertEquals(4, prenesene.size(), "v vsaki finalni skupini dva dvoboja iz predtekmovanja");
        for (Tekma t : prenesene) {
            assertTrue(srecanjeRepozitorij.najdiZaTekmo(t.getId()).isEmpty(), "preneseni izid nima srecanja");
        }

        Map<Integer, String> mesta = prijavaRepozitorij.najdiZaDogodek(moski.getId()).stream()
                .filter(p -> p.getKoncnoMesto() != null)
                .collect(Collectors.toMap(Prijava::getKoncnoMesto, p -> p.getEkipa().prikazanoIme()));
        assertEquals("NTK GORICA", mesta.get(1));
        assertEquals("PPK RAKEK", mesta.get(8));

        Tekma odigrana = tekme.stream().filter(t -> !t.jePrenesena()).findFirst().orElseThrow();
        Srecanje s = srecanjeRepozitorij.najdiZaTekmo(odigrana.getId()).orElseThrow();
        assertEquals(odigrana.getDobljeniNizi1() + odigrana.getDobljeniNizi2(), s.getDobljeneDomaci() + s.getDobljeneGost());
        Set<String> mestaPostave = postavaRepozitorij.findAll().stream()
                .filter(x -> x.getSrecanje().getId().equals(s.getId()))
                .map(x -> x.getPozicija()).collect(Collectors.toSet());
        assertTrue(mestaPostave.containsAll(Set.of("A", "B", "C", "X", "Y", "Z")), "postava iz oznak: " + mestaPostave);
        assertEquals(StatusTekmovanja.ZAKLJUCEN, moski.getStatus());
    }

    @Test
    void ekipniTurnirStiriStopnjeInOdstop() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(175);
        Dogodek d = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir).get(0);
        assertEquals(SistemTekmovanja.SKUPINE_ZA_MESTA, d.getSistemTekmovanja());
        assertEquals(FormatSrecanja.SNTL_BREZ_DVOJIC, d.getFormatSrecanja());
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(d.getId());
        assertEquals(Set.of(1, 2, 3, 4), skupine.stream().map(Skupina::getStopnja).collect(Collectors.toSet()));
        assertEquals(3, tekmaRepozitorij.najdiZaDogodek(d.getId()).stream().filter(Tekma::jePrenesena).count());
        PorociloUvozaDto.Ugotovitev izpuscene = izvedba.porocilo().vDto().opozorila().stream()
                .filter(u -> u.vrsta().startsWith("neodigrana tekma")).findFirst().orElseThrow();
        assertEquals(6, izpuscene.stevilo(), "tekme odstopljene ekipe");
    }

    @Test
    void pokalZaPrvoInTretjeMesto() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(233);
        Dogodek moski = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir).stream()
                .filter(d -> d.getIme().contains("MOŠKI")).findFirst().orElseThrow();
        assertEquals(FormatSrecanja.POKAL_NTZS, moski.getFormatSrecanja());
        assertEquals(SistemTekmovanja.IZLOCILNI, moski.getSistemTekmovanja());
        assertTrue(moski.isTekmaZaTretjeMesto());

        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(moski.getId());
        Tekma finale = tekme.stream().filter(t -> t.getFaza() == FazaTekme.GLAVNI && t.getKolo() == 2).findFirst().orElseThrow();
        assertEquals("ŽNTK MARIBOR", finale.getZmagovalec().getEkipa().prikazanoIme());
        Tekma zaTretje = tekme.stream().filter(t -> t.getFaza() == FazaTekme.TOLAZILNI).findFirst().orElseThrow();
        assertEquals(VlogaIzvora.PORAZENEC, zaTretje.getVlogaIzvora1());
        assertEquals("NTK SOBOTA", zaTretje.getZmagovalec().getEkipa().prikazanoIme());
        assertEquals(4, tekme.size(), "polfinala, finale in tekma za 3. mesto - zastarelo finale izpusceno");
    }

    /* Oznake A1..A9 so zaporedne tekme: mesta postave da sele razpored formata. */
    @Test
    void oznakeIgralcevPoZaporedniTekmi() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(138);
        Dogodek d = dogodekRepozitorij.findByTurnirIdOrderByIdAsc(izvedba.sled().idTurnir).get(0);
        assertEquals(FormatSrecanja.SNTL_BREZ_DVOJIC, d.getFormatSrecanja());
        for (Long idSrecanje : izvedba.sled().srecanja.values()) {
            for (TekmaSrecanja t : tekmaSrecanjaRepozitorij.najdiZaSrecanje(idSrecanje)) {
                assertFalse(t.getOznaka().contains("?"), "mesto tekme mora biti znano: " + t.getOznaka());
            }
        }
    }

    // ---------------------------------------------------------------------
    // Istovetnost igralcev
    // ---------------------------------------------------------------------

    /* Licenca in datum rojstva se ujemata: igralec je povezan, ne ustvarjen. */
    @Test
    void licencaInRojstvoPovezetaObstojecegaIgralca() {
        Igralec abraham = noviIgralec("Žiga", "Abraham", LocalDate.of(2012, 6, 27), Spol.MOSKI);
        abraham.setNtzsLicenca("163/18/19");
        igralecRepozitorij.save(abraham);
        UvozStupeStoritev.Izvedba izvedba = uvozi(245);
        assertEquals(abraham.getId(), povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA,
                ZunanjaPovezava.Vrsta.IGRALEC, "19204").orElseThrow().getIdLokalni());
        assertTrue(izvedba.porocilo().vDto().noviIgralci().stream().noneMatch(n -> n.idOsebe() == 19204));
    }

    /* Licenca pripada igralcu z drugim datumom rojstva: pri strogem nacinu je
       to odlocitev admina in uvoz brez nje ne gre skozi. */
    @Test
    void licencaZDrugimRojstvomJeOdlocitev() {
        Igralec drug = noviIgralec("Žiga", "Abraham", LocalDate.of(1990, 1, 5), Spol.MOSKI);
        drug.setNtzsLicenca("163/18/19");
        igralecRepozitorij.save(drug);

        UvozStupeStoritev.Izvedba strogo = uvoz.izvedi(posnetek(245), IdentitetaStupe.Nacin.STROGO, Map.of());
        assertFalse(strogo.porocilo().dovoljuje());
        PorociloUvozaDto.Odlocitev odlocitev = strogo.porocilo().vDto().odlocitve().stream()
                .filter(o -> o.idOsebe() == 19204).findFirst().orElseThrow();
        assertEquals(drug.getId(), odlocitev.kandidati().get(0).idIgralec());

        // admin odloci: to je nova oseba
        UvozStupeStoritev.Izvedba zOdlocitvijo = uvoz.izvedi(posnetek(245), IdentitetaStupe.Nacin.STROGO,
                Map.of(19204L, IdentitetaStupe.Odlocitev.nov()));
        assertTrue(zOdlocitvijo.porocilo().dovoljuje(), () -> opis(zOdlocitvijo.porocilo().vDto()));
        Long nov = povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC, "19204")
                .orElseThrow().getIdLokalni();
        assertFalse(drug.getId().equals(nov));
        assertNull(igralecRepozitorij.findById(nov).orElseThrow().getNtzsLicenca(), "licenca ostane pri prvem");
    }

    /* U-13 ekipno (Zalog): igralec je prijavljen z imenom iz ene besede
       ("Ecsy"). Pri strogem nacinu to ni napaka, ampak odlocitev, v kateri
       manjka ime - admin vpise ime in priimek in igralec nastane z njima. */
    @Test
    void imeIzEneBesedeJeOdlocitevZVpisanimImenom() {
        UvozStupeStoritev.Izvedba strogo = uvoz.izvedi(posnetek(186), IdentitetaStupe.Nacin.STROGO, Map.of());
        PorociloUvozaDto dto = strogo.porocilo().vDto();
        assertTrue(dto.napake().isEmpty(), () -> opis(dto));
        PorociloUvozaDto.Odlocitev odlocitev = dto.odlocitve().stream()
                .filter(o -> o.idOsebe() == 19828).findFirst().orElseThrow();
        assertEquals(List.of(IdentitetaStupe.MANJKA_IME), odlocitev.manjka());

        UvozStupeStoritev.Izvedba zImenom = uvoz.izvedi(posnetek(186), IdentitetaStupe.Nacin.STROGO,
                Map.of(19828L, new IdentitetaStupe.Odlocitev(0, "Samuel", "Écsy", null, null)));
        assertTrue(zImenom.porocilo().dovoljuje(), () -> opis(zImenom.porocilo().vDto()));
        Igralec samuel = igralecPoOsebi(19828);
        assertEquals("Samuel", samuel.getIme());
        assertEquals("Écsy", samuel.getPriimek());
        assertEquals(LocalDate.of(2016, 11, 24), samuel.getDatumRojstva(), "datum rojstva ostane iz vira");
    }

    /* Zgodovinski uvoz ime iz ene besede dopolni iz najnovejse prijave iste
       osebe v drugih dogodkih (ZnaneOsebeStupe) - brez napake in brez tekem
       brez boja. */
    @Test
    void znaneOsebeDopolnijoIme() {
        Map<Long, IdentitetaStupe.Oseba> znane = Map.of(19828L, new IdentitetaStupe.Oseba(19828, "Samuel Écsy",
                LocalDate.of(2016, 11, 24), Spol.MOSKI, "016/25/26", "Slovenia", null));
        UvozStupeStoritev.Izvedba izvedba = uvoz.izvedi(posnetek(186), IdentitetaStupe.Nacin.SAMODEJNO, Map.of(), znane);
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        assertTrue(izvedba.porocilo().dovoljuje(), () -> opis(dto));
        Igralec samuel = igralecPoOsebi(19828);
        assertEquals(Set.of("Samuel", "Écsy"), Set.of(samuel.getIme(), samuel.getPriimek()));
        assertTrue(dto.opozorila().stream().noneMatch(u -> u.vrsta().startsWith("igralec podtekme ni v registru")));
    }

    /* U-13 ekipno (Novo mesto): oseba brez datuma rojstva. Pri strogem nacinu je
       odlocitev, v kateri manjka datum; ko ga admin vpise, nastane nov igralec
       in njegove tekme so odigrane - ne brez boja. */
    @Test
    void osebaBrezDatumaRojstvaDobiDatumOdAdmina() {
        UvozStupeStoritev.Izvedba strogo = uvoz.izvedi(posnetek(188), IdentitetaStupe.Nacin.STROGO, Map.of());
        PorociloUvozaDto.Odlocitev odlocitev = strogo.porocilo().vDto().odlocitve().stream()
                .filter(o -> o.idOsebe() == 20384).findFirst().orElseThrow();
        assertTrue(odlocitev.manjka().contains(IdentitetaStupe.MANJKA_ROJSTVO));

        UvozStupeStoritev.Izvedba zDatumom = uvoz.izvedi(posnetek(188), IdentitetaStupe.Nacin.STROGO,
                Map.of(20384L, new IdentitetaStupe.Odlocitev(0, "Erazem", "Metljak", LocalDate.of(2014, 3, 1), Spol.MOSKI)));
        PorociloUvozaDto dto = zDatumom.porocilo().vDto();
        assertTrue(zDatumom.porocilo().dovoljuje(), () -> opis(dto));
        Igralec erazem = igralecPoOsebi(20384);
        assertEquals("Metljak", erazem.getPriimek());
        assertEquals(LocalDate.of(2014, 3, 1), erazem.getDatumRojstva());
        assertTrue(dto.opozorila().stream().noneMatch(u -> u.vrsta().startsWith("igralec podtekme ni v registru")));
    }

    // ---------------------------------------------------------------------
    // Posebnosti zapisa lig
    // ---------------------------------------------------------------------

    /* 1. SNTL zensk 2025/26: pet srecanj ima zapisane samo zmagovalce podtekem
       (nizi 0 : 0, izid ekipne tekme pri viru ni sestet). Srecanje dobi izid iz
       zmagovalcev, podtekme so odigrane z 0 : 0 - rating jih bere kot "samo
       zmagovalec" - in uvoz gre skozi vse obvezne preverbe. */
    @Test
    void srecanjeZZapisanimiSamoZmagovalci() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(104);
        Long idSrecanje = povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA,
                ZunanjaPovezava.Vrsta.SRECANJE, "32694").orElseThrow().getIdLokalni();
        Srecanje srecanje = srecanjeRepozitorij.findById(idSrecanje).orElseThrow();
        assertEquals(StatusSrecanja.KONCANO, srecanje.getStatus());
        assertEquals("NTD Kajuh-Slovan", srecanje.getEkipaDomaci().prikazanoIme());
        assertEquals(5, srecanje.getDobljeneDomaci());
        assertEquals(1, srecanje.getDobljeneGost());

        List<TekmaSrecanja> odigrane = tekmaSrecanjaRepozitorij.najdiZaSrecanje(idSrecanje).stream()
                .filter(t -> t.getStatus() == si.turnirko.modeli.StatusTekmeSrecanja.KONCANA).toList();
        assertEquals(6, odigrane.size());
        for (TekmaSrecanja t : odigrane) {
            assertEquals(IzidTekme.IGRANO, t.getIzidTip());
            assertNotNull(t.getZmagovalecStran());
            assertTrue(IzidTekme.samoZmagovalec(t.getIzidTip(), t.getDobljeniNiziDomaci(), t.getDobljeniNiziGost()));
        }
        assertEquals(31, izvedba.porocilo().vDto().opozorila().stream()
                .filter(u -> u.vrsta().startsWith("podtekma z zapisanim zmagovalcem brez nizov"))
                .mapToInt(PorociloUvozaDto.Ugotovitev::stevilo).sum());

        /* Ista liga ima srecanje brez borbe (NTK Ljubljana ni nastopila): uradna
           lestvica ji po Pravilih SNTL odsteje tocko - 5 tock za 3 zmage. */
        Long idBrezBorbe = povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA,
                ZunanjaPovezava.Vrsta.SRECANJE, "32660").orElseThrow().getIdLokalni();
        assertTrue(srecanjeRepozitorij.findById(idBrezBorbe).orElseThrow().isBrezBoja());
        Liga liga = ligaRepozitorij.findById(izvedba.sled().idLiga).orElseThrow();
        assertEquals(1, liga.getOdbitekBrezBoja());
        LestvicaEkipeDto ljubljana = lestvice.lestvica(liga.getId()).stream()
                .filter(v -> v.ekipa().equals("NTK Ljubljana")).findFirst().orElseThrow();
        assertEquals(5, ljubljana.tocke());
        assertTrue(izvedba.porocilo().vDto().preverbe().stream()
                .filter(x -> x.podrocje().equals("Lestvica") && x.opis().contains("tocke"))
                .allMatch(x -> x.obvezna() && x.ujemanje()), "tockovanje vira je skladno");
    }

    /* 1. SNTL moskih 2024/25 je tockovala tudi poraz (zmaga 2, poraz 1). Tockovanje
       lige se prebere iz uradne lestvice, zato ima lestvica Turnirka iste tocke
       kot uradna in preverba tock ostane obvezna. */
    @Test
    void tockovanjeLigeIzUradneLestvice() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(100);
        Liga liga = ligaRepozitorij.findById(izvedba.sled().idLiga).orElseThrow();
        assertEquals(2, liga.getTockeZmaga());
        assertEquals(1, liga.getTockePoraz());
        LestvicaEkipeDto prvi = lestvice.lestvica(liga.getId()).get(0);
        assertEquals("NTK SAVINJA PLASARD", prvi.ekipa());
        assertEquals(31, prvi.tocke());
        assertTrue(izvedba.porocilo().vDto().preverbe().stream()
                .filter(x -> x.podrocje().equals("Lestvica") && x.obvezna())
                .allMatch(PorociloUvozaDto.Preverba::ujemanje));
    }

    /* 3. SNTL moskih 2024/25: uradne "tocke" vira (844, 782 ...) ne sledijo
       nobenemu tockovanju. Liga ostane pri zmaga 2, neodloceno 1, poraz 0,
       preverba tock pa uvoza ne ustavi - izidi srecanj so preverjeni vsak
       posebej, razhaja se samo zapis vira. */
    @Test
    void neskladneUradneTockeNeUstavijoUvoza() {
        UvozStupeStoritev.Izvedba izvedba = uvozi(168);
        Liga liga = ligaRepozitorij.findById(izvedba.sled().idLiga).orElseThrow();
        assertEquals(2, liga.getTockeZmaga());
        assertEquals(1, liga.getTockeNeodloceno());
        assertEquals(0, liga.getTockePoraz());
        assertTrue(liga.isDovoljenoNeodloceno(), "SNTL z 10 tekmami pozna 5 : 5");
        PorociloUvozaDto dto = izvedba.porocilo().vDto();
        assertTrue(dto.opozorila().stream().anyMatch(u -> u.vrsta().startsWith("uradne tocke ne sledijo")));
        assertTrue(dto.preverbe().stream()
                .anyMatch(x -> x.podrocje().equals("Lestvica") && !x.obvezna() && !x.ujemanje()));
    }

    // ---------------------------------------------------------------------

    private Igralec igralecPoOsebi(long idOsebe) {
        Long id = povezave.findByVirAndVrstaAndZunanjiId(VirTekmovanja.STUPA, ZunanjaPovezava.Vrsta.IGRALEC,
                String.valueOf(idOsebe)).orElseThrow().getIdLokalni();
        return igralecRepozitorij.findById(id).orElseThrow();
    }

    private UvozStupeStoritev.Izvedba uvozi(long id) {
        PosnetekDogodka p = posnetek(id);
        UvozStupeStoritev.Izvedba izvedba = uvoz.izvedi(p, IdentitetaStupe.Nacin.SAMODEJNO, Map.of());
        PorociloUvozaDto porocilo = izvedba.porocilo().vDto();
        assertTrue(izvedba.porocilo().dovoljuje(), () -> opis(porocilo));
        assertTrue(porocilo.preverbe().stream().anyMatch(PorociloUvozaDto.Preverba::obvezna),
                "uskladitev mora preveriti zapisano");
        assertNotNull(izvedba.sled().idTurnir != null ? izvedba.sled().idTurnir : izvedba.sled().idLiga);
        return izvedba;
    }

    private Igralec noviIgralec(String ime, String priimek, LocalDate rojstvo, Spol spol) {
        Igralec i = new Igralec();
        i.setIme(ime);
        i.setPriimek(priimek);
        i.setDatumRojstva(rojstvo);
        i.setSpol(spol);
        return igralecRepozitorij.save(i);
    }

    /* Posnetek z dodano kopijo prvega udelezenca (nov id, is_excluded) - tako
       pri viru izgleda prijava, ki jo je organizator izkljucil. */
    private PosnetekDogodka zIzkljucenoPrijavo(long id) throws IOException {
        posnetek(id);
        Path mapa = zacasna.resolve(String.valueOf(id));
        ObjectMapper json = new ObjectMapper();
        JsonNode koren = json.readTree(mapa.resolve("udelezenci.json").toFile());
        ArrayNode udelezenci = (ArrayNode) koren.path("data").path("participants");
        ObjectNode kopija = udelezenci.get(0).deepCopy();
        kopija.put("id", 999_999_999L);
        kopija.put("is_excluded", true);
        udelezenci.add(kopija);
        json.writeValue(mapa.resolve("udelezenci.json").toFile(), koren);
        return PosnetekDogodka.beri(mapa, null, null, LocalDate.of(2026, 9, 13));
    }

    private PosnetekDogodka posnetek(long id) {
        try {
            Path izvor = Path.of(SinhronizacijaStupeTest.class.getResource("/stupa/" + id).toURI());
            Path mapa = zacasna.resolve(String.valueOf(id));
            Files.createDirectories(mapa);
            try (var datoteke = Files.list(izvor)) {
                for (Path gz : datoteke.toList()) {
                    try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
                        Files.copy(in, mapa.resolve(gz.getFileName().toString().replace(".gz", "")),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return PosnetekDogodka.beri(mapa, null, null, LocalDate.of(2026, 9, 13));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static String opis(PorociloUvozaDto p) {
        StringBuilder sb = new StringBuilder("\nSTEVCI " + p.stevci());
        p.napake().forEach(u -> sb.append("\nNAPAKA ").append(u.vrsta()).append(" x").append(u.stevilo())
                .append(" ").append(u.primeri()));
        p.odlocitve().forEach(o -> sb.append("\nODLOCITEV ").append(o.ime()).append(": ").append(o.razlog()));
        p.opozorila().forEach(u -> sb.append("\nOPOZORILO ").append(u.vrsta()).append(" x").append(u.stevilo())
                .append(" ").append(u.primeri().stream().limit(3).collect(Collectors.toList())));
        p.preverbe().forEach(x -> sb.append("\n").append(x.ujemanje() ? "OK " : x.obvezna() ? "NEUJEMANJE " : "RAZLIKA ")
                .append(x.podrocje()).append(": ").append(x.opis())
                .append(x.podrobnosti() == null ? "" : " -> " + x.podrobnosti()));
        return sb.toString();
    }
}
