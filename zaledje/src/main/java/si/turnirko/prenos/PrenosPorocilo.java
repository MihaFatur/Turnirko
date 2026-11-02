/* Porocilo prenosa: zeleno je samo, ce se je vsaka tabela ujela po stevilu
   vrstic IN po kontrolni vsoti in ni ostala nobena nepretvorljiva vrednost.
   Zapise v Markdown lahko lastnik prebere brez orodij; osebnih podatkov ne
   vsebuje razen kratkih vrednosti napacnih polj (do 40 znakov). */
package si.turnirko.prenos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PrenosPorocilo {

    /* pg == null: tabela v PostgreSQL ni bila brana (nacin preglej ali
       prenos, ustavljen pred nalaganjem). */
    record Vrstica(String tabela, long sqlite, Long pg, String shaSqlite, String shaPg) {
        boolean ujema() {
            return pg != null && pg == sqlite && shaSqlite.equals(shaPg);
        }
    }

    String nacin = "";
    String verzija = "";
    boolean preneseno;
    long steviloZavrnitev;
    final List<String> napake = new ArrayList<>();
    final List<String> zavrnitve = new ArrayList<>();
    final List<Vrstica> tabele = new ArrayList<>();
    Map<String, Long> opombe = Map.of();
    private final Map<String, Long> casiMs = new LinkedHashMap<>();

    void cas(String korak, long zacetekNs) {
        casiMs.put(korak, (System.nanoTime() - zacetekNs) / 1_000_000);
    }

    long casMs(String korak) {
        return casiMs.getOrDefault(korak, 0L);
    }

    /* Zeleno = nobene napake, nobene zavrnitve in (ker je preverba v
       PostgreSQL del prenosa) vse tabele ujete. Nacin »preglej« PostgreSQL
       ne bere, zato zanj zadosca pregled SQLite. */
    boolean zeleno() {
        if (!napake.isEmpty() || steviloZavrnitev > 0) {
            return false;
        }
        if (nacin.equals("preglej")) {
            return true;
        }
        return !tabele.isEmpty() && tabele.stream().allMatch(Vrstica::ujema);
    }

    String povzetek() {
        long vrstic = tabele.stream().mapToLong(Vrstica::sqlite).sum();
        long neujetih = tabele.stream().filter(v -> !v.ujema()).count();
        return "Prenos (" + nacin + "): " + (zeleno() ? "ZELENO" : "RDECE") + ", " + tabele.size() + " tabel, "
                + vrstic + " vrstic" + (nacin.equals("preglej") ? "" : ", neujetih tabel: " + neujetih)
                + (preneseno ? ", podatki so v PostgreSQL (potrjeno)" : ", v PostgreSQL ni spremenjeno nic")
                + ", " + casMs("skupaj") / 1000 + " s.";
    }

    String vMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Poročilo prenosa SQLite → PostgreSQL\n\n");
        sb.append("**Rezultat: ").append(zeleno() ? "ZELENO" : "RDEČE").append("** · način: ").append(nacin)
                .append(" · shema ").append(verzija.isEmpty() ? "?" : verzija)
                .append(preneseno ? " · **podatki so potrjeni v PostgreSQL**" : " · v PostgreSQL ni spremenjeno nič")
                .append("\n\n");
        if (!napake.isEmpty()) {
            sb.append("## Napake (prenos je ustavljen)\n\n");
            napake.forEach(n -> sb.append("- ").append(n).append('\n'));
            sb.append('\n');
        }
        if (steviloZavrnitev > 0) {
            sb.append("## Vrednosti, ki se ne pretvorijo (").append(steviloZavrnitev).append(")\n\n");
            zavrnitve.forEach(z -> sb.append("- ").append(z).append('\n'));
            if (steviloZavrnitev > zavrnitve.size()) {
                sb.append("- … še ").append(steviloZavrnitev - zavrnitve.size()).append('\n');
            }
            sb.append('\n');
        }
        sb.append("## Tabele\n\n");
        sb.append("| Tabela | Vrstic SQLite | Vrstic PostgreSQL | SHA-256 SQLite | SHA-256 PostgreSQL | |\n");
        sb.append("|---|---:|---:|---|---|---|\n");
        for (Vrstica v : tabele) {
            sb.append("| ").append(v.tabela()).append(" | ").append(v.sqlite()).append(" | ")
                    .append(v.pg() == null ? "–" : v.pg()).append(" | `").append(skrajsaj(v.shaSqlite())).append("` | ")
                    .append(v.shaPg() == null ? "–" : "`" + skrajsaj(v.shaPg()) + "`").append(" | ")
                    .append(v.pg() == null ? "" : v.ujema() ? "ujema" : "**NE UJEMA**").append(" |\n");
        }
        sb.append('\n');
        if (!opombe.isEmpty()) {
            sb.append("## Oblike vrednosti in odrezki\n\n");
            sb.append("Čas v SQLite je zapisan v več oblikah; vse so pretvorjene v `timestamp(3)`. ")
                    .append("Zapis »odrezano na milisekunde« pomeni, da so bile decimalke pod milisekundo odvržene ")
                    .append("(izbrana natančnost stolpca).\n\n");
            sb.append("| Stolpec : oblika | Vrednosti |\n|---|---:|\n");
            opombe.forEach((k, n) -> sb.append("| ").append(k).append(" | ").append(n).append(" |\n"));
            sb.append('\n');
        }
        sb.append("## Časi\n\n");
        casiMs.forEach((k, ms) -> sb.append("- ").append(k).append(": ").append(ms / 1000.0).append(" s\n"));
        return sb.toString();
    }

    private static String skrajsaj(String sha) {
        return sha == null || sha.length() <= 16 ? String.valueOf(sha) : sha.substring(0, 16);
    }
}
