/* Prenos podatkov iz SQLite v PostgreSQL (faza 3 prenove baze,
   docs/PRENOVA-BAZE.md, razdelek 6).

   Tri nacini:
     PREGLEJ  prebere SQLite, vsako vrednost pretvori in izracuna kontrolne
              vsote; v PostgreSQL ne zapise nicesar. Pokaze VSE vrednosti, ki
              se ne pretvorijo, naenkrat - ne po eno na zagon.
     PRENESI  pregleda, nato v ENI transakciji naloži tabele z COPY (vrstni red
              po tujih kljucih, samoreference v dveh korakih), uskladi zaporedja,
              pozene ANALYZE in primerja stanje v PostgreSQL s pregledom. Samo
              ce je porocilo zeleno, transakcijo potrdi; sicer jo razveljavi in
              baza ostane prazna.
     PREVERI  pregleda SQLite in primerja z ze preneseno bazo (nic ne piše).

   Shema v PostgreSQL je vir resnice (Flyway): tipi, ključi in vrstni red
   nalaganja se preberejo iz nje. SQLite se mora ujemati po tabelah in
   stolpcih - sicer se prenos ustavi, še preden se dotakne podatkov.

   Kontrolna vsota tabele je SHA-256 cez vrstice v kanonični obliki
   (pretvorjene vrednosti, oblika COPY), urejene po primarnem kljucu. Na strani
   SQLite se izracuna iz pretvorjenih vrednosti, na strani PostgreSQL iz tega,
   kar je baza res shranila - ujame torej tudi to, kar bi baza tiho spremenila
   (zaokrozevanje casa, drugačen niz). */
package si.turnirko.prenos;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import si.turnirko.prenos.Pretvorba.Opombe;
import si.turnirko.prenos.Pretvorba.Tip;
import si.turnirko.prenos.Pretvorba.Zavrnitev;
import si.turnirko.prenos.Shema.Stolpec;
import si.turnirko.prenos.Shema.Tabela;
import si.turnirko.prenos.Shema.TujiKljuc;

final class Prenos {

    enum Nacin {
        PREGLEJ, PRENESI, PREVERI;

        static Nacin iz(String ime) {
            return switch (ime.trim().toLowerCase()) {
                case "preglej" -> PREGLEJ;
                case "prenesi" -> PRENESI;
                case "preveri" -> PREVERI;
                default -> throw new IllegalArgumentException(
                        "Neznan nacin prenosa '" + ime + "' (preglej, prenesi ali preveri).");
            };
        }
    }

    private static final Logger dnevnik = LoggerFactory.getLogger(Prenos.class);

    /* Tabele, ki jih izhodiščna migracija napolni sama. Prenos jih zamenja z
       vsebino SQLite (v produkciji je ista, a kdor jo je spremenil, jo obdrži). */
    static final Set<String> SEMENSKE = Set.of("starostno_sidro");

    private static final int NAJVEC_ZAVRNITEV = 100;
    private static final int PAKET_COPY = 1 << 20;

    /* Vrstica v kanonicni obliki in v obliki za COPY (samoreference prazne). */
    @FunctionalInterface
    private interface Sprejemnik {
        void vrstica(Object[] vrednosti, String kanonicna, String zaCopy) throws SQLException;
    }

    private static final class Povzetek {
        long vrstic;
        final MessageDigest digest = sha256();
        String sha;

        String zakljuci() {
            if (sha == null) {
                sha = HexFormat.of().formatHex(digest.digest());
            }
            return sha;
        }
    }

    private final DataSource pg;
    private final Path sqlite;
    private final PrenosPorocilo porocilo = new PrenosPorocilo();
    private final Opombe opombe = new Opombe();

    Prenos(DataSource pg, Path sqlite) {
        this.pg = pg;
        this.sqlite = sqlite;
    }

