/* Podroben pogled srecanja: povzetek, tekmovanje, ki mu pripada (liga ali
   ekipni dogodek turnirja), format (mesta in dvojice za urejanje postave),
   trenutne postave, posamicne tekme (zapisnik) in kadra obeh ekip za izbiro
   postave. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.VirTekmovanja;

public record SrecanjePodrobnoDto(
        SrecanjeDto srecanje,
        Kontekst kontekst,
        FormatSrecanja format,
        List<String> pozicijeDomaci,
        List<String> pozicijeGost,
        boolean izbiraDvojice,
        int stVDvojici,
        List<PostavaSrecanjaDto> postave,
        List<TekmaSrecanjaDto> tekme,
        List<KaderIgralecDto> kaderDomaci,
        List<KaderIgralecDto> kaderGost
) {

    /* Kje srecanje stoji: ime tekmovanja (liga oz. turnir), dogodek pri
       turnirju, opis mesta ("3. kolo", "koncnica · polfinale · 2. tekma",
       "skupina A") in vir - uvozeno srecanje je samo za branje, zato vmesnik
       urejanja ne ponudi. Opis sestavi streznik, da ga vmesnik ne ugiba. */
    public record Kontekst(String tekmovanje, String sezona, String dogodek, String opis,
                           VirTekmovanja vir) {}
}
