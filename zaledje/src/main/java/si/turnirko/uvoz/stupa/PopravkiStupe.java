/* Popravki datumov, ki jih ima Stupa narobe ali jih sploh nima.

   Zakaj obstajajo. Uvozeno tekmovanje je samo za branje in vsak ponovni uvoz
   vsebino zapise znova iz vira - rocni popravek v bazi bi prvi ponovni uvoz
   povozil. Rating pa je zaporedna kolicina: tekma na napacnem datumu ni
   kozmeticna napaka, ampak tekma, obracunana proti napacnim ratingom. Zato
   popravek zivi tukaj, v kodi, in ga uporabi ISTA preslikava ob vsakem uvozu
   (zgodovinskem in sprotnem na /uvoz).

   Vsak popravek nosi VIR: kje je zapisan pravi datum. Popravek brez vira je
   ugibanje in sem ne sodi.

   Dve vrsti popravkov:
     - DOGODEK: zacetek in konec tekmovanja (Stupa je ob naknadnem vnosu
       zapisala dan vnosa, teden prej ali dvodnevni razpon za enodnevni turnir).
     - TEKMA: cas in/ali KOLO posamezne tekme (srecanja lige). Lige 2024/25
       so bile v Stupo vnesene naknadno, novembra 2025, brez terminov; njihovi
       "krogi" pri viru niso kola in ne tecejo po casu, zato se datum ne da
       izpeljati iz kroga - samo iz koledarja in razporeda zveze, tekmo za
       tekmo. Kolo je uradni krog iz koledarja (prestavljena tekma ostane v
       svojem krogu, cas pa je dejanski); brez njega bi stran lige v "kolu"
       zdruzevala tekme z razlicnih datumov. Tekma ima lahko samo kolo, kadar
       ima vir pravi cas (1. SNTL moski 2024/25).

   Zapis je v src/main/resources/uvoz/stupa-popravki.json. */
package si.turnirko.uvoz.stupa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PopravkiStupe {

    public static final String VIR = "/uvoz/stupa-popravki.json";

    public record Dogodek(LocalDate zacetek, LocalDate konec, String vir) {}

    /* cas ali kolo je lahko prazen, oba pa ne. */
    public record Tekma(LocalDateTime cas, Integer kolo, String vir) {}

    private static final PopravkiStupe PRIVZETI = beri(VIR);

    private final Map<Long, Dogodek> dogodki;
    private final Map<Long, Tekma> tekme;

    private PopravkiStupe(Map<Long, Dogodek> dogodki, Map<Long, Tekma> tekme) {
        this.dogodki = dogodki;
        this.tekme = tekme;
    }

    public static PopravkiStupe privzeti() {
        return PRIVZETI;
    }

    public Optional<Dogodek> dogodek(long idDogodka) {
        return Optional.ofNullable(dogodki.get(idDogodka));
    }

    public Optional<Tekma> tekma(long idTekme) {
        return Optional.ofNullable(tekme.get(idTekme));
    }

    public int steviloTekem() {
        return tekme.size();
    }

    /* Za preverbo zapisa v testih. */
    java.util.Collection<Tekma> vseTekme() {
        return tekme.values();
    }

    /* Popravki, ki jih preslikava uporabi pri tem dogodku, kot stalno
       besedilo - gre v zgostitev posnetka (PosnetekDogodka), da nov ali
       spremenjen popravek pomeni spremembo uvoza tudi pri nespremenjenem
       viru. Vir popravka v zgostitev ne gre: popravljen opis vira ne spremeni
       zapisanega. */
    public String opisZa(long idDogodka, java.util.Collection<Long> idjiTekem) {
        StringBuilder sb = new StringBuilder();
        dogodek(idDogodka).ifPresent(d -> sb.append("dogodek ").append(d.zacetek()).append(' ').append(d.konec()));
        idjiTekem.stream().sorted().forEach(id -> tekma(id).ifPresent(t ->
                sb.append(";tekma ").append(id).append(' ').append(t.cas()).append(' ').append(t.kolo())));
        return sb.toString();
    }

    static PopravkiStupe beri(String pot) {
        try (InputStream in = PopravkiStupe.class.getResourceAsStream(pot)) {
            if (in == null) {
                throw new IllegalStateException("Ni zapisa popravkov Stupe: " + pot);
            }
            JsonNode koren = new ObjectMapper().readTree(in);
            Map<Long, Dogodek> dogodki = new HashMap<>();
            for (JsonNode d : koren.path("dogodki")) {
                String vir = obveznoBesedilo(d, "vir");
                LocalDate zacetek = LocalDate.parse(obveznoBesedilo(d, "zacetek"));
                LocalDate konec = d.hasNonNull("konec") ? LocalDate.parse(d.get("konec").asText()) : zacetek;
                if (konec.isBefore(zacetek)) {
                    throw new IllegalStateException("Popravek dogodka " + d.path("id") + ": konec pred zacetkom");
                }
                if (dogodki.put(d.path("id").asLong(), new Dogodek(zacetek, konec, vir)) != null) {
                    throw new IllegalStateException("Dogodek " + d.path("id") + " je v popravkih dvakrat");
                }
            }
            Map<Long, Tekma> tekme = new HashMap<>();
            for (JsonNode t : koren.path("tekme")) {
                String vir = obveznoBesedilo(t, "vir");
                LocalDateTime cas = t.hasNonNull("cas") ? LocalDateTime.parse(t.get("cas").asText()) : null;
                Integer kolo = t.hasNonNull("kolo") ? t.get("kolo").asInt() : null;
                if (cas == null && kolo == null) {
                    throw new IllegalStateException("Popravek tekme " + t.path("id") + " nima ne casa ne kola");
                }
                if (kolo != null && kolo < 1) {
                    throw new IllegalStateException("Popravek tekme " + t.path("id") + ": kolo " + kolo);
                }
                if (tekme.put(t.path("id").asLong(), new Tekma(cas, kolo, vir)) != null) {
                    throw new IllegalStateException("Tekma " + t.path("id") + " je v popravkih dvakrat");
                }
            }
            return new PopravkiStupe(Map.copyOf(dogodki), Map.copyOf(tekme));
        } catch (IOException e) {
            throw new UncheckedIOException("Zapisa popravkov Stupe ni mogoce prebrati: " + pot, e);
        }
    }

    /* Popravek brez vira ali datuma se ne nalozi - zagon pade, da napaka v
       zapisu ne gre tiho v rating. */
    private static String obveznoBesedilo(JsonNode n, String polje) {
        String v = n.path(polje).asText("").trim();
        if (v.isEmpty()) {
            throw new IllegalStateException("Popravek " + n.path("id") + " nima polja '" + polje + "'");
        }
        return v;
    }
}
