/* Liga z urami srecanj (V31): kolo je vecer, v katerem se odigra vec krogov
   zapored - npr. ob 18.30 prvi in ob 19.45 drugi. Vsaka ekipa ta vecer igra
   dvakrat in kol je pol manj.

   Testi varujejo, da srecanje dobi uro svojega kroga (zacetek in uro v kolu)
   in da ta preziv zapis v bazo, da se ura popravi po srecanjih (in razpored ji
   sledi), da ekipa nikoli ne igra dveh srecanj hkrati - ne pri rocnem vpisu in
   ne pri popravku termina - ter da streznik zavrne ure, ki ne tecejo naprej.
   Kako se krogi zdruzijo v kola, je cista logika in jo drzi
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

    /* Primer iz zahteve: dve srecanji na vecer, ob 18.30 in ob 19.45. Stiri
       ekipe dvokrozno imajo 6 krogov, zato 3 kola; v vsakem ob 18.30 igrata
       dve srecanji (vse stiri ekipe) in ob 19.45 drugi dve. Ura mora prezivet
       zapis v bazo (glej uraTerminaPrezivizapisVBazo), zato se seja izprazni. */
    @Test
    void vsakaEkipaVKoluIgraObObehUrah() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);

        seja.flush();
        seja.clear();

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(12, srecanja.size(), "srecanj je toliko kot pri kroznem sistemu");
        assertEquals(3, srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0), "kol je pol manj");
        for (int kolo = 1; kolo <= 3; kolo++) {
            final int k = kolo;
            LocalDateTime dan = PRVI_VECER.plusDays(7L * (kolo - 1));
            assertEquals(List.of(dan.with(OB_1830), dan.with(OB_1830), dan.with(OB_1945), dan.with(OB_1945)),
                    zacetkiKola(srecanja, kolo), kolo + ". kolo po uri");
            for (Long ekipa : ekipe) {
                assertEquals(List.of(0, 1), srecanja.stream()
                        .filter(s -> s.kolo() == k && (s.idEkipaDomaci().equals(ekipa) || s.idEkipaGost().equals(ekipa)))
                        .map(SrecanjeDto::uraVKolu).sorted().toList(),
                        "ekipa igra v " + kolo + ". kolu ob vsaki uri enkrat");
            }
        }
    }

    /* Liga brez ur ostane liga kroznega sistema: kolo je krog in srecanje nima
       ure v kolu. */
    @Test
    void ligaBrezUrOstaneKrozniSistem() {
        Long liga = ustvariLigo(PRVI_VECER, null);
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);

        assertNull(ligaStoritev.najdi(liga).ureSrecanj());
        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(6, srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0));
        assertTrue(srecanja.stream().allMatch(s -> s.uraVKolu() == null));
    }

    /* Ura v semenu je ura prvega kroga kola, zato se poravna s prvo uro
       seznama - sicer bi seme in ure povedala dve razlicni stvari. */
    @Test
    void semeInUreSeUjemata() {
        Long liga = ustvariLigo(LocalDateTime.of(2026, 10, 6, 17, 0), List.of(OB_1830, OB_1945));

        LigaDto dto = ligaStoritev.najdi(liga);
        assertEquals(List.of("18:30", "19:45"), dto.ureSrecanj());
        assertEquals(PRVI_VECER, dto.zacetekPrvegaKola());
    }

    /* Ure morajo teci strogo naprej: ob vsaki se odigra cel krog, zato bi
       enaki uri pomenili dve srecanji vsake ekipe hkrati. Ena sama ura je
       navadna liga. Nedolocena ura (00:00) se ne primerja. */
    @Test
    void ureMorajoTeciNaprej() {
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, List.of(OB_1945, OB_1830)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1830)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, List.of(OB_1830)));
        List<LocalTime> enajst = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            enajst.add(LocalTime.of(10 + i, 0));
        }
        assertThrows(NeveljavenVnosIzjema.class, () -> ustvariLigo(PRVI_VECER, enajst));

        ustvariLigo(PRVI_VECER, List.of(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, OB_1945));
    }

    /* Organizator uro prestavi posameznemu srecanju. Razpored ji sledi: kolo
       se izpise po uri in ne po vrstnem redu zapisa. */
    @Test
    void uraSePrestaviPoSrecanjih() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto prvo = srecanjeStoritev.zaLigo(liga).get(0);
        LocalDateTime pozneje = PRVI_VECER.with(LocalTime.of(21, 0));

        ligaStoritev.nastaviTermine(liga, new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(prvo.id(), pozneje))));
        seja.flush();
        seja.clear();

        List<SrecanjeDto> prvoKolo = srecanjeStoritev.zaLigo(liga).stream().filter(s -> s.kolo() == 1).toList();
        assertEquals(prvo.id(), prvoKolo.get(prvoKolo.size() - 1).id(), "prestavljeno srecanje je zadnje v kolu");
        assertEquals(pozneje, prvoKolo.get(prvoKolo.size() - 1).predvidenZacetek());
        assertEquals(0, prvoKolo.get(prvoKolo.size() - 1).uraVKolu(), "krog srecanja ostane isti");
    }

    /* Srecanje ob 19.45 prestavljeno na 18.30 bi ekipi dalo dve srecanji hkrati
       - ob 18.30 obe ze igrata. Popravek se zavrne. */
    @Test
    void popravekNeSmePostavitiEkipeNaDveSrecanjiHkrati() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");
        ligaStoritev.generirajRazpored(liga);
        SrecanjeDto obDrugiUri = srecanjeStoritev.zaLigo(liga).stream()
                .filter(s -> s.kolo() == 1 && s.uraVKolu() == 1).findFirst().orElseThrow();

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(obDrugiUri.id(), PRVI_VECER)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Popravek sme nasteti samo srecanja rednega dela te lige. */
    @Test
    void popravekTujegaSrecanjaSeZavrne() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B");
        ligaStoritev.generirajRazpored(liga);

        TerminiVnos vnos = new TerminiVnos(List.of(), List.of(
                new TerminiVnos.TerminSrecanja(987654L, PRVI_VECER)));
        assertThrows(NeveljavenVnosIzjema.class, () -> ligaStoritev.nastaviTermine(liga, vnos));
    }

    /* Rocni vpis: ura v kolu pove, ob kateri uri se srecanje igra. Ekipa sme v
       kolu igrati dvakrat ob razlicnih urah - pri kolu kroznega sistema bi bila
       to napaka. */
    @Test
    void rocniVpisDovoliEkipiDveSrecanjiObRazlicnihUrah() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<SrecanjeDto> srecanja = ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                new ParVnos(1, ekipe.get(0), ekipe.get(2), 1),
                new ParVnos(1, ekipe.get(1), ekipe.get(3), 1),
                new ParVnos(1, ekipe.get(0), ekipe.get(1), 0),
                new ParVnos(1, ekipe.get(2), ekipe.get(3), 0))));

        assertEquals(List.of(PRVI_VECER, PRVI_VECER, PRVI_VECER.with(OB_1945), PRVI_VECER.with(OB_1945)),
                zacetkiKola(srecanja, 1), "kolo po uri, ne po vnosu");
        assertEquals(List.of(0, 0, 1, 1), srecanja.stream().map(SrecanjeDto::uraVKolu).toList());
    }

    /* Ob isti uri kola ekipa ne more igrati dvakrat. */
    @Test
    void rocniVpisZavrneEkipoDvakratObIstiUri() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B", "Ek C");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1), 1),
                        new ParVnos(1, ekipe.get(0), ekipe.get(2), 1)))));
    }

    /* Ura, ki je liga nima, ni vecer, ampak pomota. */
    @Test
    void rocniVpisZavrneUroZunajSeznama() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        List<Long> ekipe = dodajEkipe(liga, "Ek A", "Ek B");

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.rocniRazpored(liga, new RocniRazporedVnos(List.of(
                        new ParVnos(1, ekipe.get(0), ekipe.get(1), 2)))));
    }

    /* Predlog nosi uro v kolu: vmesnik po njej postavi par k pravi uri. */
    @Test
    void predlogNosiUroVKolu() {
        Long liga = ustvariLigo(PRVI_VECER, List.of(OB_1830, OB_1945));
        dodajEkipe(liga, "Ek A", "Ek B", "Ek C", "Ek D");

        List<ParRazporedaDto> predlog = ligaStoritev.predlogRazporeda(liga);

        assertEquals(12, predlog.size());
        assertEquals(3, predlog.stream().mapToInt(ParRazporedaDto::kolo).max().orElse(0));
        assertEquals(6, predlog.stream().filter(p -> p.uraVKolu() == 1).count(), "polovica srecanj ob 19.45");
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
