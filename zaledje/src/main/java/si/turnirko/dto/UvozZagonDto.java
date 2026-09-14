/* En zagon uvoza iz dnevnika (uvoz_zagon). povzetek je JSON s stevci,
   napakami, opozorili in izidi preverb - dnevnik ga pokaze, ne da bi bilo
   treba predogled delati znova. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.UvozZagon;

public record UvozZagonDto(
        long id,
        String zunanjiId,
        String ime,
        String zgostitev,
        String izvedel,
        LocalDateTime zacetekOb,
        LocalDateTime konecOb,
        UvozZagon.Izid izid,
        String povzetek
) {
    public static UvozZagonDto iz(UvozZagon z) {
        return new UvozZagonDto(z.getId(), z.getZunanjiId(), z.getIme(), z.getZgostitev(), z.getIzvedel(),
                z.getZacetekOb(), z.getKonecOb(), z.getIzid(), z.getPovzetek());
    }
}
