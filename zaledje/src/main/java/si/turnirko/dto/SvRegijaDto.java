/* Pregled sistema SV_REGIJA: Skupine (v kodi nivoji), njihove skupine in zrebi.

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
        /* Zrebi po nivojih, kot jih je vpisal organizator (null = samodejno). */
        List<Integer> steviloZrebov,
        List<Integer> velikostZrebov
) {

    /* Nivo (Skupina 1, 2 ...): koliko igralcev, katera mesta zajame in kakšne
       so njegove skupine ter zrebi. velikostiSkupin je po zrebu dejanska, pred
       njim predlagana (vecje skupine najprej). */
    public record NivoDto(
            int nivo,
            int velikost,
            int odMesta,
            int doMesta,
            List<Integer> velikostiSkupin,
            /* Velikost mreze ravni, na kateri se zrebi nivoja zacnejo (8 =
               cetrtfinale); zadnji zreb je lahko manjsi. */
            int velikostZreba,
            /* Stevilo zrebov, ki ga je vpisal organizator; null = samodejno. */
            Integer vpisanoZrebov,
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
            List<Long> razpored,
            /* Od kod pridejo udelezenci, po vrsti: "A1", "B1", ... "C2" (crka
               skupine in mesto v njej). Po zrebu skupin dejanski, pred njim po
               predlaganih velikostih skupin. */
            List<String> viri
    ) {}
}
