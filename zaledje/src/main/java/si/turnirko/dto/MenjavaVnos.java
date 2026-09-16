/* Menjava igralcev v eni se neodigrani tekmi srecanja: kdo jo bo res igral.
   Pri posamicni tekmi sta izpolnjena idDomaci in idGost, pri dvojicah vsi
   stirje. Streznik preveri, da je vsak igralec v kadru svoje ekipe. */
package si.turnirko.dto;

public record MenjavaVnos(
        Long idDomaci,
        Long idDomaci2,
        Long idGost,
        Long idGost2
) {}
