/* Shema ciljne baze PostgreSQL, kot jo je zgradil Flyway: tabele, stolpci,
   primarni in tuji kljuci. Ukaz "prenos" po njej ravna (vrstni red nalaganja,
   tipi, kljuc za razvrscanje), zato je shema vir resnice - SQLite se mora
   ujemati z njo, ne obratno. */
package si.turnirko.prenos;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import si.turnirko.prenos.Pretvorba.Tip;

final class Shema {

    record Stolpec(String ime, Tip tip, boolean obvezen, boolean identiteta) {}

    /* Tuji kljuc. "odlozen" pomeni, da ga nalozimo v drugem koraku (stolpci
       gredo najprej kot NULL, nato UPDATE): samoreference (liga, tekma) in
       tuji kljuci, ki bi sicer naredili krog med tabelami. */
    static final class TujiKljuc {
        final String ciljna;
        final List<String> stolpci;
        boolean odlozen;

        TujiKljuc(String ciljna, List<String> stolpci) {
            this.ciljna = ciljna;
            this.stolpci = stolpci;
        }
    }

    static final class Tabela {
        final String ime;
        final List<Stolpec> stolpci = new ArrayList<>();
        final List<String> kljuc = new ArrayList<>();
        final List<TujiKljuc> tuji = new ArrayList<>();

        Tabela(String ime) {
            this.ime = ime;
        }

        int indeks(String stolpec) {
            for (int i = 0; i < stolpci.size(); i++) {
                if (stolpci.get(i).ime().equals(stolpec)) {
                    return i;
                }
            }
            return -1;
        }

        List<TujiKljuc> odlozeni() {
            return tuji.stream().filter(k -> k.odlozen).toList();
        }

        boolean jeOdlozen(String stolpec) {
            return tuji.stream().anyMatch(k -> k.odlozen && k.stolpci.contains(stolpec));
        }
    }

    /* Tabela, ki je ne prenasamo: Flyway jo vodi sam, v SQLite in PostgreSQL
       ima drugacno zgodovino. */
    static final String FLYWAY = "flyway_schema_history";

    final Map<String, Tabela> tabele = new TreeMap<>();
    final List<String> napake = new ArrayList<>();

    static Shema preberi(Connection povezava) throws SQLException {
        Shema shema = new Shema();
        try (Statement stavek = povezava.createStatement()) {
            try (ResultSet rs = stavek.executeQuery("""
                    SELECT c.table_name, c.column_name, c.data_type, c.is_nullable, c.is_identity
                    FROM information_schema.columns c
                    JOIN information_schema.tables t
                      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                    WHERE c.table_schema = current_schema() AND t.table_type = 'BASE TABLE'
                    ORDER BY c.table_name, c.ordinal_position""")) {
                while (rs.next()) {
                    String tabela = rs.getString(1);
                    if (tabela.equals(FLYWAY)) {
                        continue;
                    }
                    Tip tip = Tip.izPostgresa(rs.getString(3));
                    if (tip == null) {
                        shema.napake.add("Stolpec " + tabela + "." + rs.getString(2) + " ima tip '"
                                + rs.getString(3) + "', ki ga prenos se ne podpira (Pretvorba.Tip).");
                        continue;
                    }
                    shema.tabele.computeIfAbsent(tabela, Tabela::new).stolpci.add(new Stolpec(
                            rs.getString(2), tip, "NO".equals(rs.getString(4)), "YES".equals(rs.getString(5))));
                }
            }
            try (ResultSet rs = stavek.executeQuery("""
                    SELECT rel.relname, a.attname
                    FROM pg_constraint c
                    JOIN pg_class rel ON rel.oid = c.conrelid
                    CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                    WHERE c.contype = 'p' AND rel.relnamespace = current_schema()::regnamespace
                    ORDER BY rel.relname, k.ord""")) {
                while (rs.next()) {
                    Tabela t = shema.tabele.get(rs.getString(1));
                    if (t != null) {
                        t.kljuc.add(rs.getString(2));
                    }
                }
            }
            try (ResultSet rs = stavek.executeQuery("""
                    SELECT c.oid, rel.relname, ref.relname, a.attname
                    FROM pg_constraint c
                    JOIN pg_class rel ON rel.oid = c.conrelid
                    JOIN pg_class ref ON ref.oid = c.confrelid
                    CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                    WHERE c.contype = 'f' AND rel.relnamespace = current_schema()::regnamespace
                    ORDER BY c.oid, k.ord""")) {
                Map<Long, TujiKljuc> poOidu = new LinkedHashMap<>();
                while (rs.next()) {
                    Tabela t = shema.tabele.get(rs.getString(2));
                    if (t == null) {
                        continue;
                    }
                    TujiKljuc k = poOidu.get(rs.getLong(1));
                    if (k == null) {
                        k = new TujiKljuc(rs.getString(3), new ArrayList<>());
                        poOidu.put(rs.getLong(1), k);
                        t.tuji.add(k);
                    }
                    k.stolpci.add(rs.getString(4));
                }
            }
        }
        for (Tabela t : shema.tabele.values()) {
            if (t.kljuc.isEmpty()) {
                shema.napake.add("Tabela " + t.ime + " nima primarnega kljuca - kontrolne vsote ne gre urediti.");
            }
        }
        return shema;
    }

