/* Podroben pogled srecanja: povzetek, format (mesta in dvojice za urejanje
   postave), trenutne postave, posamicne tekme (zapisnik) in kadra obeh ekip
   za izbiro postave. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.FormatSrecanja;

public record SrecanjePodrobnoDto(
        SrecanjeDto srecanje,
        FormatSrecanja format,
        List<String> pozicijeDomaci,
        List<String> pozicijeGost,
        boolean izbiraDvojice,
        int stVDvojici,
        List<PostavaSrecanjaDto> postave,
        List<TekmaSrecanjaDto> tekme,
        List<KaderIgralecDto> kaderDomaci,
        List<KaderIgralecDto> kaderGost
) {}
