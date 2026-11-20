/* Potrditev branja listka: izid, kot ga je organizator potrdil (morda popravil),
   in tekma, ce jo je izbral sam (sicer velja prepoznana).

   casPregledaMs in nacin sta sled pregleda za meritev (V69). Meri ju vmesnik;
   nesmiselen cas streznik zavrze (ne zapise), potrditve pa zaradi njega ne zavrne. */
package si.turnirko.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.NacinPotrditve;

public record PotrditevBranjaVnos(
        Long idTekme,
        @NotNull @Valid VnosRezultata izid,
        Long casPregledaMs,
        NacinPotrditve nacin
) {

    /* Potrditev brez sledi pregleda (starejsi vmesnik, testi). */
    public PotrditevBranjaVnos(Long idTekme, VnosRezultata izid) {
        this(idTekme, izid, null, null);
    }
}