    /* Vrstni red nalaganja: tabela pride za vsemi, na katere kaze. Samoreference
       so odlozene vedno; ce ostane krog med tabelami, odlozimo en tuji kljuc
       (samo z nullable stolpci), dokler se krog ne pretrga. */
    List<Tabela> vrstniRed() {
        for (Tabela t : tabele.values()) {
            for (TujiKljuc k : t.tuji) {
                if (k.ciljna.equals(t.ime)) {
                    odloziAliNapaka(t, k);
                }
            }
        }
        List<Tabela> red = new ArrayList<>();
        Set<String> narejene = new HashSet<>();
        while (narejene.size() < tabele.size()) {
            boolean napredek = false;
            for (Tabela t : tabele.values()) {
                if (!narejene.contains(t.ime) && narejene.containsAll(odvisnosti(t))) {
                    red.add(t);
                    narejene.add(t.ime);
                    napredek = true;
                }
            }
            if (napredek) {
                continue;
            }
            boolean pretrgan = false;
            for (Tabela t : tabele.values()) {
                if (narejene.contains(t.ime)) {
                    continue;
                }
                for (TujiKljuc k : t.tuji) {
                    if (!k.odlozen && !narejene.contains(k.ciljna) && jeNullable(t, k)) {
                        k.odlozen = true;
                        pretrgan = true;
                        break;
                    }
                }
                if (pretrgan) {
                    break;
                }
            }
            if (!pretrgan) {
                napake.add("Tabele se krozno sklicujejo in noben tuji kljuc ni nullable: "
                        + tabele.keySet().stream().filter(i -> !narejene.contains(i)).toList());
                break;
            }
        }
        return red;
    }

    private Set<String> odvisnosti(Tabela t) {
        Set<String> ciljne = new HashSet<>();
        for (TujiKljuc k : t.tuji) {
            if (!k.odlozen) {
                ciljne.add(k.ciljna);
            }
        }
        return ciljne;
    }

    private void odloziAliNapaka(Tabela t, TujiKljuc k) {
        if (jeNullable(t, k)) {
            k.odlozen = true;
        } else {
            napake.add("Samoreferenca " + t.ime + " " + k.stolpci + " je NOT NULL - vrstice ni mogoce vstaviti.");
        }
    }

    private static boolean jeNullable(Tabela t, TujiKljuc k) {
        return k.stolpci.stream().noneMatch(s -> t.stolpci.get(t.indeks(s)).obvezen());
    }
}
