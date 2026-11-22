/* Odgovor na fotografijo, ki jo je poslal sodnik (S2): id branja in enkraten zeton,
   s katerim telefon bere stanje in potrdi izid. Zeton je samo v tem odgovoru - v
   bazi je SHA-256 - in ne sme v URL, dnevnik ali localStorage. */
package si.turnirko.dto;

public record SodnikPoslanoDto(Long idBranja, String zeton) {}
