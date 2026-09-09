/* Integracijski testi ligaskih srecanj: generiranje razporeda, postava in
   generiranje tekem po SNTL formatu, pravilo predcasnega konca, stetje ELO
   (posamicne da, dvojice ne) in lestvica. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaDvojiceDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.LestvicaIgralcaLigeDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.TerminiVnos;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.repozitoriji.KlubRepozitorij;
import si.turnirko.repozitoriji.NizSrecanjaRepozitorij;

class LigaSrecanjeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;
    @Autowired private NizSrecanjaRepozitorij nizSrecanjaRepozitorij;
    /* Za preverbe, ki morajo res do baze in ne le do predpomnilnika seje. */
    @PersistenceContext private EntityManager seja;

    @Test
    void dvokroznoRazporedZaStiriEkipe() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, true);
        for (int i = 1; i <= 4; i++) {
            dodajEkipoSKadrom(liga, "Klub " + i, 3);
        }
        ligaStoritev.generirajRazpored(liga);
        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(12, srecanja.size(), "4 ekipe dvokrozno -> 12 srecanj");
        assertEquals(6, srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0));
    }

    /* Seme (zacetek prvega kola + razmik) se vpise ze ob ustvarjanju lige, ko
       ekip se ni - datumi kol nastanejo sele ob zrebu, ko je znano, koliko kol
       liga ima. */
    @Test
    void terminiKolSeIzracunajoObZrebu() {
        LocalDateTime prvo = LocalDateTime.of(2026, 10, 4, 18, 0);
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, true, prvo, 7);
        for (int i = 1; i <= 4; i++) {
            dodajEkipoSKadrom(liga, "Klub " + i, 3);
        }
        ligaStoritev.generirajRazpored(liga);

        Map<Integer, LocalDateTime> termini = terminiPoKolih(liga);
        assertEquals(6, termini.size(), "4 ekipe dvokrozno -> 6 kol");
        assertEquals(prvo, termini.get(1));
        assertEquals(prvo.plusDays(14), termini.get(3), "tretje kolo je dva razmika za prvim");
        assertEquals(prvo.plusDays(35), termini.get(6));
    }

    /* Ura mora prezivet zapis v bazo. Gonilnik sqlite-jdbc pozna eno samo
       obliko za datume in casovne zige ("yyyy-MM-dd"), zato vsakemu zigu uro
       odreze - zato ima stolpec pretvornik CasKotBesedilo. Test brez praznjenja
       seje tega ne bi ujel: entiteta bi prisla iz predpomnilnika transakcije in
       ura bi "obstala", ceprav je v bazi ni. */
    @Test
    void uraTerminaPrezivizapisVBazo() {
        LocalDateTime prvo = LocalDateTime.of(2026, 10, 4, 18, 30);
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false, prvo, 7);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        seja.flush();
        seja.clear();

        assertEquals(prvo, srecanjeStoritev.zaLigo(liga).get(0).predvidenZacetek());
        assertEquals(prvo, ligaStoritev.najdi(liga).zacetekPrvegaKola());
    }

    /* Brez semena razpored terminov nima - takrat vmesnik pri kolu se naprej
       izpise "razpored" in ne datuma. */
    @Test
    void brezSemenaSrecanjaNimajoTermina() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        assertTrue(srecanjeStoritev.zaLigo(liga).stream()
                .allMatch(s -> s.predvidenZacetek() == null));
    }

    /* Rocni popravek je last kola: prestavljeno kolo ne sme premakniti
       naslednjih (ta so ze objavljena). Vpisati ga je mogoce tudi, ko liga ze
       tece - zreb jo je postavil v V_TEKU. */
    @Test
    void rocniTerminPrestaviSamoSvojeKolo() {
        LocalDateTime prvo = LocalDateTime.of(2026, 10, 4, 18, 0);
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, true, prvo, 7);
        for (int i = 1; i <= 4; i++) {
            dodajEkipoSKadrom(liga, "Klub " + i, 3);
        }
        ligaStoritev.generirajRazpored(liga);

        LocalDateTime prestavljeno = LocalDateTime.of(2026, 10, 14, 19, 30);
        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(
                new TerminiVnos.TerminKola(2, prestavljeno))));

        Map<Integer, LocalDateTime> termini = terminiPoKolih(liga);
        assertEquals(prestavljeno, termini.get(2));
        assertEquals(prvo, termini.get(1), "prvo kolo ostane");
        assertEquals(prvo.plusDays(14), termini.get(3), "naslednja kola se ne premaknejo");
    }

    /* Kolo sme termin tudi izgubiti (zacetek null) - to ni "nedotaknjeno
       kolo", ampak izbris termina. */
    @Test
    void terminKolaJeMogoceIzbrisati() {
        LocalDateTime prvo = LocalDateTime.of(2026, 10, 4, 18, 0);
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false, prvo, 7);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(
                new TerminiVnos.TerminKola(1, null))));

        assertTrue(srecanjeStoritev.zaLigo(liga).stream()
                .allMatch(s -> s.predvidenZacetek() == null));
    }

    @Test
    void terminNeobstojecegaKolaSeZavrne() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        TerminiVnos vnos = new TerminiVnos(List.of(
                new TerminiVnos.TerminKola(9, LocalDateTime.of(2026, 10, 4, 18, 0))));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Vsa srecanja kola se igrajo isti dan, zato dobijo isti zacetek. */
    private Map<Integer, LocalDateTime> terminiPoKolih(Long idLiga) {
        Map<Integer, LocalDateTime> poKolih = new LinkedHashMap<>();
        for (SrecanjeDto s : srecanjeStoritev.zaLigo(idLiga)) {
            LocalDateTime prej = poKolih.put(s.kolo(), s.predvidenZacetek());
            if (prej != null) {
                assertEquals(prej, s.predvidenZacetek(), "srecanja istega kola imajo isti termin");
            }
        }
        return poKolih;
    }

    @Test
    void postavaGeneriraTekmeVSntlVrstnemRedu() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        List<String> oznake = p.tekme().stream().map(TekmaSrecanjaDto::oznaka).toList();
        assertEquals(List.of("dvojice", "A-X", "B-Y", "C-Z", "B-X", "A-Z", "C-Y", "B-Z", "C-X", "A-Y"),
                oznake);
        assertEquals(StatusSrecanja.POTEKA, p.srecanje().status());
    }

    /* Savinja: dvojice prve, nato A-X, B-Y, A-Y, B-X. Ker igralca dvojice ne
       izbirata (oba sta v paru), mora postava zdrzati brez oznacevanja izbire. */
    @Test
    void postavaGeneriraTekmeVSavinjaVrstnemRedu() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SAVINJA, null);
        nastaviPostavo(srecanje);

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        assertEquals(List.of("A", "B"), p.pozicijeDomaci());
        assertEquals(List.of("dvojice", "A-X", "B-Y", "A-Y", "B-X"),
                p.tekme().stream().map(TekmaSrecanjaDto::oznaka).toList());
    }

    /* Prag zmag se meri na formatu: pri Savinji (5 tekem) je 6 nemogoc. */
    @Test
    void previsokPragZmagJavi() {
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ustvariLigo(FormatSrecanja.SAVINJA, 6, false));
    }

    @Test
    void predcasniKonecOznaciPreostaleNeodigrane() {
        // prag 4: domaci dobi prve 4 tekme (dvojice + A-X + B-Y + C-Z) -> konec
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, 4);
        nastaviPostavo(srecanje);

        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(srecanje).tekme();
        for (int i = 0; i < 4; i++) {
            srecanjeStoritev.vnesiRezultat(tekme.get(i).id(),
                    new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 0, null, null));
        }

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        assertEquals(StatusSrecanja.KONCANO, p.srecanje().status());
        assertEquals(4, p.srecanje().dobljeneDomaci());
        assertEquals(0, p.srecanje().dobljeneGost());
        long neodigrane = p.tekme().stream()
                .filter(t -> t.status() == StatusTekmeSrecanja.NEODIGRANA).count();
        assertEquals(6, neodigrane, "preostalih 6 tekem je neodigranih");
    }

    @Test
    void eloSeObracunaZaPosamicneNeZaDvojice() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(srecanje).tekme();

        TekmaSrecanjaDto dvojice = tekme.get(0);   // "dvojice"
        TekmaSrecanjaDto aX = tekme.get(1);        // "A-X"
        srecanjeStoritev.vnesiRezultat(dvojice.id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));
        srecanjeStoritev.vnesiRezultat(aX.id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));

        // dvojice ne stejejo v ELO -> brez zapisa v dnevniku
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(dvojice.id()), RatingStanje.SISTEM_KLUBSKI_ELO)
                .isEmpty(), "dvojice ne smejo steti v ELO");
        // posamicna tekma steje -> dva zapisa (za oba igralca)
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(aX.id()), RatingStanje.SISTEM_KLUBSKI_ELO)
                .size(), "posamicna tekma mora obracunati ELO obema igralcema");
    }

    /* Tocke po nizih se odslej vpisujejo tudi pri ligaskih tekmah - po istih
       pravilih kot pri turnirskih (NiziPravila). */
    @Test
    void tockeNizovLigaskeTekmeSePreverijoInShranijo() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        Long aX = srecanjeStoritev.podrobno(srecanje).tekme().get(1).id();

        // neveljaven niz: 10:9 ni koncan niz
        assertThrows(NeveljavenVnosIzjema.class,
                () -> srecanjeStoritev.vnesiRezultat(aX, new VnosRezultataSrecanja(
                        null, 3, 0, null,
                        List.of(new NizVnos(11, 5), new NizVnos(10, 9), new NizVnos(11, 7)))));

        // stevilo nizov se ne ujema z rezultatom
        assertThrows(NeveljavenVnosIzjema.class,
                () -> srecanjeStoritev.vnesiRezultat(aX, new VnosRezultataSrecanja(
                        null, 3, 0, null,
                        List.of(new NizVnos(11, 5), new NizVnos(11, 7)))));

        // nemogoc vrstni red: pri 3:1 se cetrti niz po izidu 3:0 ne bi igral
        assertThrows(NeveljavenVnosIzjema.class,
                () -> srecanjeStoritev.vnesiRezultat(aX, new VnosRezultataSrecanja(
                        null, 3, 1, null,
                        List.of(new NizVnos(11, 4), new NizVnos(11, 7),
                                new NizVnos(11, 8), new NizVnos(8, 11)))));

        // zavrnjeni vnosi niso pustili delnih zapisov
        assertTrue(nizSrecanjaRepozitorij.findByTekmaIdOrderByZaporednaStAsc(aX).isEmpty(),
                "zavrnjen vnos ne sme shraniti nobenega niza");

        TekmaSrecanjaDto vnesena = srecanjeStoritev.vnesiRezultat(aX, new VnosRezultataSrecanja(
                null, 3, 1, null,
                List.of(new NizVnos(11, 4), new NizVnos(8, 11),
                        new NizVnos(11, 7), new NizVnos(12, 10))));

        assertEquals(4, nizSrecanjaRepozitorij.findByTekmaIdOrderByZaporednaStAsc(aX).size());
        // tocke gredo v odgovor in v zapisnik po vrsti, kot so bile odigrane
        assertEquals(List.of("11:4", "8:11", "11:7", "12:10"), oznakeNizov(vnesena));
        assertEquals(List.of("11:4", "8:11", "11:7", "12:10"),
                oznakeNizov(srecanjeStoritev.podrobno(srecanje).tekme().get(1)));
    }

    /* Vnos tock je neobvezen tudi v ligi - brez njih se rezultat shrani enako. */
    @Test
    void tockeNizovLigaskeTekmeSoNeobvezne() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        Long aX = srecanjeStoritev.podrobno(srecanje).tekme().get(1).id();

        srecanjeStoritev.vnesiRezultat(aX, new VnosRezultataSrecanja(null, 3, 1, null, null));

        assertTrue(nizSrecanjaRepozitorij.findByTekmaIdOrderByZaporednaStAsc(aX).isEmpty());
        assertEquals(List.of(), oznakeNizov(srecanjeStoritev.podrobno(srecanje).tekme().get(1)));
    }

    private static List<String> oznakeNizov(TekmaSrecanjaDto t) {
        return t.nizi().stream().map(n -> n.tocke1() + ":" + n.tocke2()).toList();
    }

    @Test
    void lestvicaZmagovalecPrvi() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, 4, false);
        Long ekipaA = dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        SrecanjeDto srecanje = srecanjeStoritev.zaLigo(liga).get(0);
        nastaviPostavo(srecanje.id());
        List<TekmaSrecanjaDto> tekme = srecanjeStoritev.podrobno(srecanje.id()).tekme();
        for (int i = 0; i < 4; i++) {
            srecanjeStoritev.vnesiRezultat(tekme.get(i).id(),
                    new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 0, null, null));
        }

        List<LestvicaEkipeDto> lestvica = ligaStoritev.lestvica(liga);
        assertEquals(2, lestvica.size());
        LestvicaEkipeDto prvi = lestvica.get(0);
        assertEquals(srecanje.idEkipaDomaci(), prvi.idEkipa(), "domaci zmagovalec je prvi");
        assertEquals(1, prvi.mesto());
        assertEquals(2, prvi.tocke());
        assertEquals(1, prvi.zmage());
        assertEquals(0, lestvica.get(1).tocke());
    }

    /* Ligaska posamicna tekma je za igralca enakovredna turnirski: steti mora
       tudi v medsebojni izid ("1 na 1") in v zmage/poraze na lestvici, ne le
       v ELO. */
    @Test
    void ligaskaPosamicnaStejeVMedsebojniIzidInZmage() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        Long idA = p.kaderDomaci().get(0).idIgralec();  // pozicija A
        Long idX = p.kaderGost().get(0).idIgralec();    // pozicija X

        TekmaSrecanjaDto aX = p.tekme().get(1);
        assertEquals("A-X", aX.oznaka());
        srecanjeStoritev.vnesiRezultat(aX.id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));

        DvobojDto dvoboj = statistikaStoritev.dvoboj(idA, idX);
        assertEquals(1, dvoboj.odigrane(), "ligaska tekma mora steti v medsebojni izid");
        assertEquals(1, dvoboj.zmagePrvega());
        assertEquals(0, dvoboj.zmageDrugega());
        assertEquals(3, dvoboj.niziPrvega());
        assertEquals(1, dvoboj.niziDrugega());
        assertTrue(dvoboj.tekme().get(0).ligaska(), "vrstica mora biti oznacena kot ligaska");

        Map<Long, LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica().stream()
                .collect(Collectors.toMap(LestvicaIgralcaDto::idIgralca, v -> v));
        assertEquals(1, lestvica.get(idA).odigrane());
        assertEquals(1, lestvica.get(idA).zmage());
        assertEquals(0, lestvica.get(idA).porazi());
        assertEquals(1, lestvica.get(idX).odigrane());
        assertEquals(0, lestvica.get(idX).zmage());
        assertEquals(1, lestvica.get(idX).porazi());
    }

    /* Dvojice ostanejo zunaj osebne statistike - izida para ni mogoce pripisati
       posamezniku (enako pravilo kot pri ELO). */
    @Test
    void ligaskeDvojiceNeStejejoVOsebnoStatistiko() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        Long idA = p.kaderDomaci().get(0).idIgralec();
        Long idX = p.kaderGost().get(0).idIgralec();

        TekmaSrecanjaDto dvojice = p.tekme().get(0);
        assertEquals("dvojice", dvojice.oznaka());
        srecanjeStoritev.vnesiRezultat(dvojice.id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));

        assertEquals(0, statistikaStoritev.dvoboj(idA, idX).odigrane(),
                "dvojice ne smejo steti v medsebojni izid");
        Map<Long, LestvicaIgralcaDto> lestvica = statistikaStoritev.globalnaLestvica().stream()
                .collect(Collectors.toMap(LestvicaIgralcaDto::idIgralca, v -> v));
        assertEquals(0, lestvica.get(idA).odigrane());
        assertEquals(0, lestvica.get(idX).odigrane());
    }

    /* Bilanca kadra (izpis pod vrstico lestvice) steje samo POSAMICNE tekme te
       lige. Prva dva igralca domacih sta tudi v dvojicah, ki so tu dobljene -
       ce bi dvojice stele, bi imel drugi igralec zmago, ceprav je svojo
       posamicno tekmo izgubil. */
    @Test
    void bilancaKadraStejeSamoPosamicneTekme() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto srecanje = srecanjeStoritev.zaLigo(liga).get(0);
        nastaviPostavo(srecanje.id());

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje.id());
        // dvojice domacim, A-X domacim, B-Y gostom
        srecanjeStoritev.vnesiRezultat(p.tekme().get(0).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(2).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 1, 3, null, null));

        Long idA = p.kaderDomaci().get(0).idIgralec();
        Long idB = p.kaderDomaci().get(1).idIgralec();
        Long idC = p.kaderDomaci().get(2).idIgralec();
        Map<Long, KaderIgralecDto> doma = ligaStoritev.kader(srecanje.idEkipaDomaci()).stream()
                .collect(Collectors.toMap(KaderIgralecDto::idIgralec, k -> k));

        assertEquals(1, doma.get(idA).zmage(), "A je dobil svojo posamicno tekmo");
        assertEquals(0, doma.get(idA).porazi());
        assertEquals(0, doma.get(idB).zmage(), "dvojice ne smejo steti v bilanco");
        assertEquals(1, doma.get(idB).porazi(), "B je svojo posamicno tekmo izgubil");
        assertEquals(0, doma.get(idC).zmage(), "C ni igral - bilanca ostane 0 : 0");
        assertEquals(0, doma.get(idC).porazi());
    }

    /* Kader pod vrstico lestvice je razvrscen po izkupicku za TO ekipo v tej
       ligi in ne po organizatorjevem vrstnem redu: vprasanje odprte vrstice je
       "kdo ekipo nosi". C je v kadru zadnji (vrstni red 3), a edini z zmago,
       zato mora biti prvi; A in B sta izenacena (0 : 1) in obdrzita svoj
       vrstni red. Postava srecanja te razvrstitve NE deli - tam mesta A/B/C
       dolocajo vrstni red kadra. */
    @Test
    void kaderJeRazvrscenPoZmagahZaEkipo() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        Long idA = p.kaderDomaci().get(0).idIgralec();
        Long idB = p.kaderDomaci().get(1).idIgralec();
        Long idC = p.kaderDomaci().get(2).idIgralec();

        // A-X in B-Y gostom, C-Z domacim
        srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 1, 3, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(2).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 1, 3, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(3).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));

        List<Long> vrstniRed = ligaStoritev.kader(p.srecanje().idEkipaDomaci()).stream()
                .map(KaderIgralecDto::idIgralec).toList();
        assertEquals(List.of(idC, idA, idB), vrstniRed,
                "zmagovalec na vrh, izenacena ohranita organizatorjev vrstni red");

        assertEquals(List.of(idA, idB, idC),
                srecanjeStoritev.podrobno(srecanje).kaderDomaci().stream()
                        .map(KaderIgralecDto::idIgralec).toList(),
                "postava mora ostati v organizatorjevem vrstnem redu (mesta A/B/C)");
    }

    /* Bilanca kadra je bilanca PRI TEJ ekipi in ne v celi ligi. Liga brez
       prepovedi dvojne registracije sme istega igralca voditi v dveh kadrih -
       tam je za eno ekipo zmagal, za drugo izgubil, zato mora biti pri prvi na
       vrhu in pri drugi na dnu. */
    @Test
    void bilancaKadraStejeSamoTekmeZaTistoEkipo() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        Long ekipaA = dodajEkipoSKadrom(liga, "Klub A", 3);
        Long ekipaB = dodajEkipoSKadrom(liga, "Klub B", 3);
        Long ekipaC = dodajEkipoSKadrom(liga, "Klub C", 3);
        /* Vrstni red 1 in priimek pred ostalimi ga postavita na celo obeh
           kadrov (ORDER BY vrstni_red, priimek), zato ga nastaviPostavo obakrat
           uvrsti na prvo mesto (A oz. X). */
        Igralec dvojni = noviIgralec("Dvojno", "Aaadvojni");
        ligaStoritev.dodajVKader(ekipaA, new KaderVnos(dvojni.getId(), 1));
        ligaStoritev.dodajVKader(ekipaB, new KaderVnos(dvojni.getId(), 1));
        ligaStoritev.generirajRazpored(liga);

        odigrajPrvoPosamicno(najdiSrecanje(liga, ekipaA, ekipaC), ekipaA, true);
        odigrajPrvoPosamicno(najdiSrecanje(liga, ekipaB, ekipaC), ekipaB, false);

        List<KaderIgralecDto> kaderA = ligaStoritev.kader(ekipaA);
        assertEquals(dvojni.getId(), kaderA.get(0).idIgralec(), "za ekipo A je edini z zmago");
        assertEquals(1, kaderA.get(0).zmage());
        assertEquals(0, kaderA.get(0).porazi(), "poraz pri drugi ekipi se ne sme pristeti");

        List<KaderIgralecDto> kaderB = ligaStoritev.kader(ekipaB);
        assertEquals(dvojni.getId(), kaderB.get(kaderB.size() - 1).idIgralec(),
                "za ekipo B je edini s porazom, zato gre na dno");
        assertEquals(0, kaderB.get(kaderB.size() - 1).zmage(),
                "zmaga pri drugi ekipi se ne sme pristeti");
        assertEquals(1, kaderB.get(kaderB.size() - 1).porazi());
    }

    /* Lestvica posameznikov lige steje SAMO posamicne tekme te lige. Dvojice so
       tu dobljene, njun drugi igralec pa je svojo posamicno tekmo izgubil - ce
       bi dvojice stele, bi imel zmago. */
    @Test
    void lestvicaIgralcevStejeSamoPosamicneTekmeTeLige() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto srecanje = srecanjeStoritev.zaLigo(liga).get(0);
        nastaviPostavo(srecanje.id());

        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje.id());
        // dvojice domacim, A-X domacim (3:1), B-Y gostom (1:3)
        srecanjeStoritev.vnesiRezultat(p.tekme().get(0).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(2).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 1, 3, null, null));

        Long idA = p.kaderDomaci().get(0).idIgralec();
        Long idB = p.kaderDomaci().get(1).idIgralec();
        Long idC = p.kaderDomaci().get(2).idIgralec();
        Map<Long, LestvicaIgralcaLigeDto> po = ligaStoritev.lestvicaIgralcev(liga).stream()
                .collect(Collectors.toMap(LestvicaIgralcaLigeDto::idIgralec, v -> v));

        assertEquals(4, po.size(), "na lestvici so samo igralci z odigrano posamicno tekmo");
        assertFalse(po.containsKey(idC), "C ni igral, zato vrstice nima");
        assertEquals(1, po.get(idA).zmage());
        assertEquals(0, po.get(idA).porazi());
        assertEquals(100, po.get(idA).odstotek());
        assertEquals(3, po.get(idA).dobljeniNizi());
        assertEquals(1, po.get(idA).prejetiNizi());
        assertEquals(0, po.get(idB).zmage(), "dvojice ne smejo steti med zmage posameznika");
        assertEquals(1, po.get(idB).porazi());
    }

    /* Merilo lestvice so zmage; sele ob izenacenju odloca uspesnost. Igralec z
       dvema zmagama iz treh tekem mora biti pred tistim z eno samo zmago iz ene
       tekme (100 %), sicer bi vrh lestvice zasedel, kdor je igral najmanj. */
    @Test
    void lestvicaIgralcevUrediPoZmagahInSeleNatoPoUspesnosti() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, true);
        Long ekipaA = dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        /* Postava jemlje kader po vrstnem redu, zato prvi in drugi clan ekipe A
           vselej stojita na prvem oz. drugem mestu svoje strani. */
        List<KaderIgralecDto> kaderA = ligaStoritev.kader(ekipaA);
        Long prviA = kaderA.get(0).idIgralec();
        Long drugiA = kaderA.get(1).idIgralec();

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        for (int i = 0; i < srecanja.size(); i++) {
            SrecanjeDto s = srecanja.get(i);
            nastaviPostavo(s.id());
            SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
            boolean aDoma = s.idEkipaDomaci().equals(ekipaA);
            // prvi clan ekipe A dobi svojo tekmo v obeh srecanjih
            srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(), izid(aDoma));
            // drugi clan pa samo v prvem - ena zmaga iz ene same tekme
            if (i == 0) {
                srecanjeStoritev.vnesiRezultat(p.tekme().get(2).id(), izid(aDoma));
            }
        }

        List<LestvicaIgralcaLigeDto> lestvica = ligaStoritev.lestvicaIgralcev(liga);
        LestvicaIgralcaLigeDto prvi = lestvica.get(0);
        assertEquals(prviA, prvi.idIgralec(), "dve zmagi sta pred eno zmago s 100 %");
        assertEquals(1, prvi.mesto());
        assertEquals(2, prvi.zmage());
        assertEquals(2, prvi.odigrane());
        assertEquals(100, prvi.odstotek());
        LestvicaIgralcaLigeDto drugi = lestvica.get(1);
        assertEquals(drugiA, drugi.idIgralec());
        assertEquals(1, drugi.zmage(), "prav tako 100 %, a iz ene same tekme");
        assertEquals(100, drugi.odstotek());
    }

    /* Dvojica je ista dvojica tudi, ko igra v gosteh: dvokrozna liga postavi
       isti par enkrat kot domaci in enkrat kot gost, na lestvici pa mora ostati
       ena sama vrstica z obema tekmama. */
    @Test
    void lestvicaDvojicZdruziParNeGledeNaStran() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, true);
        Long ekipaA = dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(2, srecanja.size(), "dve ekipi dvokrozno -> dve srecanji");
        for (SrecanjeDto s : srecanja) {
            nastaviPostavo(s.id());
            SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(s.id());
            // dvojice (prva tekma po SNTL) dobi vselej par ekipe A
            srecanjeStoritev.vnesiRezultat(p.tekme().get(0).id(),
                    izid(s.idEkipaDomaci().equals(ekipaA)));
        }

        List<LestvicaDvojiceDto> dvojice = ligaStoritev.lestvicaDvojic(liga);
        assertEquals(2, dvojice.size(), "dva para, vsak z eno vrstico");
        LestvicaDvojiceDto prva = dvojice.get(0);
        assertEquals(1, prva.mesto());
        assertEquals(2, prva.odigrane(), "obe tekmi para stejeta v isto vrstico");
        assertEquals(2, prva.zmage());
        assertEquals(0, prva.porazi());
        assertEquals(100, prva.odstotek());
        assertEquals(6, prva.dobljeniNizi());
        assertEquals(2, prva.prejetiNizi());
        assertNotEquals(prva.idPrvi(), prva.idDrugi(), "par sta dva razlicna igralca");
        assertEquals(0, dvojice.get(1).zmage());
        assertEquals(2, dvojice.get(1).porazi());
    }

    /* Posamicne tekme na lestvico dvojic ne smejo - in obratno. Sicer bi
       najboljsa dvojica postal par, ki dvojic sploh ni igral. */
    @Test
    void lestvicaDvojicNeStejePosamicnihTekem() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        nastaviPostavo(srecanje);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(),  // A-X
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null, null));

        assertTrue(ligaStoritev.lestvicaDvojic(p.srecanje().idLiga()).isEmpty(),
                "brez odigranih dvojic je lestvica dvojic prazna");
        assertEquals(2, ligaStoritev.lestvicaIgralcev(p.srecanje().idLiga()).size());
    }

    /* Seznam ekip nosi velikost kadra, da vrstica ekipe ne potrebuje svoje
       poizvedbe na kader. */
    @Test
    void seznamEkipPoveVelikostKadra() {
        Long liga = ustvariLigo(FormatSrecanja.SNTL, null, false);
        Long ekipaA = dodajEkipoSKadrom(liga, "Klub A", 3);
        Long ekipaB = dodajEkipoSKadrom(liga, "Klub B", 0);

        Map<Long, Integer> kadri = ligaStoritev.ekipe(liga).stream()
                .collect(Collectors.toMap(EkipaDto::id, EkipaDto::steviloKadra));
        assertEquals(3, kadri.get(ekipaA));
        assertEquals(0, kadri.get(ekipaB), "ekipa brez kadra ima 0, ne manjkajoce vrednosti");
    }

    @Test
    void napacnoSteviloIgralcevZaDvojiceJavi() {
        Long srecanje = pripraviEnoSrecanje(FormatSrecanja.SNTL, null);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(srecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        List<String> pd = p.pozicijeDomaci();
        List<String> pg = p.pozicijeGost();
        // domaci: vsi trije oznaceni za dvojice (napaka - dovoljena sta 2)
        for (int i = 0; i < pd.size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, pd.get(i),
                    p.kaderDomaci().get(i).idIgralec(), true));
        }
        for (int i = 0; i < pg.size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, pg.get(i),
                    p.kaderGost().get(i).idIgralec(), i < 2));
        }
        assertThrows(NeveljavenVnosIzjema.class,
                () -> srecanjeStoritev.nastaviPostavo(srecanje, new PostavaVnos(mesta)));
    }

    // ---------- pomozne metode ----------

    /* Izid 3:1 za tisto stran, ki naj tekmo dobi. Pri dvokrozni ligi ista ekipa
       enkrat gostuje, zato je "kdo je zmagal" odvisen od strani in ne od izida. */
    private static si.turnirko.dto.VnosRezultataSrecanja izid(boolean zmagaDomacih) {
        return new si.turnirko.dto.VnosRezultataSrecanja(
                null, zmagaDomacih ? 3 : 1, zmagaDomacih ? 1 : 3, null, null);
    }

    private Long ustvariLigo(FormatSrecanja format, Integer zmagZaSrecanje, boolean dvokrozno) {
        return ustvariLigo(format, zmagZaSrecanje, dvokrozno, null, null);
    }

    private Long ustvariLigo(FormatSrecanja format, Integer zmagZaSrecanje, boolean dvokrozno,
                             LocalDateTime zacetekPrvegaKola, Integer razmikDni) {
        LigaVnos v = new LigaVnos("Test liga", "2025/26", SpolKategorija.MOSKI, format, 5,
                zmagZaSrecanje, dvokrozno, 2, 1, 0, true, false, true, null,
                zacetekPrvegaKola, razmikDni);
        return ligaStoritev.ustvari(v).id();
    }

    private Long dodajEkipoSKadrom(Long idLiga, String klubIme, int stIgralcev) {
        Klub klub = klubRepozitorij.save(new Klub(klubIme, null));
        var ekipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(klub.getId(), null, null));
        for (int i = 1; i <= stIgralcev; i++) {
            Igralec ig = noviIgralec("Ig" + klubIme.replace(" ", "") + i, "Pri" + i);
            ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
        }
        return ekipa.id();
    }

    /* Liga z dvema ekipama (po 3 igralci), generiran razpored; vrne id edinega
       srecanja. */
    private Long pripraviEnoSrecanje(FormatSrecanja format, Integer zmagZaSrecanje) {
        Long liga = ustvariLigo(format, zmagZaSrecanje, false);
        dodajEkipoSKadrom(liga, "Klub A", 3);
        dodajEkipoSKadrom(liga, "Klub B", 3);
        ligaStoritev.generirajRazpored(liga);
        return srecanjeStoritev.zaLigo(liga).get(0).id();
    }

    /* Postava po mestih formata: vsakemu mestu igralec iz kadra po vrsti, prva
       dva na strani gresta v dvojice (pri dvomestnih formatih torej oba). */
    private void nastaviPostavo(Long idSrecanje) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        List<String> pd = p.pozicijeDomaci();
        List<String> pg = p.pozicijeGost();
        for (int i = 0; i < pd.size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, pd.get(i),
                    p.kaderDomaci().get(i).idIgralec(), i < 2)); // prva dva v dvojice
        }
        for (int i = 0; i < pg.size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, pg.get(i),
                    p.kaderGost().get(i).idIgralec(), i < 2));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
        assertFalse(srecanjeStoritev.podrobno(idSrecanje).tekme().isEmpty());
    }

    /* Srecanje dveh dolocenih ekip - ne glede na to, katera je domaca. */
    private Long najdiSrecanje(Long idLiga, Long ekipa1, Long ekipa2) {
        return srecanjeStoritev.zaLigo(idLiga).stream()
                .filter(s -> List.of(s.idEkipaDomaci(), s.idEkipaGost())
                        .containsAll(List.of(ekipa1, ekipa2)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("srecanja teh dveh ekip ni"))
                .id();
    }

    /* Postavi obe ekipi in odigra prvo posamicno tekmo (mesti A-X); izid se
       zapise tako, da jo ekipa "zmagovalka" dobi oz. izgubi. */
    private void odigrajPrvoPosamicno(Long idSrecanje, Long idEkipa, boolean zmaga) {
        nastaviPostavo(idSrecanje);
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        boolean domaca = p.srecanje().idEkipaDomaci().equals(idEkipa);
        boolean zmagaDomacih = domaca == zmaga;
        TekmaSrecanjaDto prva = p.tekme().get(1); // za dvojicami: A-X
        srecanjeStoritev.vnesiRezultat(prva.id(), new si.turnirko.dto.VnosRezultataSrecanja(
                null, zmagaDomacih ? 3 : 1, zmagaDomacih ? 1 : 3, null, null));
    }
}
