/* Vnos novega dogodka (tekmovanja) na turnirju. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Disciplina;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.SpolKategorija;

public record DogodekVnos(
        @NotBlank(message = "ime dogodka je obvezno")
        @Size(min = 3, max = 60, message = "ime dogodka mora imeti od 3 do 60 znakov") String ime,
        @NotNull(message = "spolna kategorija je obvezna") SpolKategorija spolKategorija,
        String starostnaKategorija,
        @NotNull(message = "stevilo nizov je obvezno") Integer privzetoSteviloNizov,
        Double prijavnina,
        LocalDate rokPrijave,
        // null pomeni privzeto (POSAMICNO); DVOJICE zahtevajo izlocilni sistem
        Disciplina disciplina,
        // null pomeni privzeto (IZLOCILNI); doloca ga organizator ob dogodku
        SistemTekmovanja sistemTekmovanja,
        // obvezni pri sistemu SKUPINE, sicer se ne upostevata
        // (razpon preveri TurnirjiStoritev, ker je pravilo domensko)
        Integer steviloSkupin,
        Integer velikostSkupine
) {}
