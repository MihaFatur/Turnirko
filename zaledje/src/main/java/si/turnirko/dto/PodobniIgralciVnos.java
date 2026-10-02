/* Vpis, za katerega obrazec vprasa, ali v bazi ze obstaja podoben igralec
   (POST /igralci/podobni in /igralci/podobni/podrobno).

   To je poizvedba in ne vpis, a gre po POST-u: datum rojstva je osebni
   podatek in ne sme v naslov zahteve (dnevnike streznikov in posrednikov).
   Omejitve so ohlapnejse od IgralecVnos: obrazec vprasa ze med
   izpolnjevanjem, zato prekratko ime ni napaka tega klica. */
package si.turnirko.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.Spol;

public record PodobniIgralciVnos(
        @NotBlank(message = "ime je obvezno")
        @Size(max = 100, message = "ime je predolgo") String ime,
        @NotBlank(message = "priimek je obvezen")
        @Size(max = 100, message = "priimek je predolg") String priimek,
        // neobvezen: kratka oblika imena (Miha / Mihael) se brez njega ne zozi
        Spol spol,
        // neobvezen; javni izpis ga NE uporablja (glej IgralciStoritev.najdiPodobne)
        @Past(message = "datum rojstva mora biti v preteklosti") LocalDate datumRojstva
) {}
