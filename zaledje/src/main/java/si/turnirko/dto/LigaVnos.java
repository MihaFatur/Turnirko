/* Vnos (ustvarjanje/urejanje) lige - pravila tekmovanja.

   Prehodov (visja/nizja liga, napreduje, izpade) tu namenoma NI: pravila so
   zaklenjena, ko liga ni vec v pripravi, mesto lige v piramidi pa se sme
   popraviti kadar koli (na razpored ne vpliva). Zanje je PrehodiVnos in svoja
   koncna tocka. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import si.turnirko.modeli.FormatSrecanja;
import si.turnirko.modeli.PredlogaLige;
import si.turnirko.modeli.RavenTekmovanja;
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
        // raven tekmovanja (teza v Turnirko ratingu); null = privzeto KLUBSKO
        RavenTekmovanja raven,
        // ekipe imajo jakostni vrstni red in zreb jih razdeli v pare
        // (zgornja polovica s spodnjo); null = privzeto izklopljeno
        Boolean enakomernaRazvrstitev,
        // predloga uradnega ekipnega zapisnika; null = privzeto (SNTL_23)
        PredlogaLige predlogaListka,
        // seme terminov: kdaj se igra prvo kolo (ura prvega srecanja kola,
        // 00:00 = ura ni dolocena) in na koliko dni sledijo naslednja.
        // null = terminov ni; datumi kol se izracunajo sele ob generiranju
        // razporeda, ko je znano, koliko kol liga sploh ima
        LocalDateTime zacetekPrvegaKola,
        Integer razmikDni,
        // koncnica po rednem delu: koliko najboljsih ekip (2, 4, 8) in koliko
        // zmag za serijo; oboje null = liga brez koncnice
        Integer koncnicaEkip,
        Integer koncnicaZmag,
        // ure srecanj v kolu (V31): kolo je vecer z ure.size() srecanji
        // zapored in ekipa v njem sme igrati veckrat; null = kolo kroznega
        // sistema (vsaka ekipa enkrat, vsa srecanja ob uri iz semena)
        List<LocalTime> ureSrecanj
) {

    /* Vnos lige brez koncnice (kot pred V28). */
    public LigaVnos(String ime, String sezona, SpolKategorija spolKategorija,
                    FormatSrecanja formatSrecanja, Integer steviloNizov, Integer zmagZaSrecanje,
                    Boolean dvokrozno, Integer tockeZmaga, Integer tockeNeodloceno,
                    Integer tockePoraz, Boolean dovoljenoNeodloceno,
                    Boolean prepovedDvojneRegistracije, RavenTekmovanja raven,
                    Boolean enakomernaRazvrstitev, PredlogaLige predlogaListka,
                    LocalDateTime zacetekPrvegaKola, Integer razmikDni) {
        this(ime, sezona, spolKategorija, formatSrecanja, steviloNizov, zmagZaSrecanje,
                dvokrozno, tockeZmaga, tockeNeodloceno, tockePoraz, dovoljenoNeodloceno,
                prepovedDvojneRegistracije, raven, enakomernaRazvrstitev, predlogaListka,
                zacetekPrvegaKola, razmikDni, null, null, null);
    }

    /* Vnos lige s koncnico in s kolom kroznega sistema (kot pred V31). */
    public LigaVnos(String ime, String sezona, SpolKategorija spolKategorija,
                    FormatSrecanja formatSrecanja, Integer steviloNizov, Integer zmagZaSrecanje,
                    Boolean dvokrozno, Integer tockeZmaga, Integer tockeNeodloceno,
                    Integer tockePoraz, Boolean dovoljenoNeodloceno,
                    Boolean prepovedDvojneRegistracije, RavenTekmovanja raven,
                    Boolean enakomernaRazvrstitev, PredlogaLige predlogaListka,
                    LocalDateTime zacetekPrvegaKola, Integer razmikDni,
                    Integer koncnicaEkip, Integer koncnicaZmag) {
        this(ime, sezona, spolKategorija, formatSrecanja, steviloNizov, zmagZaSrecanje,
                dvokrozno, tockeZmaga, tockeNeodloceno, tockePoraz, dovoljenoNeodloceno,
                prepovedDvojneRegistracije, raven, enakomernaRazvrstitev, predlogaListka,
                zacetekPrvegaKola, razmikDni, koncnicaEkip, koncnicaZmag, null);
    }
}
