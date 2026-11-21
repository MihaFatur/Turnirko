/* Ena razlicica mreze stevk v zgodovini ucenja (F6.4, samo admin). Natancnosti so
   delezi 0-1; preizkusne stevke so stevke listkov in vadnice, ki niso bile v ucenju,
   zlati nabor so celi listki (celice), MNIST splosne oblike stevk. natancnost je
   null pri prekinjenem ucenju, zlati_* pri premajhnem naboru. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import si.turnirko.modeli.MrezaStevkZapis;

public record RazlicicaMrezeDto(
        Long id,
        LocalDateTime ustvarjenoOb,
        MrezaStevkZapis.Nacin nacin,
        int vzorcev,
        Integer preizkusnih,
        float natancnostPrej,
        Float natancnost,
        Float mnistPrej,
        Float mnist,
        Integer zlatih,
        Float zlatiPrej,
        Float zlati,
        Integer trajanjeMs,
        boolean sprejeta,
        boolean prekinjena,
        String razlog,
        LocalDateTime umaknjenaOb
) {}
