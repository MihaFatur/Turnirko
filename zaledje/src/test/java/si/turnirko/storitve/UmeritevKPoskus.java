/* Meritev K nad KOPIJO prave baze - ni redni test.

   Parametri Turnirko ratinga so umerjeni po metodi "napovej, nato posodobi"
   (prequential log-loss): vsaka tekma je napovedana s stanjem PRED njo in
   napoved se oceni z izidom. Ko se formula spremeni (oktobra 2026 je odsla
   set-margina), je treba K izmeriti znova, ne ugibati. Poskus preracuna vso
   zgodovino s K, pomnozenim s faktorjem, in izpise log-loss na istem izboru
   tekem kot prvotno umerjanje (od 2024, oba igralca z vsaj 20 tekmami).

   Zagon (vsak faktor nad svojo kopijo baze, ker preracun bazo prepise):

     mvnw test -Dtest=UmeritevKPoskus -Dturnirko.umeritev.baza=<kopija.db>
         -Dturnirko.umeritev.k=0.9

   Brez lastnosti turnirko.umeritev.baza se ne izvede. */
package si.turnirko.storitve;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "turnirko.umeritev.baza", matches = ".+")
class UmeritevKPoskus {

    private static final double FAKTOR = Double.parseDouble(System.getProperty("turnirko.umeritev.k", "1.0"));

    @DynamicPropertySource
    static void baza(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + System.getProperty("turnirko.umeritev.baza"));
    }

    @TestConfiguration
    static class PomnozenK {
        @Bean
        @Primary
        TurnirkoRatingStoritev pomnozenRating() {
            return new TurnirkoRatingStoritev() {
                @Override
                public int kFaktor(int stTekem, boolean poVrnitvi) {
                    return (int) Math.round(super.kFaktor(stTekem, poVrnitvi) * FAKTOR);
                }
            };
        }
    }

    @Autowired PreracunRatingaStoritev preracun;
    @Autowired JdbcTemplate jdbc;

    @Test
    void izmeri() {
        long zacetek = System.currentTimeMillis();
        preracun.preracunajOd(null);
        long trajanje = (System.currentTimeMillis() - zacetek) / 1000;

        // vrstice s tekmo po casovni vrsti preracuna; st. tekem igralca pred tekmo
        List<Map<String, Object>> vrstice = jdbc.queryForList("""
                SELECT id_igralec, coalesce(id_tekma, -id_tekma_srecanja) AS tekma, velja_ob, pricakovano, tocke
                FROM rating_zgodovina
                WHERE sistem = 'TURNIRKO' AND (id_tekma IS NOT NULL OR id_tekma_srecanja IS NOT NULL)
                ORDER BY id""");
        Map<Long, Integer> stTekem = new HashMap<>();
        Map<Long, double[]> poTekmi = new HashMap<>(); // tekma -> [p prvega, izid prvega, min st. tekem, od 2024]
        for (Map<String, Object> v : vrstice) {
            long igralec = ((Number) v.get("id_igralec")).longValue();
            long tekma = ((Number) v.get("tekma")).longValue();
            int prej = stTekem.merge(igralec, 1, Integer::sum) - 1;
            Object p = v.get("pricakovano");
            double[] t = poTekmi.get(tekma);
            if (t == null) {
                boolean od2024 = String.valueOf(v.get("velja_ob")).compareTo("2024-01-01") >= 0;
                poTekmi.put(tekma, new double[] {
                        p == null ? Double.NaN : ((Number) p).doubleValue(),
                        ((Number) v.get("tocke")).doubleValue(), prej, od2024 ? 1 : 0});
            } else {
                t[2] = Math.min(t[2], prej);
                if (Double.isNaN(t[0]) && p != null) {
                    // prvi je novinec na uvrstitvi (brez napovedi): velja napoved drugega
                    t[0] = ((Number) p).doubleValue();
                    t[1] = ((Number) v.get("tocke")).doubleValue();
                }
            }
        }
        double vsota = 0, vsotaVse = 0;
        int n = 0, nVse = 0;
        for (double[] t : poTekmi.values()) {
            if (Double.isNaN(t[0])) {
                continue;
            }
            double p = Math.min(1 - 1e-9, Math.max(1e-9, t[0]));
            double ll = -(t[1] * Math.log(p) + (1 - t[1]) * Math.log(1 - p));
            vsotaVse += ll;
            nVse++;
            if (t[3] == 1 && t[2] >= 20) {
                vsota += ll;
                n++;
            }
        }
        System.out.printf("UMERITEV k=%.2f log-loss(od 2024, oba >=20)=%.5f n=%d | vse=%.5f n=%d | %d s%n",
                FAKTOR, vsota / n, n, vsotaVse / nVse, nVse, trajanje);
    }
}
