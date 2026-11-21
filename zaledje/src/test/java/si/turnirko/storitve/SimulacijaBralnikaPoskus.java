/* Meritev bralnika na vecjem vzorcu simuliranih fotografij (F2: vsaka sprememba
   bralnika se meri tu, preden se sprejme; sprememba, ki poslabsa katerokoli
   merilo, se ne sprejme).

   Ni v obicajni zbirki testov (ime ne konca s Test): tece nekaj minut.
   Zagon: mvnw test -Dtest=SimulacijaBralnikaPoskus -Dsurefire.failIfNoSpecifiedTests=false
   (-Dlistkov=N, privzeto 150; -Dbeta=0.5 -DvecBranj=true za posamezen korak F2;
   -Dzgodovina=true za tekme po pogostosti izidov iz zgodovine; -Dravni=obicajne,samo-prst
   za izbor ravni). Ravni tecejo vzporedno, zato je cas branja v
   izpisu visji kot na prostem strezniku (za cas: -Dvzporedno=false).
   Izpis: za vsako raven delez pravilnih celic, tihe napake, listki brez popravka in
   cas branja; tretja raven se razdeli se po posameznih tezavah. */
package si.turnirko.storitve;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import si.turnirko.storitve.SimuliraneFotografijeTest.Pogoji;

class SimulacijaBralnikaPoskus {

    private static final int LISTKOV = Integer.getInteger("listkov", 150);
    private static final boolean VZPOREDNO = Boolean.parseBoolean(System.getProperty("vzporedno", "true"));
    private static LokalniBralnikListka bralnik;

    @Test
    void izmeri() throws IOException {
        Pogoji z = SimuliraneFotografijeTest.ZAHTEVNO;
        Map<String, Pogoji> ravni = new LinkedHashMap<>();
        ravni.put("obicajne", SimuliraneFotografijeTest.OBICAJNO);
        ravni.put("zahtevne", z);
        ravni.put("neugodne", SimuliraneFotografijeTest.NEUGODNO);
        ravni.put("  samo pregib", new Pogoji(z.razmikOd(), z.razmikDo(), z.visinaOd(), z.visinaDo(), z.kot(),
                z.perspektiva(), z.zamegli(), true, 0, 0, 0));
        ravni.put("  samo prst", new Pogoji(z.razmikOd(), z.razmikDo(), z.visinaOd(), z.visinaDo(), z.kot(),
                z.perspektiva(), z.zamegli(), false, 1, 0, 0));
        ravni.put("  samo popravki", new Pogoji(z.razmikOd(), z.razmikDo(), z.visinaOd(), z.visinaDo(), z.kot(),
                z.perspektiva(), z.zamegli(), false, 0, 0.05, 0));
        ravni.put("  samo evropske", new Pogoji(z.razmikOd(), z.razmikDo(), z.visinaOd(), z.visinaDo(), z.kot(),
                z.perspektiva(), z.zamegli(), false, 0, 0, 1));
        SimuliraneFotografijeTest.izmeri(new Random(0), 1, SimuliraneFotografijeTest.OBICAJNO); // priprava
        SimuliraneFotografijeTest.tekmePoZgodovini = Boolean.getBoolean("zgodovina");
        if (System.getProperty("beta") != null || System.getProperty("vecBranj") != null) {
            bralnik = SimuliraneFotografijeTest.vgrajenaMreza(Double.parseDouble(System.getProperty("beta", "0.5")),
                    Boolean.parseBoolean(System.getProperty("vecBranj", "true")));
        }
        // seme ravni je njeno mesto v celotnem seznamu, da izbor ravni ne spremeni slik
        Map<String, Integer> semena = new java.util.HashMap<>();
        for (String ime : ravni.keySet()) {
            semena.put(ime, semena.size() + 1);
        }
        String izbor = System.getProperty("ravni");
        if (izbor != null) {
            ravni.keySet().removeIf(ime -> !java.util.Arrays.asList(izbor.split(",")).contains(ime.trim().replace(" ", "-")));
        }
        String[] imena = ravni.keySet().toArray(new String[0]);
        Stream<Integer> indeksi = Stream.iterate(0, i -> i < imena.length, i -> i + 1);
        String[] izpis = (VZPOREDNO ? indeksi.parallel() : indeksi)
                .map(i -> meri(imena[i], ravni.get(imena[i]), semena.get(imena[i])))
                .toArray(String[]::new);
        for (String vrstica : izpis) {
            System.out.println(vrstica);
        }
    }

    private static String meri(String ime, Pogoji pogoji, long seme) {
        try {
            SimuliraneFotografijeTest.Meritev m = bralnik == null
                    ? SimuliraneFotografijeTest.izmeri(new Random(seme), LISTKOV, pogoji)
                    : SimuliraneFotografijeTest.izmeri(new Random(seme), LISTKOV, pogoji, bralnik);
            return String.format("MERITEV %-16s neprebranih %3d  prav %.4f  tihe %.4f  brez popravka %3d/%d  %.0f ms",
                    ime, m.neprebranih(), m.prav(), m.tihe(), m.brezPopravka(), m.listkov(), m.msNaListek());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
