/* Vrstni red kategorij v kolu (V78; docs/pravila/mize.md): id-ji dogodkov turnirjev s
   skupnimi mizami, kot jih je razvrstil organizator. Prazen seznam vrne privzeti red
   (po turnirju in nastanku kategorije). */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RedKategorijVnos(
        @NotNull(message = "Seznam kategorij je obvezen (lahko je prazen).")
        @Size(max = 200)
        List<@NotNull Long> idjiDogodkov
) {}
