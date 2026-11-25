/* List vadnice stevk v seznamu (brez slike in stevk). */
package si.turnirko.dto;

import java.time.LocalDateTime;

public record VadnicaDto(
        Long id,
        int stevk,
        int praznih,
        int preskocenih,
        boolean odobrena,
        LocalDateTime ustvarjenoOb,
        LocalDateTime odobrenoOb,
        /* Razlicica postavitve lista (GeometrijaVadnice): pregled razvrsti stevke po
           vrsticah te razlicice. */
        int razlicica) {

    /* Seznam listov in koliko stevk vadnice je odobrenih (gredo v ucenje). */
    public record Seznam(java.util.List<VadnicaDto> listi, long stevkVUcenju) {}

    /* Stevka za pregled: okence lista, prava vrednost, sivine 28 x 28 (base64,
       784 bajtov, 0 = papir) in ali jo mreza ze zdaj prebere prav. */
    public record Stevka(Long id, int okence, int vrednost, String slika, boolean mrezaPrav) {}

    /* List s stevkami za pregled pred odobritvijo. */
    public record Podrobno(VadnicaDto list, java.util.List<Stevka> stevke) {}
}
