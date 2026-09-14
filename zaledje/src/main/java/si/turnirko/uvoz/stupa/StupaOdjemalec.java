/* Branje javnega API-ja Stupa Events (sistem NTZS).

   API je javen - edini pogoj je glava "tenant: ntzs". Tri pasti, ki jih je
   pokazal ze zgodovinski posnetek (uvoz-stupa/README.md):
   * streznik ne poslje charseta - odgovor se bere kot bajti UTF-8,
   * parameter offset pri tekmah ne deluje - tekme se preberejo z enim velikim
     limitom in preveri se, da jih je toliko, kolikor jih napove total_count,
   * per_page pri kategorijah je najvec 100.
   Odgovori se zapisejo nespremenjeni: posnetek je arhiv, preslikava pa bere
   samo arhiv (PosnetekDogodka). */
package si.turnirko.uvoz.stupa;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class StupaOdjemalec {

    private static final Duration CAKANJE = Duration.ofSeconds(120);

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${turnirko.stupa.naslov:https://testbackend.stupaevents.com}")
    private String naslov;

    @Value("${turnirko.stupa.tenant:ntzs}")
    private String tenant;

    /* Sezone (vrstice s season_name, id). */
    public List<JsonNode> sezone() throws IOException {
        return PosnetekDogodka.seznam(json.readTree(prenesi("/ott/v1/list_seasons?page=1&limit=100")).path("seasons"));
    }

    /* Vsi dogodki (turnirji in lige). */
    public List<JsonNode> dogodki() throws IOException {
        JsonNode odgovor = json.readTree(prenesi("/ott/v1/get_events?per_page=500&page_num=1"));
        List<JsonNode> dogodki = PosnetekDogodka.seznam(odgovor.path("data"));
        if (odgovor.path("total").isInt() && odgovor.path("total").asInt() != dogodki.size()) {
            throw new IOException("Stupa je vrnila " + dogodki.size() + " od " + odgovor.path("total").asInt() + " dogodkov.");
        }
        return dogodki;
    }

    /* Posnetek enega dogodka v mapo (glej PosnetekDogodka za postavitev). */
    public void posnemiDogodek(JsonNode vrstica, JsonNode sezona, Path mapa) throws IOException {
        long id = vrstica.path("id").asLong();
        Files.createDirectories(mapa);
        Files.writeString(mapa.resolve("dogodek.json"), json.writeValueAsString(vrstica), StandardCharsets.UTF_8);
        Files.writeString(mapa.resolve("sezona.json"), sezona == null ? "{}" : json.writeValueAsString(sezona),
                StandardCharsets.UTF_8);

        byte[] kategorije = prenesi("/ott/v1/get_events_categories?event_id=" + id + "&per_page=100");
        if (json.readTree(kategorije).path("data").size() >= 100) {
            throw new IOException("Dogodek " + id + " ima vsaj 100 kategorij - posnetek bi bil nepopoln.");
        }
        Files.write(mapa.resolve("kategorije.json"), kategorije);
        Files.write(mapa.resolve("stopnje.json"), prenesi("/ott/v1/get_stages?event_id=" + id));
        // z uradno razvrstitvijo skupin (group_rank, group_points) za uskladitev
        Files.write(mapa.resolve("skupine.json"), prenesi("/ott/v1/get_groups?event_id=" + id + "&load_participants=true"));
        Files.write(mapa.resolve("kola.json"), prenesi("/ott/v1/get_rounds?event_id=" + id));

        byte[] udelezenci = prenesi("/ott/v1/get_event_participants?event_id=" + id + "&per_page=5000");
        JsonNode u = json.readTree(udelezenci).path("data");
        if (u.path("count").isInt() && u.path("count").asInt() != u.path("participants").size()) {
            throw new IOException("Stupa je vrnila " + u.path("participants").size() + " od "
                    + u.path("count").asInt() + " udelezencev dogodka " + id + ".");
        }
        Files.write(mapa.resolve("udelezenci.json"), udelezenci);

        byte[] tekme = prenesi("/ott/v1/matches?event_id=" + id
                + "&limit=10000&load_sub_matches=true&load_walkover_reason=true&include_summary=true");
        JsonNode t = json.readTree(tekme);
        if (t.path("total_count").isInt() && t.path("total_count").asInt() != t.path("matches").size()) {
            throw new IOException("Stupa je vrnila " + t.path("matches").size() + " od "
                    + t.path("total_count").asInt() + " tekem dogodka " + id + ".");
        }
        Files.write(mapa.resolve("tekme.json"), tekme);
    }

    private byte[] prenesi(String pot) throws IOException {
        HttpRequest zahteva = HttpRequest.newBuilder(URI.create(naslov + pot))
                .header("tenant", tenant)
                .header("Accept", "application/json")
                .timeout(CAKANJE)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> odgovor = http.send(zahteva, HttpResponse.BodyHandlers.ofByteArray());
            if (odgovor.statusCode() != 200) {
                throw new IOException("Stupa je na " + pot + " vrnila status " + odgovor.statusCode() + ".");
            }
            return odgovor.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Prenos iz Stupe je bil prekinjen.", e);
        }
    }
}
