/* Izbran organizatorski paket pri zamenjavi (PUT /api/v1/narocnina/paket). */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

import si.turnirko.modeli.Paket;

public record NarocninaPaketVnos(@NotNull(message = "paket je obvezen") Paket paket) {}
