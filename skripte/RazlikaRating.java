// Primerjava ratinga v SQLite in PostgreSQL po popolnem preracunu na obeh
// (docs/PRENOVA-BAZE.md, razdelek 6.2/4). Isto kodo za obracun poganjata obe
// aplikaciji; ce je rating po preracunu v obeh bazah enak do zadnjega bita,
// pomeni, da se izracun ne zanasa na nic, kar bi se razlikovalo med bazama
// (vrstni red izenacenih vrstic, tipi, zaokrozevanje).
//
// Zakaj ne SHA-256 po tabelah (ukaz prenos, nacin »preveri«): po preracunu
// so vrstice zapisane znova, zato se razlikujeta surogatni kljuc `id`
// (SQLite ga dodeljuje po najvecjem obstojecem, PostgreSQL po zaporedju) in
// `rating_zgodovina.ustvarjen_ob` (SQLite hrani samo datum, PostgreSQL cas
// zapisa). Tu se vrstice zato poisejo po NARAVNEM kljucu (igralec, tekma,
// razlog, cas veljavnosti) in primerjajo vse ostale vrednosti.
//
// Zagon (Java 21+, gonilnika SQLite in PostgreSQL na poti razredov):
//   java -cp postgresql.jar;sqlite-jdbc.jar skripte/RazlikaRating.java \
//        <pot/do/sqlite.db> jdbc:postgresql://localhost:5433/turnirko
// Uporabnik postgres brez gesla (lokalna razvojna baza).
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RazlikaRating {

    public static void main(String[] a) throws Exception {
        try (Connection s = DriverManager.getConnection("jdbc:sqlite:" + a[0]);
             Connection p = DriverManager.getConnection(a[1], "postgres", "")) {
            stanje(s, p);
            zgodovina(s, p);
        }
    }

    /* Vrednost v obliki, ki je enaka v obeh bazah (cas z »T« in brez, logicno 0/1). */
    static String v(Object o) {
        if (o == null) {
            return "NULL";
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime().toString().replace('T', ' ');
        }
        if (o instanceof String x && x.length() >= 10 && x.charAt(4) == '-' && x.charAt(7) == '-') {
            return x.replace('T', ' ');
        }
        return String.valueOf(o);
    }

    static Map<String, List<String>> beri(Connection c, String sql, int kljuc, int n) throws Exception {
        Map<String, List<String>> m = new TreeMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                StringBuilder k = new StringBuilder();
                for (int i = 1; i <= kljuc; i++) {
                    k.append(v(rs.getObject(i))).append('|');
                }
                List<String> vr = new ArrayList<>();
                for (int i = kljuc + 1; i <= n; i++) {
                    vr.add(v(rs.getObject(i)));
                }
                if (m.put(k.toString(), vr) != null) {
                    System.out.println("   OPOZORILO: dvojnik naravnega kljuca " + k);
                }
            }
        }
        return m;
    }

    static void primerjaj(String ime, Map<String, List<String>> a, Map<String, List<String>> b,
                          String[] stolpci, int kljuc) {
        int razlicnih = 0;
        int samoA = 0;
        int samoB = 0;
        Map<String, Integer> poStolpcu = new TreeMap<>();
        List<String> primeri = new ArrayList<>();
        for (var e : a.entrySet()) {
            List<String> drugi = b.get(e.getKey());
            if (drugi == null) {
                samoA++;
                continue;
            }
            boolean razlika = false;
            for (int i = 0; i < e.getValue().size(); i++) {
                if (!e.getValue().get(i).equals(drugi.get(i))) {
                    razlika = true;
                    poStolpcu.merge(stolpci[kljuc + i], 1, Integer::sum);
                    if (primeri.size() < 6) {
                        primeri.add(e.getKey() + " " + stolpci[kljuc + i] + ": SQLite=" + e.getValue().get(i)
                                + " PostgreSQL=" + drugi.get(i));
                    }
                }
            }
            if (razlika) {
                razlicnih++;
            }
        }
        for (String k : b.keySet()) {
            if (!a.containsKey(k)) {
                samoB++;
            }
        }
        System.out.println("== " + ime + ": vrstic SQLite " + a.size() + ", PostgreSQL " + b.size()
                + ", vrstic z razliko " + razlicnih + ", samo v SQLite " + samoA + ", samo v PostgreSQL " + samoB);
        System.out.println("   razlike po stolpcih: " + poStolpcu);
        primeri.forEach(s -> System.out.println("   " + s));
    }

    static void stanje(Connection s, Connection p) throws Exception {
        String q = "SELECT id_igralec, sistem, vrednost, st_tekem, zadnja_tekma_ob, preostanek_vrnitve, "
                + "prva_tekma_ob, postavljen, zunanja_uvrstitev_ob, verzija FROM rating_stanje";
        String[] st = {"id_igralec", "sistem", "vrednost", "st_tekem", "zadnja_tekma_ob", "preostanek_vrnitve",
                "prva_tekma_ob", "postavljen", "zunanja_uvrstitev_ob", "verzija"};
        var a = beri(s, q, 2, 10);
        var b = beri(p, q, 2, 10);
        // logicni stolpec: SQLite 0/1, PostgreSQL true/false
        a.values().forEach(l -> l.set(5, l.get(5).equals("1") ? "true" : l.get(5).equals("0") ? "false" : l.get(5)));
        primerjaj("rating_stanje", a, b, st, 2);
    }

    static void zgodovina(Connection s, Connection p) throws Exception {
        String q = "SELECT id_igralec, COALESCE(id_tekma, 0), COALESCE(id_tekma_srecanja, 0), COALESCE(razlog, ''), "
                + "velja_ob, sprememba, nova_vrednost, tocke, k, teza, pricakovano FROM rating_zgodovina";
        String[] st = {"id_igralec", "id_tekma", "id_tekma_srecanja", "razlog", "velja_ob", "sprememba",
                "nova_vrednost", "tocke", "k", "teza", "pricakovano"};
        primerjaj("rating_zgodovina", beri(s, q, 5, 11), beri(p, q, 5, 11), st, 5);
    }
}
