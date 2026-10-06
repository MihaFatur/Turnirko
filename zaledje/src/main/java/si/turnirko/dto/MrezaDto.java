/* Celotna slika dogodka: podatki o dogodku, prijave in vse tekme.
   Glede na sistem tekmovanja je zapolnjeno tudi:
   - skupine (s sprotno lestvico) pri sistemih SKUPINE_IZLOCILNI, SKUPINE in SV_REGIJA,
   - lestvica (skupna) pri kroznem sistemu KROZNI,
   - izbor (crta reza in predogled skupin) pri sistemu SKUPINE,
   - svRegija (nivoji, skupine in zrebi) pri sistemu SV_REGIJA.
   Pri izlocilnem sistemu so vsi prazni in prikaze se le mreza.

   Pri sistemu SKUPINE so prijave urejene po jakostnem vrstnem redu (tako, kot
   jih vidi in ureja administrator), sicer po priimku. */
package si.turnirko.dto;

import java.util.List;

public record MrezaDto(
        DogodekDto dogodek,
        List<PrijavaDto> prijave,
        List<TekmaDto> tekme,
        List<SkupinaDto> skupine,
        List<VrsticaLestviceDto> lestvica,
        IzborDto izbor,
        SvRegijaDto svRegija
) {}
