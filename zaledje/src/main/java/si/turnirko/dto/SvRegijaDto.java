/* Pregled sistema SV_REGIJA: nivoji, njihove skupine in zrebi.

   Pred zrebom skupin je to PREDOGLED razreza (kako bi se igralci razdelili po
   trenutnem stevilu prijav in nastavitvah), po zrebu pa stanje iz baze. Razrez
   racuna streznik, da vmesnik ne podvaja domenskih pravil (koliko nivojev,
   kako velike skupine, kateri zreb odloca katera mesta). */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.FazaTekme;

public record SvRegijaDto(
        List<NivoDto> nivoji,
        /* Zakaj zreb (se) ni mogoc; null pomeni, da je vse pripravljeno. */
        String zadrzek,
        /* Skupine so ze izzrebane (v bazi obstajajo skupine in tekme). */
        boolean skupineZrebane,
        /* Zreb skupin je mogoce se razveljaviti oz. ponoviti: nobena tekma ni
           zacetek ali konec. */
        boolean skupineUredljive,
        /* Veljavni nastavitvi (z upostevanimi privzetki): skupin na poln nivo
           in igralcev v skupini. */
        int skupinNaNivo,
        int velikostSkupine,
        /* Nastavitve, kot jih je vpisal organizator (prazno = samodejno). */
        Integer steviloNivojev,
        List<Integer> velikostiNivojev,
        List<Integer> rangovVZreb
) {

    /* Nivo (tezavnostna skupina): koliko igralcev, katera mesta zajame in
       kakšne so njegove skupine ter zrebi. velikostiSkupin je po zrebu
       dejanska, pred njim predlagana (vecje skupine najprej). */
    public record NivoDto(
            int nivo,
            int velikost,
            int odMesta,
            int doMesta,
            List<Integer> velikostiSkupin,
            /* Koliko rangov iz vsake skupine pride v en zreb (1 ali 2). */
            int rangovVZreb,
            List<ZrebDto> zrebi
    ) {}

    /* En zreb nivoja. id je prazen pred zrebom skupin; uredljiv pomeni, da so
       tekme zgrajene in nobena se ni zacela, torej se razpored mest se sme
       spremeniti ali ponovno izzrebati. */
    public record ZrebDto(
            Long id,
            int indeks,
            FazaTekme faza,
            String ime,
            int prvoMesto,
            int zadnjeMesto,
            int stUdelezencev,
            int velikostMreze,
            boolean zgrajen,
            boolean rocni,
            boolean uredljiv,
            /* Prijave po mestih v mrezi od vrha navzdol (null = prosto mesto);
               prazen seznam, dokler zreb ni zgrajen. */
            List<Long> razpored
    ) {}
}
