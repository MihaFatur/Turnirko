/* Mreza stevk (MrezaStevk, UcenjeStevk) in poravnava (Preslikava).

   Domenska pravila, ki jih ta test varuje:
   - vgrajena mreza (naucena na MNIST) prebere preizkusne stevke MNIST, ki jih pri
     ucenju ni videla, z natancnostjo vsaj 98 %;
   - zapis mreze je brez izgub (nocno ucenje jo shrani v bazo in nalozi znova);
   - ucenje zmanjsa izgubo (vzvratno razsirjanje je pravilno);
   - homografija iz stirih tock jih preslika natanko;
   - stevka gre v mrezo v obliki MNIST: tezisce v sredini, okvir 20 px. */
package si.turnirko.branje;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class MrezaStevkTest {

    @Test
    void vgrajenaMrezaBereMnist() throws IOException {
        MrezaStevk mreza;
        try (InputStream v = MrezaStevkTest.class.getResourceAsStream("/branje/stevke.bin")) {
            mreza = MrezaStevk.preberi(v);
        }
        List<UcenjeStevk.Vzorec> preizkus;
        try (InputStream v = MrezaStevkTest.class.getResourceAsStream("/branje/mnist-preizkus.bin.gz")) {
            preizkus = UcenjeStevk.beriIzbor(v);
        }
        assertEquals(1000, preizkus.size());
        assertTrue(UcenjeStevk.natancnost(mreza, preizkus) >= 0.98);
    }

    @Test
    void zapisJeBrezIzgub() throws IOException {
        MrezaStevk m = MrezaStevk.nakljucna(new Random(3));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        m.zapisi(b);
        MrezaStevk n = MrezaStevk.preberi(new ByteArrayInputStream(b.toByteArray()));
        float[] x = new float[784];
        x[300] = 1;
        x[301] = 0.5f;
        assertArrayEquals(m.verjetnosti(x, 1), n.verjetnosti(x, 1));
    }

    @Test
    void ucenjeZmanjsaIzgubo() {
        // majhen umeten nabor: navpicna crta = 1, vodoravna = 7
        List<UcenjeStevk.Vzorec> vzorci = new ArrayList<>();
        Random r = new Random(5);
        for (int i = 0; i < 64; i++) {
            byte[] s = new byte[784];
            boolean navpicna = i % 2 == 0;
            int zamik = 10 + r.nextInt(8);
            for (int k = 6; k < 22; k++) {
                s[navpicna ? k * 28 + zamik : zamik * 28 + k] = (byte) 255;
            }
            vzorci.add(new UcenjeStevk.Vzorec(s, navpicna ? 1 : 7));
        }
        MrezaStevk m = MrezaStevk.nakljucna(new Random(1));
        double prej = 1 - UcenjeStevk.natancnost(m, vzorci);
        UcenjeStevk.uci(m, vzorci, 8, 0.003f, 2, 9L, null);
        double potem = 1 - UcenjeStevk.natancnost(m, vzorci);
        assertTrue(potem < prej || potem == 0, "napaka " + prej + " -> " + potem);
        assertTrue(UcenjeStevk.natancnost(m, vzorci) > 0.9);
    }

    @Test
    void homografijaPreslikaStiriTocke() {
        double[][] iz = {{0, 0}, {100, 0}, {100, 80}, {0, 80}};
        double[][] v = {{12, 7}, {205, 30}, {190, 190}, {3, 160}};
        Preslikava h = Preslikava.homografija(iz, v);
        for (int i = 0; i < 4; i++) {
            double[] p = h.uporabi(iz[i][0], iz[i][1]);
            assertEquals(v[i][0], p[0], 1e-6);
            assertEquals(v[i][1], p[1], 1e-6);
        }
    }

    @Test
    void stevkaJeVOblikiMnist() {
        // pravokotnik 10 x 40 px (enica) v celici sirine 100
        List<Integer> tocke = new ArrayList<>();
        for (int y = 5; y < 45; y++) {
            for (int x = 30; x < 40; x++) {
                tocke.add(y * 100 + x);
            }
        }
        float[] vhod = RazrezCelice.vMnist(tocke, 100).vhod();
        double vsota = 0;
        double cx = 0;
        double cy = 0;
        int vrstic = 0;
        for (int y = 0; y < 28; y++) {
            boolean imaCrnilo = false;
            for (int x = 0; x < 28; x++) {
                vsota += vhod[y * 28 + x];
                cx += vhod[y * 28 + x] * x;
                cy += vhod[y * 28 + x] * y;
                imaCrnilo |= vhod[y * 28 + x] > 0;
            }
            if (imaCrnilo) {
                vrstic++;
            }
        }
        assertEquals(20, vrstic, "visina 20 px");
        assertEquals(13.5, cx / vsota, 0.6);
        assertEquals(13.5, cy / vsota, 0.6);
    }
}
