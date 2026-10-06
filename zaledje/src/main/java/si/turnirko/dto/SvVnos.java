/* Vnosi sistema SV_REGIJA: nastavitve razreza, rocni vpis skupin in rocna
   razporeditev mest v zrebu. */
package si.turnirko.dto;

import java.util.List;

public final class SvVnos {

    private SvVnos() {}

    /* Nastavitve razreza (samo v pripravi). Vse so neobvezne: prazno pomeni
       samodejno (stevilo prijav doloci nivoje, skupine so 4 x 4). Ce so
       velikostiNivojev vpisane, veljajo namesto samodejne razdelitve.
       rangovVZreb: po nivojih 1 ali 2 (privzeto 2) - koliko rangov iz vsake
       skupine pride v en zreb. */
    public record Nastavitve(Integer steviloNivojev, List<Integer> velikostiNivojev,
                             Integer steviloSkupin, Integer velikostSkupine,
                             List<Integer> rangovVZreb) {

        public Nastavitve(Integer steviloNivojev, List<Integer> velikostiNivojev,
                          Integer steviloSkupin, Integer velikostSkupine) {
            this(steviloNivojev, velikostiNivojev, steviloSkupin, velikostSkupine, null);
        }
    }

    /* Rocni vpis skupin: nivoji po vrsti (prvi je najmocnejsi), vsak s
       skupinami, vsaka s prijavami po id-ju. Vsak prijavljeni mora nastopati
       natanko enkrat. */
    public record Skupine(List<Nivo> nivoji) {
        public record Nivo(List<List<Long>> skupine) {}
    }

    /* Rocna razporeditev mest v mrezi: id prijav po mestih od vrha navzdol, null
       za prosto mesto. Dolzina mora biti enaka velikosti mreze. */
    public record Mesta(List<Long> mesta) {}
}
