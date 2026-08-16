/* Branje posnetka podatkov iz Stupa Events.

   Uvoznik NE klice spletnega API-ja - bere samo posnetek na disku
   (mapa "surovo", ki jo naredi skripta uvoz-stupa/posnetek.ps1). Tako je
   uvoz ponovljiv in ni odvisen od tujega streznika, ki je poleg tega testni.

   Delamo z drevesom JsonNode in ne z desetinami DTO razredov: shema Stupe ima
   na tekmo cez sto polj, od katerih jih potrebujemo priblizno petnajst, in
   enkratni uvoznik ne sme s sabo prinesti tolikega bremena. */
package si.turnirko.uvoz;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

public class StupaArhiv {

    private final Path koren;
    private final ObjectMapper mapper = new ObjectMapper();

    public StupaArhiv(Path koren) {
        this.koren = koren;
        if (!Files.isDirectory(koren)) {
            throw new IllegalArgumentException("Mapa s posnetkom ne obstaja: " + koren);
        }
    }

    /* Vsi dogodki iz seznama (turnirji in lige skupaj). */
    public List<JsonNode> dogodki() {
        return seznam(beri(koren.resolve("dogodki.json")).path("data"));
    }

    public List<JsonNode> sezone() {
        return seznam(beri(koren.resolve("sezone.json")).path("seasons"));
    }

    public List<JsonNode> kategorije(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "kategorije.json").path("data"));
    }

    public List<JsonNode> stopnje(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "stopnje.json").path("data"));
    }

    public List<JsonNode> skupine(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "skupine.json").path("data"));
    }

    public List<JsonNode> kola(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "kola.json").path("data"));
    }

    public List<JsonNode> udelezenci(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "udelezenci.json").path("data").path("participants"));
    }

    /* Vse tekme dogodka. V posnetku so v eni sami datoteki, ker parameter
       offset na koncnem naslovu /ott/v1/matches ne deluje (druga stran vrne
       prazen seznam) - posnetek jih zato potegne z enim velikim limitom. */
    public List<JsonNode> tekme(long idDogodka) {
        return seznam(beriDogodka(idDogodka, "tekme.json").path("matches"));
    }

    private JsonNode beriDogodka(long idDogodka, String datoteka) {
        return beri(koren.resolve("dogodki").resolve(String.valueOf(idDogodka)).resolve(datoteka));
    }

    private JsonNode beri(Path pot) {
        if (!Files.isRegularFile(pot)) {
            return MissingNode.getInstance();
        }
        try {
            String vsebina = Files.readString(pot);
            // PowerShellova skripta za posnetek zapise UTF-8 z BOM; Jackson ga
            // ne prenese, zato ga odstranimo tu in ne zahtevamo novega prenosa
            if (!vsebina.isEmpty() && vsebina.charAt(0) == '﻿') {
                vsebina = vsebina.substring(1);
            }
            return mapper.readTree(vsebina);
        } catch (IOException e) {
            throw new UncheckedIOException("Ne morem prebrati " + pot, e);
        }
    }

    private static List<JsonNode> seznam(JsonNode vozlisce) {
        if (vozlisce == null || !vozlisce.isArray()) {
            return List.of();
        }
        List<JsonNode> r = new ArrayList<>(vozlisce.size());
        vozlisce.forEach(r::add);
        return r;
    }
}
