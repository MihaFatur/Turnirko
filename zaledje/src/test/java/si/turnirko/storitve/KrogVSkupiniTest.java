/* Razvrstitev igralcev, ki so v KROGU (enako zmag in porazov): odloca samo
   izkupicek iz tekem MED njimi - najprej razlika nizov, nato razlika tock. */
package si.turnirko.storitve;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VnosRezultata;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.Tekma;

class KrogVSkupiniTest extends IntegracijskiTest {

    /* Primer iz pravil: A dobi vse tri tekme, B, C in D pa so v krogu
       (vsak ena zmaga, dva poraza). Med njimi: B-C 3:0, C-D 3:1, D-B 3:0,
       torej D +1, B 0, C -1. Skupna razlika nizov tega NE sme povoziti. */
    @Test
    void krogTrehRazsodiRazlikaNizovMedNjimi() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 2);
        odigraj(dogodek, a, c, 3, 1);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, c, d, 3, 1);
        odigraj(dogodek, d, b, 3, 0);

        assertEquals(idji(a, d, b, c), lestvica(dogodek), "krog razsodi razlika nizov med clani");

        // isti vrstni red mora pristati tudi v mestih v skupini
        assertEquals(List.of(1, 2, 3, 4), List.of(
                        mesto(a), mesto(d), mesto(b), mesto(c)),
                "mesta v skupini se ujemajo z lestvico");

        // izpis mora znati povedati, katera mesta je dolocil krog
        List<VrsticaLestviceDto> vrstice = razvrstitevStoritev.lestvica(
                clani(dogodek), tekmeDogodka(dogodek.getId()));
        assertNull(vrstica(vrstice, a).krog(), "zmagovalec skupine ni v krogu");
        assertEquals(List.of(1, 1, 1), List.of(
                        vrstica(vrstice, b).krog(),
                        vrstica(vrstice, c).krog(),
                        vrstica(vrstice, d).krog()),
                "B, C in D so isti krog");
    }

    /* Krog treh, ki NI cikel: B je premagal C in D, C pa D. Odloci stevilo
       medsebojnih zmag (PST 20. clen), ceprav sta B in C po razliki nizov v
       krogu izenacena (oba +2) in ima C boljso SKUPNO razliko nizov. */
    @Test
    void vKroguNajprejOdlocijoMedsebojneZmage() {
        Dogodek dogodek = pripraviSkupinskiDogodek(6, 1, 6);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);
        Prijava e = clani.get(4);
        Prijava f = clani.get(5);

        // A dobi vse
        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, a, e, 3, 0);
        odigraj(dogodek, a, f, 3, 0);
        // krog B, C, D: B > C, B > D, C > D
        odigraj(dogodek, b, c, 3, 2);
        odigraj(dogodek, b, d, 3, 2);
        odigraj(dogodek, c, d, 3, 0);
        // ostalo tako, da imajo B, C in D vsi 2 zmagi in 3 poraze
        odigraj(dogodek, e, b, 3, 0);
        odigraj(dogodek, f, b, 3, 0);
        odigraj(dogodek, c, e, 3, 0);
        odigraj(dogodek, f, c, 3, 0);
        odigraj(dogodek, d, e, 3, 0);
        odigraj(dogodek, d, f, 3, 0);
        odigraj(dogodek, f, e, 3, 0);

        List<VrsticaLestviceDto> vrstice = razvrstitevStoritev.lestvica(
                clani(dogodek), tekmeDogodka(dogodek.getId()));
        assertEquals(List.of(2, 2, 2), List.of(
                        vrstica(vrstice, b).zmage(),
                        vrstica(vrstice, c).zmage(),
                        vrstica(vrstice, d).zmage()),
                "B, C in D so v krogu (po 2 zmagi)");
        assertEquals(-7, razlikaNizov(vrstice, b), "B ima najslabso SKUPNO razliko nizov v krogu");
        assertEquals(-1, razlikaNizov(vrstice, c));

        assertEquals(idji(a, f, b, c, d, e),
                vrstice.stream().map(VrsticaLestviceDto::idPrijave).toList(),
                "B je pred C, ker je v krogu zbral vec zmag");
    }

    /* Ko so razlike nizov v krogu enake (vsi 3:0 v krogu), odloci razlika
       tock: B +33-18 = +15, D -6+18 = +12, C -33+6 = -27. */
    @Test
    void izenacenaRazlikaNizovGreNaRazlikoTock() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 0);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, a, d, 3, 0);
        odigraj(dogodek, b, c, 3, 0, nizi(11, 0, 11, 0, 11, 0));
        odigraj(dogodek, c, d, 3, 0, nizi(11, 9, 11, 9, 11, 9));
        odigraj(dogodek, d, b, 3, 0, nizi(11, 5, 11, 5, 11, 5));

        assertEquals(idji(a, b, d, c), lestvica(dogodek),
                "vsi v krogu imajo razliko nizov 0, zato odloci razlika tock");
    }

    /* Izenacena dvojica: odloci njuna medsebojna tekma, tudi ce ima
       porazenec boljso SKUPNO razliko nizov (A +1, B +5, a A je premagal B). */
    @Test
    void dvojicoRazsodiMedsebojnaTekmaInNeSkupnaRazlika() {
        Dogodek dogodek = pripraviSkupinskiDogodek(4, 1, 4);
        zrebStoritev.izvediZreb(dogodek.getId());
        List<Prijava> clani = clani(dogodek);
        Prijava a = clani.get(0);
        Prijava b = clani.get(1);
        Prijava c = clani.get(2);
        Prijava d = clani.get(3);

        odigraj(dogodek, a, b, 3, 2);
        odigraj(dogodek, a, c, 3, 0);
        odigraj(dogodek, d, a, 3, 0);
        odigraj(dogodek, b, c, 3, 0);
        odigraj(dogodek, b, d, 3, 0);
        odigraj(dogodek, c, d, 3, 0);

        List<VrsticaLestviceDto> lestvica = razvrstitevStoritev.lestvica(
                clani(dogodek), tekmeDogodka(dogodek.getId()));
        assertEquals(1, razlikaNizov(lestvica, a), "A ima skupno razliko nizov +1");
        assertEquals(5, razlikaNizov(lestvica, b), "B ima boljso skupno razliko nizov +5");
        assertEquals(idji(a, b, c, d), lestvica.stream().map(VrsticaLestviceDto::idPrijave).toList(),
                "A je pred B, ker ga je premagal");
    }

    // ------------------------------------------------------------------

    /* Clani edine skupine po jakostnem mestu z zreba (A, B, C, D). */
    private List<Prijava> clani(Dogodek dogodek) {
        return prijavaRepozitorij.najdiZaDogodek(dogodek.getId()).stream()
                .filter(p -> p.getIdSkupina() != null)
                .sorted(Comparator.comparing(Prijava::getStNosilca))
                .toList();
    }

    private void odigraj(Dogodek dogodek, Prijava zmagovalec, Prijava porazenec,
                         int niziZmagovalca, int niziPorazenca) {
        odigraj(dogodek, zmagovalec, porazenec, niziZmagovalca, niziPorazenca, null);
    }

    /* Vnese rezultat ne glede na to, na kateri strani tekme kdo stoji.
       Nizi so podani z zmagovalcevega zornega kota. */
    private void odigraj(Dogodek dogodek, Prijava zmagovalec, Prijava porazenec,
                         int niziZmagovalca, int niziPorazenca,
                         List<NizVnos> nizi) {
        Tekma tekma = tekmeDogodka(dogodek.getId()).stream()
                .filter(t -> jeUdelezenec(t, zmagovalec.getId())
                        && jeUdelezenec(t, porazenec.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tekme med izbranima igralcema ni"));

        boolean zmagovalecJePrvi = tekma.getPrijava1().getId().equals(zmagovalec.getId());
        List<NizVnos> zaVnos = nizi == null || zmagovalecJePrvi ? nizi
                : nizi.stream()
                        .map(n -> new NizVnos(n.tocke2(), n.tocke1()))
                        .toList();
        tekmaStoritev.vnesiRezultat(tekma.getId(), new VnosRezultata(null,
                zmagovalecJePrvi ? niziZmagovalca : niziPorazenca,
                zmagovalecJePrvi ? niziPorazenca : niziZmagovalca,
                null, zaVnos));
    }

    /* Tocke nizov po parih, z zmagovalcevega zornega kota: 11, 0, 11, 0 ... */
    private static List<NizVnos> nizi(int... tocke) {
        return java.util.stream.IntStream.range(0, tocke.length / 2)
                .mapToObj(i -> new NizVnos(tocke[2 * i], tocke[2 * i + 1]))
                .toList();
    }

    private List<Long> lestvica(Dogodek dogodek) {
        return razvrstitevStoritev.lestvica(clani(dogodek), tekmeDogodka(dogodek.getId()))
                .stream().map(VrsticaLestviceDto::idPrijave).toList();
    }

    private static List<Long> idji(Prijava... prijave) {
        return java.util.Arrays.stream(prijave).map(Prijava::getId).toList();
    }

    private Integer mesto(Prijava prijava) {
        return prijavaRepozitorij.findById(prijava.getId()).orElseThrow().getMestoVSkupini();
    }

    private static VrsticaLestviceDto vrstica(List<VrsticaLestviceDto> lestvica, Prijava prijava) {
        return lestvica.stream()
                .filter(v -> v.idPrijave().equals(prijava.getId()))
                .findFirst().orElseThrow();
    }

    private static int razlikaNizov(List<VrsticaLestviceDto> lestvica, Prijava prijava) {
        VrsticaLestviceDto vrstica = vrstica(lestvica, prijava);
        return vrstica.niziZa() - vrstica.niziProti();
    }

    private static boolean jeUdelezenec(Tekma tekma, Long idPrijave) {
        return (tekma.getPrijava1() != null && tekma.getPrijava1().getId().equals(idPrijave))
                || (tekma.getPrijava2() != null && tekma.getPrijava2().getId().equals(idPrijave));
    }
}
