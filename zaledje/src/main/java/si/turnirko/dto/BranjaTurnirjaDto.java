/* Vrsta branj listkov enega turnirja in merilo natancnosti bralnika:
   koliko potrjenih listkov je organizator potrdil brez popravka. */
package si.turnirko.dto;

import java.util.List;

public record BranjaTurnirjaDto(
        List<BranjeListkaDto> branja,
        int potrjenih,
        int brezPopravka,
        // stikalo »Sodniki slikajo listke« (S2): null = izklopljeno
        si.turnirko.modeli.SodniskaPotrditev sodnikiListki
) {}
