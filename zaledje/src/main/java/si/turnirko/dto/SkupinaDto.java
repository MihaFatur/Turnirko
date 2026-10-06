/* Skupina z izracunano lestvico - izpis za skupinski del tekmovanja.

   Stopnja loci predtekmovalne skupine (1) od skupin, ki nastanejo iz njihovih
   uvrstitev (finalne skupine za mesta, druga skupinska stopnja); ime jih
   poimenuje ("1.–4. mesto"), prvo mesto pa pove, katero koncno mesto odloca
   zmagovalec skupine. Nivo je tezavnostna skupina sistema SV_REGIJA (drugod 1). */
package si.turnirko.dto;

import java.util.List;

public record SkupinaDto(
        Long id,
        String oznaka,
        int stopnja,
        String ime,
        Integer prvoMesto,
        int nivo,
        List<VrsticaLestviceDto> lestvica
) {}