    PrenosPorocilo izvedi(Nacin nacin) {
        porocilo.nacin = nacin.name().toLowerCase();
        long zacetek = System.nanoTime();
        try (Connection s = odpriSqlite(); Connection p = pg.getConnection()) {
            p.setAutoCommit(false);
            try {
                try (Statement st = p.createStatement()) {
                    // vloga aplikacije ima omejitve (statement_timeout ...), ki za prenos ne veljajo
                    st.execute("SET LOCAL statement_timeout = 0");
                    st.execute("SET LOCAL lock_timeout = 0");
                    st.execute("SET LOCAL idle_in_transaction_session_timeout = 0");
                }
                izvediVPovezavah(s, p, nacin);
                if (porocilo.zeleno() && nacin == Nacin.PRENESI) {
                    p.commit();
                    porocilo.preneseno = true;
                } else {
                    p.rollback();
                }
            } catch (Exception napaka) {
                p.rollback();
                dnevnik.error("Prenos se je ustavil", napaka);
                porocilo.napake.add("Prenos se je ustavil: " + napaka);
            }
        } catch (Exception napaka) {
            dnevnik.error("Prenos se ni mogel začeti", napaka);
            porocilo.napake.add("Prenos se ni mogel začeti: " + napaka);
        }
        porocilo.opombe = opombe.stevci();
        porocilo.cas("skupaj", zacetek);
        return porocilo;
    }

    private void izvediVPovezavah(Connection s, Connection p, Nacin nacin) throws SQLException {
        long t0 = System.nanoTime();
        Shema shema = Shema.preberi(p);
        porocilo.napake.addAll(shema.napake);
        preveriStrukturo(s, shema);
        preveriVerzijo(s, p);
        if (!porocilo.napake.isEmpty()) {
            return;
        }
        List<Tabela> red = shema.vrstniRed();
        porocilo.napake.addAll(shema.napake);
        if (!porocilo.napake.isEmpty()) {
            return;
        }
        porocilo.cas("shema in struktura", t0);

        t0 = System.nanoTime();
        Map<String, Povzetek> izSqlite = new LinkedHashMap<>();
        for (Tabela t : red) {
            Povzetek povzetek = preberiSqlite(s, t, opombe, (v, kan, copy) -> { });
            povzetek.zakljuci();
            izSqlite.put(t.ime, povzetek);
            dnevnik.info("Pregled {}: {} vrstic", t.ime, povzetek.vrstic);
        }
        porocilo.cas("pregled SQLite", t0);
        if (porocilo.steviloZavrnitev > 0) {
            porocilo.napake.add(porocilo.steviloZavrnitev
                    + " vrednosti se ne da pretvoriti - prenos je ustavljen (glej seznam spodaj).");
        }
        if (!porocilo.napake.isEmpty() || nacin == Nacin.PREGLEJ) {
            for (Tabela t : red) {
                Povzetek povzetek = izSqlite.get(t.ime);
                porocilo.tabele.add(new PrenosPorocilo.Vrstica(t.ime, povzetek.vrstic, null,
                        povzetek.zakljuci(), null));
            }
            return;
        }

        if (nacin == Nacin.PRENESI) {
            t0 = System.nanoTime();
            preveriPrazno(p, red);
            if (!porocilo.napake.isEmpty()) {
                return;
            }
            naloziVse(s, p, red, izSqlite);
            porocilo.cas("nalaganje (COPY)", t0);
        }

        t0 = System.nanoTime();
        for (Tabela t : red) {
            Povzetek v = izSqlite.get(t.ime);
            Povzetek v2 = preberiPg(p, t);
            porocilo.tabele.add(new PrenosPorocilo.Vrstica(t.ime, v.vrstic, v2.vrstic,
                    v.zakljuci(), v2.zakljuci()));
        }
        porocilo.cas("preverba v PostgreSQL", t0);
    }

    // ---------------------------------------------------------------- struktura

    private Connection odpriSqlite() throws SQLException {
        if (!Files.isRegularFile(sqlite)) {
            throw new SQLException("Datoteke SQLite ni: " + sqlite.toAbsolutePath());
        }
        Properties lastnosti = new Properties();
        lastnosti.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY: prenos izvora ne more spremeniti
        String pot = sqlite.toAbsolutePath().toString().replace('\\', '/');
        return DriverManager.getConnection("jdbc:sqlite:" + pot, lastnosti);
    }

