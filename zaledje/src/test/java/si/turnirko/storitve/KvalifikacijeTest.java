/* Kvalifikacije med ligami (V41): cone na lestvici, krizni pari iz koncnih
   lestvic, liga kvalifikacij z eno tekmo, serijo ali malo ligo in
   razveljavitev v celoti. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.KoncnicaDto;
import si.turnirko.dto.KvalifikacijePredlogDto;
import si.turnirko.dto.KvalifikacijeVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.PrehodiVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.NacinKvalifikacij;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.StranEkipe;
import si.turnirko.repozitoriji.LigaRepozitorij;

class KvalifikacijeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KoncnicaStoritev koncnicaStoritev;
    @Autowired private KvalifikacijeStoritev kvalifikacijeStoritev;
    @Autowired private LigaRepozitorij ligaRepozitorij;

    /* Cone od roba navznoter: napreduje, kvalifikacije gor, ..., kvalifikacije
       dol, izpade. */
    @Test
    void lestvicaOznaciKvalifikacije() {
        Long liga = ustvariLigo("Savinja liga B");
        List<Long> ekipe = dodajEkipe(liga, "B", 6);
        ligaStoritev.nastaviPrehode(liga, new PrehodiVnos(null, null, 2, 1, 1, 1));
        ligaStoritev.generirajRazpored(liga);
        odigrajRedniDel(liga, ekipe);

        List<String> cone = ligaStoritev.lestvica(liga).stream().map(LestvicaEkipeDto::cona).toList();
        assertEquals(Arrays.asList("NAPREDUJE", "NAPREDUJE", "KVALIFIKACIJE_GOR", null,
                "KVALIFIKACIJE_DOL", "IZPADE"), cone);
    }

    /* Savinja liga: 3. iz A (tik nad izpadom) proti 2. iz B (tik pod
       napredovanjem), ena tekma, doma ekipa visje lige. */
    @Test
    void enaTekmaMedLigama() {
        Lige l = dveLigi(1, 1);

        KvalifikacijePredlogDto predlog = kvalifikacijeStoritev.predlog(l.a, l.b);
        assertTrue(predlog.koncano());
        assertTrue(predlog.ovire().isEmpty(), predlog.ovire().toString());
        assertEquals("Kvalifikacije Savinja liga A/B", predlog.predlaganoIme());
        assertEquals(1, predlog.pari().size());
        assertEquals(l.ekipeA.get(2), predlog.pari().get(0).visja().idEkipa());
        assertEquals(3, predlog.pari().get(0).visja().mesto());
        assertEquals(l.ekipeB.get(1), predlog.pari().get(0).nizja().idEkipa());

        LigaDto kval = kvalifikacijeStoritev.ustvari(l.a, new KvalifikacijeVnos(l.b, NacinKvalifikacij.ENA_TEKMA, null));
        assertEquals("Kvalifikacije Savinja liga A/B", kval.ime());
        assertEquals(l.a, kval.idKvalifikacijeVisja());
        assertEquals(l.b, kval.idKvalifikacijeNizja());
        assertEquals(StatusTekmovanja.V_TEKU, kval.status());
        assertEquals(2, kval.steviloEkip());

        KoncnicaDto k = koncnicaStoritev.koncnica(kval.id());
        assertEquals(1, k.serije().size());
        KoncnicaDto.Serija par = k.serije().get(0);
        assertEquals("kvalifikacije", par.imeKroga());
        assertEquals("Savinja liga A", par.stran1().liga());
        assertEquals("Savinja liga B", par.stran2().liga());
        assertEquals(3, par.stran1().mesto());
        assertEquals(2, par.stran2().mesto());
        assertEquals(1, par.tekme().size(), "ena tekma");
        SrecanjeDto tekma = par.tekme().get(0);
        assertEquals(par.stran1().idEkipa(), tekma.idEkipaDomaci(), "doma igra ekipa visje lige");

        // kader je kopiran - postava se da sestaviti in tekma odigrati
        odigraj(tekma.id(), tekma.idEkipaGost());
        KoncnicaDto po = koncnicaStoritev.koncnica(kval.id());
        assertEquals(par.stran2().idEkipa(), po.serije().get(0).idZmagovalec());

        // lestvici obeh lig ostaneta nedotaknjeni, kvalifikacij ni mogoce ustvariti dvakrat
        assertEquals(4, ligaStoritev.lestvica(l.a).size());
        assertFalse(kvalifikacijeStoritev.predlog(l.a, l.b).ovire().isEmpty());
        assertThrows(DomenskaIzjema.class, () -> kvalifikacijeStoritev.ustvari(l.a,
                new KvalifikacijeVnos(l.b, NacinKvalifikacij.ENA_TEKMA, null)));
    }

    /* Krizni pari: najbolje uvrscena ekipa visje lige z najslabse uvrsceno
       nizje. Odlocen par ne vlece naprej (en krog, ne mreza). */
    @Test
    void krizniPariVSeriji() {
        Lige l = dveLigi(2, 2);   // A: 2. in 3. (izpade 4.); B: 1. in 2. (brez napredovanja)

        KvalifikacijePredlogDto predlog = kvalifikacijeStoritev.predlog(l.a, l.b);
        assertEquals(2, predlog.pari().size());
        assertEquals(l.ekipeA.get(1), predlog.pari().get(0).visja().idEkipa());
        assertEquals(l.ekipeB.get(1), predlog.pari().get(0).nizja().idEkipa());
        assertEquals(l.ekipeA.get(2), predlog.pari().get(1).visja().idEkipa());
        assertEquals(l.ekipeB.get(0), predlog.pari().get(1).nizja().idEkipa());

        LigaDto kval = kvalifikacijeStoritev.ustvari(l.a,
                new KvalifikacijeVnos(l.b, NacinKvalifikacij.SERIJA_DO_2, "Baraž A–B"));
        assertEquals("Baraž A–B", kval.ime());
        KoncnicaDto k = koncnicaStoritev.koncnica(kval.id());
        assertEquals(2, k.serije().size());
        assertTrue(k.serije().stream().allMatch(s -> s.krog() == 1 && s.tekme().size() == 3));

        // serija prvega para: ekipa visje lige dobi dve tekmi
        KoncnicaDto.Serija prvi = k.serije().get(0);
        Long visja = prvi.stran1().idEkipa();
        odigraj(prvi.tekme().get(0).id(), visja);
        odigraj(prvi.tekme().get(1).id(), visja);
        KoncnicaDto po = koncnicaStoritev.koncnica(kval.id());
        assertEquals(visja, po.serije().get(0).idZmagovalec());
        assertEquals(2, po.serije().get(0).tekme().size(), "odvecna tretja tekma izgine");
        assertNull(po.serije().get(1).idZmagovalec());
    }

    @Test
    void poParihLeObEnakemSteviluEkip() {
        Lige l = dveLigi(1, 2);

        KvalifikacijePredlogDto predlog = kvalifikacijeStoritev.predlog(l.a, l.b);
        assertTrue(predlog.pari().isEmpty());
        assertThrows(DomenskaIzjema.class, () -> kvalifikacijeStoritev.ustvari(l.a,
                new KvalifikacijeVnos(l.b, NacinKvalifikacij.ENA_TEKMA, null)));

        // mala liga: vse tri ekipe, zgornja igra v visji ligi
        LigaDto kval = kvalifikacijeStoritev.ustvari(l.a,
                new KvalifikacijeVnos(l.b, NacinKvalifikacij.VSAK_Z_VSAKIM, null));
        assertEquals(StatusTekmovanja.PRIPRAVA, kval.status());
        assertEquals(3, kval.steviloEkip());
        assertEquals(1, kval.stNapreduje());
        assertEquals(2, kval.stIzpade());
        assertFalse(kval.dvokrozno());
        assertNull(kval.koncnicaEkip());
        assertTrue(ligaStoritev.ekipe(kval.id()).stream().allMatch(e -> e.steviloKadra() == 3));

        // naprej tece kot navadna liga
        ligaStoritev.generirajRazpored(kval.id());
        assertEquals(3, srecanjeStoritev.zaLigo(kval.id()).size());
    }

    @Test
    void predKoncemRednegaDelaKvalifikacijNiMogoceUstvariti() {
        Long a = ustvariLigo("Savinja liga A");
        Long b = ustvariLigo("Savinja liga B");
        dodajEkipe(a, "A", 4);
        dodajEkipe(b, "B", 4);
        ligaStoritev.nastaviPrehode(a, new PrehodiVnos(null, List.of(b), 0, 1, 0, 1));
        ligaStoritev.nastaviPrehode(b, new PrehodiVnos(a, null, 1, 0, 1, 0));
        ligaStoritev.generirajRazpored(a);
        ligaStoritev.generirajRazpored(b);

        KvalifikacijePredlogDto predlog = kvalifikacijeStoritev.predlog(a, b);
        assertFalse(predlog.koncano());
        assertEquals(2, predlog.ovire().size(), "redni del nobene od lig ni odigran");
        assertThrows(DomenskaIzjema.class, () -> kvalifikacijeStoritev.ustvari(a,
                new KvalifikacijeVnos(b, NacinKvalifikacij.ENA_TEKMA, null)));
    }

    @Test
    void razveljavitevZbriseLigoKvalifikacij() {
        Lige l = dveLigi(1, 1);
        LigaDto kval = kvalifikacijeStoritev.ustvari(l.a, new KvalifikacijeVnos(l.b, NacinKvalifikacij.ENA_TEKMA, null));

        // koncnica kvalifikacij se ne razveljavi sama zase
        assertThrows(DomenskaIzjema.class, () -> koncnicaStoritev.razveljavi(kval.id()));

        kvalifikacijeStoritev.razveljavi(kval.id());
        assertTrue(ligaRepozitorij.findById(kval.id()).isEmpty());
        assertNull(kvalifikacijeStoritev.predlog(l.a, l.b).idKvalifikacije());

        // in jih je mogoce ustvariti znova
        assertNotNull(kvalifikacijeStoritev.ustvari(l.a,
                new KvalifikacijeVnos(l.b, NacinKvalifikacij.SERIJA_DO_2, null)).id());
    }

    /* Prehodi se dajo vpisati ze ob nastanku lige. */
    @Test
    void prehodiObNastankuLige() {
        Long a = ustvariLigo("Savinja liga A");
        LigaVnos v = new LigaVnos("Savinja liga B", "25/26", SpolKategorija.MESANO, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, false, false, RavenTekmovanja.KLUBSKO, false, null, null, null,
                null, null, null, new PrehodiVnos(a, null, 2, 1, 1, 1));
        LigaDto b = ligaStoritev.ustvari(v);
        assertEquals(a, b.idVisjaLiga());
        assertEquals(2, b.stNapreduje());
        assertEquals(1, b.stKvalifikacijeGor());
        assertEquals(1, b.stKvalifikacijeDol());
    }

    @Test
    void predlaganoIme() {
        assertEquals("Kvalifikacije Savinja liga A/B",
                KvalifikacijeStoritev.predlaganoIme("Savinja liga A", "Savinja liga B"));
        assertEquals("Kvalifikacije 1. SNTL / 2. SNTL vzhod",
                KvalifikacijeStoritev.predlaganoIme("1. SNTL", "2. SNTL vzhod"));
    }

    // ---------- pomozno ----------

    private record Lige(Long a, Long b, List<Long> ekipeA, List<Long> ekipeB) {}

    /* Ligi A (zgoraj) in B s po stirimi ekipami, odigran redni del (lestvica
       po vrstnem redu dodajanja). A: izpade 1 in toliko kvalifikacij za
       obstanek; B: napreduje (4 - 2 - kvalB > 0 ? 1 : 0) in kvalB kvalifikacij. */
    private Lige dveLigi(int kvalA, int kvalB) {
        Long a = ustvariLigo("Savinja liga A");
        Long b = ustvariLigo("Savinja liga B");
        List<Long> ekipeA = dodajEkipe(a, "A", 4);
        List<Long> ekipeB = dodajEkipe(b, "B", 4);
        int napredujeB = kvalB >= 2 ? 0 : 1;
        ligaStoritev.nastaviPrehode(a, new PrehodiVnos(null, List.of(b), 0, 1, 0, kvalA));
        ligaStoritev.nastaviPrehode(b, new PrehodiVnos(a, null, napredujeB, 0, kvalB, 0));
        ligaStoritev.generirajRazpored(a);
        ligaStoritev.generirajRazpored(b);
        odigrajRedniDel(a, ekipeA);
        odigrajRedniDel(b, ekipeB);
        return new Lige(a, b, ekipeA, ekipeB);
    }

    private Long ustvariLigo(String ime) {
        LigaVnos v = new LigaVnos(ime, "25/26", SpolKategorija.MESANO, FormatSrecanja.SAVINJA, 5,
                null, false, 2, 1, 0, false, false, RavenTekmovanja.KLUBSKO, false, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }

    private List<Long> dodajEkipe(Long idLiga, String oznaka, int stevilo) {
        List<Long> ekipe = new ArrayList<>();
        for (int i = 1; i <= stevilo; i++) {
            Klub klub = klubRepozitorij.save(new Klub("Klub " + oznaka + i, null));
            EkipaDto ekipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(klub.getId(), null, null));
            for (int j = 1; j <= 3; j++) {
                Igralec ig = noviIgralec("Ig" + oznaka + i + "x" + j, "Pri" + j);
                ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), j));
            }
            ekipe.add(ekipa.id());
        }
        return ekipe;
    }

    /* Redni del: ekipa z manjsim indeksom premaga ekipo z vecjim. */
    private void odigrajRedniDel(Long idLiga, List<Long> ekipe) {
        for (SrecanjeDto s : srecanjeStoritev.zaLigo(idLiga)) {
            int d = ekipe.indexOf(s.idEkipaDomaci());
            int g = ekipe.indexOf(s.idEkipaGost());
            odigraj(s.id(), d < g ? s.idEkipaDomaci() : s.idEkipaGost());
        }
    }

    /* Odigra srecanje tako, da ga dobi dana ekipa (kot v KoncnicaTest). */
    private void odigraj(Long idSrecanje, Long idZmagovalca) {
        SrecanjePodrobnoDto p = srecanjeStoritev.podrobno(idSrecanje);
        List<PostavaVnos.MestoVnos> mesta = new ArrayList<>();
        for (int i = 0; i < p.pozicijeDomaci().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.DOMACI, p.pozicijeDomaci().get(i),
                    p.kaderDomaci().get(i).idIgralec(), i < p.stVDvojici()));
        }
        for (int i = 0; i < p.pozicijeGost().size(); i++) {
            mesta.add(new PostavaVnos.MestoVnos(StranEkipe.GOST, p.pozicijeGost().get(i),
                    p.kaderGost().get(i).idIgralec(), i < p.stVDvojici()));
        }
        srecanjeStoritev.nastaviPostavo(idSrecanje, new PostavaVnos(mesta));
        boolean domaci = p.srecanje().idEkipaDomaci().equals(idZmagovalca);
        for (TekmaSrecanjaDto t : srecanjeStoritev.podrobno(idSrecanje).tekme()) {
            SrecanjePodrobnoDto zdaj = srecanjeStoritev.podrobno(idSrecanje);
            if (zdaj.srecanje().status() == StatusSrecanja.KONCANO) {
                break;
            }
            TekmaSrecanjaDto sveza = zdaj.tekme().stream().filter(x -> x.id().equals(t.id())).findFirst().orElseThrow();
            if (sveza.status() != StatusTekmeSrecanja.CAKA) {
                continue;
            }
            srecanjeStoritev.vnesiRezultat(t.id(), new VnosRezultataSrecanja(
                    null, domaci ? 3 : 0, domaci ? 0 : 3, null, null));
        }
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(idSrecanje).srecanje().status());
    }
}
