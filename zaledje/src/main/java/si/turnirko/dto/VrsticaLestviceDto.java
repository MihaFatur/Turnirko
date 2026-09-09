/* Ena vrstica lestvice (krozni sistem ali skupina): povzetek uspeha
   enega udelezenca.

   Pozor: niziZa/niziProti sta izkupicek v CELI skupini in ob izenacenju NE
   pojasnita vrstnega reda - o tem odloca samo izkupicek med izenacenimi
   (glej RazvrstitevStoritev). Zato je tu se `krog`: kdor je bil v krogu, ki
   ga je razsodil medsebojni izkupicek, dobi njegovo zaporedno stevilko
   (clani istega kroga isto), sicer je prazen. Prikaz s tem oznaci mesta,
   kjer skupna razlika nizov namenoma ne sledi vrstnemu redu. */
package si.turnirko.dto;

public record VrsticaLestviceDto(
        Long idPrijave,
        Long idIgralca,
        String polnoIme,
        String klub,
        int odigrane,
        int zmage,
        int porazi,
        int niziZa,
        int niziProti,
        Integer mesto,
        Integer krog
) {}