    private void preveriStrukturo(Connection s, Shema shema) throws SQLException {
        Map<String, List<String>> izSqlite = new LinkedHashMap<>();
        try (Statement st = s.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                     + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            List<String> imena = new ArrayList<>();
            while (rs.next()) {
                imena.add(rs.getString(1));
            }
            for (String ime : imena) {
                List<String> stolpci = new ArrayList<>();
                try (ResultSet info = st.executeQuery("PRAGMA table_info(\"" + ime + "\")")) {
                    while (info.next()) {
                        stolpci.add(info.getString("name"));
                    }
                }
                izSqlite.put(ime, stolpci);
            }
        }
        izSqlite.remove(Shema.FLYWAY);
        for (String ime : new TreeSet<>(izSqlite.keySet())) {
            if (!shema.tabele.containsKey(ime)) {
                porocilo.napake.add("Tabela " + ime + " je v SQLite, v PostgreSQL je ni "
                        + "(manjka dvojnica migracije v db/migration/postgresql?).");
            }
        }
        for (Tabela t : shema.tabele.values()) {
            List<String> stolpci = izSqlite.get(t.ime);
            if (stolpci == null) {
                porocilo.napake.add("Tabela " + t.ime + " je v PostgreSQL, v SQLite je ni.");
                continue;
            }
            Set<String> cilj = t.stolpci.stream().map(Stolpec::ime).collect(Collectors.toCollection(TreeSet::new));
            Set<String> izvor = new TreeSet<>(stolpci);
            if (!cilj.equals(izvor)) {
                Set<String> manjka = new TreeSet<>(cilj);
                manjka.removeAll(izvor);
                Set<String> odvec = new TreeSet<>(izvor);
                odvec.removeAll(cilj);
                porocilo.napake.add("Stolpci tabele " + t.ime + " se ne ujemajo: v SQLite manjkajo " + manjka
                        + ", v SQLite so odvec " + odvec + ".");
            }
        }
    }

