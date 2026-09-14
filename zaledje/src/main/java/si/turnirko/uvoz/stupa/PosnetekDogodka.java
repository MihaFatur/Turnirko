/* Posnetek enega dogodka Stupe na disku: vse, kar preslikava potrebuje, in nic
   drugega kot branje.

   Uvoz ne bere spletnega API-ja neposredno. Najprej nastane posnetek (mapa
   JSON datotek), predogled in uvoz pa tečeta nad ISTIM posnetkom - admin tako
   uvozi natanko tisto, kar je pregledal, in ne tega, kar je vir vrnil minuto
   pozneje. Zgostitev posnetka gre v dnevnik uvoza.

   Datoteke v mapi dogodka (ista postavitev kot uvoz-stupa/surovo/dogodki/{id}):
     kategorije.json, stopnje.json, skupine.json, kola.json, udelezenci.json,
     tekme.json in - pri posnetku za sinhronizacijo - dogodek.json in
     sezona.json (vrstici iz seznama dogodkov in sezon).
   skupine.json je pri novih posnetkih prenesen z load_participants=true in
   nosi uradno razvrstitev skupin (group_rank, group_points); starejsi posnetki
   je nimajo in uskladitev takrat tece brez nje. */
package si.turnirko.uvoz.stupa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import si.turnirko.uvoz.UvozOblike;

public final class PosnetekDogodka {

    /* Datoteke posnetka v stalnem vrstnem redu - po njem se racuna zgostitev. */
    static final List<String> DATOTEKE = List.of(
            "kategorije.json", "stopnje.json", "skupine.json", "kola.json", "udelezenci.json", "tekme.json");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode vrstica;
    private final String imeSezone;
    private final LocalDate datumPosnetka;
    private final String zgostitev;

    private final List<JsonNode> kategorije;
    private final List<JsonNode> stopnje;
    private final List<JsonNode> skupine;
    private final List<JsonNode> kola;
    private final List<JsonNode> udelezenci;
    private final List<JsonNode> tekme;
    private final int osirotelihTekem;
    private final boolean imaUradneSkupine;

    private final Map<Long, JsonNode> stopnjaPoId = new HashMap<>();
    private final Map<Long, JsonNode> skupinaPoId = new HashMap<>();
    private final Map<Long, JsonNode> udelezenecPoId = new HashMap<>();

    private PosnetekDogodka(JsonNode vrstica, String imeSezone, LocalDate datumPosnetka, String zgostitev,
                            Map<String, JsonNode> vsebina) {
        this.vrstica = vrstica;
        this.imeSezone = imeSezone;
        this.datumPosnetka = datumPosnetka;
        this.zgostitev = zgostitev;

        this.kategorije = seznam(vsebina.get("kategorije.json").path("data"));
        this.stopnje = new ArrayList<>(seznam(vsebina.get("stopnje.json").path("data")));
        this.stopnje.sort(Comparator.comparingInt((JsonNode s) -> s.path("order").asInt())
                .thenComparingLong(s -> s.path("id").asLong()));
        this.skupine = seznam(vsebina.get("skupine.json").path("data"));
        this.kola = seznam(vsebina.get("kola.json").path("data"));
        this.udelezenci = seznam(vsebina.get("udelezenci.json").path("data").path("participants"));

        stopnje.forEach(s -> stopnjaPoId.put(s.path("id").asLong(), s));
        skupine.forEach(g -> skupinaPoId.put(g.path("id").asLong(), g));
        udelezenci.forEach(u -> udelezenecPoId.put(u.path("id").asLong(), u));
        this.imaUradneSkupine = skupine.stream().anyMatch(g -> g.has("participants") && g.path("participants").size() > 0);

        /* Tekme skupin, ki jih vir ne pozna vec, so ostanki razveljavljenih
           zrebov (npr. 90 neodigranih tekem pri dogodku 192) - v tekmovanje ne
           sodijo. */
        List<JsonNode> vse = seznam(vsebina.get("tekme.json").path("matches"));
        List<JsonNode> veljavne = new ArrayList<>();
        for (JsonNode t : vse) {
            if (stopnjaTekme(t) != null) {
                veljavne.add(t);
            }
        }
        this.tekme = veljavne;
        this.osirotelihTekem = vse.size() - veljavne.size();
    }

    /* Prebere mapo dogodka. vrstica in imeSezone sta pri posnetku za
       sinhronizacijo v mapi sami (dogodek.json, sezona.json); pri
       zgodovinskem posnetku ju poda klicatelj iz skupnega seznama. */
    public static PosnetekDogodka beri(Path mapa, JsonNode vrstica, String imeSezone, LocalDate datumPosnetka) {
        if (!Files.isDirectory(mapa)) {
            throw new IllegalArgumentException("Mapa posnetka ne obstaja: " + mapa);
        }
        Map<String, JsonNode> vsebina = new HashMap<>();
        MessageDigest zgostitev = sha256();
        for (String ime : DATOTEKE) {
            byte[] bajti = beriBajte(mapa.resolve(ime));
            zgostitev.update(ime.getBytes(StandardCharsets.UTF_8));
            zgostitev.update(bajti);
            vsebina.put(ime, razcleni(bajti, mapa.resolve(ime)));
        }
        JsonNode v = vrstica != null ? vrstica : razcleni(beriBajte(mapa.resolve("dogodek.json")), mapa);
        String sezona = imeSezone;
        if (sezona == null) {
            sezona = UvozOblike.ocisti(razcleni(beriBajte(mapa.resolve("sezona.json")), mapa)
                    .path("season_name").asText(null));
        }
        return new PosnetekDogodka(v, sezona, datumPosnetka,
                HexFormat.of().formatHex(zgostitev.digest()), vsebina);
    }

