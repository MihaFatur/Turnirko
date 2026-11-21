/* Umeritev mej popravljene celice (F2.5, RazrezCelice.Vsebina.popravljena):
   na simuliranih fotografijah polovica celic dobi precrtano napacno vrednost in
   pravo ob njej, polovica samo vrednost. Izpis: razporeditev znacilk (delez
   crnila, skupine potez, razpon zapisa) v obeh skupinah in koliko celic trenutne
   meje uvrstijo prav.

   Ni v obicajni zbirki testov. Zagon:
   mvnw test -Dtest=PopravljenaCelicaPoskus -Dsurefire.failIfNoSpecifiedTests=false */
package si.turnirko.storitve;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import si.turnirko.branje.CeliceListka;
import si.turnirko.branje.RazrezCelice;

class PopravljenaCelicaPoskus {

    @Test
    void umeri() throws IOException {
        SimuliraneFotografijeTest.izmeri(new Random(0), 1, SimuliraneFotografijeTest.OBICAJNO); // priprava
        for (SimuliraneFotografijeTest.Pogoji pogoji : List.of(SimuliraneFotografijeTest.OBICAJNO,
                SimuliraneFotografijeTest.ZAHTEVNO)) {
            Random r = new Random(5);
            List<double[]> navadne = new ArrayList<>();
            List<double[]> popravljene = new ArrayList<>();
            int[] prav = new int[2];
            int[] vseh = new int[2];
            for (int k = 0; k < Integer.getInteger("listkov", 60); k++) {
                BufferedImage izpolnjen = SimuliraneFotografijeTest.kopija(SimuliraneFotografijeTest.listek);
                boolean[][] oznaka = new boolean[5][2];
                for (int i = 0; i < 5; i++) {
                    for (int s = 0; s < 2; s++) {
                        CeliceListka.Vrstica v = SimuliraneFotografijeTest.celice.nizi().get(i);
                        CeliceListka.Celica c = s == 0 ? v.levo() : v.desno();
                        int vrednost = r.nextInt(14);
                        oznaka[i][s] = r.nextBoolean();
                        if (oznaka[i][s]) {
                            SimuliraneFotografijeTest.napisiPopravljeno(izpolnjen, c, vrednost, r, pogoji);
                        } else {
                            SimuliraneFotografijeTest.napisi(izpolnjen, c, vrednost, r, pogoji);
                        }
                    }
                }
                byte[] foto = SimuliraneFotografijeTest.fotografija(izpolnjen, r, pogoji);
                LokalniBralnikListka.Izrez izrez;
                try {
                    izrez = LokalniBralnikListka.izrezi(SimuliraneFotografijeTest.bralnik.poravnaj(foto));
                } catch (BralnikListka.NapakaBranjaListka e) {
                    continue;
                }
                for (int i = 0; i < Math.min(5, izrez.nizi().size()); i++) {
                    for (int s = 0; s < 2; s++) {
                        RazrezCelice.Vsebina vs = izrez.nizi().get(i)[s];
                        if (vs.prazna()) {
                            continue;
                        }
                        double[] z = {vs.crnilo(), vs.skupin(), vs.razpon()};
                        (oznaka[i][s] ? popravljene : navadne).add(z);
                        int skupina = oznaka[i][s] ? 1 : 0;
                        vseh[skupina]++;
                        if (vs.popravljena() == oznaka[i][s]) {
                            prav[skupina]++;
                        }
                    }
                }
            }
            System.out.printf("UMERITEV %s: navadne prav %d/%d, popravljene prav %d/%d%n",
                    pogoji == SimuliraneFotografijeTest.ZAHTEVNO ? "zahtevne" : "obicajne", prav[0], vseh[0], prav[1], vseh[1]);
            izpisi("navadne", navadne);
            izpisi("popravljene", popravljene);
        }
    }

    private static void izpisi(String ime, List<double[]> z) {
        String[] imena = {"crnilo", "skupin", "razpon"};
        for (int f = 0; f < 3; f++) {
            final int ff = f;
            double[] v = z.stream().mapToDouble(x -> x[ff]).sorted().toArray();
            if (v.length == 0) {
                continue;
            }
            System.out.printf("UMERITEV   %-12s %-7s min %.3f  p5 %.3f  p50 %.3f  p95 %.3f  max %.3f%n", ime, imena[f],
                    v[0], v[(int) (v.length * 0.05)], v[v.length / 2], v[(int) (v.length * 0.95)], v[v.length - 1]);
        }
    }
}
