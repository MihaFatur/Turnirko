/* Opis slike (V54): javni naslov je /api/v1/slike/{zgostitev}, meri sta meri
   razlicice TISK (iz njiju vmesnik ve razmerje stranic, preden se slika nalozi).
   Nosita ga LigaDto in TurnirDto (logotip) in knjiznica logotipov. */
package si.turnirko.dto;

import si.turnirko.modeli.Slika;

public record SlikaDto(Long id, String zgostitev, int sirina, int visina) {

    public static SlikaDto iz(Slika slika) {
        return slika == null
                ? null
                : new SlikaDto(slika.getId(), slika.getZgostitev(), slika.getSirina(), slika.getVisina());
    }
}
