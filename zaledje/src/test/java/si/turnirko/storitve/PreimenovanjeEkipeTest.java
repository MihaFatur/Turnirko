/* Preimenovanje ekipe lige (LigaStoritev.preimenujEkipo): dovoljeno v VSAKEM
   stanju lige, tudi ko ta tece - to je tisto, zaradi cesar obstaja, saj se
   napaka v imenu opazi, ko je razpored ze razposlan in ekipe ni vec mogoce
   odstraniti in vpisati znova. Spremeni se samo ime; ekipa ostane ista (id,
   klub, zaporedna, kader, srecanja), zato lestvica in razpored takoj kazeta
   novo ime.

   Ekipni dogodek ima svoje teste v EkipniTurnirTest, kopije ekip v ligi
   kvalifikacij pa v KvalifikacijeTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaImeVnos;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.KaderVnos;
import si.turnirko.dto.LestvicaEkipeDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.izjeme.NiNajdenoIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.Igralec;
import si.turnirko.modeli.Klub;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.repozitoriji.KlubRepozitorij;

class PreimenovanjeEkipeTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @Autowired private KlubRepozitorij klubRepozitorij;

    /* Jedro naloge: liga je ze izzrebana in tece, ekipa pa dobi novo ime. Id
       ekipe ostane isti, zato razpored in lestvica nosita novo ime brez
       kakrsnegakoli popravljanja srecanj. */
    @Test
    void ekipoLigeVTekuSeLahkoPreimenuje() {
        Long liga = ustvariLigo();
        EkipaDto kuhinja = dodajKader(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja")), "Kuhar");
        EkipaDto pisarna = dodajKader(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Pisarna")), "Pisar");
        ligaStoritev.generirajRazpored(liga);
        assertEquals(StatusTekmovanja.V_TEKU, ligaStoritev.najdi(liga).dejanskiStatus(), "liga tece");

        EkipaDto po = ligaStoritev.preimenujEkipo(kuhinja.id(), new EkipaImeVnos("Kuhinja 2000"));

        assertEquals(kuhinja.id(), po.id(), "ekipa je ista, spremenilo se je samo ime");
        assertEquals("Kuhinja 2000", po.prikazanoIme());
        assertEquals(kuhinja.steviloKadra(), po.steviloKadra(), "kader ostane");

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(1, srecanja.size(), "razpored se ne dotakne");
        SrecanjeDto srecanje = srecanja.get(0);
        assertEquals(kuhinja.id().equals(srecanje.idEkipaDomaci()) ? "Kuhinja 2000" : "Pisarna",
                srecanje.domaci());
        assertEquals(kuhinja.id().equals(srecanje.idEkipaGost()) ? "Kuhinja 2000" : "Pisarna",
                srecanje.gost());

        List<LestvicaEkipeDto> lestvica = ligaStoritev.lestvica(liga);
        assertTrue(lestvica.stream().anyMatch(v -> v.idEkipa().equals(kuhinja.id())
                && v.ekipa().equals("Kuhinja 2000")), "lestvica kaze novo ime");
        assertTrue(lestvica.stream().anyMatch(v -> v.idEkipa().equals(pisarna.id())
                && v.ekipa().equals("Pisarna")), "druga ekipa je nedotaknjena");
        assertEquals(List.of("Kuhinja 2000", "Pisarna"),
                ligaStoritev.ekipe(liga).stream().map(EkipaDto::prikazanoIme).toList(),
                "seznam ekip je spet po abecedi");
    }

    /* Odstranitev je vezana na pripravo, preimenovanje ne - napaka v imenu bi
       sicer ostala do konca sezone. */
    @Test
    void odstranitevJePoZrebuZaprtaPreimenovanjeNe() {
        Long liga = ustvariLigo();
        EkipaDto a = dodajKader(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Ekipa A")), "Aa");
        dodajKader(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Ekipa B")), "Bb");
        ligaStoritev.generirajRazpored(liga);

        assertThrows(DomenskaIzjema.class, () -> ligaStoritev.odstraniEkipo(a.id()));
        assertEquals("Ekipa AA", ligaStoritev.preimenujEkipo(a.id(), new EkipaImeVnos("Ekipa AA")).prikazanoIme());
    }

    /* Klubska ekipa dobi lastno ime, klub in zaporedna ostaneta; prazno ime jo
       vrne na "Klub N". */
    @Test
    void klubskaEkipaObdrziKlubInSeSmeVrnitiNaPrivzetoIme() {
        Long liga = ustvariLigo();
        Klub klub = klubRepozitorij.save(new Klub("Savinja", null));
        EkipaDto prva = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
        EkipaDto druga = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
        assertEquals("Savinja 2", druga.prikazanoIme());

        EkipaDto poimenovana = ligaStoritev.preimenujEkipo(druga.id(), new EkipaImeVnos("  Zeleni zmaji "));
        assertEquals("Zeleni zmaji", poimenovana.prikazanoIme(), "presledki ob robu se odrezejo");
        assertEquals("Zeleni zmaji", poimenovana.ime());
        assertEquals(klub.getId(), poimenovana.idKlub(), "klub ostane");
        assertEquals(2, poimenovana.zaporedna(), "zaporedna ostane");

        EkipaDto nazaj = ligaStoritev.preimenujEkipo(druga.id(), new EkipaImeVnos("   "));
        assertNull(nazaj.ime());
        assertEquals("Savinja 2", nazaj.prikazanoIme());
        assertEquals("Savinja 1", prva.prikazanoIme(), "prva ekipa kluba je nedotaknjena");
    }

    /* Brez kluba je ime edino poimenovanje, zato ga prosta ekipa ne more izgubiti. */
    @Test
    void prostaEkipaImenaNeSmeIzgubiti() {
        Long liga = ustvariLigo();
        EkipaDto prosta = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.preimenujEkipo(prosta.id(), new EkipaImeVnos("  ")));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.preimenujEkipo(prosta.id(), new EkipaImeVnos(null)));
        assertEquals("Kuhinja", ligaStoritev.ekipe(liga).get(0).prikazanoIme(), "ime je ostalo");
    }

    /* Meji stojita v shemi (CHECK 2 do 60); zavrniti ju je treba z jasnim
       sporocilom in ne s tem, da baza zavrne zapis. */
    @Test
    void imeMoraImetiOdDvehDoSestdesetZnakov() {
        Long liga = ustvariLigo();
        EkipaDto ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("K")));
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("x".repeat(61))));
        assertEquals("Ab", ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("Ab")).prikazanoIme());
        assertEquals("x".repeat(60),
                ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("x".repeat(60))).prikazanoIme());
    }

    /* Ekipi se v razporedu in na lestvici locita samo po prikazanem imenu -
       trk je prepovedan cez oba tipa ekipe in brez razlike med velikimi in
       malimi crkami, kot pri dodajanju. */
    @Test
    void imeIstoKotDrugaEkipaLigeSeZavrne() {
        Long liga = ustvariLigo();
        Klub klub = klubRepozitorij.save(new Klub("Savinja", null));
        EkipaDto klubska = ligaStoritev.dodajEkipo(liga, new EkipaVnos(klub.getId(), null, null));
        EkipaDto prosta = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));
        EkipaDto druga = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Pisarna"));

        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.preimenujEkipo(druga.id(), new EkipaImeVnos("kuhinja")),
                "druga prosta, brez razlike med velikimi in malimi crkami");
        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.preimenujEkipo(prosta.id(), new EkipaImeVnos("savinja 1")),
                "prosta si ne sme izposoditi privzetega imena klubske ekipe");
        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.preimenujEkipo(klubska.id(), new EkipaImeVnos("KUHINJA")),
                "klubska si ne sme izposoditi imena proste");
    }

    /* Ime, ki ga ekipa ze ima, ni trk sam s sabo: ponovna shranitev in
       popravek velikih crk morata iti skozi. */
    @Test
    void ekipaSeSmePreimenovatiVSvojeIme() {
        Long liga = ustvariLigo();
        EkipaDto ekipa = ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, "Kuhinja"));

        assertEquals("Kuhinja", ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("Kuhinja")).prikazanoIme());
        assertEquals("KUHINJA", ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("KUHINJA")).prikazanoIme());
    }

    /* Isto ime v drugi ligi ni trk - ekipa zivi samo v svoji ligi. */
    @Test
    void istoImeVDrugiLigiJeDovoljeno() {
        Long prva = ustvariLigo();
        Long druga = ustvariLigo();
        ligaStoritev.dodajEkipo(prva, new EkipaVnos(null, null, "Kuhinja"));
        EkipaDto ekipa = ligaStoritev.dodajEkipo(druga, new EkipaVnos(null, null, "Pisarna"));

        assertEquals("Kuhinja", ligaStoritev.preimenujEkipo(ekipa.id(), new EkipaImeVnos("Kuhinja")).prikazanoIme());
    }

    @Test
    void neobstojecaEkipaSeZavrne() {
        assertThrows(NiNajdenoIzjema.class,
                () -> ligaStoritev.preimenujEkipo(999_999L, new EkipaImeVnos("Kuhinja")));
    }

    // ---------- Pomozno ----------

    private Long ustvariLigo() {
        LigaVnos v = new LigaVnos("Rekreacijska liga", "2025/26", SpolKategorija.MESANO,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false, RavenTekmovanja.URADNO, false, null,
                null, null);
        return ligaStoritev.ustvari(v).id();
    }

    /* SAVINJA potrebuje dva igralca na stran. */
    private EkipaDto dodajKader(EkipaDto ekipa, String priimek) {
        for (int i = 1; i <= 2; i++) {
            Igralec ig = noviIgralec("Igralec" + i, priimek + i);
            ligaStoritev.dodajVKader(ekipa.id(), new KaderVnos(ig.getId(), i));
        }
        return new EkipaDto(ekipa.id(), ekipa.idKlub(), ekipa.klub(), ekipa.zaporedna(), ekipa.ime(),
                ekipa.prikazanoIme(), ekipa.stNosilca(), 2);
    }
}
