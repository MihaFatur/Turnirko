/* Koncnica lige (V28): nastane iz koncne lestvice rednega dela, pari sledijo
   nosilskemu vrstnemu redu, serija traja do liga.koncnicaZmag zmag, neodigrane
   tekme odlocene serije izginejo, zmagovalec napreduje v naslednji krog.
   Lestvica rednega dela srecanj koncnice ne steje. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.KoncnicaDto;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.PostavaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.SrecanjePodrobnoDto;
import si.turnirko.dto.TekmaSrecanjaDto;
import si.turnirko.dto.VnosRezultataSrecanja;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusSrecanja;
import si.turnirko.modeli.StatusTekmeSrecanja;
import si.turnirko.modeli.StranEkipe;

class KoncnicaTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KoncnicaStoritev koncnicaStoritev;

    /* Stiri ekipe, enokrozno, koncnica stirih na dve zmagi. Ekipa i premaga
       vse ekipe z vecjim indeksom, zato je lestvica rednega dela 1, 2, 3, 4. */
    @Test
    void koncnicaNastaneIzLestviceInTeceDoFinala() {
        Long liga = ustvariLigo(4, 2);
        List<Long> ekipe = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            ekipe.add(dodajEkipoSKadrom(liga, "Klub " + i));
        }
        ligaStoritev.generirajRazpored(liga);
        odigrajRedniDel(liga, ekipe);

        List<LestvicaEkipeDto> lestvica = ligaStoritev.lestvica(liga);
        assertEquals(ekipe, lestvica.stream().map(LestvicaEkipeDto::idEkipa).toList());

        KoncnicaDto koncnica = koncnicaStoritev.ustvari(liga);
        assertEquals(3, koncnica.serije().size(), "polfinale (2) + finale (1)");
        KoncnicaDto.Serija polfinale1 = koncnica.serije().get(0);
        KoncnicaDto.Serija polfinale2 = koncnica.serije().get(1);
        assertEquals("polfinale", polfinale1.imeKroga());
        // nosilski vrstni red: 1-4 in 3-2; bolje uvrscena ekipa na prvi strani
        assertEquals(ekipe.get(0), polfinale1.stran1().idEkipa());
        assertEquals(ekipe.get(3), polfinale1.stran2().idEkipa());
        assertEquals(ekipe.get(1), polfinale2.stran1().idEkipa());
        assertEquals(ekipe.get(2), polfinale2.stran2().idEkipa());
        assertNull(koncnica.serije().get(2).stran1(), "finale caka na zmagovalca polfinala");

        // tri mogoce tekme: prva pri slabse uvrsceni, druga in odlocilna pri bolje
        List<SrecanjeDto> tekme1 = polfinale1.tekme();
        assertEquals(3, tekme1.size());
        assertEquals(ekipe.get(3), tekme1.get(0).idEkipaDomaci());
        assertEquals(ekipe.get(0), tekme1.get(1).idEkipaDomaci());
        assertEquals(ekipe.get(0), tekme1.get(2).idEkipaDomaci());

        // lestvica rednega dela se s koncnico ne spremeni
        assertEquals(lestvica, ligaStoritev.lestvica(liga));

        // prva ekipa dobi prvi dve tekmi: serija je odlocena, tretja tekma izgine
        odigraj(tekme1.get(0).id(), ekipe.get(0));
        odigraj(tekme1.get(1).id(), ekipe.get(0));
        koncnica = koncnicaStoritev.koncnica(liga);
        polfinale1 = koncnica.serije().get(0);
        assertEquals(ekipe.get(0), polfinale1.idZmagovalec());
        assertEquals(2, polfinale1.stran1().zmage());
        assertEquals(2, polfinale1.tekme().size(), "neodigrana tretja tekma ni vec v seriji");
        assertEquals(ekipe.get(0), koncnica.serije().get(2).stran1().idEkipa());
        assertTrue(koncnica.serije().get(2).tekme().isEmpty(), "finale se nima nasprotnika");

        // drugi polfinale gre na tri tekme: zmaga tretja ekipa
        List<SrecanjeDto> tekme2 = polfinale2.tekme();
        odigraj(tekme2.get(0).id(), ekipe.get(2));
        odigraj(tekme2.get(1).id(), ekipe.get(1));
        odigraj(tekme2.get(2).id(), ekipe.get(2));
        koncnica = koncnicaStoritev.koncnica(liga);
        KoncnicaDto.Serija finale = koncnica.serije().get(2);
        assertEquals("finale", finale.imeKroga());
        assertEquals(ekipe.get(0), finale.stran1().idEkipa());
        assertEquals(ekipe.get(2), finale.stran2().idEkipa());
        assertEquals(3, finale.tekme().size(), "tekme finala nastanejo, ko sta znani obe ekipi");

        odigraj(finale.tekme().get(0).id(), ekipe.get(2));
        odigraj(finale.tekme().get(1).id(), ekipe.get(2));
        finale = koncnicaStoritev.koncnica(liga).serije().get(2);
        assertEquals(ekipe.get(2), finale.idZmagovalec(), "prvak je zmagovalec finala, ne prvi po rednem delu");

        // opis tekme koncnice v zapisniku
        SrecanjePodrobnoDto zapisnik = srecanjeStoritev.podrobno(finale.tekme().get(0).id());
        assertEquals("končnica · finale · 1. tekma", zapisnik.kontekst().opis());
    }

    @Test
    void koncnicaSeleIzKoncanegaRednegaDela() {
        Long liga = ustvariLigo(2, 2);
        dodajEkipoSKadrom(liga, "Klub A");
        dodajEkipoSKadrom(liga, "Klub B");
        ligaStoritev.generirajRazpored(liga);

        DomenskaIzjema napaka = assertThrows(DomenskaIzjema.class, () -> koncnicaStoritev.ustvari(liga));
        assertTrue(napaka.getMessage().contains("neodigranih"));
        assertTrue(!koncnicaStoritev.koncnica(liga).pripravljenaZaZacetek());
    }

    /* Srecanje, ki se lahko konca neodloceno, serije ne more odlociti - liga s
       koncnico zato potrebuje prag zmag, ce ima format sodo stevilo tekem. */
    @Test
    void koncnicaZahtevaSrecanjeBrezNeodlocenega() {
        LigaVnos v = new LigaVnos("Liga brez praga", "2026/27", SpolKategorija.MOSKI, FormatSrecanja.SNTL, 5,
                null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null, null, null, 4, 2);
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.ustvari(v));

        LigaVnos napacnoEkip = new LigaVnos("Liga treh", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL_PRVA, 5, 4, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false,
                null, null, null, 3, 2);
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.ustvari(napacnoEkip));
    }

    /* Domace pravice tekme, ki se se ni zacela, sme organizator zamenjati. */
    @Test
    void zamenjavaDomacinaPredZacetkom() {
        Long liga = ustvariLigo(2, 1);
        Long a = dodajEkipoSKadrom(liga, "Klub A");
        Long b = dodajEkipoSKadrom(liga, "Klub B");
        ligaStoritev.generirajRazpored(liga);
        odigrajRedniDel(liga, List.of(a, b));
        KoncnicaDto koncnica = koncnicaStoritev.ustvari(liga);
        SrecanjeDto finale = koncnica.serije().get(0).tekme().get(0);
        assertEquals(a, finale.idEkipaDomaci(), "ena sama tekma je pri bolje uvrsceni ekipi");

        SrecanjeDto zamenjano = koncnicaStoritev.zamenjajDomacina(finale.id());
        assertEquals(b, zamenjano.idEkipaDomaci());
    }

    // ---------- pomozno ----------

    private Long ustvariLigo(int koncnicaEkip, int zmag) {
        LigaVnos v = new LigaVnos("Liga s koncnico", "2026/27", SpolKategorija.MOSKI,
                FormatSrecanja.SNTL_PRVA, 5, 4, false, 2, 1, 0, true, false,
                RavenTekmovanja.URADNO, false, null, null, null, koncnicaEkip, zmag);
        return ligaStoritev.ustvari(v).id();
    }

    private Long dodajEkipoSKadrom(Long idLiga, String klubIme) {
        Klub klub = klubRepozitorij.save(new Klub(klubIme, null));
        var ekipa = ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(klub.getId(), null, null));
        for (int i = 1; i <= 3; i++) {
            Igralec ig = noviIgralec("Ig" + klubIme.replace(" ", "") + i, "Pri" + i);
            ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
        }
        return ekipa.id();
    }

    /* Redni del: ekipa z manjsim indeksom premaga ekipo z vecjim. */
    private void odigrajRedniDel(Long idLiga, List<Long> ekipe) {
        for (SrecanjeDto s : srecanjeStoritev.zaLigo(idLiga)) {
            int d = ekipe.indexOf(s.idEkipaDomaci());
            int g = ekipe.indexOf(s.idEkipaGost());
            odigraj(s.id(), d < g ? s.idEkipaDomaci() : s.idEkipaGost());
        }
    }

    /* Odigra srecanje tako, da ga dobi dana ekipa: postava po vrsti kadra,
       nato posamicne tekme 3 : 0 za zmagovalca, dokler srecanje ni koncano. */
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
        assertNotNull(srecanjeStoritev.podrobno(idSrecanje));
        assertEquals(StatusSrecanja.KONCANO, srecanjeStoritev.podrobno(idSrecanje).srecanje().status());
    }
}
