/* Vnos postave srecanja: igralci na mestih obeh strani in oznaka para za
   dvojice. Streznik preveri, da so mesta skladna s formatom, igralci iz
   kadra in par pravilne velikosti. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.StranEkipe;

public record PostavaVnos(
        @NotNull List<MestoVnos> mesta
) {

    public record MestoVnos(
            @NotNull(message = "stran je obvezna") StranEkipe stran,
            @NotBlank(message = "pozicija je obvezna") String pozicija,
            @NotNull(message = "igralec je obvezen") Long idIgralec,
            boolean vDvojici
    ) {}
}