    // ---------- Dogodek ----------

    public long id() { return vrstica.path("id").asLong(); }
    public String ime() { return UvozOblike.ocisti(vrstica.path("name").asText(null)); }
    public boolean jeLiga() { return "L".equals(vrstica.path("event_type").asText()); }
    public LocalDate zacetek() { return UvozOblike.datum(vrstica.path("event_start_date").asText(null)); }
    public LocalDate konec() { return UvozOblike.datum(vrstica.path("event_end_date").asText(null)); }
    public String imeSezone() { return imeSezone; }
    public String zgostitev() { return zgostitev; }
    public LocalDate datumPosnetka() { return datumPosnetka; }
    public int osirotelihTekem() { return osirotelihTekem; }
    public boolean imaUradneSkupine() { return imaUradneSkupine; }

    /* Dogodek je mimo, ko je dan konca pred dnem posnetka. Neodigrana tekma
       koncanega dogodka se ne bo nikoli odigrala (odstop, zastarela tekma
       pri viru) in v tekmovanje ne sodi; pri dogodku, ki se tece, pa je
       tekma, ki pride na vrsto. */
    public boolean jeMimo() {
        LocalDate k = konec() != null ? konec() : zacetek();
        return k != null && k.isBefore(datumPosnetka);
    }

    /* Prizorisce iz dodatnih polj dogodka ("Venue"). */
    public String prizorisce() {
        for (JsonNode sekcija : vrstica.path("extra_json")) {
            for (JsonNode polje : sekcija.path("section_json_with_values")) {
                if ("Venue".equalsIgnoreCase(polje.path("field_title").asText())) {
                    return UvozOblike.ocisti(polje.path("field_value").asText(null));
                }
            }
        }
        return null;
    }

    // ---------- Sestavni deli ----------

    public List<JsonNode> kategorije() { return kategorije; }
    public List<JsonNode> skupine() { return skupine; }
    public List<JsonNode> udelezenci() { return udelezenci; }
    public List<JsonNode> tekme() { return tekme; }

    public JsonNode udelezenec(long id) { return udelezenecPoId.get(id); }
    public JsonNode skupina(long id) { return skupinaPoId.get(id); }

    /* Stopnje kategorije po vrsti. */
    public List<JsonNode> stopnjeKategorije(long idKategorije) {
        return stopnje.stream().filter(s -> s.path("event_category_id").asLong() == idKategorije).toList();
    }

    /* Skupine stopnje po vrsti. */
    public List<JsonNode> skupineStopnje(long idStopnje) {
        return skupine.stream()
                .filter(g -> g.path("stage_id").asLong() == idStopnje)
                .sorted(Comparator.comparingInt((JsonNode g) -> g.path("order").asInt())
                        .thenComparingLong(g -> g.path("id").asLong()))
                .toList();
    }

    /* Kola skupine po vrsti. */
    public List<JsonNode> kolaSkupine(long idSkupine) {
        return kola.stream()
                .filter(k -> k.path("group_id").asLong() == idSkupine)
                .sorted(Comparator.comparingInt(k -> k.path("order").asInt()))
                .toList();
    }

    public List<JsonNode> tekmeSkupine(long idSkupine) {
        return tekme.stream()
                .filter(t -> t.path("group_id").asLong() == idSkupine)
                .sorted(Comparator.comparingInt((JsonNode t) -> t.path("round").path("order").asInt())
                        .thenComparingInt(t -> t.path("order").asInt())
                        .thenComparingInt(t -> t.path("leg").asInt(0))
                        .thenComparingLong(t -> t.path("id").asLong()))
                .toList();
    }

    /* Stopnja, v katero sodi tekma (tekma -> skupina -> stopnja); null pri
       tekmi skupine, ki je vir ne pozna. */
    public JsonNode stopnjaTekme(JsonNode tekma) {
        JsonNode skupina = skupinaPoId.get(tekma.path("group_id").asLong());
        return skupina == null ? null : stopnjaPoId.get(skupina.path("stage_id").asLong());
    }

    // ---------- Branje datotek ----------

    private static byte[] beriBajte(Path pot) {
        try {
            return Files.isRegularFile(pot) ? Files.readAllBytes(pot) : new byte[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Ne morem prebrati " + pot, e);
        }
    }

    private static JsonNode razcleni(byte[] bajti, Path pot) {
        if (bajti.length == 0) {
            return MissingNode.getInstance();
        }
        String vsebina = new String(bajti, StandardCharsets.UTF_8);
        // posnetki PowerShellove skripte imajo lahko oznako BOM, ki je Jackson ne prenese
        if (vsebina.charAt(0) == '﻿') {
            vsebina = vsebina.substring(1);
        }
        try {
            return JSON.readTree(vsebina);
        } catch (IOException e) {
            throw new UncheckedIOException("Posnetek ni veljaven JSON: " + pot, e);
        }
    }

    static List<JsonNode> seznam(JsonNode vozlisce) {
        if (vozlisce == null || !vozlisce.isArray()) {
            return List.of();
        }
        List<JsonNode> r = new ArrayList<>(vozlisce.size());
        vozlisce.forEach(r::add);
        return r;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
