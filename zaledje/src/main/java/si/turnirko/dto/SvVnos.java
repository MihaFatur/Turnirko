/* Vnosi sistema SV_REGIJA: nastavitve razreza, rocni vpis skupin in rocna
   razporeditev mest v zrebu. */
package si.turnirko.dto;

import java.util.List;

public final class SvVnos {

    private SvVnos() {}

    /* Nastavitve razreza (samo v pripravi). Vse so neobvezne: prazno pomeni
       samodejno (stevilo prijav doloci nivoje, skupine so 4 x 4). Ce so
       velikostiNivojev vpisane, veljajo namesto samodejne razdelitve.
       Zrebi po nivojih (null na mestu nivoja = samodejno): steviloZrebov je
       stevilo izlocilnih zrebov nivoja, velikostZrebov velikost mreze ravni, na
       kateri se zacnejo (2, 4, 8, 16, 32; privzeto 8 = cetrtfinale). */
    public record Nastavitve(Integer steviloNivojev, List<Integer> velikostiNivojev,
                             Integer steviloSkupin, Integer velikostSkupine,
                             List<Integer> steviloZrebov, List<Integer> velikostZrebov) {

        public Nastavitve(Integer steviloNivojev, List<Integer> velikostiNivojev,
                          Integer steviloSkupin, Integer velikostSkupine) {
            this(steviloNivojev, velikostiNivojev, steviloSkupin, velikostSkupine, null, null);
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
