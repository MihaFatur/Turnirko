/* Kategorija na lestvici je izpeljana, ne shranjena - zato jo mora varovati
   test: meja tece po koledarskem letu (kot starostne skupine v namiznem
   tenisu), ne po rojstnem dnevu. */
package si.turnirko.modeli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class KategorijaIgralcaTest {

    private static final LocalDate DANES = LocalDate.of(2026, 3, 1);

    @Test
    void mladinecInVeteranStaPredSpolom() {
        // 18 let v letu 2026 -> se mladinec, ceprav je zenska
        assertEquals(KategorijaIgralca.U19,
                KategorijaIgralca.izpelji(Spol.ZENSKI, LocalDate.of(2008, 12, 31), DANES));
        // 40 let v letu 2026 -> veteran, ceprav je moski
        assertEquals(KategorijaIgralca.VETERANI,
                KategorijaIgralca.izpelji(Spol.MOSKI, LocalDate.of(1986, 12, 31), DANES));
    }

    @Test
    void vmesnaStarostSeLociPoSpolu() {
        assertEquals(KategorijaIgralca.CLANI,
                KategorijaIgralca.izpelji(Spol.MOSKI, LocalDate.of(2000, 6, 15), DANES));
        assertEquals(KategorijaIgralca.CLANICE,
                KategorijaIgralca.izpelji(Spol.ZENSKI, LocalDate.of(2000, 6, 15), DANES));
    }

    /* Meja je koledarsko leto: kdor v letu dopolni 19, ni vec U19 ze januarja,
       in kdor jih dopolni 18, je U19 se decembra. */
    @Test
    void mejaTecePoKoledarskemLetuInNePoRojstnemDnevu() {
        assertEquals(KategorijaIgralca.CLANI,
                KategorijaIgralca.izpelji(Spol.MOSKI, LocalDate.of(2007, 12, 31), DANES));
        assertEquals(KategorijaIgralca.U19,
                KategorijaIgralca.izpelji(Spol.MOSKI, LocalDate.of(2008, 1, 1), DANES));
    }

    @Test
    void brezPodatkaKategorijeNi() {
        assertNull(KategorijaIgralca.izpelji(null, LocalDate.of(2000, 1, 1), DANES));
        assertNull(KategorijaIgralca.izpelji(Spol.MOSKI, null, DANES));
    }
}
