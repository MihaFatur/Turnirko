/* Integracijski testi ligaskih srecanj: generiranje razporeda, postava in
   generiranje tekem po SNTL formatu, pravilo predcasnega konca, stetje ELO
   (posamicne da, dvojice ne) in lestvica. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.DvobojDto;
import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderIgralecDto;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LestvicaIgralcaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
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

class LigaSrecanjeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;

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
                    new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 0, null));
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
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));
        srecanjeStoritev.vnesiRezultat(aX.id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));

        // dvojice ne stejejo v ELO -> brez zapisa v dnevniku
        assertTrue(ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(dvojice.id()), RatingStanje.SISTEM_KLUBSKI_ELO)
                .isEmpty(), "dvojice ne smejo steti v ELO");
        // posamicna tekma steje -> dva zapisa (za oba igralca)
        assertEquals(2, ratingZgodovinaRepozitorij
                .spremembeZaTekmeSrecanja(List.of(aX.id()), RatingStanje.SISTEM_KLUBSKI_ELO)
                .size(), "posamicna tekma mora obracunati ELO obema igralcema");
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
                    new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 0, null));
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
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));

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
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));

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
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(1).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 3, 1, null));
        srecanjeStoritev.vnesiRezultat(p.tekme().get(2).id(),
                new si.turnirko.dto.VnosRezultataSrecanja(null, 1, 3, null));

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

    private Long ustvariLigo(FormatSrecanja format, Integer zmagZaSrecanje, boolean dvokrozno) {
        LigaVnos v = new LigaVnos("Test liga", "2025/26", SpolKategorija.MOSKI, format, 5,
                zmagZaSrecanje, dvokrozno, 2, 1, 0, true, false, true, null);
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
}
