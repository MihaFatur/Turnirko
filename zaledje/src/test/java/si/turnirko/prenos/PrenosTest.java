package si.turnirko.prenos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import si.turnirko.prenos.Prenos.Nacin;

/* Prenos SQLite -> PostgreSQL na pravi shemi: SQLite zgradijo prave migracije
   (db/migration/sqlite), PostgreSQL pa prav tako (db/migration/postgresql).
   Podatki so majhni, a vsebujejo vse tezave, ki jih pozna produkcija: logicne
   vrednosti kot 0/1, stiri oblike casa, decimalke pod milisekundo, znake, ki
   jih COPY ubeza, samoreferenco na VISJI id in cisto zaporedje. */
class PrenosTest {

    private Path mapa;
    private Path datotekaSqlite;
    private String imeBaze;
    private PGSimpleDataSource baza;

    @BeforeEach
    void pripravi() throws Exception {
        mapa = Files.createTempDirectory("prenos-test");
        datotekaSqlite = mapa.resolve("izvor.db");
        imeBaze = "prenos_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(urlBaze("postgres"));
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + imeBaze + " ENCODING 'UTF8' LOCALE_PROVIDER builtin "
                    + "BUILTIN_LOCALE 'C.UTF-8' TEMPLATE template0");
        }
        baza = new PGSimpleDataSource();
        baza.setUrl(urlBaze(imeBaze));
        // Prenos pozna shemo do V61 (zadnja verzija, ki jo je imela produkcija v SQLite). Poznejse migracije
        // so samo za PostgreSQL in test bi brez omejitve padel ob prvi novi (V62).
        Flyway.configure().dataSource(baza).locations("classpath:db/migration/postgresql").target("61").load().migrate();

        Flyway.configure().dataSource("jdbc:sqlite:" + datotekaSqlite.toString().replace('\\', '/'), null, null)
                .locations("classpath:db/migration/sqlite").load().migrate();
        sqlite("""
                INSERT INTO kraj (postna_st, ime) VALUES (3000, 'Celje'), (3310, 'Žalec');
                INSERT INTO klub (id, ime, kratica) VALUES
                    (1, 'NTK Žalec', 'NTK'),
                    (2, 'Klub s tabulatorjem' || char(9) || 'in novo' || char(10) || 'vrstico \\ in poševnico', NULL);
                INSERT INTO igralec (id, ime, priimek, spol, datum_rojstva, email, postna_st, id_klub,
                                     arhiviran, ustvarjen_ob, posodobljen_ob, rekreativni_vstop) VALUES
                    (1, 'Ana', 'Novak', 'ZENSKI', '2001-02-03', 'ana@primer.si', 3000, 1,
                     0, '2026-10-05', '2026-10-05T18:30', 1),
                    (2, 'Žan', 'Čeh', 'MOSKI', '1990-12-31', NULL, NULL, 2,
                     1, '2026-10-05 08:15:20', '2026-10-05T18:30:15.123456789', 0),
                    (41, 'Mojca', 'Šuštar', 'ZENSKI', '1975-06-15', NULL, 3310, NULL,
                     0, '2026-10-06T07:00:00', NULL, 0);
                INSERT INTO liga (id, ime, spol_kategorija, format_srecanja, stevilo_nizov, status, ustvarjen_ob,
                                  id_visja_liga) VALUES
                    (1, 'Liga A', 'MOSKI', 'SNTL', 5, 'PRIPRAVA', '2026-09-01', 2),
                    (2, 'Liga B', 'MOSKI', 'SNTL', 5, 'PRIPRAVA', '2026-09-01', NULL);
                INSERT INTO slika (id, zgostitev, namen, sirina, visina, id_nalozil, ustvarjena_ob) VALUES
                    (1, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'LOGOTIP', 480, 240, NULL, '2026-10-20');
                INSERT INTO slika_razlicica (id, id_slika, velikost, vrsta, sirina, visina, vsebina) VALUES
                    (1, 1, 'TISK', 'image/png', 480, 240, x'89504e470d0a1a0a005c0909ff0a');
                """);
    }

    @AfterEach
    void pospravi() throws Exception {
        try (Connection admin = DriverManager.getConnection(urlBaze("postgres"));
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + imeBaze + " WITH (FORCE)");
        }
        try (var tok = Files.walk(mapa)) {
            tok.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void prenesePodatkeInUjemaVsakoTabelo() throws Exception {
        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI);

        assertTrue(porocilo.zeleno(), porocilo.vMarkdown());
        assertTrue(porocilo.preneseno);
        assertTrue(porocilo.tabele.stream().allMatch(PrenosPorocilo.Vrstica::ujema));
        assertEquals(3L, vrstic(porocilo, "igralec"));
        assertEquals(70L, vrstic(porocilo, "starostno_sidro"));

        try (Connection p = baza.getConnection(); Statement st = p.createStatement()) {
            // logicne vrednosti
            assertEquals("1:f,2:t", niz(st, "SELECT string_agg(id || ':' || CASE WHEN arhiviran THEN 't' ELSE 'f' END, ','"
                    + " ORDER BY id) FROM igralec WHERE id IN (1, 2)"));
            // oblike casa
            assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), cas(st, "SELECT ustvarjen_ob FROM igralec WHERE id = 1"));
            assertEquals(LocalDateTime.of(2026, 10, 5, 18, 30), cas(st, "SELECT posodobljen_ob FROM igralec WHERE id = 1"));
            assertEquals(LocalDateTime.of(2026, 10, 5, 8, 15, 20), cas(st, "SELECT ustvarjen_ob FROM igralec WHERE id = 2"));
            assertEquals(LocalDateTime.of(2026, 10, 5, 18, 30, 15, 123_000_000),
                    cas(st, "SELECT posodobljen_ob FROM igralec WHERE id = 2"));
            assertEquals(LocalDateTime.of(2026, 10, 6, 7, 0), cas(st, "SELECT ustvarjen_ob FROM igralec WHERE id = 41"));
            // znaki, ki jih COPY ubeza
            assertEquals("Klub s tabulatorjem\tin novo\nvrstico \\ in poševnico",
                    niz(st, "SELECT ime FROM klub WHERE id = 2"));
            // samoreferenca na visji id je postavljena
            assertEquals("2", niz(st, "SELECT id_visja_liga FROM liga WHERE id = 1"));
            assertNull(niz(st, "SELECT id_visja_liga FROM liga WHERE id = 2"));
            // bajti (BLOB) z nicelnim bajtom, poševnico, tabulatorjem in novo vrstico ostanejo nespremenjeni
            assertEquals("89504e470d0a1a0a005c0909ff0a", niz(st, "SELECT encode(vsebina, 'hex') FROM slika_razlicica"));
            // zaporedje nadaljuje za najvecjim id-jem (41), ne pri 1
            st.execute("INSERT INTO igralec (ime, priimek, spol, datum_rojstva, ustvarjen_ob) "
                    + "VALUES ('Nov', 'Igralec', 'MOSKI', '2000-01-01', now())");
            assertEquals("42", niz(st, "SELECT max(id) FROM igralec"));
        }
        // odrezki so v poročilu
        assertEquals(1L, porocilo.opombe.get("igralec.posodobljen_ob : odrezano na milisekunde"));
        assertTrue(porocilo.opombe.containsKey("igralec.ustvarjen_ob : 9999-99-99"));
    }

    @Test
    void nepretvorljivaVrednostUstaviPrenosInPostgresOstanePrazen() throws Exception {
        sqlite("PRAGMA ignore_check_constraints = ON; "
                + "UPDATE igralec SET arhiviran = 2 WHERE id = 1; "
                + "UPDATE igralec SET datum_rojstva = 'neki' WHERE id = 2;");

        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI);

        assertFalse(porocilo.zeleno());
        assertFalse(porocilo.preneseno);
        assertEquals(2, porocilo.steviloZavrnitev);
        String napake = String.join("\n", porocilo.zavrnitve);
        assertTrue(napake.contains("igralec.arhiviran [id=1]"), napake);
        assertTrue(napake.contains("igralec.datum_rojstva [id=2]"), napake);
        assertEquals("0", vPostgresu("SELECT count(*) FROM klub"));
        assertEquals("0", vPostgresu("SELECT count(*) FROM igralec"));
    }

    @Test
    void pregledPostgresaNeSpremeni() throws Exception {
        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PREGLEJ);

        assertTrue(porocilo.zeleno(), porocilo.vMarkdown());
        assertFalse(porocilo.preneseno);
        assertEquals("0", vPostgresu("SELECT count(*) FROM igralec"));
        assertEquals("70", vPostgresu("SELECT count(*) FROM starostno_sidro"));
    }

    @Test
    void neprazneCiljneTabeleZavrnePrenos() throws Exception {
        assertTrue(new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI).zeleno());

        PrenosPorocilo drugic = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI);

        assertFalse(drugic.zeleno());
        assertTrue(String.join("\n", drugic.napake).contains("ni prazna"), drugic.vMarkdown());
        assertEquals("3", vPostgresu("SELECT count(*) FROM igralec"));
    }

    @Test
    void preverbaPoPrenosuJeZelenaInUjameSpremembo() throws Exception {
        assertTrue(new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI).zeleno());
        assertTrue(new Prenos(baza, datotekaSqlite).izvedi(Nacin.PREVERI).zeleno());

        try (Connection p = baza.getConnection(); Statement st = p.createStatement()) {
            st.execute("UPDATE igralec SET priimek = 'Spremenjen' WHERE id = 1");
        }
        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PREVERI);

        assertFalse(porocilo.zeleno());
        assertTrue(porocilo.tabele.stream().filter(v -> !v.ujema()).map(PrenosPorocilo.Vrstica::tabela).toList()
                .equals(List.of("igralec")), porocilo.vMarkdown());
    }

    @Test
    void razlicniVerziji_shemeUstavitaPrenos() throws Exception {
        sqlite("DELETE FROM flyway_schema_history WHERE installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history)");

        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI);

        assertFalse(porocilo.zeleno());
        assertTrue(String.join("\n", porocilo.napake).contains("Verziji shem se ne ujemata"), porocilo.vMarkdown());
        assertEquals("0", vPostgresu("SELECT count(*) FROM igralec"));
    }

    @Test
    void odvecenStolpecVSqliteUstaviPrenos() throws Exception {
        sqlite("ALTER TABLE klub ADD COLUMN novo_polje TEXT");

        PrenosPorocilo porocilo = new Prenos(baza, datotekaSqlite).izvedi(Nacin.PRENESI);

        assertFalse(porocilo.zeleno());
        assertTrue(String.join("\n", porocilo.napake).contains("Stolpci tabele klub"), porocilo.vMarkdown());
    }

    @Test
    void vrstniRedNaloziStarsePredOtroki() throws Exception {
        try (Connection p = baza.getConnection()) {
            List<String> red = Prenos.vrstniRed(p);
            assertTrue(red.indexOf("kraj") < red.indexOf("igralec"));
            assertTrue(red.indexOf("klub") < red.indexOf("igralec"));
            assertTrue(red.indexOf("turnir") < red.indexOf("dogodek"));
            assertTrue(red.indexOf("dogodek") < red.indexOf("tekma"));
            Set<String> odlozeni = Prenos.odlozeniStolpci(p);
            assertTrue(odlozeni.contains("liga.id_visja_liga"), odlozeni.toString());
            assertTrue(odlozeni.contains("tekma.id_izvor_tekma_1"), odlozeni.toString());
            assertTrue(odlozeni.contains("tekma.id_prenesena"), odlozeni.toString());
        }
    }

    // ------------------------------------------------------------------ pomozno

    private static long vrstic(PrenosPorocilo porocilo, String tabela) {
        return porocilo.tabele.stream().filter(v -> v.tabela().equals(tabela)).findFirst().orElseThrow().sqlite();
    }

    private static String urlBaze(String ime) {
        String url = System.getProperty("spring.datasource.url");
        return url.replaceFirst("/turnirko(\\?|$)", "/" + ime + "$1");
    }

    private void sqlite(String skript) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + datotekaSqlite.toString().replace('\\', '/'));
             Statement st = c.createStatement()) {
            for (String stavek : razdeli(skript)) {
                st.execute(stavek);
            }
        }
    }

    /* Podatki testa podpicja ne vsebujejo, zato je locilo kar podpicje. */
    private static List<String> razdeli(String skript) {
        return java.util.Arrays.stream(skript.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String vPostgresu(String sql) throws SQLException {
        try (Connection p = baza.getConnection(); Statement st = p.createStatement()) {
            return niz(st, sql);
        }
    }

    private static String niz(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static LocalDateTime cas(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, LocalDateTime.class);
        }
    }
}
