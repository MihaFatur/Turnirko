/* Pogostost izidov nizov iz baze (F2.1): poizvedba tece na pravem PostgreSQL,
   neveljavni nizi se izpustijo, baza z malo nizi ohrani vgrajeno porazdelitev. */
package si.turnirko.storitve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import si.turnirko.branje.PogostostNizov;
import si.turnirko.repozitoriji.NizRepozitorij;

class PogostostNizovStoritevTest extends IntegracijskiTest {

    @Autowired NizRepozitorij nizi;
    @Autowired PogostostNizovStoritev pogostost;

    @Test
    void poizvedbaTeceNaBazi() {
        List<Object[]> vrstice = nizi.pogostostIzidov();
        assertNotNull(vrstice);
        for (Object[] v : vrstice) {
            assertEquals(3, v.length);
        }
    }

    @Test
    void premaloNizovOhraniVgrajeno() {
        // testna baza ima kvecjemu nekaj nizov iz drugih testov
        assertSame(PogostostNizov.VGRAJENA, pogostost.osvezi());
        assertSame(PogostostNizov.VGRAJENA, pogostost.trenutna());
    }

    @Test
    void neveljavniNiziSeIzpustijo() {
        PogostostNizov p = PogostostNizovStoritev.izVrstic(List.of(
                new Object[] {11, 7, 900L},
                new Object[] {13, 11, 100L},
                new Object[] {11, 10, 50L},     // ni niz
                new Object[] {15, 9, 50L},      // ni niz
                new Object[] {50, 48, 5L}));    // daljsi, kot ga bralnik pozna
        assertEquals(1000, p.nizov());
        assertTrue(p.verjetnost(11, 7) > 0.8);
    }
}
