/* Prosta ekipa: nastop v ligi brez zapisa v registru klubov (rekreacijske in
   medpodjetniske lige). Testi varujejo troje - da ekipa brez kluba nastane in
   se poimenuje sama, da brez imena ne nastane, in da se prosta in klubska
   ekipa v isti ligi obnasata enakovredno (razpored, lestvica). Zadnje je tudi
   regresija za LEVI stik na klub v EkipaRepozitorij: notranji bi prosto ekipo
   tiho izpustil iz razporeda in lestvice. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.repozitoriji.KlubRepozitorij;

class ProstaEkipaTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;

    @Test
    void prostaEkipaNastaneSamoZImenom() {
        Long liga = ustvariLigo();
        EkipaDto e = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));

        assertNull(e.idKlub(), "prosta ekipa kluba nima");
        assertNull(e.klub());
        assertEquals("Kuhinja", e.prikazanoIme());
        assertEquals(1, e.zaporedna(), "prosta ekipa se nima s cim steti - vedno prva");
    }

    /* Brez kluba je ime edino poimenovanje ekipe, zato ga ni mogoce izpustiti. */
    @Test
    void ekipaBrezKlubaInBrezImenaSeZavrne() {
        Long liga = ustvariLigo();
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, null)));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "   ")));
    }

    /* Ekipi se v razporedu in na lestvici locita samo po prikazanem imenu -
       tudi cez oba tipa (klubska z lastnim imenom proti prosti). */
    @Test
    void enakoPrikazanoImeVIstiLigiSeZavrne() {
        Long liga = ustvariLigo();
        ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));

        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "kuhinja")),
                "velike in male crke ne naredijo druge ekipe");

        Klub klub = klubRepozitorij.save(new Klub("Namizni klub", null));
        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, "Kuhinja")),
                "tudi klubska ekipa si imena ne sme sposoditi");
    }

    /* Prosta ekipa zivi samo v svoji ligi - isto ime je drugje prosto. */
    @Test
    void istoImeVDrugiLigiJeDovoljeno() {
        Long prva = ustvariLigo();
        Long druga = ustvariLigo();
        ligaStoritev.dodajEkipo(prva, new EkipaVnos(null, null, "Kuhinja"));

        EkipaDto e = ligaStoritev.dodajEkipo(druga, new EkipaVnos(null, null, "Kuhinja"));
        assertNotNull(e.id());
    }

    /* Klubska ekipa sme odslej dobiti lastno ime; klub ostane zabelezen. */
    @Test
    void klubskaEkipaObdrziKlubTudiZLastnimImenom() {
        Long liga = ustvariLigo();
        Klub klub = klubRepozitorij.save(new Klub("Savinja", null));

        EkipaDto e = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, "Zeleni zmaji"));
        assertEquals(klub.getId(), e.idKlub());
        assertEquals("Savinja", e.klub());
        assertEquals("Zeleni zmaji", e.prikazanoIme());
    }

    /* Mesana liga: prosta ekipa mora priti v razpored in na lestvico enako kot
       klubska. Notranji stik na klub bi jo tu izgubil. */
    @Test
    void prostaInKlubskaEkipaVIstiLigi() {
        Long liga = ustvariLigo();
        Klub klub = klubRepozitorij.save(new Klub("Savinja", null));
        Long klubska = dodajKader(ligaStoritev.dodajEkipo(liga,
                new EkipaVnos(klub.getId(), null, null)), "Savinjcan");
        Long prosta = dodajKader(ligaStoritev.dodajEkipo(liga,
                new EkipaVnos(null, null, "Kuhinja")), "Kuhar");

        ligaStoritev.generirajRazpored(liga);

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(1, srecanja.size(), "dve ekipi enokrozno -> eno srecanje");
        assertTrue(List.of(srecanja.get(0).idEkipaDomaci(), srecanja.get(0).idEkipaGost())
                .containsAll(List.of(klubska, prosta)), "obe ekipi sta v razporedu");

        List<LestvicaEkipeDto> lestvica = ligaStoritev.lestvica(liga);
        assertEquals(2, lestvica.size());
        LestvicaEkipeDto vrsticaProste = lestvica.stream()
                .filter(v -> v.idEkipa().equals(prosta)).findFirst().orElseThrow();
        assertEquals("Kuhinja", vrsticaProste.ekipa());
        assertNull(vrsticaProste.klub(), "prosta ekipa v stolpcu kluba nima cesa pokazati");
    }

    // ---------- Pomozno ----------

    private Long ustvariLigo() {
        LigaVnos v = new LigaVnos("Rekreacijska liga", "2025/26", SpolKategorija.MESANO,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false, true, null,
                null, null);
        return ligaStoritev.ustvari(v).id();
    }

    /* SAVINJA potrebuje dva igralca na stran; kader je se vedno iz skupnega
       registra igralcev - prosta je ekipa, ne igralci. */
    private Long dodajKader(EkipaDto ekipa, String priimek) {
        for (int i = 1; i <= 2; i++) {
            Igralec ig = noviIgralec("Igralec" + i, priimek + i);
            ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
        }
        return ekipa.id();
    }
}
