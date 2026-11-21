/* Lega celic na poravnanem listku (F4.1): vmesnik iz poravnane slike
   (GET /api/v1/branja-listkov/{id}/poravnana) izreze vsako celico ob njenem
   polju. Koordinate v px poravnane slike (sirina x visina), vkljucno z robom
   celice; vrstica = [levo x0, y0, x1, y1, desno x0, y0, x1, y1] v stolpcih
   listka (levi stolpec ni nujno udelezenec 1, glej BranjeListkaDto.obrnjeno).
   Samo stevila, brez imen. */
package si.turnirko.dto;

import java.util.List;

public record CeliceListkaDto(int sirina, int visina, List<int[]> nizi, int[] rezultat) {}
