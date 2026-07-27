/* Skupina z izracunano lestvico - izpis za skupinski del tekmovanja. */
package si.turnirko.dto;

import java.util.List;

public record SkupinaDto(
        Long id,
        String oznaka,
        List<VrsticaLestviceDto> lestvica
) {}
