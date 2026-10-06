/* Koncne tocke sistema SV_REGIJA: nastavitve razreza, rocni vpis skupin in
   rocna razporeditev mest v zrebu nivoja.

   Branje (nivoji, skupine, zrebi in tekme) je v GET /dogodki/{id}. Predloga sta
   POST, ker vsak klic zreba znova - gost ju ne sme sprozati, rezultat pa ni
   podatek, ki bi ga kdo bral. */
package si.turnirko.kontrolerji;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import si.turnirko.dto.DogodekDto;
import si.turnirko.dto.SvPredlogDto;
import si.turnirko.dto.SvVnos;
import si.turnirko.dto.SvZrebPredlogDto;
import si.turnirko.storitve.SvRegijaStoritev;
import si.turnirko.storitve.ZrebStoritev;

@RestController
@RequestMapping("/api/v1/dogodki")
public class SvRegijaKontroler {

    private final SvRegijaStoritev svRegija;
    private final ZrebStoritev zrebStoritev;

    public SvRegijaKontroler(SvRegijaStoritev svRegija, ZrebStoritev zrebStoritev) {
        this.svRegija = svRegija;
        this.zrebStoritev = zrebStoritev;
    }

    /* Nastavitve razreza: stevilo nivojev, rocne velikosti nivojev, skupin na
       nivo, velikost skupine. Samo v pripravi. */
    @PutMapping("/{id}/sv/nastavitve")
    public DogodekDto nastavitve(@PathVariable Long id, @RequestBody SvVnos.Nastavitve vnos) {
        return DogodekDto.iz(svRegija.nastavi(id, vnos));
    }

    /* Predlog skupin brez zapisa: isti zreb, kot bi ga izvedel dogodek. */
    @PostMapping("/{id}/sv/predlog")
    public SvPredlogDto predlog(@PathVariable Long id) {
        return svRegija.predlogSkupin(id);
    }

    /* Rocni vpis skupin namesto nakljucnega zreba; velja tudi kot popravek
       skupin po zrebu, dokler se nobena tekma ni zacela. */
    @PutMapping("/{id}/sv/skupine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skupine(@PathVariable Long id, @RequestBody SvVnos.Skupine vnos) {
        zrebStoritev.izvediRocniZrebSv(id, vnos);
    }

    /* Razveljavi zreb skupin: dogodek se vrne v pripravo. */
    @PostMapping("/{id}/sv/razveljavi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void razveljavi(@PathVariable Long id) {
        svRegija.razveljaviZrebSkupin(id);
    }

    /* Predlog razporeditve v mrezi zreba brez zapisa. */
    @PostMapping("/sv/zrebi/{idZreba}/predlog")
    public SvZrebPredlogDto predlogZreba(@PathVariable Long idZreba) {
        return svRegija.predlogZreba(idZreba);
    }

    /* Rocna razporeditev mest v mrezi zreba (dokler se zreb ni zacel). */
    @PutMapping("/sv/zrebi/{idZreba}/mesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mesta(@PathVariable Long idZreba, @RequestBody SvVnos.Mesta vnos) {
        svRegija.nastaviMesta(idZreba, vnos.mesta());
    }

    /* Ponovno nakljucno razporedi zreb (dokler se ni zacet). */
    @PostMapping("/sv/zrebi/{idZreba}/znova")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void znova(@PathVariable Long idZreba) {
        svRegija.znovaZrebaj(idZreba);
    }
}
