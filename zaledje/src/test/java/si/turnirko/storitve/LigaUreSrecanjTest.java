/* Liga z urami srecanj (V31): kolo je vecer z nekaj srecanji zapored, npr. ob
   18.30 in ob 19.45, in ekipa v njem sme igrati veckrat.

   Testi varujejo, da vsako srecanje dobi uro svojega mesta in da ta preziv
   zapis v bazo, da se ura popravi po srecanjih (in razpored ji sledi), da
   ekipa nikoli ne igra dveh srecanj hkrati - ne pri zrebu, ne pri rocnem
   vpisu in ne pri popravku termina - ter da streznik zavrne ure, ki ne tecejo
   naprej. Kako se pari razdelijo po vecerih, je cista logika in jo drzi
   RazporedStoritevTest. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaDto;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.ParRazporedaDto;
import si.turnirko.dto.RocniRazporedVnos;
import si.turnirko.dto.RocniRazporedVnos.ParVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.dto.TerminiVnos;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.RavenTekmovanja;
import si.turnirko.modeli.SpolKategorija;

class LigaUreSrecanjTest extends IntegracijskiTest {

    private static final LocalTime OB_1830 = LocalTime.of(18, 30);
    private static final LocalTime OB_1945 = LocalTime.of(19, 45);
    private static final LocalDateTime PRVI_VECER = LocalDateTime.of(2026, 10, 6, 18, 30);

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;
    @PersistenceContext private EntityManager seja;

    /* Primer iz zahteve: dve srecanji na vecer, ob 18.30 in ob 19.45. Ura mora
       prezivet zapis v bazo (glej uraTerminaPrezivizapisVBazo), zato se seja
       pred branjem izprazni. */
    @Test
    void srecanjaKolaSeZacnejoObUrahLige() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");
        ligaStoritev.generirajRazpored(liga);

        seja.flush();
        seja.clear();

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(6, srecanja.size(), "tri ekipe dvokrozno: 6 srecanj");
        assertEquals(3, srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0),
                "po dve srecanji na vecer: 3 kola");
        for (int kolo = 1; kolo <= 3; kolo++) {
            LocalDateTime dan = PRVI_VECER.plusDays(7L * (kolo - 1));
            assertEquals(List.of(dan.toLocalDate().atTime(OB_1830), dan.toLocalDate().atTime(OB_1945)),
                    zacetkiKola(srecanja, kolo), kolo + ". kolo");
        }
    }

    /* Smisel lige z urami: ekipa sme v istem kolu igrati dvakrat - pri treh
       ekipah in dveh srecanjih na vecer drugace sploh ne gre. */
    @Test
    void ekipaVKoluIgraVeckrat() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");
        ligaStoritev.generirajRazpored(liga);

        List<SrecanjeDto> prvoKolo = srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == 1).toList();
        List<Long> nastopi = new ArrayList<>();
        prvoKolo.forEach(s -> {
            nastopi.add(s.idEkipaDomaci());
            nastopi.add(s.idEkipaGost());
        });
        assertEquals(3, nastopi.stream().distinct().count(), "v kolu igrajo vse tri ekipe, ena dvakrat");
    }

    /* Ura v semenu je ura prvega srecanja kola, zato se poravna s prvo uro
       seznama - sicer bi seme in ure povedala dve razlicni stvari. */
    @Test
    void semeInUreSeUjemata() {
        Long liga = ustvariLigo(LocalDateTime.of(2026, 10, 6, 17, 0), List.of(OB_1830, OB_1945));

        LigaDto dto = ligaStoritev.najdi(liga);
        assertEquals(List.of("18:30", "19:45"), dto.ureSrecanj());
        assertEquals(PRVI_VECER, dto.zacetekPrvegaKola());
    }

    /* Liga brez ur ostane liga kroznega sistema. */
    @Test
    void ligaBrezUrNimaUr() {
        Long liga = ustvariLigo(PRVI_VECER, null);

        assertNull(ligaStoritev.najdi(liga).ureSrecanj());
    }

    /* Ure morajo teci naprej (razpored jih izpise po uri); enaki uri pa sta
       dve mizi hkrati in sta dovoljeni, prav tako nedolocena ura. */
    @Test
    void ureSrecanjMorajoTeciNaprej() {
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ustvariLigo(PRVI_VECER, List.of(OB_1945, OB_1830)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, List.of()));
        List<LocalTime> enajst = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            enajst.add(LocalTime.of(10 + i, 0));
        }
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, enajst));

        ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1830, OB_1945));
        ustvariLigo(PRVI_VECER, List.of(LocalTime.MIDNIGHT, OB_1945, LocalTime.MIDNIGHT));
    }

    /* Organizator uro zamenja posameznemu srecanju. Razpored ji sledi: kolo
       se izpise po uri in ne po vrstnem redu zapisa. */
    @Test
    void uraSeZamenjaPoSrecanjih() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");
        ligaStoritev.generirajRazpored(liga);
        List<SrecanjeDto> prvoKolo = srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == 1).toList();
        SrecanjeDto prvo = prvoKolo.get(0);
        SrecanjeDto drugo = prvoKolo.get(1);
        LocalDateTime dan = PRVI_VECER.toLocalDate().atStartOfDay();

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(prvo.id(), dan.with(OB_1945)),
                new TerminiVnos.TerminSrecanja(drugo.id(), dan.with(OB_1830)))));
        seja.flush();
        seja.clear();

        List<SrecanjeDto> poPopravku = srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == 1).toList();
        assertEquals(List.of(drugo.id(), prvo.id()), poPopravku.stream().map(SrecanjeDto::id).toList(),
                "kolo se izpise po uri");
        assertEquals(dan.with(OB_1830), poPopravku.get(0).predvidenZacetek());
    }

    /* Ekipa, ki v kolu igra dvakrat, ne sme dobiti obeh srecanj ob isti uri.
       Popravek, ki bi to naredil, se zavrne. */
    @Test
    void popravekNeSmePostavitiEkipeNaDveSrecanjiHkrati() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto drugo = srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == 1).toList().get(1);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(drugo.id(), PRVI_VECER)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Popravek sme nasteti samo srecanja rednega dela te lige. */
    @Test
    void popravekTujegaSrecanjaSeZavrne() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");
        ligaStoritev.generirajRazpored(liga);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(987654L, PRVI_VECER)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Rocni vpis: mesto pove uro. Ekipa sme v kolu igrati dvakrat ob razlicnih
       urah - pri kolu kroznega sistema bi bila to napaka. */
    @Test
    void rocniVpisDovoliEkipiDveSrecanjiObRazlicnihUrah() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C");

        List<SrecanjeDto> srecanja = ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(1), ekipe.get(2), 1),
                new ParVnos(1, ekipe.get(0), ekipe.get(1), 0))));

        assertEquals(List.of("Ek A-Ek B", "Ek B-Ek C"), srecanja.stream()
                .map(s -> s.domaci() + "-" + s.gost()).toList(), "kolo po uri, ne po vnosu");
        assertEquals(List.of(PRVI_VECER, PRVI_VECER.with(OB_1945)), zacetkiKola(srecanja, 1));
    }

    /* Dve mizi ob isti uri: ekipa ne more igrati na obeh. */
    @Test
    void rocniVpisZavrneEkipoNaDvehMizahHkrati() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1830));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1), 0),
                        new ParVnos(1, ekipe.get(0), ekipe.get(2), 1)))));
    }

    /* Kolo nima vec mest, kot je ur: srecanje brez ure ni vecer, ampak pomota. */
    @Test
    void rocniVpisZavrneSrecanjeBrezUre() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1), 0),
                        new ParVnos(1, ekipe.get(2), ekipe.get(3), 2)))), "tretje mesto");
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1), 1),
                        new ParVnos(1, ekipe.get(2), ekipe.get(3), 1)))), "isto mesto dvakrat");
    }

    /* Predlog nosi mesta: vmesnik po njih postavi par v vrstico prave ure. */
    @Test
    void predlogNosiMestaVKolu() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C");

        List<ParRazporedaDto> predlog = ligaStoritev.predlogRazporeda(liga);

        assertEquals(6, predlog.size());
        assertTrue(predlog.stream().allMatch(p -> p.mesto() == 0 || p.mesto() == 1));
        assertEquals(3, predlog.stream().filter(p -> p.mesto() == 1).count(), "vsak vecer ima drugo srecanje");
    }

    // ---------- pomozne metode ----------

    private List<LocalDateTime> zacetkiKola(List<SrecanjeDto> srecanja, int kolo) {
        return srecanja.stream().filter(s -> s.kolo() == kolo).map(SrecanjeDto::predvidenZacetek).toList();
    }

    private List<Long> dodajEkipe(Long idLiga, String... imena) {
        List<Long> idji = new ArrayList<>();
        for (String ime : imena) {
            idji.add(ligaStoritev.dodajEkipo(idLiga, new EkipaVnos(null, null, ime)).id());
        }
        return idji;
    }

    private Long ustvariLigo(LocalDateTime prvoKolo, List<LocalTime> ure) {
        LigaVnos v = new LigaVnos("Vecerna liga", "2026/27", SpolKategorija.MESANO,
                FormatSrecanja.SAVINJA, 5, null, true, 2, 1, 0, true, false,
                RavenTekmovanja.REKREATIVNO, false, null, prvoKolo, 7, null, null, ure);
        return ligaStoritev.ustvari(v).id();
    }
}
