/* Kalkulator na javni strani z razlago ratinga: kaj bi zmaga oziroma poraz
   naredila dvema izmisljenima igralcema (rating, stevilo tekem, vrnitev).

   Isti obrazec kot obracun (TurnirkoRatingStoritev) in kot zasebna napoved na
   profilu (NapovedTekmeDto, zato iste vrstice za ravni) - le da igralca ne
   prideta iz baze, ampak iz polj, ki jih gledalec vpise sam. */
package si.turnirko.dto;

import java.util.List;

public record IzracunTekmeDto(
        // verjetnost zmage prvega igralca v odstotkih
        int pricakovanOdstotek,
        // ista verjetnost, nezaokrozena: iz nje stran izpise racun
        // (K x teza x (izid - verjetnost)) tako, kot ga je izracunal obracun
        double pricakovano,
        int k,
        int kNasprotnika,
        List<NapovedTekmeDto.Raven> ravni
) {}
