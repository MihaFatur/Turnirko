/* Nadgradnja ze prijavljenega racuna na placljiv paket (POST /api/v1/placila/nadgradnja). */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.CiklusPlacila;
import si.turnirko.modeli.Paket;

public record NadgradnjaVnos(
        @NotNull(message = "paket je obvezen") Paket paket,
        CiklusPlacila ciklus
) {}
