/* Izid uvoza dogodka iz Stupe. */
package si.turnirko.dto;

import si.turnirko.modeli.UvozZagon;

public record IzidUvozaDto(
        long idZagona,
        UvozZagon.Izid izid,
        Long idTurnir,
        Long idLiga,
        /* koliko tekem je preracun ratinga odigral znova (null, ce ga ni bilo) */
        Integer preracunanihTekem,
        /* nepricakovana napaka (izid NAPAKA) */
        String napaka,
        PorociloUvozaDto porocilo
) {}
