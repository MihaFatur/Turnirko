/* Koncnica lige: vsi krogi s serijami in njihovimi tekmami. Javno kot vsak
   GET - gledalec na strani lige vidi, kdo igra s kom in kako stoji serija. */
package si.turnirko.dto;

import java.util.List;

public record KoncnicaDto(
        // koliko ekip igra koncnico in koliko zmag potrebuje serija
        int ekip,
        int zmagZaSerijo,
        // ali je redni del koncan in koncnica se ni ustvarjena - vmesnik po
        // tem ponudi urejevalcu "Ustvari koncnico"
        boolean pripravljenaZaZacetek,
        List<Serija> serije
) {

    public record Stran(Long idEkipa, String ekipa, Integer mesto, int zmage) {}

    public record Serija(
            Long id,
            int krog,
            int par,
            // "finale", "polfinale", "cetrtfinale"
            String imeKroga,
            Stran stran1,
            Stran stran2,
            Long idZmagovalec,
            List<SrecanjeDto> tekme
    ) {}
}
