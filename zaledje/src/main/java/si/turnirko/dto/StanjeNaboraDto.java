/* Zlati nabor in zadnja meritev bralnika nad njim (F1.5, samo admin).
   Meritev tece v ozadju (minute); vmesnik sprasuje, dokler tece. */
package si.turnirko.dto;

import java.time.LocalDateTime;

public record StanjeNaboraDto(
        long listkov,
        int najvec,
        boolean tece,
        int obdelanih,
        int vseh,
        LocalDateTime zacetek,
        LocalDateTime konec,
        /* mreza, ki je merila */
        String mreza,
        /* null, dokler ni koncane meritve */
        MeritevBranjaDto.Skupina rezultat,
        /* razlog, ce zadnja meritev ni uspela */
        String napaka,
        /* isto samo za listke, katerih stevke niso v ucenju (prava neznana pisava) */
        MeritevBranjaDto.Skupina preizkusni,
        /* in za listke, na katerih se je mreza ucila */
        MeritevBranjaDto.Skupina ucni,
        /* diagnostika (vzroki napak, stevke, umerjenost, galerija); DiagnozaBranja */
        DiagnozaNaboraDto diagnoza
) {}
