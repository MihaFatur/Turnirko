/* Osebe Stupe, kot jih poznajo VSI posnetki zgodovinskega uvoza skupaj.

   Stupa podatke osebe hrani pri vsaki prijavi posebej: isti igralec (isti
   user_role_id) je v enem dogodku brez datuma rojstva, v drugem z njim, ime
   pa organizatorji sproti popravljajo ("Ecsy" na U-13 ekipnem, "Samuel Ecsy"
   dva meseca pozneje). Preslikava enega dogodka tega ne vidi in bi osebo z
   manjkajocimi podatki izpustila (njene tekme bi postale brez boja), osebo z
   imenom iz ene besede pa zavrnila.

   Zbirnik zato pregleda prijave in tekme vseh dogodkov PO DATUMU in za vsako
   osebo obdrzi najnovejse znane podatke: datum rojstva, spol, licenco in
   drzavo iz zadnjega dogodka, ki jih ima, ime pa iz zadnjega dogodka, v
   katerem ima ime IN priimek. IdentitetaStupe z njimi le DOPOLNI osebo -
   podatek, ki ga dogodek ima, ostane. */
package si.turnirko.uvoz.stupa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ZnaneOsebeStupe {

    /* Mapa posnetka enega dogodka in datum dogodka (za vrstni red). */
    public record Posnetek(Path mapa, LocalDate datum) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private ZnaneOsebeStupe() {}

    public static Map<Long, IdentitetaStupe.Oseba> zberi(List<Posnetek> posnetki) {
        List<Posnetek> poVrsti = new ArrayList<>(posnetki);
        poVrsti.sort(Comparator.comparing(Posnetek::datum, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<Long, IdentitetaStupe.Oseba> znane = new HashMap<>();
        for (Posnetek p : poVrsti) {
            for (String datoteka : List.of("udelezenci.json", "tekme.json")) {
                JsonNode vsebina = beri(p.mapa().resolve(datoteka));
                if (vsebina != null) {
                    obisci(vsebina, znane);
                }
            }
        }
        return znane;
    }

    /* Zapis osebe je vsak predmet z user_role_id, imenom in meta_data (zapisi
       starsev - klubov in regij - imena osebe nimajo). */
    private static void obisci(JsonNode vozel, Map<Long, IdentitetaStupe.Oseba> znane) {
        if (vozel.isArray()) {
            vozel.forEach(v -> obisci(v, znane));
            return;
        }
        if (!vozel.isObject()) {
            return;
        }
        if (vozel.has("user_role_id") && vozel.has("name") && vozel.has("meta_data")) {
            IdentitetaStupe.Oseba o = IdentitetaStupe.oseba(vozel);
            if (o.id() != 0) {
                znane.merge(o.id(), o, ZnaneOsebeStupe::novejsa);
            }
        }
        vozel.forEach(v -> obisci(v, znane));
    }

    /* Novejsi zapis povozi starejsega, a le s podatki, ki jih ima. */
    private static IdentitetaStupe.Oseba novejsa(IdentitetaStupe.Oseba stara, IdentitetaStupe.Oseba nova) {
        String ime = nova.polnoIme() != null
                && (IdentitetaStupe.besed(nova.polnoIme()) >= 2 || stara.polnoIme() == null)
                ? nova.polnoIme() : stara.polnoIme();
        return new IdentitetaStupe.Oseba(stara.id(), ime,
                nova.rojstvo() != null ? nova.rojstvo() : stara.rojstvo(),
                nova.spol() != null ? nova.spol() : stara.spol(),
                nova.licenca() != null ? nova.licenca() : stara.licenca(),
                nova.drzava() != null ? nova.drzava() : stara.drzava(),
                nova.klub() != null ? nova.klub() : stara.klub());
    }

    private static JsonNode beri(Path pot) {
        if (!Files.isRegularFile(pot)) {
            return null;
        }
        try {
            String vsebina = Files.readString(pot);
            if (!vsebina.isEmpty() && vsebina.charAt(0) == '﻿') {
                vsebina = vsebina.substring(1);
            }
            return JSON.readTree(vsebina);
        } catch (IOException e) {
            return null;
        }
    }
}
