/* Javna profilna slika igralca (V56): samo, kar sme videti gost - id igralca in
   naslov slike (/api/v1/slike/profil/{zgostitev}). Igralec, ki je tu ni, slike
   nima ali je ne kaže (brez Premium, čaka na admina). Namenoma NI polje v
   IgralecJavniDto: tam je zgornja meja javnega (ime, priimek, klub ...), slika pa
   je izjema, ki jo je igralec dal sam in jo lahko kadarkoli umakne. */
package si.turnirko.dto;

public record SlikaIgralcaDto(Long idIgralec, String zgostitev) {}
