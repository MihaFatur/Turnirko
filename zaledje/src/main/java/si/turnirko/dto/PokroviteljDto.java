/* Pokrovitelj lige ali turnirja (V56): javno - trak na strani in noga tiska. */
package si.turnirko.dto;

import si.turnirko.modeli.Pokrovitelj;

public record PokroviteljDto(
        String ime,
        // Spletna stran pokrovitelja (http/https); null, ce je ni.
        String povezava,
        // Glavni pokrovitelj stoji prvi in vecji (najvec eden).
        boolean glavni,
        SlikaDto slika
) {

    public static PokroviteljDto iz(Pokrovitelj p) {
        return new PokroviteljDto(p.getIme(), p.getPovezava(), p.isGlavni(), SlikaDto.iz(p.getSlika()));
    }
}