    /* SQLite mora biti na isti verziji sheme kot izhodisce v PostgreSQL: sicer
       prenos nosi podatke ob shemi, ki je ne pozna. PG-only migracije (53.1)
       so izvzete. */
    private void preveriVerzijo(Connection s, Connection p) throws SQLException {
        int[] izvor = null;
        try (Statement st = s.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = 0")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    porocilo.napake.add("SQLite ima neuspelo migracijo v flyway_schema_history.");
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1")) {
                while (rs.next()) {
                    izvor = vecja(izvor, rs.getString(1));
                }
            }
        }
        int[] cilj = null;
        try (Statement st = p.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history "
                     + "WHERE success AND version IS NOT NULL AND version NOT LIKE '%.%'")) {
            while (rs.next()) {
                cilj = vecja(cilj, rs.getString(1));
            }
        }
        if (izvor == null || cilj == null || !java.util.Arrays.equals(izvor, cilj)) {
            porocilo.napake.add("Verziji shem se ne ujemata: SQLite " + opis(izvor) + ", PostgreSQL " + opis(cilj)
                    + ". Vsaka migracija mora imeti dvojnico v obeh mapah.");
        } else {
            porocilo.verzija = opis(izvor);
        }
    }

    private static int[] vecja(int[] trenutna, String verzija) {
        if (verzija == null) {
            return trenutna;
        }
        String[] deli = verzija.split("\\.");
        int[] nova = new int[deli.length];
        for (int i = 0; i < deli.length; i++) {
            nova[i] = Integer.parseInt(deli[i]);
        }
        if (trenutna == null) {
            return nova;
        }
        for (int i = 0; i < Math.max(trenutna.length, nova.length); i++) {
            int a = i < trenutna.length ? trenutna[i] : 0;
            int b = i < nova.length ? nova[i] : 0;
            if (a != b) {
                return b > a ? nova : trenutna;
            }
        }
        return trenutna;
    }

    private static String opis(int[] verzija) {
        if (verzija == null) {
            return "(ni zapisa)";
        }
        return "V" + java.util.Arrays.stream(verzija).mapToObj(String::valueOf).collect(Collectors.joining("."));
    }

    // ------------------------------------------------------------------ branje

    private Povzetek preberiSqlite(Connection s, Tabela t, Opombe opombe, Sprejemnik sprejemnik)
            throws SQLException {
        int n = t.stolpci.size();
        int[] kljuc = t.kljuc.stream().mapToInt(t::indeks).toArray();
        boolean[] odlozen = new boolean[n];
        boolean imaOdlozene = false;
        for (int i = 0; i < n; i++) {
            odlozen[i] = t.jeOdlozen(t.stolpci.get(i).ime());
            imaOdlozene |= odlozen[i];
        }
        String sql = "SELECT " + t.stolpci.stream().map(c -> citiraj(c.ime())).collect(Collectors.joining(", "))
                + " FROM " + citiraj(t.ime)
                + " ORDER BY " + t.kljuc.stream().map(Prenos::citiraj).collect(Collectors.joining(", "));
        Povzetek povzetek = new Povzetek();
        try (Statement st = s.createStatement()) {
            st.setFetchSize(5000);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Object[] v = new Object[n];
                    boolean zavrnjena = false;
                    for (int i = 0; i < n; i++) {
                        Stolpec c = t.stolpci.get(i);
                        Object surova = rs.getObject(i + 1);
                        if (surova == null) {
                            if (c.obvezen()) {
                                zavrni(t.ime, c.ime(), vrstica(rs, t, kljuc), "NULL v stolpcu NOT NULL");
                                zavrnjena = true;
                            }
                            continue;
                        }
                        try {
                            v[i] = Pretvorba.izSqlite(c.tip(), surova, t.ime + "." + c.ime(), opombe);
                        } catch (Zavrnitev z) {
                            zavrni(t.ime, c.ime(), vrstica(rs, t, kljuc), z.getMessage());
                            zavrnjena = true;
                        }
                    }
                    if (zavrnjena) {
                        continue;
                    }
                    String kanonicna = vrstica(v, null);
                    String zaCopy = imaOdlozene ? vrstica(v, odlozen) : kanonicna;
                    posodobi(povzetek, kanonicna);
                    sprejemnik.vrstica(v, kanonicna, zaCopy);
                }
            }
        }
        return povzetek;
    }

    private Povzetek preberiPg(Connection p, Tabela t) throws SQLException {
        int n = t.stolpci.size();
        String sql = "SELECT " + t.stolpci.stream().map(c -> citiraj(c.ime())).collect(Collectors.joining(", "))
                + " FROM " + citiraj(t.ime) + " ORDER BY "
                + t.kljuc.stream()
                        .map(k -> citiraj(k) + (t.stolpci.get(t.indeks(k)).tip() == Tip.BESEDILO ? " COLLATE \"C\"" : ""))
                        .collect(Collectors.joining(", "));
        Povzetek povzetek = new Povzetek();
        try (Statement st = p.createStatement()) {
            st.setFetchSize(5000);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    Object[] v = new Object[n];
                    for (int i = 0; i < n; i++) {
                        v[i] = preberiPg(rs, i + 1, t.stolpci.get(i).tip());
                    }
                    posodobi(povzetek, vrstica(v, null));
                }
            }
        }
        return povzetek;
    }

    /* Vrednost stolpca, kot jo vrne baza, v istem tipu kot pretvorba iz SQLite
       (getObject(i, Long.class) pri int4 gonilnik ne podpira). */
    private static Object preberiPg(ResultSet rs, int stolpec, Tip tip) throws SQLException {
        return switch (tip) {
            case CELO64, CELO32 -> {
                long n = rs.getLong(stolpec);
                yield rs.wasNull() ? null : (Object) n;
            }
            case LOGICNO -> {
                boolean b = rs.getBoolean(stolpec);
                yield rs.wasNull() ? null : (Object) b;
            }
            case DECIMALNO -> {
                double d = rs.getDouble(stolpec);
                yield rs.wasNull() ? null : (Object) d;
            }
            case BESEDILO -> rs.getString(stolpec);
            case DATUM -> rs.getObject(stolpec, LocalDate.class);
            case CAS -> rs.getObject(stolpec, LocalDateTime.class);
            case BAJTI -> rs.getBytes(stolpec);
        };
    }

    private static void posodobi(Povzetek povzetek, String kanonicna) {
        povzetek.digest.update(kanonicna.getBytes(StandardCharsets.UTF_8));
        povzetek.digest.update((byte) '\n');
        povzetek.vrstic++;
    }

    private static String vrstica(Object[] v, boolean[] prazni) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append('\t');
            }
            Pretvorba.zapisi(sb, prazni != null && prazni[i] ? null : v[i]);
        }
        return sb.toString();
    }

    /* Opis vrstice za sporocilo o napaki: vrednosti primarnega kljuca. */
    private static String vrstica(ResultSet rs, Tabela t, int[] kljuc) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int k : kljuc) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t.stolpci.get(k).ime()).append('=').append(Pretvorba.skrajsaj(String.valueOf(rs.getObject(k + 1))));
        }
        return sb.toString();
    }

    private void zavrni(String tabela, String stolpec, String vrstica, String razlog) {
        porocilo.steviloZavrnitev++;
        if (porocilo.zavrnitve.size() < NAJVEC_ZAVRNITEV) {
            porocilo.zavrnitve.add(tabela + "." + stolpec + " [" + vrstica + "]: " + razlog);
        }
    }

    // ------------------------------------------------------------------ pisanje

    private void preveriPrazno(Connection p, List<Tabela> red) throws SQLException {
        try (Statement st = p.createStatement()) {
            for (Tabela t : red) {
                if (SEMENSKE.contains(t.ime)) {
                    continue;
                }
                try (ResultSet rs = st.executeQuery("SELECT EXISTS (SELECT 1 FROM " + citiraj(t.ime) + ")")) {
                    rs.next();
                    if (rs.getBoolean(1)) {
                        porocilo.napake.add("Ciljna tabela " + t.ime + " ni prazna - prenos gre samo v svezo bazo "
                                + "(po Flywayu, brez zagona aplikacije).");
                    }
                }
            }
        }
    }

    private void naloziVse(Connection s, Connection p, List<Tabela> red, Map<String, Povzetek> pricakovano)
            throws SQLException {
        Map<Tabela, List<Object[]>> odlozeni = new LinkedHashMap<>();
        try (Statement st = p.createStatement()) {
            for (String seme : SEMENSKE) {
                st.execute("DELETE FROM " + citiraj(seme));
            }
        }
        for (Tabela t : red) {
            List<Object[]> zaPosodobitev = new ArrayList<>();
            long vrstic = nalozi(s, p, t, zaPosodobitev);
            if (vrstic != pricakovano.get(t.ime).vrstic) {
                throw new SQLException("Tabela " + t.ime + ": COPY je zapisal " + vrstic + " vrstic, pregled jih je "
                        + "našel " + pricakovano.get(t.ime).vrstic + ".");
            }
            if (!zaPosodobitev.isEmpty()) {
                odlozeni.put(t, zaPosodobitev);
            }
            dnevnik.info("Preneseno {}: {} vrstic", t.ime, vrstic);
        }
        for (Map.Entry<Tabela, List<Object[]>> e : odlozeni.entrySet()) {
            posodobiOdlozene(p, e.getKey(), e.getValue());
        }
        uskladiZaporedja(p, red);
        try (Statement st = p.createStatement()) {
            st.execute("ANALYZE");
        }
    }

    private long nalozi(Connection s, Connection p, Tabela t, List<Object[]> zaPosodobitev) throws SQLException {
        CopyManager copy = p.unwrap(PGConnection.class).getCopyAPI();
        String stolpci = t.stolpci.stream().map(c -> citiraj(c.ime())).collect(Collectors.joining(", "));
        CopyIn vhod = copy.copyIn("COPY " + citiraj(t.ime) + " (" + stolpci + ") FROM STDIN");
        ByteArrayOutputStream vmesnik = new ByteArrayOutputStream(PAKET_COPY + 4096);
        int[] kljuc = t.kljuc.stream().mapToInt(t::indeks).toArray();
        int[] odlozeni = t.stolpci.stream().filter(c -> t.jeOdlozen(c.ime())).mapToInt(c -> t.indeks(c.ime())).toArray();
        try {
            preberiSqlite(s, t, new Opombe(), (v, kan, zaCopy) -> {
                vmesnik.writeBytes(zaCopy.getBytes(StandardCharsets.UTF_8));
                vmesnik.write('\n');
                if (vmesnik.size() >= PAKET_COPY) {
                    vhod.writeToCopy(vmesnik.toByteArray(), 0, vmesnik.size());
                    vmesnik.reset();
                }
                if (odlozeni.length > 0 && java.util.Arrays.stream(odlozeni).anyMatch(i -> v[i] != null)) {
                    Object[] posodobitev = new Object[odlozeni.length + kljuc.length];
                    for (int i = 0; i < odlozeni.length; i++) {
                        posodobitev[i] = v[odlozeni[i]];
                    }
                    for (int i = 0; i < kljuc.length; i++) {
                        posodobitev[odlozeni.length + i] = v[kljuc[i]];
                    }
                    zaPosodobitev.add(posodobitev);
                }
            });
            if (vmesnik.size() > 0) {
                vhod.writeToCopy(vmesnik.toByteArray(), 0, vmesnik.size());
            }
            return vhod.endCopy();
        } catch (SQLException | RuntimeException napaka) {
            if (vhod.isActive()) {
                vhod.cancelCopy();
            }
            throw napaka;
        }
    }

    /* Samoreference in tuji kljuci, ki prekinejo krog: gredo v COPY kot NULL,
       tukaj pa se vsi stolpci tabele postavijo v enem UPDATE (CHECK, ki
       zahteva vzporedno vrednost obeh, tako vedno drzi). */
    private void posodobiOdlozene(Connection p, Tabela t, List<Object[]> vrstice) throws SQLException {
        List<String> odlozeni = t.stolpci.stream().map(Stolpec::ime).filter(t::jeOdlozen).toList();
        String sql = "UPDATE " + citiraj(t.ime) + " SET "
                + odlozeni.stream().map(c -> citiraj(c) + " = ?").collect(Collectors.joining(", "))
                + " WHERE " + t.kljuc.stream().map(k -> citiraj(k) + " = ?").collect(Collectors.joining(" AND "));
        try (PreparedStatement ps = p.prepareStatement(sql)) {
            for (Object[] vrstica : vrstice) {
                for (int i = 0; i < vrstica.length; i++) {
                    ps.setObject(i + 1, vrstica[i]);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
        dnevnik.info("Samoreference {}: {} vrstic posodobljenih", t.ime, vrstice.size());
    }

    private void uskladiZaporedja(Connection p, List<Tabela> red) throws SQLException {
        try (Statement st = p.createStatement()) {
            for (Tabela t : red) {
                for (Stolpec c : t.stolpci) {
                    if (!c.identiteta()) {
                        continue;
                    }
                    Long najvec;
                    try (ResultSet rs = st.executeQuery("SELECT max(" + citiraj(c.ime()) + ") FROM " + citiraj(t.ime))) {
                        rs.next();
                        najvec = rs.getObject(1, Long.class);
                    }
                    if (najvec != null) {
                        try (PreparedStatement ps = p.prepareStatement(
                                "SELECT setval(pg_get_serial_sequence(?, ?), ?, true)")) {
                            ps.setString(1, citiraj(t.ime));
                            ps.setString(2, c.ime());
                            ps.setLong(3, najvec);
                            ps.execute();
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------- pomozno

    private static String citiraj(String ime) {
        return '"' + ime.replace("\"", "\"\"") + '"';
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException napaka) {
            throw new IllegalStateException(napaka);
        }
    }

    /* Za teste: katere tabele ima shema v vrstnem redu nalaganja. */
    static List<String> vrstniRed(Connection p) throws SQLException {
        Shema shema = Shema.preberi(p);
        List<String> imena = new ArrayList<>();
        for (Tabela t : shema.vrstniRed()) {
            imena.add(t.ime);
        }
        return imena;
    }

    static Set<String> odlozeniStolpci(Connection p) throws SQLException {
        Shema shema = Shema.preberi(p);
        shema.vrstniRed();
        Set<String> stolpci = new HashSet<>();
        for (Tabela t : shema.tabele.values()) {
            for (TujiKljuc k : t.odlozeni()) {
                for (String stolpec : k.stolpci) {
                    stolpci.add(t.ime + "." + stolpec);
                }
            }
        }
        return stolpci;
    }
}
