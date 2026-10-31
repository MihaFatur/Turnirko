/* Povezava osebnega koledarja .ics (OsebniKoledarStoritev): zeton, iz katerega
   vmesnik sestavi naslov https://.../api/v1/koledar-ics/{zeton}.ics. Naslova ne
   sestavlja streznik: vmesnik pozna naslov, na katerem ga gledalec odpira. */
package si.turnirko.dto;

public record KoledarPovezavaDto(String zeton) {}
