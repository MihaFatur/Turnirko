/* Vnos (ustvarjanje/urejanje) lige - pravila tekmovanja.

   Prehodov (visja/nizja liga, napreduje, izpade) tu namenoma NI: pravila so
   zaklenjena, ko liga ni vec v pripravi, mesto lige v piramidi pa se sme
   popraviti kadar koli (na razpored ne vpliva). Zanje je PrehodiVnos in svoja
   koncna tocka. */
package si.turnirko.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.PredlogaLige;
import si.turnirko.modeli.SpolKategorija;

public record LigaVnos(
        @NotBlank(message = "ime lige je obvezno")
        @Size(min = 3, max = 80, message = "ime lige mora imeti od 3 do 80 znakov") String ime,
        String sezona,
        @NotNull(message = "spolna kategorija je obvezna") SpolKategorija spolKategorija,
        @NotNull(message = "format srecanja je obvezen") FormatSrecanja formatSrecanja,
        @NotNull(message = "stevilo nizov je obvezno") Integer steviloNizov,
        // null = odigrajo se vse tekme srecanja; sicer prvi do N zmag
        Integer zmagZaSrecanje,
        Boolean dvokrozno,
        Integer tockeZmaga,
        Integer tockeNeodloceno,
        Integer tockePoraz,
        Boolean dovoljenoNeodloceno,
        Boolean prepovedDvojneRegistracije,
        Boolean stejeVElo,
        // ekipe imajo jakostni vrstni red in zreb jih razdeli v pare
        // (zgornja polovica s spodnjo); null = privzeto izklopljeno
        Boolean enakomernaRazvrstitev,
        // predloga uradnega ekipnega zapisnika; null = privzeto (SNTL_23)
        PredlogaLige predlogaListka,
        // seme terminov: kdaj se igra prvo kolo (ura velja za celo kolo,
        // 00:00 = ura ni dolocena) in na koliko dni sledijo naslednja.
        // null = terminov ni; datumi kol se izracunajo sele ob generiranju
        // razporeda, ko je znano, koliko kol liga sploh ima
        LocalDateTime zacetekPrvegaKola,
        Integer razmikDni
) {}
