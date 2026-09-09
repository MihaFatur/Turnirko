/* Enakomerna razvrstitev ekip: jakostni vrstni red ekip in zreb po parih.
   Testi varujejo pot od konca do konca - da ekipe dobijo mesta, da jih
   organizator prestavlja kot celoto, da razpored pare res uposteva in da liga
   BREZ te oznake ostane nespremenjena. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.dto.EkipaDto;
import si.turnirko.dto.EkipaVnos;
import si.turnirko.dto.LigaVnos;
import si.turnirko.dto.SrecanjeDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.izjeme.NeveljavenVnosIzjema;
import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.SpolKategorija;

class LigaEnakomernaRazvrstitevTest extends IntegracijskiTest {

    @Autowired private LigaStoritev ligaStoritev;
    @Autowired private SrecanjeStoritev srecanjeStoritev;

    /* Nova ekipa gre na dno lestvice: kam sodi, ve samo organizator. */
    @Test
    void noveEkipeDobijoMestaPoVrsti() {
        Long liga = ustvariLigo(true);
        for (String ime : List.of("Prva", "Druga", "Tretja")) {
            ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime));
        }
        List<EkipaDto> ekipe = ligaStoritev.ekipe(liga);

        assertEquals(List.of("Prva", "Druga", "Tretja"),
                ekipe.stream().map(EkipaDto::prikazanoIme).toList(),
                "seznam ekip lige z enakomerno razvrstitvijo tece po jakosti, ne po abecedi");
        assertEquals(List.of(1, 2, 3), ekipe.stream().map(EkipaDto::stNosilca).toList());
    }

    /* Vrzel na sredini bi organizatorju kazala stevilke, ki ne ustrezajo
       mestom v seznamu. */
    @Test
    void odhodEkipeStrneMesta() {
        Long liga = ustvariLigo(true);
        for (String ime : List.of("Prva", "Druga", "Tretja")) {
            ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime));
        }
        Long druga = ligaStoritev.ekipe(liga).get(1).id();
        ligaStoritev.odstraniEkipo(druga);

        List<EkipaDto> ekipe = ligaStoritev.ekipe(liga);
        assertEquals(List.of("Prva", "Tretja"), ekipe.stream().map(EkipaDto::prikazanoIme).toList());
        assertEquals(List.of(1, 2), ekipe.stream().map(EkipaDto::stNosilca).toList());
    }

    @Test
    void vrstniRedMoraNastetiVseEkipeNatankoEnkrat() {
        Long liga = ustvariLigo(true);
        List<Long> idji = new ArrayList<>();
        for (String ime : List.of("Ek A", "Ek B", "Ek C")) {
            idji.add(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime)).id());
        }

        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.shraniVrstniRedEkip(liga, List.of(idji.get(0), idji.get(1))),
                "delni seznam bi tiho pustil ekipo brez mesta");
        assertThrows(NeveljavenVnosIzjema.class,
                () -> ligaStoritev.shraniVrstniRedEkip(liga,
                        List.of(idji.get(0), idji.get(0), idji.get(1))),
                "ista ekipa dvakrat");

        List<EkipaDto> po = ligaStoritev.shraniVrstniRedEkip(liga,
                List.of(idji.get(2), idji.get(0), idji.get(1)));
        assertEquals(List.of("Ek C", "Ek A", "Ek B"), po.stream().map(EkipaDto::prikazanoIme).toList());
        assertEquals(List.of(1, 2, 3), po.stream().map(EkipaDto::stNosilca).toList());
    }

    /* Po zrebu je razpored zapisan; sprememba mest bi trdila nekaj, kar na
       tekmovanje nima vec vpliva. */
    @Test
    void vrstniRedPoZrebuNiVecMogoc() {
        Long liga = ustvariLigo(true);
        List<Long> idji = new ArrayList<>();
        for (String ime : List.of("Ek A", "Ek B")) {
            idji.add(ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime)).id());
        }
        ligaStoritev.generirajRazpored(liga);

        assertThrows(DomenskaIzjema.class,
                () -> ligaStoritev.shraniVrstniRedEkip(liga, List.of(idji.get(1), idji.get(0))));
    }

    /* Primer iz zahteve: 10 ekip A..J po moci. Pari so A-F, B-G, C-H, D-I in
       E-J; par v vsakem krogu (dveh kolih) igra proti istemu nasprotnemu paru,
       torej vsaka ekipa dobi enega nasprotnika iz zgornje in enega iz spodnje
       polovice. Krog, v katerem par ostane brez nasprotnega para, je njegov
       medsebojni dvoboj. */
    @Test
    void zrebDesetihEkipTecePoParih() {
        Long liga = ustvariLigo(true);
        List<String> imena = List.of("Ek A", "Ek B", "Ek C", "Ek D", "Ek E",
                "Ek F", "Ek G", "Ek H", "Ek I", "Ek J");
        for (String ime : imena) {
            ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime));
        }
        ligaStoritev.generirajRazpored(liga);

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(45, srecanja.size(), "vsak z vsakim: 45 srecanj");
        int kol = srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0);
        assertEquals(10, kol, "5 krogov po dve koli");

        for (int krog = 1; krog <= 5; krog++) {
            final int prvo = 2 * krog - 1;
            List<SrecanjeDto> vKrogu = srecanja.stream()
                    .filter(s -> s.kolo() == prvo || s.kolo() == prvo + 1)
                    .toList();
            for (int p = 0; p < 5; p++) {
                String zgornja = imena.get(p);
                String spodnja = imena.get(p + 5);
                List<String> nasprotnikiZgornje = nasprotniki(vKrogu, zgornja);
                List<String> nasprotnikiSpodnje = nasprotniki(vKrogu, spodnja);
                if (nasprotnikiZgornje.contains(spodnja)) {
                    assertEquals(List.of(spodnja), nasprotnikiZgornje,
                            "v svojem prostem krogu par igra samo medsebojni dvoboj");
                    continue;
                }
                assertEquals(nasprotnikiZgornje, nasprotnikiSpodnje,
                        "ekipi para " + zgornja + "-" + spodnja + " imata iste nasprotnike");
                assertEquals(2, nasprotnikiZgornje.size());
                assertTrue(imena.indexOf(nasprotnikiZgornje.get(0)) < 5
                                ^ imena.indexOf(nasprotnikiZgornje.get(1)) < 5,
                        "en nasprotnik iz zgornje, en iz spodnje polovice");
            }
        }
    }

    /* Pri osmih ekipah (sodo parov) se sezona konca s kolom, v katerem vsaka
       ekipa igra samo s svojim parom - in kol je toliko kot doslej. */
    @Test
    void zadnjeKoloOsmihEkipSoDvobojiParov() {
        Long liga = ustvariLigo(true);
        List<String> imena = List.of("Ek A", "Ek B", "Ek C", "Ek D", "Ek E", "Ek F", "Ek G", "Ek H");
        for (String ime : imena) {
            ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime));
        }
        ligaStoritev.generirajRazpored(liga);

        List<SrecanjeDto> srecanja = srecanjeStoritev.zaLigo(liga);
        assertEquals(7, srecanja.stream().mapToInt(SrecanjeDto::kolo).max().orElse(0));
        List<String> zadnje = srecanja.stream()
                .filter(s -> s.kolo() == 7)
                .map(s -> s.domaci() + "-" + s.gost())
                .sorted()
                .toList();
        assertEquals(List.of("Ek A-Ek E", "Ek B-Ek F", "Ek C-Ek G", "Ek D-Ek H"), zadnje);
    }

    /* Liga brez oznake ostane pri navadnem kroznem sistemu - 8 ekip, 7 kol in
       zadnje kolo NI seznam dvobojev parov. */
    @Test
    void ligaBrezOznakeZrebaPoStarem() {
        Long liga = ustvariLigo(false);
        for (String ime : List.of("Ek A", "Ek B", "Ek C", "Ek D", "Ek E", "Ek F", "Ek G", "Ek H")) {
            ligaStoritev.dodajEkipo(liga, new EkipaVnos(null, null, ime));
        }
        ligaStoritev.generirajRazpored(liga);

        List<String> zadnje = srecanjeStoritev.zaLigo(liga).stream()
                .filter(s -> s.kolo() == 7)
                .map(s -> s.domaci() + "-" + s.gost())
                .sorted()
                .toList();
        assertEquals(4, zadnje.size());
        assertNotEquals(List.of("Ek A-Ek E", "Ek B-Ek F", "Ek C-Ek G", "Ek D-Ek H"), zadnje,
                "navadni krozni sistem parov ne pozna");
    }

    // ---------- pomozne metode ----------

    /* Nasprotniki ekipe med danimi srecanji, urejeni po imenu. */
    private List<String> nasprotniki(List<SrecanjeDto> srecanja, String ekipa) {
        return srecanja.stream()
                .filter(s -> s.domaci().equals(ekipa) || s.gost().equals(ekipa))
                .map(s -> s.domaci().equals(ekipa) ? s.gost() : s.domaci())
                .sorted()
                .toList();
    }

    private Long ustvariLigo(boolean enakomerna) {
        LigaVnos v = new LigaVnos("Test liga", "2025/26", SpolKategorija.MOSKI,
                FormatSrecanja.SAVINJA, 5, null, false, 2, 1, 0, true, false, true,
                enakomerna, null, null, null);
        return ligaStoritev.ustvari(v).id();
    }
}
