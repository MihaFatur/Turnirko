/* Nastavitev zavihka »Zanimivosti« za organizatorja: kateri razdelki na tem
   tekmovanju sploh pridejo v poštev (razdelki) in kateri so gledalcem skriti
   (skrite). Branje je javno, ker v tem ni nobenih osebnih podatkov - le imena
   razdelkov. */
package si.turnirko.dto;

import java.util.List;

import si.turnirko.modeli.PostavkaZanimivosti;

public record ZanimivostiNastavitveDto(
        List<PostavkaZanimivosti> razdelki,
        List<PostavkaZanimivosti> skrite
) {}
