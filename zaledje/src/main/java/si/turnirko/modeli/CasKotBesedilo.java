/* Pretvornik LocalDateTime <-> TEXT za stolpce, ki morajo nositi tudi URO.

   Zakaj sploh: gonilnik sqlite-jdbc pozna eno samo nastavitev oblike za datume
   (date_string_format) in ta v application.properties velja "yyyy-MM-dd" - z
   njo pise IN bere tako datume kot casovne zige. Zato vsak LocalDateTime, ki
   gre skozi gonilnik, ob shranjevanju izgubi uro (ustvarjen_ob je v bazi
   "2026-07-23", ne "2026-07-23 18:00"). Nastavitev "timestamp_string_format"
   iz application.properties gonilnik ne pozna in jo tiho spregleda.

   S pretvornikom postane stolpec za Hibernate navaden niz (setString/getString)
   in gonilnikova obdelava datumov ga sploh ne obravnava - ura ostane. Uporabimo
   ga tam, kjer je ura del pomena (termin kola), ne povsod: sprememba globalne
   oblike bi zahtevala prepis vseh datumskih stolpcev v ze obstojecih bazah.

   Zapis je ISO ("2026-10-04T18:00"), branje pa prenese tudi stare zapise brez
   ure ("2026-10-04") in s presledkom namesto T - te je pisal gonilnik, preden
   je stolpec dobil pretvornik. */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CasKotBesedilo implements AttributeConverter<LocalDateTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalDateTime vrednost) {
        return vrednost == null ? null : vrednost.toString();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String zapis) {
        if (zapis == null || zapis.isBlank()) {
            return null;
        }
        String t = zapis.trim().replace(' ', 'T');
        if (t.length() <= 10) {
            // star zapis brez ure: dan brez ure je polnoc
            return LocalDate.parse(t).atStartOfDay();
        }
        // odrezemo morebitne milisekunde ("...T18:00:00.000")
        return LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t);
    }
}
